package com.vinyl.launcher.system

import android.content.Context
import android.graphics.Canvas
import android.graphics.drawable.Icon
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.createBitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Ikon kecil notifikasi untuk OSD beranda (pengganti deretan ikon di status bar), hanya di memori. Yang dibaca dari
 * notifikasi HANYA nama paket dan ikon kecilnya (gambar monokrom yang sama dengan di status bar); judul, isi, dan ekstra
 * tidak pernah diakses. Diisi [com.vinyl.launcher.service.MediaListenerService].
 */
object NotificationIcons {
    data class Entry(val packageName: String, val icon: ImageBitmap)

    const val MAX_ICONS = 5
    private const val ICON_PX = 48

    private val _icons = MutableStateFlow<List<Entry>>(emptyList())
    val icons: StateFlow<List<Entry>> = _icons.asStateFlow()

    /** Ikon yang sudah dirender, per paket + sumber ikon (sumber daya sama = gambar sama). */
    private val cache = LruCache<String, ImageBitmap>(24)

    /**
     * @param count jumlah notifikasi (tanpa ringkasan grup)
     * @param newestFirst nama paket & ikon kecil, terbaru dulu (satu per paket)
     */
    fun publish(context: Context, count: Int, newestFirst: List<Pair<String, Icon?>>) {
        DeviceStatusMonitor.onNotificationCount(count)
        _icons.value = newestFirst.take(MAX_ICONS).mapNotNull { (pkg, icon) ->
            icon ?: return@mapNotNull null
            val key = if (icon.type == Icon.TYPE_RESOURCE) "$pkg:${runCatching { icon.resPackage }.getOrNull()}:${icon.resId}" else null
            val cached = key?.let { cache.get(it) }
            val bmp = cached ?: render(context, icon)?.also { b -> key?.let { cache.put(it, b) } }
            bmp?.let { Entry(pkg, it) }
        }
    }

    /** Akses notifikasi dicabut/terputus: tidak ada data (OSD menyembunyikan bagian notifikasi). */
    fun clear(accessGranted: Boolean) {
        _icons.value = emptyList()
        DeviceStatusMonitor.onNotificationCount(if (accessGranted) 0 else -1)
    }

    private fun render(context: Context, icon: Icon): ImageBitmap? = runCatching {
        val d = icon.loadDrawable(context) ?: return null
        val b = createBitmap(ICON_PX, ICON_PX)
        d.setBounds(0, 0, ICON_PX, ICON_PX)
        d.draw(Canvas(b))
        b.asImageBitmap()
    }.getOrNull()
}
