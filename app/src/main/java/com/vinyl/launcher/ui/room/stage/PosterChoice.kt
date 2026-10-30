package com.vinyl.launcher.ui.room.stage

import com.vinyl.launcher.data.PosterPhotoRules
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

    /** Putaran (derajat) agar foto tegak menurut tag EXIF; aturannya di [PosterPhotoRules.exifRotation]. */
    fun exifRotation(orientation: Int): Int = PosterPhotoRules.exifRotation(orientation)
}
