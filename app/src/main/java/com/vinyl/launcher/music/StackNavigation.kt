package com.vinyl.launcher.music

import kotlin.math.roundToInt

/**
 * Aritmetika gestur rak Tumpukan, dipisah dari UI supaya tanda arah dan batasnya bisa diuji.
 *
 * `position` adalah posisi pecahan: 2,0 = sampul ke-3 tepat di depan; 2,4 = sedang bergeser ke sampul ke-4.
 * Menggeser jari KE ATAS (delta/velocity negatif) memajukan tumpukan (posisi naik).
 */
object StackNavigation {
    /** Jari bergerak sejauh ini (dalam tinggi sampul) = tumpukan maju satu sampul. */
    const val DRAG_PER_COVER = 0.6f

    /** Seberapa jauh lemparan melewati sampul: sampul per (tinggi sampul per detik) kecepatan jari. */
    const val FLICK_GAIN = 0.12f

    /** Satu lemparan tidak boleh melewati lebih dari ini, supaya antrean ratusan lagu tidak terlewati tanpa sengaja. */
    const val MAX_FLICK_COVERS = 4f

    private fun last(count: Int) = (count - 1).coerceAtLeast(0)

    /** Posisi setelah jari bergeser [deltaPx] (negatif = ke atas). Selalu dalam 0..indeksTerakhir. */
    fun dragTo(position: Float, deltaPx: Float, coverPx: Float, count: Int): Float {
        val max = last(count).toFloat()
        if (!(coverPx > 0f) || !deltaPx.isFinite()) return position.coerceIn(0f, max)
        return (position - deltaPx / (coverPx * DRAG_PER_COVER)).coerceIn(0f, max)
    }

    /** Sampul yang dituju saat jari dilepas dengan kecepatan [velocityPxPerSec]; gerak pelan mendarat di sampul terdekat. */
    fun settleTarget(position: Float, velocityPxPerSec: Float, coverPx: Float, count: Int): Int {
        val max = last(count)
        if (!(coverPx > 0f) || !velocityPxPerSec.isFinite()) return position.roundToInt().coerceIn(0, max)
        val flick = (-velocityPxPerSec / coverPx * FLICK_GAIN).coerceIn(-MAX_FLICK_COVERS, MAX_FLICK_COVERS)
        return (position + flick).roundToInt().coerceIn(0, max)
    }

    /** Sampul yang dianggap sedang di depan pada [position]. */
    fun focusOf(position: Float, count: Int): Int = position.roundToInt().coerceIn(0, last(count))
}

/** Aturan daftar antrean aplikasi musik. */
object QueuePlan {
    /** `MediaSession.QueueItem.UNKNOWN_ID`: aplikasi yang tidak memberi id unik memberi semua item -1. */
    const val UNKNOWN_ID = -1L

    /** Hanya item ber-id sah yang bisa dilompati dengan skipToQueueItem. */
    fun isPlayable(id: Long): Boolean = id >= 0

    /** Indeks yang ditampilkan paling atas: satu lagu sebelum yang sedang diputar; null bila tidak ada yang sedang diputar. */
    fun scrollTarget(currentIndex: Int, size: Int): Int? =
        if (currentIndex in 0 until size) (currentIndex - 1).coerceAtLeast(0) else null
}
