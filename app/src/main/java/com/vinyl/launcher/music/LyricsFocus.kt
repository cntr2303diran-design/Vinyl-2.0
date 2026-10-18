package com.vinyl.launcher.music

import com.vinyl.launcher.ui.theme.Tokens
import kotlin.math.abs

/** Tampilan satu baris lirik pada jarak tertentu dari baris yang sedang dinyanyikan. */
data class LineLook(
    /** 0..1, pengali opasitas teks putih. */
    val alpha: Float,
    /** Skala teks (1 = ukuran penuh). */
    val scale: Float,
    /** Radius kabur dalam dp; 0 = tajam. */
    val blurDp: Float,
)

/**
 * Hierarki visual lirik layar penuh ala Apple Music: baris yang sedang dinyanyikan besar dan terang, baris lain
 * mengecil, dan baris yang jauh sedikit mengabur. Bobot huruf sama di semua baris supaya teks tidak berpindah bungkus
 * saat baris aktif berganti.
 *
 * Kontras: baris yang diredupkan TIDAK boleh lebih redup dari [INACTIVE_ALPHA], dan latar layar lirik dihitung agar
 * teks seredup itu pun tetap >= 4,5:1 (lihat [INACTIVE_TEXT_ARGB] dipakai sebagai salah satu teks acuan BackdropDim).
 * Hierarki dibangun dari ukuran, ketebalan, dan kabur, bukan dari menghilangkan teks.
 */
object LyricsFocus {
    const val ACTIVE_ALPHA = 1f
    const val INACTIVE_ALPHA = 0.64f
    const val MAX_BLUR_DP = 3f
    private const val NEAR_SCALE = 0.94f
    private const val FAR_SCALE = 0.88f
    private const val BLUR_PER_STEP = 1f

    /** Putih beralpha [INACTIVE_ALPHA] sebagai ARGB: teks terredup yang pernah muncul di layar lirik. */
    val INACTIVE_TEXT_ARGB: Long = (Math.round(INACTIVE_ALPHA * 255f).toLong() shl 24) or 0xFFFFFFL

    /** Teks acuan untuk menghitung peredam latar layar lirik: teks aktif (primer) dan teks tak aktif. */
    val BACKDROP_TEXTS: List<Long> = listOf(Tokens.Argb.TEXT_PRIMARY, INACTIVE_TEXT_ARGB)

    /** Jarak (jumlah baris) dari baris aktif; [active] = -1 sebelum baris pertama. */
    fun distance(index: Int, active: Int): Int = abs(index - active)

    /**
     * @param blurEnabled false di Android < 12 (tanpa Modifier.blur) atau saat pengguna mematikannya.
     */
    fun look(distance: Int, blurEnabled: Boolean): LineLook {
        val d = distance.coerceAtLeast(0)
        if (d == 0) return LineLook(ACTIVE_ALPHA, 1f, 0f)
        val scale = if (d == 1) NEAR_SCALE else FAR_SCALE
        val blur = if (blurEnabled && d >= 2) ((d - 1) * BLUR_PER_STEP).coerceAtMost(MAX_BLUR_DP) else 0f
        return LineLook(INACTIVE_ALPHA, scale, blur)
    }
}

/**
 * Kapan daftar lirik boleh menggulung otomatis mengikuti lagu. Begitu pengguna menggulung sendiri, ikuti dijeda
 * sebentar agar daftar tidak "merebut" jarinya, lalu kembali ke baris aktif.
 */
object AutoFollow {
    const val GRACE_MS = 3500L

    /** @param lastUserScrollMs waktu gulir manual terakhir (ms, jam monoton); null = belum pernah. */
    fun shouldFollow(lastUserScrollMs: Long?, nowMs: Long, userScrolling: Boolean): Boolean {
        if (userScrolling) return false
        val last = lastUserScrollMs ?: return true
        return nowMs - last >= GRACE_MS
    }
}
