package com.vinyl.launcher.player

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorsFactory

/**
 * Daftar pengurai wadah untuk pemutar bawaan: pengurai bawaan Media3 ditambah [AiffExtractor]. AIFF diperiksa lebih dulu
 * karena pengenalannya ketat (harus berawalan "FORM….AIFF/AIFC"), jadi tidak pernah salah mengenali berkas lain.
 */
@OptIn(UnstableApi::class)
object PlayerExtractors {
    val factory: ExtractorsFactory = object : ExtractorsFactory {
        private val defaults = DefaultExtractorsFactory()

        override fun createExtractors(): Array<Extractor> = arrayOf<Extractor>(AiffExtractor()) + defaults.createExtractors()

        override fun createExtractors(uri: Uri, responseHeaders: Map<String, List<String>>): Array<Extractor> =
            arrayOf<Extractor>(AiffExtractor()) + defaults.createExtractors(uri, responseHeaders)
    }
}
