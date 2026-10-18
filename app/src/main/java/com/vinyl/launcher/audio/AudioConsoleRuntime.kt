package com.vinyl.launcher.audio

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.audiofx.AudioEffect
import androidx.core.content.ContextCompat
import com.vinyl.launcher.data.AudioConsoleRules
import com.vinyl.launcher.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Bagian Audio Console yang hidup selama proses launcher hidup (bukan layanan latar baru):
 *  - menerima siaran sesi efek dari aplikasi musik dan meneruskannya ke [EqEngine];
 *  - menerapkan pengaturan EQ terbaru ke [EqEngine];
 *  - menjalankan aturan otomasi saat keluaran audio berganti, dan mencatat hasil terakhirnya di [log].
 * Pemantau perangkat audio hanya didaftarkan selama ada aturan yang membutuhkannya.
 */
object AudioConsoleRuntime {
    private const val MAX_LOG = 8

    private val _log = MutableStateFlow<List<Automation.Result>>(emptyList())
    val log: StateFlow<List<Automation.Result>> = _log.asStateFlow()

    /** True bila aturan "USB DAC tersambung → buka console" terpicu dan belum ditampilkan beranda. */
    private val _consoleRequested = MutableStateFlow(false)
    val consoleRequested: StateFlow<Boolean> = _consoleRequested.asStateFlow()
    fun consumeConsoleRequest() { _consoleRequested.value = false }

    private var started = false
    private var monitoring = false
    private var lastActive: OutputDevice? = null
    private var previousPreset: String? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val id = intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, -1)
            val pkg = intent.getStringExtra(AudioEffect.EXTRA_PACKAGE_NAME).orEmpty()
            when (intent.action) {
                AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION -> if (id > 0) EqEngine.open(id, pkg, SessionOrigin.APP)
                AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION -> if (id > 0) EqEngine.close(id)
            }
        }
    }

    /** Dipanggil sekali dari Application.onCreate di main thread. */
    fun start(app: Application, scope: CoroutineScope) {
        if (started) return
        started = true
        // Siaran ini dikirim aplikasi musik (aplikasi lain), jadi penerima harus diekspor. Isinya hanya nomor sesi & nama
        // paket; nomor sesi divalidasi dan jumlah sesi dibatasi (EqSessions).
        val filter = IntentFilter().apply {
            addAction(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION)
            addAction(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION)
        }
        ContextCompat.registerReceiver(app, receiver, filter, ContextCompat.RECEIVER_EXPORTED)

        val settings = SettingsRepository.get(app).settings
        scope.launch { settings.map { it.audio }.distinctUntilChanged().collect { EqEngine.update(it) } }
        scope.launch {
            settings.map { it.audio.profiles.isNotEmpty() || it.audio.openConsoleOnUsb }.distinctUntilChanged().collect { need ->
                if (need && !monitoring) { AudioMonitor.acquire(app); monitoring = true }
                if (!need && monitoring) { AudioMonitor.release(); monitoring = false }
            }
        }
        scope.launch {
            AudioMonitor.state.map { it.active.device }
                .distinctUntilChanged { a, b -> a?.profileKey == b?.profileKey }
                .collect { device -> onActiveChanged(app, device) }
        }
    }

    private suspend fun onActiveChanged(app: Application, after: OutputDevice?) {
        val before = lastActive
        lastActive = after
        val repo = SettingsRepository.get(app)
        val audio = repo.settings.value.audio
        val decision = Automation.onOutputChanged(
            before, after, AudioConsoleRules.profileMap(audio), audio.preset, previousPreset, audio.restoreOnDisconnect,
        )
        previousPreset = decision.previous
        decision.apply?.let { name ->
            var applied = false
            repo.update { s ->
                val next = AudioConsoleRules.applyPreset(s.audio, name)
                applied = next.preset == name
                s.copy(audio = next)
            }
            record(
                rule = if (after != null && AudioConsoleRules.profileMap(audio)[after.profileKey] == name) "Profil ${after.name}" else "Perangkat terputus",
                outcome = when {
                    !applied -> Automation.Outcome.FAILED
                    !audio.eqEnabled -> Automation.Outcome.SKIPPED
                    else -> Automation.Outcome.DONE
                },
                detail = when {
                    !applied -> "Preset \"$name\" tidak ditemukan"
                    !audio.eqEnabled -> "Preset \"$name\" dipilih, tetapi EQ sedang mati"
                    else -> decision.reason + ": \"$name\""
                },
            )
        }
        if (audio.openConsoleOnUsb && Automation.isUsbDac(after) && !Automation.isUsbDac(before)) {
            _consoleRequested.value = true
            record("USB DAC tersambung", Automation.Outcome.DONE, "Audio Console dibuka saat beranda tampil")
        }
    }

    private fun record(rule: String, outcome: Automation.Outcome, detail: String) {
        _log.update { (listOf(Automation.Result(rule, outcome, detail, System.currentTimeMillis())) + it).take(MAX_LOG) }
    }
}
