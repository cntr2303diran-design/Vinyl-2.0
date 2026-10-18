package com.vinyl.launcher.screenshot

import android.graphics.Bitmap
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.vinyl.launcher.data.AlbumMotion
import com.vinyl.launcher.data.CassetteShell
import com.vinyl.launcher.data.KnobStyle
import com.vinyl.launcher.data.RetroSettings
import com.vinyl.launcher.data.RoomTone
import com.vinyl.launcher.data.TurntablePosition
import com.vinyl.launcher.data.TvBody
import com.vinyl.launcher.data.VfdColor
import com.vinyl.launcher.media.NowPlaying
import com.vinyl.launcher.ui.room.RoomBackdrop
import com.vinyl.launcher.ui.room.RoomFloor
import com.vinyl.launcher.ui.room.RoomStage
import com.vinyl.launcher.ui.room.VfdTicker
import com.vinyl.launcher.ui.room.WallSource
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Cover buatan untuk tes (gradien + lingkaran), bukan gambar berhak cipta. */
internal fun fakeCover(a: Int = 0xFF1F4E79.toInt(), b: Int = 0xFFE07A3F.toInt()): ImageBitmap {
    val bmp = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888)
    val c = android.graphics.Canvas(bmp)
    c.drawRect(0f, 0f, 400f, 400f, Paint().apply { shader = LinearGradient(0f, 0f, 400f, 400f, a, b, Shader.TileMode.CLAMP) })
    c.drawCircle(250f, 160f, 90f, Paint().apply { color = 0xFFF3D9A4.toInt(); isAntiAlias = true })
    c.drawRect(0f, 300f, 400f, 400f, Paint().apply { color = 0xFF14202B.toInt() })
    return bmp.asImageBitmap()
}

internal fun playing(art: ImageBitmap?) = NowPlaying(
    hasSession = true, title = "Lagu Percobaan", artist = "Artis Contoh", album = "Album", art = art,
    durationMs = 258_000, isPlaying = true, positionMs = 154_000, accentColor = 0xFFE07A3F.toInt(), canSeek = true,
)

@Composable
internal fun RoomPreview(np: NowPlaying, retro: RetroSettings, landscape: Boolean) {
    var floor by remember { mutableStateOf(RoomFloor.UNKNOWN) }
    Box(Modifier.fillMaxSize()) {
        RoomBackdrop(
            tone = retro.room, floor = floor,
            source = np.art?.let { WallSource.Cover(it) } ?: WallSource.Blank,
            animate = false, glow = Color(0xFFE07A3F), glowAlpha = if (np.hasTrack) 0.12f else 0f,
        )
        RoomStage(
            np = np, retro = retro, tvSize = 0.85f, position = TurntablePosition.CENTER, landscape = landscape,
            accent = Color(0xFFE07A3F), motionAllowed = false,
            vfdLines = listOf(VfdTicker.line("clock", "21:47")),
            onScreenTap = {}, onFloor = { floor = it },
            modifier = Modifier.fillMaxSize().padding(16.dp),
        )
    }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RoomShots {
    @get:Rule
    val compose = createComposeRule()

    private fun shot(name: String) = compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")

    @Test
    @Config(qualifiers = "w411dp-h891dp-xxhdpi")
    fun phoneStandby() {
        compose.setContent { RoomPreview(NowPlaying(), RetroSettings(), landscape = false) }
        shot("tahap3_ponsel_standby")
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xxhdpi")
    fun phoneCover() {
        compose.setContent { RoomPreview(playing(fakeCover()), RetroSettings(), landscape = false) }
        shot("tahap3_ponsel_cover")
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp-land-mdpi")
    fun tabletCover() {
        compose.setContent { RoomPreview(playing(fakeCover()), RetroSettings(tvBody = TvBody.WALNUT, knobStyle = KnobStyle.BRASS, vfdColor = VfdColor.GREEN), landscape = true) }
        shot("tahap3_tablet_cover_walnut")
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp-land-mdpi")
    fun tabletSpaceAgeDimRoom() {
        compose.setContent {
            RoomPreview(
                playing(fakeCover(0xFF2E7D32.toInt(), 0xFFF9A825.toInt())),
                RetroSettings(tvBody = TvBody.SPACE_AGE, knobStyle = KnobStyle.CHROME, room = RoomTone.DIM, cassetteShell = CassetteShell.TRANSPARENT, albumMotion = AlbumMotion.ZOOM),
                landscape = true,
            )
        }
        shot("tahap3_tablet_spaceage_redup")
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xxhdpi")
    fun phoneCharcoalNoCover() {
        compose.setContent { RoomPreview(playing(null), RetroSettings(tvBody = TvBody.CHARCOAL), landscape = false) }
        shot("tahap3_ponsel_charcoal_tanpa_cover")
    }
}
