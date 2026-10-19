package com.vinyl.launcher.ui.room.vhs

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.vinyl.launcher.ui.theme.Tokens
import kotlinx.coroutines.delay
import kotlin.random.Random

/*
 * Lapisan "rekaman VHS" di atas ruang TV: butir film, pita gangguan di tepi bawah (head switching), gangguan tracking
 * sesekali yang merayap ke atas, dan tepi gelap. Hanya MENGGELAPKAN/menambah butir tipis — teks OSD digambar di atasnya
 * sehingga tidak terpengaruh. Diperbarui ~12 kali per detik (bukan tiap frame) agar hemat baterai; diam total bila
 * "Hapus animasi" atau hemat daya aktif.
 */

private const val NOISE = 160

private fun noiseBitmap(seed: Int): ImageBitmap {
    val rnd = Random(seed)
    val px = IntArray(NOISE * NOISE) {
        val v = rnd.nextInt(256)
        val a = 18 + rnd.nextInt(40)
        (a shl 24) or (v shl 16) or (v shl 8) or v
    }
    return Bitmap.createBitmap(px, NOISE, NOISE, Bitmap.Config.ARGB_8888).asImageBitmap()
}

@Composable
fun VhsFilm(animate: Boolean, modifier: Modifier = Modifier) {
    val noise = remember { noiseBitmap(23) }
    val brush = remember(noise) { ShaderBrush(ImageShader(noise, TileMode.Repeated, TileMode.Repeated)) }
    var tick by remember { mutableIntStateOf(0) }
    var glitchAt by remember { mutableLongStateOf(-1L) }
    var now by remember { mutableLongStateOf(0L) }
    LaunchedEffect(animate) {
        if (!animate) return@LaunchedEffect
        var next = Tokens.Motion.TRACKING_EVERY_MS / 2L
        var t = 0L
        while (true) {
            delay(Tokens.Motion.FILM_FRAME_MS.toLong())
            t += Tokens.Motion.FILM_FRAME_MS
            tick++
            now = t
            if (t >= next) {
                glitchAt = t
                next = t + Tokens.Motion.TRACKING_EVERY_MS / 2 + Random.nextLong(Tokens.Motion.TRACKING_EVERY_MS.toLong())
            }
        }
    }
    // Lapisan sendiri: butir yang menggambar ulang 12×/detik tidak ikut memaksa gambar ulang panggung di bawahnya
    Canvas(modifier.graphicsLayer { }.clearAndSetSemantics { }) {
        val w = size.width
        val h = size.height
        val r = Random(tick)
        // Butir: tekstur noise berulang, digeser acak tiap frame
        val ox = r.nextFloat() * NOISE
        val oy = r.nextFloat() * NOISE
        translate(-ox, -oy) {
            drawRect(brush, topLeft = Offset.Zero, size = Size(w + NOISE, h + NOISE), alpha = 0.55f)
        }
        // Tepi gelap ala lensa kamera
        drawRect(
            Brush.radialGradient(
                0f to Color.Transparent, 0.62f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.55f),
                center = Offset(w / 2f, h / 2f), radius = maxOf(w, h) * 0.72f,
            ),
        )
        // Pita gangguan di tepi bawah (head switching): beberapa garis bergeser
        val band = h * 0.012f
        for (i in 0 until 4) {
            val y = h - band * (i + 1) * 0.9f
            val shift = (r.nextFloat() - 0.5f) * w * 0.04f
            drawLine(Color.White.copy(alpha = 0.06f + r.nextFloat() * 0.05f), Offset(shift, y), Offset(w + shift, y), strokeWidth = band * 0.5f)
        }
        // Gangguan tracking: pita bergaris yang merayap naik selama ~0,7 detik
        if (glitchAt >= 0L) {
            val p = (now - glitchAt).toFloat() / Tokens.Motion.TRACKING_GLITCH_MS
            if (p in 0f..1f) {
                val y = h * (1f - p)
                val bh = h * 0.05f
                drawRect(Color.Black.copy(alpha = 0.10f), topLeft = Offset(0f, y - bh), size = Size(w, bh))
                for (i in 0 until 7) {
                    val ly = y - bh * r.nextFloat()
                    val len = w * (0.2f + r.nextFloat() * 0.6f)
                    val x0 = r.nextFloat() * (w - len)
                    drawLine(Color.White.copy(alpha = 0.10f + r.nextFloat() * 0.12f), Offset(x0, ly), Offset(x0 + len, ly), strokeWidth = 1.5f + r.nextFloat() * 2f)
                }
            }
        }
    }
}
