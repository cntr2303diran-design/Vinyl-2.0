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
 * Memasang EQ (DynamicsProcessing: pre-EQ 3 pita + penguatan masukan per kanal) ke sesi audio aplikasi lain.
 * Launcher tidak memutar musik sendiri, jadi efek hanya bisa dipasang ke sesi yang:
 *  1. dibuka aplikasi musik lewat siaran standar [AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION] ([SessionOrigin.APP]);
 *  2. dideteksi lewat Akses lanjutan ([SessionOrigin.DETECTED]);
 *  3. sesi 0 = keluaran gabungan ([SessionOrigin.GLOBAL]) — sudah usang di Android dan tidak dijamin; eksperimental.
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

    /** Hanya disentuh di thread "vinyl-eq". */
    private val effects = HashMap<Int, DynamicsProcessing>()
    private var config = AudioConsoleSettings()

    fun open(id: Int, packageName: String, origin: SessionOrigin) = scope.launch {
        _sessions.update { EqSessions.open(it, id, packageName, origin) }
        sync()
    }

    fun close(id: Int) = scope.launch {
        if (id == 0) return@launch // sesi 0 hanya diatur oleh sakelar eksperimental, bukan oleh siaran aplikasi lain
        release(id)
        _sessions.update { EqSessions.close(it, id) }
    }

    /** Terapkan pengaturan terbaru (dipanggil setiap pengaturan Audio Console berubah). */
    fun update(settings: AudioConsoleSettings) = scope.launch {
        config = settings
        if (settings.eqEnabled && settings.globalExperimental) {
            _sessions.update { EqSessions.open(it, 0, "", SessionOrigin.GLOBAL) }
        } else {
            release(0)
            _sessions.update { EqSessions.close(it, 0) }
        }
        sync()
    }

    private fun sync() {
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
            val dp = effects[session.id] ?: create(session.id) ?: continue
            val ok = runCatching { applyValues(dp, s) }.isSuccess
            val state = when {
                !ok -> SessionState.FAILED
                runCatching { dp.hasControl() }.getOrDefault(false) -> SessionState.ACTIVE
                else -> SessionState.NO_CONTROL
            }
            _sessions.update { EqSessions.withState(it, session.id, state, if (ok) "" else "nilai ditolak") }
        }
    }

    private fun create(id: Int): DynamicsProcessing? {
        return try {
            val cfg = DynamicsProcessing.Config.Builder(
                DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                /* channelCount = */ 2,
                /* preEqInUse = */ true, /* preEqBandCount = */ Eq.CUTOFFS.size,
                /* mbcInUse = */ false, 0,
                /* postEqInUse = */ false, 0,
                /* limiterInUse = */ false,
            ).build()
            val dp = DynamicsProcessing(PRIORITY, id, cfg)
            dp.setControlStatusListener { _, granted ->
                scope.launch {
                    if (effects[id] === dp) _sessions.update { EqSessions.withState(it, id, if (granted) SessionState.ACTIVE else SessionState.NO_CONTROL) }
                }
            }
            val status = dp.setEnabled(true)
            if (status != AudioEffect.SUCCESS) {
                dp.release()
                _sessions.update { EqSessions.withState(it, id, SessionState.FAILED, "kode $status") }
                return null
            }
            effects[id] = dp
            dp
        } catch (e: Exception) {
            // Hanya jenis galat yang dicatat; nama paket aplikasi lain tidak ditulis ke log
            Log.w(TAG, "Efek tidak bisa dipasang: ${e.javaClass.simpleName}")
            _sessions.update { EqSessions.withState(it, id, SessionState.FAILED, failure(e)) }
            null
        }
    }

    private fun failure(e: Exception): String = when (e) {
        is UnsupportedOperationException -> "perangkat tidak menyediakan efek ini"
        is IllegalArgumentException -> "sesi tidak dikenal sistem"
        else -> "ditolak sistem"
    }

    private fun applyValues(dp: DynamicsProcessing, s: AudioConsoleSettings) {
        val bands = Eq.bands(s.values)
        for (i in bands.indices) {
            dp.setPreEqBandAllChannelsTo(i, DynamicsProcessing.EqBand(true, Eq.CUTOFFS[i], bands[i]))
        }
        val (left, right) = Eq.inputGains(s.values, s.safeHeadroom)
        dp.setInputGainbyChannel(0, left)
        dp.setInputGainbyChannel(1, right)
    }

    private fun release(id: Int) {
        effects.remove(id)?.let { dp ->
            runCatching { dp.setControlStatusListener(null) }
            runCatching { dp.release() }
        }
    }
}
