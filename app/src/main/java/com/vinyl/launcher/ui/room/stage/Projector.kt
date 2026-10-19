package com.vinyl.launcher.ui.room.stage

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.core.graphics.scale
import androidx.compose.ui.unit.dp
import com.vinyl.launcher.data.ProjectorSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

/** Isi proyeksi yang sudah diputuskan pemanggil. */
sealed interface ProjectionContent {
    data class Picture(val image: ImageBitmap) : ProjectionContent
    class Visualizer(val content: @Composable () -> Unit) : ProjectionContent
    /** Lagu tanpa cover: bidang warna aksen. */
    data class Tint(val color: Color) : ProjectionContent
}

/**
 * Gambar proyektor di dinding belakang TV. Tidak ada proyektor yang terlihat — hanya cahayanya: tepi lembut, sedikit
 * trapesium (proyektor di bawah mengarah ke atas), hitam yang tidak pernah benar-benar hitam, buram sesuai fokus, dan
 * goyangan pita VHS bila diaktifkan. TV dan meja digambar di depannya.
 *
 * @param rect kotak proyeksi di layar (dp), dari [StageLayout.Frame.projection]
 */
@Composable
fun WallProjection(
    rect: SRect,
    content: ProjectionContent,
    settings: ProjectorSettings,
    animate: Boolean,
    modifier: Modifier = Modifier,
) {
    // Ukuran pilihan pengguna sudah diterapkan di [StageLayout] (tepi atas gambar selalu di dalam layar)
    val w = rect.width
    val h = rect.height
    val left = rect.left
    val top = rect.top
    // Nilai animasi dibaca di dalam graphicsLayer (fase gambar), bukan saat komposisi: tidak ada rekomposisi tiap frame
    val phase: State<Float>? = if (animate) {
        rememberInfiniteTransition(label = "proj")
            .animateFloat(0f, 1f, infiniteRepeatable(tween(24_000, easing = LinearEasing), RepeatMode.Restart), label = "kenburns")
    } else null
    Box(
        modifier
            .offset(left.dp, top.dp)
            .size(w.dp, h.dp)
            .clearAndSetSemantics { }
            .graphicsLayer {
                alpha = settings.brightness.coerceIn(0.1f, 1f)
                // Trapesium halus: proyektor di bawah gambar
                rotationX = -4f
                cameraDistance = 9f * density
                if (settings.vhs && phase != null) {
                    // Goyang horizontal pita (beberapa piksel), seperti gambar VHS yang tidak stabil
                    val wobble = phase.value
                    translationX = (sin(wobble * 2 * PI * 37) * 0.6 + sin(wobble * 2 * PI * 113) * 0.4).toFloat() * 1.5f.dp.toPx()
                }
                compositingStrategy = CompositingStrategy.Offscreen
            }
            .drawWithContent {
                drawContent()
                // Tepi lembut di keempat sisi (cahaya proyektor memudar), lalu hitam yang terangkat sedikit
                drawRect(Brush.horizontalGradient(0f to Color.Transparent, 0.08f to Color.Black, 0.92f to Color.Black, 1f to Color.Transparent), blendMode = BlendMode.DstIn)
                drawRect(Brush.verticalGradient(0f to Color.Transparent, 0.1f to Color.Black, 0.88f to Color.Black, 1f to Color.Transparent), blendMode = BlendMode.DstIn)
                drawRect(Color(0xFF1A1D24).copy(alpha = 0.35f), blendMode = BlendMode.Screen)
                // Garis piksel proyektor halus
                var y = 0f
                val gap = 3.dp.toPx()
                while (y < size.height) {
                    drawLine(Color.Black.copy(alpha = 0.12f), androidx.compose.ui.geometry.Offset(0f, y), androidx.compose.ui.geometry.Offset(size.width, y), strokeWidth = 1f)
                    y += gap
                }
            },
    ) {
        when (content) {
            is ProjectionContent.Picture -> {
                // Ken Burns pelan saat musik berjalan
                // Fokus dipanggang ke gambar sekali (bitmap diperkecil, diperbesar halus): RenderEffect blur pada lapisan yang
                // bergerak tiap frame menghitung ulang blur layar penuh 60×/detik dan membuat beranda tersendat
                Image(
                    rememberSoftFocus(content.image, settings.focus), null, contentScale = ContentScale.Crop,
                    filterQuality = FilterQuality.Medium,
                    modifier = Modifier.fillMaxSize().graphicsLayer {
                        val wobble = phase?.value ?: 0f
                        val s = 1.06f + 0.06f * sin(wobble * 2 * PI).toFloat()
                        scaleX = s; scaleY = s
                        translationX = size.width * 0.02f * sin(wobble * 2 * PI + 1.3).toFloat()
                    },
                )
            }
            is ProjectionContent.Visualizer -> Box(Modifier.fillMaxSize().background(Color(0xFF05060A))) { content.content() }
            is ProjectionContent.Tint -> Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(content.color, Color(0xFF0B0C10)))))
        }
    }
}

/**
 * Gambar proyeksi yang sudah "tidak fokus" menurut [focus] (1 = tajam). Dibuat sekali di thread latar dengan memperkecil
 * bitmap (sampai 1/12); saat digambar membesar dengan filter halus, jadi lembut tanpa efek blur layar penuh tiap frame.
 * Sebelum jadi (atau bila bitmap tidak bisa diperkecil), gambar asli yang ditampilkan.
 */
@Composable
private fun rememberSoftFocus(image: ImageBitmap, focus: Float): ImageBitmap {
    val factor = 1 + ((1f - focus.coerceIn(0f, 1f)) * 11f).roundToInt()
    if (factor <= 1) return image
    val soft by produceState(initialValue = image, image, factor) {
        value = withContext(Dispatchers.Default) {
            runCatching {
                val src = image.asAndroidBitmap()
                src.scale(maxOf(8, src.width / factor), maxOf(8, src.height / factor)).asImageBitmap()
            }.getOrDefault(image)
        }
    }
    return soft
}
