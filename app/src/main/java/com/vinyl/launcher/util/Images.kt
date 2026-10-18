package com.vinyl.launcher.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory

/** Pembungkus tipis BitmapFactory: selalu diperkecil dulu agar gambar besar tidak membuat OOM. */
object Images {
    /** Batas ukuran berkas gambar yang mau diunduh/dibaca dari sumber luar (byte). */
    const val MAX_BYTES = 12 * 1024 * 1024

    /**
     * Decode [bytes] dengan sisi terpanjang sedikitnya [target] piksel (diperkecil hanya dengan
     * faktor pangkat 2). Null bila bukan gambar yang valid.
     */
    fun decodeSampled(bytes: ByteArray, target: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val opts = BitmapFactory.Options().apply { inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, target) }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    }
}
