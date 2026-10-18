package com.vinyl.launcher.media

import com.vinyl.launcher.music.SoftSkipPlan
import com.vinyl.launcher.music.VolumeGuardPlan
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * "Pergantian lembut": volume turun cepat, perintah ganti lagu dikirim, lalu volume naik pelan ke nilai semula.
 * Ini BUKAN crossfade dua lagu — launcher tidak memutar audio, jadi tidak mungkin menumpuk dua lagu.
 *
 * Jaminan: (1) perintah ganti lagu SELALU dikirim tepat sekali, apa pun yang terjadi pada pemudaran;
 * (2) volume dikembalikan ke nilai pengguna, kecuali pengguna sendiri mengubahnya di tengah jalan;
 * (3) tidak berjalan bersamaan dengan sleep timer yang sedang memudarkan volume.
 */
internal object SoftSkip {
    private const val SETTLE_MS = 250L
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** True selama pemudaran berlangsung; sleep timer menunggu ini selesai sebelum membaca volume asli. */
    @Volatile var active = false
        private set

    /** Jalankan [action] (kirim perintah ganti lagu) dengan volume memudar di sekelilingnya. Panggil dari main thread. */
    fun run(action: () -> Unit) {
        val original = VolumeController.current()
        if (active || SleepTimer.isFading || !VolumeController.canControl || !SoftSkipPlan.canFade(original)) {
            action()      // ketukan beruntun / timer sedang memudar / volume tak bisa diatur → ganti lagu biasa
            return
        }
        active = true
        scope.launch { ramp(original, action) }
    }

    private suspend fun ramp(original: Int, action: () -> Unit) {
        var lastSet = original
        var ran = false
        var overridden = false
        val guard = VolumeController.guard

        /** Atur volume bila pengguna belum menyentuhnya; kembalikan false bila ia menyentuhnya. */
        fun apply(index: Int): Boolean {
            if (VolumeGuardPlan.userOverrode(lastSet, VolumeController.current())) { overridden = true; return false }
            if (index != lastSet) {
                VolumeController.set(index)
                lastSet = index
                guard.updateLastSet(index)
            }
            return true
        }

        try {
            guard.save(original, original)
            var t = 0L
            while (t < SoftSkipPlan.DOWN_MS && apply(SoftSkipPlan.downIndex(original, t))) {
                delay(SoftSkipPlan.STEP_MS); t += SoftSkipPlan.STEP_MS
            }
            if (!overridden) apply(0)
            // ran diset SETELAH aksi berjalan, di blok yang sama: pembatalan di antaranya tidak bisa menghilangkan perintah
            withContext(Dispatchers.Main) { action(); ran = true }
            if (!overridden) {
                delay(SETTLE_MS)
                t = 0L
                while (t < SoftSkipPlan.UP_MS && apply(SoftSkipPlan.upIndex(original, t))) {
                    delay(SoftSkipPlan.STEP_MS); t += SoftSkipPlan.STEP_MS
                }
            }
        } finally {
            withContext(NonCancellable) {
                if (!ran) withContext(Dispatchers.Main) { action() }   // jaminan (1): perintah tetap terkirim
                val target = VolumeGuardPlan.restoreTarget(original, lastSet, VolumeController.current())
                if (target != null) VolumeController.set(target)
                guard.clear()
                active = false
            }
        }
    }
}
