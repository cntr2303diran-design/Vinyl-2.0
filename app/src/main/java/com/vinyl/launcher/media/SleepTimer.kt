package com.vinyl.launcher.media

import android.content.Context
import android.os.SystemClock
import com.vinyl.launcher.music.SleepPhase
import com.vinyl.launcher.music.SleepTimerPlan
import com.vinyl.launcher.music.VolumeGuardPlan
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Sleep timer: menghitung mundur, memudarkan volume di akhir, menjeda sesi musik, lalu MEMULIHKAN volume asli
 * pengguna agar pemutaran berikutnya tidak senyap. Seluruh keputusan waktu/volume ada di [SleepTimerPlan] dan
 * [VolumeGuardPlan] (murni, teruji); objek ini hanya pelaksananya.
 *
 * Jaminan: volume tidak pernah dibiarkan rendah. Bila timer dibatalkan di tengah pemudaran → dipulihkan seketika;
 * bila proses mati di tengah pemudaran → dipulihkan saat aplikasi hidup lagi ([init]); bila pengguna menekan
 * tombol volume selama pemudaran → timer berhenti menyentuh volume dan membiarkan pilihan pengguna.
 *
 * Batas: timer hidup di proses launcher. Bila sistem mematikan proses (jarang untuk aplikasi Home yang sedang
 * memutar musik), timer ikut hilang, tetapi volume tetap dipulihkan.
 */
object SleepTimer {
    sealed interface State {
        object Idle : State
        /** [phase] adalah [SleepPhase.Counting] atau [SleepPhase.Fading]. */
        class Running(val phase: SleepPhase, val totalMs: Long, val endOfTrack: Boolean) : State
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _slept = MutableStateFlow(false)
    /** True setelah timer selesai dan menjeda musik, sampai musik diputar lagi ([clearSlept]); TV menampilkan "mati". */
    val slept: StateFlow<Boolean> = _slept.asStateFlow()

    fun clearSlept() { _slept.value = false }

    @Volatile private var job: Job? = null
    @Volatile private var appContext: Context? = null

    /** True selama volume sedang dipudarkan (pergantian lembut tidak boleh ikut mengatur volume). */
    val isFading: Boolean
        get() = (_state.value as? State.Running)?.phase is SleepPhase.Fading

    /** Panggil sekali saat proses dimulai: menyiapkan pengatur volume dan memulihkan volume yang tertinggal. */
    fun init(context: Context) {
        appContext = context.applicationContext
        VolumeController.init(context)
        scope.launch { restoreLeftoverVolume() }
    }

    fun start(minutes: Int) = launchTimer(SleepTimerPlan.durationMs(minutes), endOfTrack = false)

    /** Mulai timer yang berakhir saat lagu ini selesai. False bila durasi lagu tidak diketahui / sisanya terlalu pendek. */
    fun startAtEndOfTrack(np: NowPlaying): Boolean {
        val ms = SleepTimerPlan.endOfTrackMs(np.durationMs, np.positionAt()) ?: return false
        launchTimer(ms, endOfTrack = true)
        return true
    }

    /** Batalkan timer; volume yang sedang dipudarkan dipulihkan oleh blok finally di [run]. */
    fun cancel() {
        job?.cancel()
    }

    private fun launchTimer(durationMs: Long, endOfTrack: Boolean) {
        _slept.value = false
        val previous = job
        job = scope.launch {
            previous?.cancelAndJoin()    // pemulihan volume timer lama selesai dulu sebelum yang baru membaca volume
            run(SystemClock.elapsedRealtime(), durationMs, endOfTrack)
        }
    }

    private suspend fun run(startMs: Long, durationMs: Long, endOfTrack: Boolean) {
        val canFade = VolumeController.canControl
        var original = -1       // < 0 = volume belum disentuh
        var lastSet = -1
        MediaRepository.log("Sleep timer dimulai: ${SleepTimerPlan.format(durationMs)}${if (endOfTrack) " (sampai lagu selesai)" else ""}")
        try {
            while (true) {
                val phase = SleepTimerPlan.phaseAt(startMs, durationMs, SystemClock.elapsedRealtime())
                if (phase is SleepPhase.Finished) break
                if (phase is SleepPhase.Fading && canFade) {
                    if (original < 0) {
                        // Jangan membaca volume asli di tengah pergantian lembut (volumenya sedang turun sementara)
                        var waited = 0L
                        while (SoftSkip.active && waited < 2_000) { delay(100); waited += 100 }
                        original = VolumeController.current()
                        lastSet = original
                        if (original > 0) VolumeController.guard.save(original, original)
                    } else if (VolumeGuardPlan.userOverrode(lastSet, VolumeController.current())) {
                        MediaRepository.log("Sleep timer berhenti: volume diubah pengguna")
                        return      // finally: tidak memulihkan (restoreTarget = null), pilihan pengguna dihormati
                    }
                    if (original > 0) {
                        val idx = SleepTimerPlan.volumeIndex(original, phase.volumeFraction)
                        if (idx != lastSet) {
                            VolumeController.set(idx)
                            lastSet = idx
                            VolumeController.guard.updateLastSet(idx)
                        }
                    }
                }
                _state.value = State.Running(phase, durationMs, endOfTrack)
                delay(SleepTimerPlan.TICK_MS)
            }
            // Waktu habis: jeda, tunggu sesi benar-benar berhenti, baru (di finally) volume dipulihkan
            MediaRepository.log("Sleep timer selesai → jeda")
            withContext(Dispatchers.Main) { MediaRepository.pause("Sleep timer") }
            _slept.value = true
            withTimeoutOrNull(2_000) { MediaRepository.state.first { !it.isPlaying } }
            delay(300)
        } finally {
            withContext(NonCancellable) {
                if (original > 0) {
                    val target = VolumeGuardPlan.restoreTarget(original, lastSet, VolumeController.current())
                    if (target != null) VolumeController.set(target)
                    VolumeController.guard.clear()
                }
                _state.value = State.Idle
            }
        }
    }

    /** Proses mati saat volume dipudarkan: pulihkan hanya bila volume masih persis di tempat kita meninggalkannya. */
    private fun restoreLeftoverVolume() {
        val guard = runCatching { VolumeController.guard }.getOrNull() ?: return
        val left = guard.read() ?: return
        val target = VolumeGuardPlan.restoreTarget(left.original, left.lastSet, VolumeController.current())
        if (target != null) {
            VolumeController.set(target)
            MediaRepository.log("Volume dipulihkan ke $target setelah aplikasi dimulai ulang")
        }
        guard.clear()
    }
}
