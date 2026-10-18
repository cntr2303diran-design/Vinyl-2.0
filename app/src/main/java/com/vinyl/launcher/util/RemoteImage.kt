package com.vinyl.launcher.util

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Pemuat gambar mini (tanpa library): bitmap langsung, path file, content:// atau https, dengan cache memori. */
object RemoteImage {
    private val cache = LruCache<String, ImageBitmap>(80)

    suspend fun load(context: Context, source: String): ImageBitmap? {
        cache.get(source)?.let { return it }
        val bmp = withContext(Dispatchers.IO) {
            runCatching {
                val u = Uri.parse(source)
                // Hanya https (internet), content/android.resource (penyedia konten), atau path file biasa
                // (yang sudah disaring SettingsSanitizer ke folder internal). Skema file:// ditolak.
                val bytes = when (u.scheme?.lowercase()) {
                    "https" -> Http.get(source, Images.MAX_BYTES, connectMs = 6_000, readMs = 8_000)
                    "content", "android.resource" ->
                        context.contentResolver.openInputStream(u)?.use { it.readAtMost(Images.MAX_BYTES) }
                    null -> java.io.File(source).takeIf { it.isFile && it.length() <= Images.MAX_BYTES }?.readBytes()
                    else -> null
                }
                bytes?.let { Images.decodeSampled(it, MAX_DIM) }
            }.getOrNull()
        } ?: return null
        return bmp.asImageBitmap().also { cache.put(source, it) }
    }

    private const val MAX_DIM = 400
}

/** Gambar dari bitmap (prioritas) atau sumber string (URI/path). */
@Composable
fun rememberImage(bitmap: Bitmap?, source: String?): ImageBitmap? {
    val context = LocalContext.current
    val img by produceState<ImageBitmap?>(bitmap?.asImageBitmap(), bitmap, source) {
        if (bitmap == null && source != null) value = RemoteImage.load(context, source)
    }
    return img
}
