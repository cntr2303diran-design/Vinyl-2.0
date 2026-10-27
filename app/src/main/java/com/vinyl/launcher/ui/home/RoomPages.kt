package com.vinyl.launcher.ui.home

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

/*
 * Bagian Compose dari navigasi tiga halaman (aturannya di PageNav). Halaman-halaman tidak memakai HorizontalPager:
 * ruang TV sudah punya satu detektor goresan untuk swipe atas/bawah DAN gestur gambar sendiri, dan pager akan
 * merebut gerakan itu. Jadi setiap halaman digeser dengan graphicsLayer (murah, tanpa re-layout) mengikuti [PagePager],
 * dan halaman MENGIKUTI JARI sejak melewati ambang sentuh — bukan baru bergerak setelah jari diangkat.
 */

/**
 * Posisi tiga halaman: satu angka (indeks halaman, pecahan saat bergerak) yang ditulis oleh jari (seretan langsung) atau
 * pegas (penyelesaian & perpindahan lewat kode). Nilainya dibaca HANYA di fase gambar (blok graphicsLayer) atau lewat
 * derivedStateOf, sehingga beranda tidak disusun ulang pada tiap frame geser.
 *
 * Seluruh anggota dipanggil dari thread utama (gestur, komposisi, efek).
 */
@Stable
class PagePager(initialIndex: Int, private val scope: CoroutineScope) {
    private val pos = mutableFloatStateOf(initialIndex.toFloat())
    private val width = mutableIntStateOf(0)
    private var job: Job? = null
    private var token: Any? = null
    private var dragStart = initialIndex

    /** Indeks halaman, pecahan saat bergerak. Jangan dibaca saat komposisi. */
    val position: Float get() = pos.floatValue

    /** Lebar satu halaman (px); diisi dari ukuran layar. */
    var widthPx: Int
        get() = width.intValue
        set(value) { width.intValue = value }

    /** Indeks halaman yang sedang ditempati atau dituju (bilangan bulat). */
    var targetIndex: Int = initialIndex
        private set

    /** Jari sedang menyeret halaman. */
    var dragging by mutableStateOf(false)
        private set

    /** Pegas penyelesaian sedang berjalan. */
    var settling by mutableStateOf(false)
        private set

    /** Halaman sedang bergerak (diseret atau meluncur): efek hias yang berat sebaiknya menunggu. */
    val moving: Boolean get() = dragging || settling

    /** Jari menyentuh halaman: hentikan pegas yang berjalan (halaman "ditangkap") dan mulai mengikuti jari. */
    fun startDrag() {
        job?.cancel()
        job = null
        token = null
        settling = false
        dragStart = targetIndex
        dragging = true
    }

    /** Jari bergeser [dx] px (positif = ke kanan): halaman ikut, posisi dijepit di ujung. */
    fun dragByPx(dx: Float) {
        val w = widthPx
        if (w <= 0 || dx.isNaN()) return
        pos.floatValue = (pos.floatValue - dx / w).coerceIn(0f, (PageNav.COUNT - 1).toFloat())
    }

    /**
     * Jari dilepas dengan kecepatan [velocityXPxPerSec] (positif = ke kanan). Memutuskan halaman tujuan, menjalankan pegas
     * ke sana (membawa kecepatan jari), dan mengembalikan indeks tujuan.
     */
    fun finishDrag(velocityXPxPerSec: Float, animated: Boolean): Int {
        val w = widthPx.coerceAtLeast(1).toFloat()
        val v = if (velocityXPxPerSec.isNaN()) 0f else -velocityXPxPerSec / w
        val target = PageNav.settleIndex(dragStart, pos.floatValue, v, w)
        dragging = false
        settleTo(target, v, animated)
        return target
    }

    /** Pindah ke halaman berindeks [index] bila belum ada di sana atau menuju ke sana (perpindahan lewat kode). */
    fun goTo(index: Int, animated: Boolean) {
        if (dragging) return
        if (index == targetIndex && (settling || pos.floatValue == index.toFloat())) return
        settleTo(index, 0f, animated)
    }

    /** Pegas kritis ke [index] dengan kecepatan awal [velocity] (halaman/detik); tanpa animasi → langsung. */
    fun settleTo(index: Int, velocity: Float, animated: Boolean) {
        job?.cancel()
        targetIndex = index
        dragging = false
        val target = index.toFloat()
        if (!animated || abs(pos.floatValue - target) < 0.0005f) {
            token = null
            settling = false
            pos.floatValue = target
            return
        }
        val me = Any()
        token = me
        settling = true
        job = scope.launch {
            try {
                animate(
                    initialValue = pos.floatValue,
                    targetValue = target,
                    initialVelocity = velocity.coerceIn(-MAX_FLING, MAX_FLING),
                    animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = SETTLE_STIFFNESS, visibilityThreshold = 0.0008f),
                ) { value, _ -> pos.floatValue = value.coerceIn(0f, (PageNav.COUNT - 1).toFloat()) }
                pos.floatValue = target
            } finally {
                // Pegas lain yang menggantikan ini sudah memasang tokennya sendiri; jangan matikan penandanya
                if (token === me) {
                    token = null
                    settling = false
                }
            }
        }
    }

    private companion object {
        /** Cukup lunak agar terasa meluncur (±0,3 dtk), cukup kaku agar tidak terasa lambat. */
        const val SETTLE_STIFFNESS = 420f

        /** Batas kecepatan awal pegas (halaman/detik) agar lemparan ekstrem tidak membuat lompatan. */
        const val MAX_FLING = 12f
    }
}

/**
 * Geser halaman berindeks [index] agar tampil sesuai posisi [PagePager.position]. graphicsLayer juga memindahkan area
 * sentuh, jadi halaman di luar layar tidak menerima ketukan. Posisi & lebar dibaca di dalam blok layer (fase gambar).
 */
fun Modifier.roomPage(index: Int, pager: PagePager): Modifier = graphicsLayer {
    translationX = (index - pager.position) * pager.widthPx
}

/** True bila halaman berindeks [index] (sebagian) terlihat pada posisi [position]; halaman lain tidak perlu digambar. */
fun pageVisible(index: Int, position: Float): Boolean = abs(index - position) < 0.999f

/**
 * Seret halaman dari area KOSONG halaman samping (perpustakaan, konsol). Isi yang bisa digulir (rak, daftar) mengonsumsi
 * gerakannya sendiri; begitu perubahan sudah terkonsumsi gestur ini diabaikan, sehingga menggulir rak tidak pernah
 * memindahkan halaman. Gerak yang lebih vertikal daripada horizontal (rasio 1,5) bukan untuk kita.
 *
 * [onSettled] menerima indeks halaman tujuan saat jari dilepas; [animated] false = tanpa animasi ("Hapus animasi").
 */
fun Modifier.sidePageDrag(pager: PagePager, animated: () -> Boolean, onSettled: (Int) -> Unit): Modifier =
    pointerInput(pager) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val tracker = VelocityTracker()
            tracker.addPosition(down.uptimeMillis, down.position)
            var dragging = false
            while (true) {
                val event = awaitPointerEvent()
                val ch = event.changes.firstOrNull { it.id == down.id } ?: break
                if (ch.isConsumed) {
                    if (!dragging) return@awaitEachGesture
                    break
                }
                if (!ch.pressed) break
                tracker.addPosition(ch.uptimeMillis, ch.position)
                val total = ch.position - down.position
                if (!dragging) {
                    if (total.getDistance() > viewConfiguration.touchSlop) {
                        if (abs(total.x) <= abs(total.y) * 1.5f) return@awaitEachGesture
                        dragging = true
                        pager.startDrag()
                        pager.dragByPx(total.x - sign(total.x) * viewConfiguration.touchSlop)
                        ch.consume()
                    }
                } else {
                    pager.dragByPx(ch.positionChange().x)
                    ch.consume()
                }
            }
            if (dragging) onSettled(pager.finishDrag(tracker.calculateVelocity().x, animated()))
        }
    }
