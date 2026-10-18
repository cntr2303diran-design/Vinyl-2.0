package com.vinyl.launcher.util

import androidx.compose.ui.geometry.Offset
import com.vinyl.launcher.data.CustomGesture
import com.vinyl.launcher.data.SettingsSanitizer
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Pengenal gestur satu goresan (gaya "$1 recognizer"):
 * goresan di-resample jadi [N] titik berjarak sama, dipusatkan & diskalakan ke kotak satuan,
 * lalu dibandingkan titik demi titik. Arah & urutan gambar ikut dihitung,
 * jadi "garis ke atas" berbeda dengan "garis ke bawah".
 */
object GestureRecognizer {
    /** Jumlah titik per gestur; satu sumber dengan pembersih pengaturan agar impor cadangan selalu cocok. */
    const val N = SettingsSanitizer.GESTURE_POINTS

    /** Skor minimal agar dianggap cocok (0..1). */
    const val THRESHOLD = 0.80f

    /** Goresan minimal (px) agar diperiksa — mencegah ketukan biasa dianggap gestur. */
    const val MIN_LENGTH_PX = 160f

    /** Ubah goresan mentah menjadi daftar x,y datar yang sudah dinormalisasi; null bila terlalu pendek. */
    fun normalize(raw: List<Offset>): List<Float>? {
        if (raw.size < 4) return null
        val total = pathLength(raw)
        if (total < 1f) return null
        val pts = resample(raw, N)
        val cx = pts.sumOf { it.x.toDouble() }.toFloat() / pts.size
        val cy = pts.sumOf { it.y.toDouble() }.toFloat() / pts.size
        var minX = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE
        var minY = Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
        pts.forEach { minX = minOf(minX, it.x); maxX = maxOf(maxX, it.x); minY = minOf(minY, it.y); maxY = maxOf(maxY, it.y) }
        val scale = max(max(maxX - minX, maxY - minY), 1f)
        val out = ArrayList<Float>(N * 2)
        pts.forEach { out.add((it.x - cx) / scale); out.add((it.y - cy) / scale) }
        return out
    }

    /** Kemiripan 0..1 antara dua goresan ternormalisasi. */
    fun score(a: List<Float>, b: List<Float>): Float {
        if (a.size != b.size || a.isEmpty()) return 0f
        var sum = 0f
        val n = a.size / 2
        for (i in 0 until n) sum += hypot(a[2 * i] - b[2 * i], a[2 * i + 1] - b[2 * i + 1])
        val avg = sum / n
        return (1f - avg / (0.5f * sqrt(2f))).coerceIn(0f, 1f)
    }

    /** Gestur tersimpan yang paling cocok dengan [path], atau null jika tidak ada yang cukup mirip. */
    fun match(path: List<Offset>, candidates: List<CustomGesture>): CustomGesture? {
        if (candidates.isEmpty() || pathLength(path) < MIN_LENGTH_PX) return null
        val norm = normalize(path) ?: return null
        return candidates
            .map { it to score(norm, it.points) }
            .filter { it.second >= THRESHOLD }
            .maxByOrNull { it.second }
            ?.first
    }

    private fun pathLength(p: List<Offset>): Float {
        var d = 0f
        for (i in 1 until p.size) d += (p[i] - p[i - 1]).getDistance()
        return d
    }

    private fun resample(src: List<Offset>, n: Int): List<Offset> {
        val interval = pathLength(src) / (n - 1)
        val out = ArrayList<Offset>(n)
        out.add(src[0])
        var acc = 0f
        var prev = src[0]
        var i = 1
        val pts = src.toMutableList()
        while (i < pts.size) {
            val cur = pts[i]
            val d = (cur - prev).getDistance()
            if (d > 0f && acc + d >= interval) {
                val t = (interval - acc) / d
                val q = Offset(prev.x + t * (cur.x - prev.x), prev.y + t * (cur.y - prev.y))
                out.add(q)
                pts.add(i, q)   // titik baru jadi awal segmen berikutnya
                prev = q
                acc = 0f
            } else {
                acc += d
                prev = cur
            }
            i++
        }
        while (out.size < n) out.add(src.last())
        return out.take(n)
    }
}
