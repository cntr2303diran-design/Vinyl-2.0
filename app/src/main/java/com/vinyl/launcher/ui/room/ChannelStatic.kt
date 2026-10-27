package com.vinyl.launcher.ui.room

import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.IntSize
import com.vinyl.launcher.ui.theme.Tokens
import kotlin.math.roundToInt
import kotlin.random.Random

/** Jumlah gambar derau yang bergantian selama ledakan statis. */
private const val NOISE_FRAMES = 4
private const val NOISE_W = 80
private const val NOISE_H = 60

/**
 * Hujan statis singkat di layar TV setiap kali [trigger] berubah ("ganti channel" saat kaset berganti). Tidak ada ledakan
 * pada komposisi pertama, dan tidak ada bila [enabled] false (animasi dimatikan / hemat daya). Gambar derau dibuat sekali
 * (empat bitmap kecil yang diperbesar tanpa penghalusan) dan digambar hanya selama ledakan ±[Tokens.Motion.STATIC_BURST_MS]
 * ms; di luar itu lapisan ini tidak menggambar apa-apa. Tingkat ledakan dibaca di fase gambar, bukan saat komposisi.
 */
@Composable
internal fun ChannelStatic(trigger: Any?, enabled: Boolean, modifier: Modifier = Modifier) {
    val level = remember { Animatable(0f) }
    var previous by remember { mutableStateOf(trigger) }
    LaunchedEffect(trigger) {
        if (previous == trigger) return@LaunchedEffect
        previous = trigger
        if (!enabled) return@LaunchedEffect
        level.snapTo(1f)
        level.animateTo(0f, tween(Tokens.Motion.STATIC_BURST_MS, easing = LinearEasing))
    }
    val frames = remember { noiseFrames() }
    Canvas(modifier.fillMaxSize().clearAndSetSemantics { }) {
        val a = level.value
        if (a <= 0f) return@Canvas
        drawImage(
            frames[(a * 24f).toInt() % NOISE_FRAMES],
            dstSize = IntSize(size.width.roundToInt().coerceAtLeast(1), size.height.roundToInt().coerceAtLeast(1)),
            alpha = (a * 0.9f).coerceIn(0f, 0.9f),
            filterQuality = FilterQuality.None,
        )
    }
}

/** Derau abu-abu acak, tetap (benih tetap) agar tampilannya sama setiap kali. */
private fun noiseFrames(): List<ImageBitmap> = List(NOISE_FRAMES) { f ->
    val rnd = Random(1_000 + f)
    val pixels = IntArray(NOISE_W * NOISE_H) {
        val g = rnd.nextInt(256)
        (0xFF shl 24) or (g shl 16) or (g shl 8) or g
    }
    Bitmap.createBitmap(pixels, NOISE_W, NOISE_H, Bitmap.Config.ARGB_8888).asImageBitmap()
}
