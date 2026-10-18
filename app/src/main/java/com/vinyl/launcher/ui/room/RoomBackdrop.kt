package com.vinyl.launcher.ui.room

import android.os.Build
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.vinyl.launcher.data.RoomTone
import com.vinyl.launcher.ui.theme.RetroColors
import com.vinyl.launcher.ui.theme.Tokens
import kotlin.math.roundToInt

/** Posisi panggung di layar (piksel, koordinat akar) yang dilaporkan ruang TV agar latar menggambar lantai sejajar. */
@Immutable
data class RoomFloor(val floorY: Float, val projectionLeft: Float, val projectionTop: Float, val projectionWidth: Float, val projectionHeight: Float) {
    companion object {
        val UNKNOWN = RoomFloor(Float.NaN, 0f, 0f, 0f, 0f)
    }
}

/** Apa yang diproyeksikan ke dinding. */
sealed interface WallSource {
    data object Blank : WallSource
    data class Cover(val art: ImageBitmap) : WallSource
    data class Picture(val image: ImageBitmap) : WallSource
}

/**
 * Ruang putih hampa: dinding dengan gradasi hangat, garis pertemuan dinding–lantai yang samar, lantai, dan hasil
 * proyeksi di dinding belakang TV. Tidak ada proyektor atau berkas cahaya yang terlihat — hanya cahayanya di dinding.
 *
 * @param wallColor/wallGradient pengganti dinding putih bila pengguna memilih latar warna/gradien.
 * @param animate proyeksi bergerak pelan (Ken Burns) — hanya saat musik diputar dan animasi diizinkan.
 */
@Composable
fun RoomBackdrop(
    tone: RoomTone,
    floor: RoomFloor,
    source: WallSource,
    animate: Boolean,
    glow: Color,
    glowAlpha: Float,
    modifier: Modifier = Modifier,
    wallColor: Color? = null,
    wallGradient: Pair<Color, Color>? = null,
    projectionModifier: Modifier = Modifier,
) {
    val light = tone == RoomTone.LIGHT
    val wallTop = if (light) RetroColors.WallTop else Color(0xFF2B2D2A)
    val wallBase = if (light) RetroColors.WarmWhite else RetroColors.CharcoalDeep
    val floorColor = if (light) RetroColors.Floor else Color(0xFF1E201D)
    val corner = if (light) RetroColors.Corner else Color(0xFF191A18)

    Box(modifier.fillMaxSize().clearAndSetSemantics { }) {
        Canvas(Modifier.fillMaxSize()) {
            val h = size.height
            val fy = if (floor.floorY.isNaN()) h * 0.72f else floor.floorY.coerceIn(h * 0.2f, h)
            // Dinding
            when {
                wallGradient != null -> drawRect(Brush.verticalGradient(listOf(wallGradient.first, wallGradient.second), endY = fy), size = Size(size.width, fy))
                wallColor != null -> drawRect(wallColor, size = Size(size.width, fy))
                else -> drawRect(Brush.verticalGradient(listOf(wallTop, wallBase), endY = fy), size = Size(size.width, fy))
            }
            // Lantai: sedikit lebih gelap menjauh dari dinding, memberi kesan bidang datar
            drawRect(
                Brush.verticalGradient(listOf(wallBase, floorColor), startY = fy, endY = h),
                topLeft = Offset(0f, fy), size = Size(size.width, h - fy),
            )
            // Sudut dinding–lantai: pita lembut, bukan garis tegas
            val band = h * 0.035f
            drawRect(
                Brush.verticalGradient(listOf(Color.Transparent, corner.copy(alpha = 0.55f), Color.Transparent), startY = fy - band, endY = fy + band),
                topLeft = Offset(0f, fy - band), size = Size(size.width, band * 2),
            )
        }

        // Proyeksi di dinding
        if (!floor.floorY.isNaN() && floor.projectionWidth > 0f) {
            val density = LocalDensity.current
            val image = when (source) {
                is WallSource.Cover -> source.art
                is WallSource.Picture -> source.image
                WallSource.Blank -> null
            }
            Crossfade(image, animationSpec = tween(Tokens.Motion.PROJECTION_CROSSFADE_MS), label = "projection") { img ->
                if (img != null) {
                    Projection(
                        img, animate, light,
                        Modifier
                            .offset { IntOffset(floor.projectionLeft.roundToInt(), floor.projectionTop.roundToInt()) }
                            .size(with(density) { floor.projectionWidth.toDp() }, with(density) { floor.projectionHeight.toDp() })
                            .then(projectionModifier),
                    )
                }
            }
        }

        // Cahaya layar TV di dinding & lantai (warna dari cover). Digambar dengan BlendMode.Screen: cahaya hanya bisa
        // MENERANGKAN, jadi tidak pernah menurunkan kontras teks gelap di ruang terang.
        if (glowAlpha > 0f && !floor.floorY.isNaN()) {
            Canvas(Modifier.fillMaxSize()) {
                val cx = floor.projectionLeft + floor.projectionWidth / 2f
                val cy = floor.floorY - floor.projectionHeight * 0.25f
                val r = floor.projectionWidth * 0.75f
                val a = glowAlpha.coerceIn(0f, Tokens.A11y.FX_PEAK * 3f)
                drawCircle(Brush.radialGradient(listOf(glow.copy(alpha = a), Color.Transparent), center = Offset(cx, cy), radius = r), r, Offset(cx, cy), blendMode = BlendMode.Screen)
                // Pantulan tipis di lantai depan TV
                val fy = floor.floorY
                drawOval(
                    Brush.radialGradient(listOf(glow.copy(alpha = a * 0.6f), Color.Transparent), center = Offset(cx, fy + r * 0.12f), radius = r * 0.7f),
                    topLeft = Offset(cx - r * 0.7f, fy), size = Size(r * 1.4f, r * 0.24f),
                    blendMode = BlendMode.Screen,
                )
            }
        }
    }
}

/** Gambar yang "jatuh" di dinding: lembut, tepinya memudar, sedikit bergerak bila diizinkan. */
@Composable
private fun Projection(image: ImageBitmap, animate: Boolean, light: Boolean, modifier: Modifier) {
    val drift = if (animate) {
        val t = rememberInfiniteTransition(label = "kenburns")
        val v by t.animateFloat(0f, 1f, infiniteRepeatable(tween(24_000, easing = LinearEasing), RepeatMode.Reverse), label = "kb")
        v
    } else 0.5f
    Box(
        modifier
            // Tepi memudar: gambar dikalikan masker radial (lapisan offscreen agar BlendMode berlaku)
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                // Keempat tepi memudar (dua masker linear dikalikan), jadi tidak ada sisi persegi yang tegas
                drawRect(
                    Brush.horizontalGradient(0f to Color.Transparent, 0.22f to Color.Black, 0.78f to Color.Black, 1f to Color.Transparent),
                    blendMode = BlendMode.DstIn,
                )
                drawRect(
                    Brush.verticalGradient(0f to Color.Transparent, 0.28f to Color.Black, 0.80f to Color.Black, 1f to Color.Transparent),
                    blendMode = BlendMode.DstIn,
                )
            }
            .alpha(RoomInk.PROJECTION_ALPHA.toFloat()),
    ) {
        Image(
            image, null, contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val s = 1.08f + 0.06f * drift
                    scaleX = s; scaleY = s
                    translationX = (drift - 0.5f) * size.width * 0.04f
                }
                .then(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Modifier.blur(if (light) 6.dp else 4.dp) else Modifier),
        )
    }
}
