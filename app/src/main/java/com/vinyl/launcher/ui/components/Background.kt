package com.vinyl.launcher.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalWindowInfo
import com.vinyl.launcher.data.BackgroundSettings
import com.vinyl.launcher.data.BackgroundType
import com.vinyl.launcher.data.VisualSettings
import com.vinyl.launcher.fx.FxOverlay
import com.vinyl.launcher.fx.FxPlan
import com.vinyl.launcher.fx.FxState
import com.vinyl.launcher.fx.fxBackdrop
import com.vinyl.launcher.ui.theme.toColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Muat gambar dari file internal dengan downsampling sesuai ukuran layar. */
@Composable
fun rememberFileImage(path: String?): ImageBitmap? {
    // Ukuran jendela sebenarnya dalam piksel (bukan dp yang dibulatkan dari Configuration)
    val container = LocalWindowInfo.current.containerSize
    val maxPx = maxOf(container.width, container.height).coerceAtLeast(512)
    val img by produceState<ImageBitmap?>(null, path, maxPx) {
        value = if (path == null) null else withContext(Dispatchers.IO) {
            runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(path, bounds)
                var sample = 1
                while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxPx) sample *= 2
                BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
            }.getOrNull()
        }
    }
    return img
}

/**
 * Latar home screen / layar kunci.
 * Mode ALBUM memakai [AmbientBackdrop] (cover di-blur + gumpalan warna bergerak ala Apple Music).
 * [cinematic] = transisi warna sinematik saat cover berganti (hanya berlaku di mode ALBUM).
 */
@Composable
fun BackgroundLayer(
    settings: BackgroundSettings,
    albumColor: Color,
    modifier: Modifier = Modifier,
    art: ImageBitmap? = null,
    swatches: List<Int> = emptyList(),
    animate: Boolean = false,
    dim: Float = 0.38f,
    cinematic: Boolean = false,
    fx: FxState? = null,
    fxPlan: FxPlan = FxPlan.OFF,
    visual: VisualSettings = VisualSettings(),
    fxTint: Color = Color.White,
) {
    Box(modifier.fillMaxSize()) {
        when (settings.type) {
            BackgroundType.ALBUM -> AmbientBackdrop(
                art, swatches, albumColor, animate, dim = dim, cinematic = cinematic,
                fx = fx, fxPlan = fxPlan, parallaxMode = visual.parallax, parallaxStrength = visual.parallaxStrength,
            )
            BackgroundType.SOLID -> Box(Modifier.fillMaxSize().background(settings.solidColor.toColor()))
            BackgroundType.GRADIENT -> Box(
                Modifier.fillMaxSize().background(
                    Brush.linearGradient(listOf(settings.gradientStart.toColor(), settings.gradientEnd.toColor()))
                )
            )
            BackgroundType.IMAGE -> {
                val img = rememberFileImage(settings.imagePath)
                Box(Modifier.fillMaxSize().background(Color(0xFF0E0E10)))
                if (img != null) {
                    Image(
                        img, null, contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().fxBackdrop(fx, fxPlan, visual.parallax, visual.parallaxStrength),
                    )
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.28f)))
                }
            }
        }
        // Cahaya & partikel di atas peredam, di bawah konten, untuk semua jenis latar
        if (fx != null && (fxPlan.glow || fxPlan.particles)) {
            FxOverlay(fx, fxPlan, fxTint, visual.parallax, visual.parallaxStrength)
        }
    }
}
