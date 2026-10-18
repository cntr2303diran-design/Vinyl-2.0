package com.vinyl.launcher.music

import kotlin.math.roundToInt

/** Keadaan sleep timer pada suatu saat. */
sealed interface SleepPhase {
    /** Timer tidak berjalan. */
    object Idle : SleepPhase
    /** Menghitung mundur; volume belum disentuh. */
    class Counting(val remainingMs: Long) : SleepPhase
    /** Volume memudar; [volumeFraction] 1 → 0. */
    class Fading(val volumeFraction: Float, val remainingMs: Long) : SleepPhase
    /** Waktu habis: saatnya menjeda lalu memulihkan volume. */
    object Finished : SleepPhase
}

/**
 * Perencana sleep timer — murni (waktu dan volume dilewatkan sebagai parameter) agar bisa diuji tanpa Android.
 * Pelaksananya (media/SleepTimer) hanya mengukur waktu dengan jam monoton, memanggil fungsi di sini, lalu
 * mengatur volume dan menjeda sesi media.
 */
object SleepTimerPlan {
    const val MIN_MINUTES = 1
    const val MAX_MINUTES = 240
    val PRESET_MINUTES: List<Int> = listOf(5, 10, 15, 30, 45, 60, 90)
    const val MAX_FADE_MS = 30_000L
    const val TICK_MS = 1_000L

    /** Sisa lagu minimal agar opsi "sampai lagu selesai" masuk akal. */
    const val MIN_END_OF_TRACK_MS = 5_000L
    /** Selesai sedikit sebelum lagu berakhir agar lagu berikutnya tidak sempat terdengar. */
    const val END_GUARD_MS = 1_500L

    fun clampMinutes(minutes: Int): Int = minutes.coerceIn(MIN_MINUTES, MAX_MINUTES)

    fun durationMs(minutes: Int): Long = clampMinutes(minutes) * 60_000L

    /**
     * Durasi sampai akhir lagu (dikurangi [END_GUARD_MS]), atau null bila durasi lagu tidak diketahui / sisanya
     * terlalu pendek. Dihitung sekali saat dipilih; menggeser posisi lagu setelahnya tidak mengubah timer.
     */
    fun endOfTrackMs(trackDurationMs: Long, positionMs: Long): Long? {
        if (trackDurationMs <= 0) return null
        val remaining = trackDurationMs - positionMs.coerceIn(0, trackDurationMs) - END_GUARD_MS
        return remaining.takeIf { it >= MIN_END_OF_TRACK_MS }
    }

    /** Lama pemudaran: hingga 30 dtk, tetapi tidak lebih dari sepertiga timer supaya timer pendek tetap punya hitung mundur. */
    fun fadeMsFor(durationMs: Long): Long = minOf(MAX_FADE_MS, durationMs / 3).coerceAtLeast(0)

    fun phaseAt(startMs: Long, durationMs: Long, nowMs: Long): SleepPhase {
        if (durationMs <= 0) return SleepPhase.Finished
        val elapsed = (nowMs - startMs).coerceAtLeast(0)   // jam mundur tidak boleh memperpanjang/membalik timer
        if (elapsed >= durationMs) return SleepPhase.Finished
        val remaining = durationMs - elapsed
        val fade = fadeMsFor(durationMs)
        return if (elapsed < durationMs - fade) SleepPhase.Counting(remaining)
        else SleepPhase.Fading(remaining.toFloat() / fade, remaining)   // fade > 0 di cabang ini
    }

    /** Pengali volume untuk [fraction] waktu tersisa (1 → 0). Kuadratik ≈ persepsi kenyaringan, bukan garis lurus. */
    fun volumeFactor(fraction: Float): Float = fraction.coerceIn(0f, 1f).let { it * it }

    /**
     * Indeks volume (0..[original]) untuk [fraction]. Tidak pernah melebihi volume awal pengguna karena
     * [volumeFactor] dijepit <= 1; batas bawah 0 menangani [original] negatif.
     */
    fun volumeIndex(original: Int, fraction: Float): Int =
        (original * volumeFactor(fraction)).roundToInt().coerceAtLeast(0)

    /** Format "mm:ss" atau "h:mm:ss" untuk sisa waktu. */
    fun format(ms: Long): String {
        val total = (ms.coerceAtLeast(0) + 999) / 1000      // dibulatkan ke atas: tidak pernah menampilkan 0:00 selagi masih berjalan
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }
}

/**
 * Pemulihan volume. Saat timer/pergantian lembut mengubah volume, volume asli dan volume terakhir yang kita atur
 * dicatat. Bila proses mati di tengah jalan, volume bisa tertinggal pelan — saat aplikasi hidup lagi, fungsi ini
 * memutuskan apakah aman mengembalikannya.
 */
object VolumeGuardPlan {
    /**
     * Indeks yang harus dipulihkan, atau null = jangan sentuh. Dipulihkan HANYA bila volume sekarang masih persis
     * seperti yang terakhir kita atur (pengguna tidak menyentuhnya) dan memang lebih rendah dari aslinya.
     */
    fun restoreTarget(original: Int, lastSet: Int, current: Int): Int? =
        if (current == lastSet && original > current) original else null

    /** True bila volume berubah di luar kendali kita (pengguna menekan tombol volume). */
    fun userOverrode(lastSet: Int, current: Int): Boolean = current != lastSet
}

/**
 * "Pergantian lembut": volume turun cepat, lagu diganti, lalu naik pelan. Ini BUKAN crossfade dua lagu — launcher
 * tidak memutar audio, jadi tidak bisa menumpuk dua lagu. Efeknya menghaluskan jeda antar lagu.
 */
object SoftSkipPlan {
    const val DOWN_MS = 200L
    const val UP_MS = 450L
    const val STEP_MS = 40L
    /** Volume dengan kurang dari ini langkah tidak bisa dipudarkan secara berarti. */
    const val MIN_STEPS = 3

    fun canFade(originalIndex: Int): Boolean = originalIndex >= MIN_STEPS

    /** Indeks volume pada [tMs] di fase turun (0 → [DOWN_MS]): [original] di awal, 0 di akhir. */
    fun downIndex(original: Int, tMs: Long): Int {
        val f = 1f - (tMs.toFloat() / DOWN_MS).coerceIn(0f, 1f)
        return SleepTimerPlan.volumeIndex(original, f)
    }

    /** Indeks volume pada [tMs] di fase naik (0 → [UP_MS]): 0 di awal, [original] di akhir (ease-out). */
    fun upIndex(original: Int, tMs: Long): Int {
        val p = (tMs.toFloat() / UP_MS).coerceIn(0f, 1f)
        val eased = 1f - (1f - p) * (1f - p)
        return (original * eased).roundToInt().coerceIn(0, original.coerceAtLeast(0))
    }
}
