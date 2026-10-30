package com.vinyl.launcher.ui.room.stage

import com.vinyl.launcher.data.PosterSettings
import com.vinyl.launcher.data.PosterSource

/** Apa yang tampak di dalam poster (murni, teruji). */
enum class PosterShown { PHOTO, ALBUM, EMPTY }

object PosterChoice {
    /**
     * Foto pilihan sendiri tampil bila sumbernya foto dan berkasnya berhasil dibaca; kalau tidak jatuh ke cover album bila ada,
     * dan bila tidak ada keduanya poster kosong (kertas polos dengan tanda tambah, agar tetap tampak bisa diketuk).
     */
    fun shown(p: PosterSettings, photoLoaded: Boolean, hasArt: Boolean): PosterShown = when {
        p.source == PosterSource.PHOTO && p.photoPath != null && photoLoaded -> PosterShown.PHOTO
        hasArt -> PosterShown.ALBUM
        else -> PosterShown.EMPTY
    }

    /** Sisi yang dipotong supaya gambar memenuhi bingkai tanpa melar (center-crop): pecahan 0..1 dari lebar dan tinggi sumber. */
    fun cropFraction(srcW: Int, srcH: Int, dstW: Float, dstH: Float): Pair<Float, Float> {
        if (srcW <= 0 || srcH <= 0 || dstW <= 0f || dstH <= 0f) return 1f to 1f
        val srcAspect = srcW.toFloat() / srcH
        val dstAspect = dstW / dstH
        return if (srcAspect > dstAspect) (dstAspect / srcAspect) to 1f else 1f to (srcAspect / dstAspect)
    }

    /**
     * Putaran searah jarum jam (derajat) yang dibutuhkan agar foto tegak menurut tag EXIF orientasi
     * (nilai `ExifInterface.ORIENTATION_*`: 3 = 180°, 6 = 90°, 8 = 270°). Nilai lain (normal, tak dikenal, cermin) = 0.
     */
    fun exifRotation(orientation: Int): Int = when (orientation) {
        3 -> 180
        6 -> 90
        8 -> 270
        else -> 0
    }
}
