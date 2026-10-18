package com.vinyl.launcher.ui.home

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.abs

/*
 * Bagian Compose dari navigasi tiga halaman (aturannya di PageNav). Halaman-halaman tidak memakai HorizontalPager:
 * ruang TV sudah punya satu detektor goresan untuk swipe atas/bawah DAN gestur gambar sendiri, dan pager akan
 * merebut gerakan itu. Jadi setiap halaman hanya digeser dengan graphicsLayer (murah, tanpa re-layout) dan
 * perpindahan diputuskan detektor masing-masing halaman.
 */

/**
 * Geser halaman berindeks [index] agar tampil sesuai posisi animasi [position] (indeks halaman aktif, pecahan saat
 * transisi). [widthPx] lebar layar. graphicsLayer juga memindahkan area sentuh, jadi halaman di luar layar tidak
 * menerima ketukan.
 */
fun Modifier.roomPage(index: Int, position: Float, widthPx: Int): Modifier = graphicsLayer {
    translationX = (index - position) * widthPx
}

/** True bila halaman berindeks [index] (sebagian) terlihat pada posisi [position]; halaman lain tidak perlu digambar. */
fun pageVisible(index: Int, position: Float): Boolean = abs(index - position) < 0.999f

/**
 * Deteksi geser horizontal di area KOSONG halaman samping (perpustakaan, konsol). Isi yang bisa digulir (rak, daftar)
 * mengonsumsi gerakannya sendiri; begitu ada perubahan yang terkonsumsi, gestur ini diabaikan sehingga menggulir rak
 * tidak pernah memindahkan halaman. [onSwipe] menerima total dx (px, positif = ke kanan).
 */
fun Modifier.sidePageSwipe(onSwipe: (Float) -> Unit): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        var last = down.position
        while (true) {
            val event = awaitPointerEvent()
            val ch = event.changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
            if (ch.isConsumed) return@awaitEachGesture
            if (!ch.pressed) break
            last = ch.position
        }
        val dx = last.x - down.position.x
        val dy = last.y - down.position.y
        if (abs(dx) > abs(dy) * 1.5f) onSwipe(dx)
    }
}
