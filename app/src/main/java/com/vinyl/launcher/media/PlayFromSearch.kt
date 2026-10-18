package com.vinyl.launcher.media

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.provider.MediaStore
import com.vinyl.launcher.data.PlayRecord

/**
 * Putar ulang lagu dari koleksi lewat intent standar "putar dari pencarian" (didukung Spotify, YouTube Music, dll.).
 * Dicoba dulu ke aplikasi yang memutarnya; bila gagal, ke aplikasi apa pun yang menangani intent itu.
 * Dipakai bersama oleh rak piringan dan pencarian app drawer.
 */
fun playFromSearch(context: Context, r: PlayRecord) {
    fun intent(pkg: String?) = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply {
        putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/audio")
        putExtra(MediaStore.EXTRA_MEDIA_TITLE, r.title)
        putExtra(MediaStore.EXTRA_MEDIA_ARTIST, r.artist)
        putExtra(SearchManager.QUERY, "${r.title} ${r.artist}")
        pkg?.let { setPackage(it) }
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(intent(r.pkg)) }
        .recoverCatching { context.startActivity(intent(null)) }
}
