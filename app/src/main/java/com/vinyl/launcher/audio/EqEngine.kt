package com.vinyl.launcher.audio

import android.media.audiofx.AudioEffect
import android.media.audiofx.DynamicsProcessing
import android.util.Log
import com.vinyl.launcher.data.AudioConsoleSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

/**
 * Memasang EQ (DynamicsProcessing: pre-EQ 31 pita 1/3 oktaf + penguatan masukan per kanal) ke sesi audio aplikasi lain.
 * Bentuk EQ (3 pita, 10 pita grafis, atau profil parametrik/AutoEQ) dipetakan ke 31 pita itu oleh [Eq.bandGains]. Efek
 * dipasang ke sesi yang:
 *  1. dibuka aplikasi musik lewat siaran standar [AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION] ([SessionOrigin.APP]);
 *  2. dideteksi lewat Akses lanjutan ([SessionOrigin.DETECTED]);
 *  3. sesi 0 = keluaran gabungan ([SessionOrigin.GLOBAL]) — sebagai cadangan bila tidak ada sesi aplikasi
 *     ([AudioConsoleSettings.globalFallback]) atau selalu (eksperimental). Cara ini usang di Android dan tidak dijamin.
 * (Pemutar bawaan launcher memakai EQ parametriknya sendiri di dalam pemutar, bukan efek ini.)
 *
 * Semua kerja efek (panggilan binder ke audioserver) berjalan di satu thread khusus, bukan di main thread. Status tiap sesi
 * (aktif / dikendalikan aplikasi lain / gagal) diterbitkan apa adanya di [sessions].
 */
object EqEngine {
    private const val TAG = "VinylEq"
    private const val PRIORITY = 0

    private val dispatcher = Executors.newSingleThreadExecutor { r -> Thread(r, "vinyl-eq").apply { isDaemon = true } }.asCoroutineDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    private val _sessions = MutableStateFlow<List<EqSession>>(emptyList())
    val sessions: StateFlow<List<EqSession>> = _sessions.asStateFlow()

    /** Efek per sesi beserta batas atas pita-pitanya. Hanya disentuh di thread "vinyl-eq". */
    private class Fx(val dp: DynamicsProcessing, val cutoffs: FloatArray)

    private val effects = HashMap<Int, Fx>()
    private var config = AudioConsoleSettings()

    fun open(id: Int, packageName: String, origin: SessionOrigin) = scope.launch {
        _sessions.update { list ->
            val opened = EqSessions.open(list, id, packageName, origin)
            // Sesi yang dibuka lagi oleh aplikasinya boleh dicoba ulang walau sebelumnya ditolak sistem
            val wasFailed = list.any { it.id == id && it.state == SessionState.FAILED }
            if (wasFailed) EqSessions.withState(opened, id, SessionState.PENDING) else opened
        }
        sync()
    }

    fun close(id: Int) = scope.launch {
        if (id == 0) return@launch // sesi 0 hanya diatur pengaturan (cadangan/eksperimental), bukan oleh siaran aplikasi lain
        release(id)
        _sessions.update { EqSessions.close(it, id) }
        // Sesi aplikasi terakhir tertutup → cadangan keluaran gabungan bisa dipasang lagi
        sync()
    }

    /**
     * Sesi yang terdeteksi lewat Akses lanjutan saat ini ([entries] = nomor sesi → paket). Sesi DETECTED yang tidak ada
     * lagi ditutup; sesi dari sumber lain tidak disentuh. [entries] kosong = hapus semua sesi terdeteksi.
     */
    fun setDetected(entries: List<Pair<Int, String>>) = scope.launch {
        val ids = entries.map { it.first }.toSet()
        val gone = _sessions.value.filter { it.origin == SessionOrigin.DETECTED && it.id !in ids }.map { it.id }
        gone.forEach(::release)
        _sessions.update { list ->
            var l = list.filterNot { it.id in gone }
            entries.forEach { (id, pkg) -> l = EqSessions.open(l, id, pkg, SessionOrigin.DETECTED) }
            l
        }
        sync()
    }

    /** Terapkan pengaturan terbaru (dipanggil setiap pengaturan Audio Console berubah). */
    fun update(settings: AudioConsoleSettings) = scope.launch {
        config = settings
        sync()
    }

    /**
     * Sesi 0 dipasang bila diminta selalu (eksperimental), atau sebagai cadangan selama tidak ada sesi aplikasi —
     * dilepas begitu aplikasi musik membuka sesinya sendiri, agar EQ tidak terpasang dua kali.
     */
    private fun syncGlobal() {
        val s = config
        val appSessions = _sessions.value.any { it.origin != SessionOrigin.GLOBAL }
        val want = s.eqEnabled && (s.globalExperimental || (s.globalFallback && !appSessions))
        val has = _sessions.value.any { it.id == 0 }
        if (want && !has) _sessions.update { EqSessions.open(it, 0, "", SessionOrigin.GLOBAL) }
        if (!want && has) {
            release(0)
            _sessions.update { EqSessions.close(it, 0) }
        }
    }

    private fun sync() {
        syncGlobal()
        val s = config
        // Sesi yang sudah ditutup: lepaskan efeknya
        val ids = _sessions.value.map { it.id }.toSet()
        effects.keys.filterNot { it in ids }.forEach(::release)
        if (!s.eqEnabled) {
            effects.keys.toList().forEach(::release)
            _sessions.update { list -> list.map { it.copy(state = SessionState.PENDING, detail = "") } }
            return
        }
        for (session in _sessions.value) {
            // Sesi yang sudah ditolak sistem tidak dicoba ulang di setiap perubahan nilai (mis. saat fader digeser);
            // percobaan ulang terjadi saat EQ dinyalakan ulang atau sesi dibuka lagi.
            if (session.state == SessionState.FAILED && effects[session.id] == null) continue
            val fx = effects[session.id] ?: create(session.id) ?: continue
            val ok = runCatching { applyValues(fx, s) }.isSuccess
            val state = when {
                !ok -> SessionState.FAILED
                runCatching { fx.dp.hasControl() }.getOrDefault(false) -> SessionState.ACTIVE
                else -> SessionState.NO_CONTROL
            }
            _sessions.update { EqSessions.withState(it, session.id, state, if (ok) "" else "nilai ditolak") }
        }
    }

    /** Tata letak pita yang dicoba, dari yang paling halus; perangkat yang menolak 31 pita mendapat 10 lalu 3. */
    private val LAYOUTS = listOf(Eq.DP_CUTOFFS, Eq.GRAPHIC_FREQS.mapIndexed { i, f -> if (i == Eq.GRAPHIC_FREQS.lastIndex) 20_000f else kotlin.math.sqrt(f * Eq.GRAPHIC_FREQS[i + 1]) }.toFloatArray(), Eq.CUTOFFS)

    private fun create(id: Int): Fx? {
        var last: Exception? = null
        for (cutoffs in LAYOUTS) {
            try {
                val cfg = DynamicsProcessing.Config.Builder(
                    DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                    /* channelCount = */ 2,
                    /* preEqInUse = */ true, /* preEqBandCount = */ cutoffs.size,
                    /* mbcInUse = */ false, 0,
                    /* postEqInUse = */ false, 0,
                    /* limiterInUse = */ false,
                ).build()
                val dp = DynamicsProcessing(PRIORITY, id, cfg)
                val fx = Fx(dp, cutoffs)
                dp.setControlStatusListener { _, granted ->
                    scope.launch {
                        if (effects[id] === fx) _sessions.update { EqSessions.withState(it, id, if (granted) SessionState.ACTIVE else SessionState.NO_CONTROL) }
                    }
                }
                val status = dp.setEnabled(true)
                if (status != AudioEffect.SUCCESS) {
                    dp.release()
                    _sessions.update { EqSessions.withState(it, id, SessionState.FAILED, "kode $status") }
                    return null
                }
                effects[id] = fx
                return fx
            } catch (e: UnsupportedOperationException) {
                // Efek tidak ada sama sekali di perangkat ini: tata letak lain tidak akan membantu
                last = e
                break
            } catch (e: Exception) {
                last = e
            }
        }
        val e = last ?: IllegalStateException()
        // Hanya jenis galat yang dicatat; nama paket aplikasi lain tidak ditulis ke log
        Log.w(TAG, "Efek tidak bisa dipasang: ${e.javaClass.simpleName}")
        _sessions.update { EqSessions.withState(it, id, SessionState.FAILED, failure(e)) }
        return null
    }

    private fun failure(e: Exception): String = when (e) {
        is UnsupportedOperationException -> "perangkat tidak menyediakan efek ini"
        is IllegalArgumentException -> "sesi tidak dikenal sistem"
        else -> "ditolak sistem"
    }

    private fun applyValues(fx: Fx, s: AudioConsoleSettings) {
        val gains = Eq.bandGains(s, fx.cutoffs)
        for (i in gains.indices) {
            fx.dp.setPreEqBandAllChannelsTo(i, DynamicsProcessing.EqBand(true, fx.cutoffs[i], gains[i]))
        }
        val (left, right) = Eq.inputGainsFor(s, gains)
        fx.dp.setInputGainbyChannel(0, left)
        fx.dp.setInputGainbyChannel(1, right)
    }

    private fun release(id: Int) {
        effects.remove(id)?.let { fx ->
            runCatching { fx.dp.setControlStatusListener(null) }
            runCatching { fx.dp.release() }
        }
    }
}
