package com.vinyl.launcher.screenshot

import android.graphics.Bitmap
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.vinyl.launcher.data.RetroSettings
import com.vinyl.launcher.data.RoomTone
import com.vinyl.launcher.data.TvBody
import com.vinyl.launcher.media.NowPlaying
import com.vinyl.launcher.ui.room.KnobState
import com.vinyl.launcher.ui.room.RoomScene
import com.vinyl.launcher.ui.room.SceneStatus
import com.vinyl.launcher.ui.room.SceneTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Cover buatan untuk tes: langit senja, bulan sabit, bukit pasir (bukan gambar berhak cipta). */
internal fun fakeCover(): ImageBitmap {
    val s = 600f
    val bmp = Bitmap.createBitmap(600, 600, Bitmap.Config.ARGB_8888)
    val c = android.graphics.Canvas(bmp)
    c.drawRect(0f, 0f, s, s, Paint().apply { shader = LinearGradient(0f, 0f, 0f, s * 0.7f, 0xFF5E7FA8.toInt(), 0xFFC9C6C0.toInt(), Shader.TileMode.CLAMP) })
    c.drawCircle(s * 0.55f, s * 0.12f, s * 0.035f, Paint().apply { color = 0xFFF4F1E8.toInt(); isAntiAlias = true })
    c.drawCircle(s * 0.565f, s * 0.11f, s * 0.033f, Paint().apply { color = 0xFF7E97B5.toInt(); isAntiAlias = true })
    val dune = Path().apply { moveTo(0f, s * 0.75f); quadTo(s * 0.45f, s * 0.22f, s * 0.7f, s * 0.5f); quadTo(s * 0.85f, s * 0.62f, s, s * 0.6f); lineTo(s, s); lineTo(0f, s); close() }
    c.drawPath(dune, Paint().apply { shader = LinearGradient(0f, s * 0.3f, s * 0.6f, s, 0xFFD3A774.toInt(), 0xFF7A5634.toInt(), Shader.TileMode.CLAMP); isAntiAlias = true })
    val dune2 = Path().apply { moveTo(0f, s * 0.86f); quadTo(s * 0.5f, s * 0.68f, s, s * 0.84f); lineTo(s, s); lineTo(0f, s); close() }
    c.drawPath(dune2, Paint().apply { color = 0xFF5B3E26.toInt(); isAntiAlias = true })
    return bmp.asImageBitmap()
}

internal fun playing(art: ImageBitmap?) = NowPlaying(
    hasSession = true, title = "Desert Moon", artist = "Dune Wanderer", album = "Album", art = art,
    durationMs = 258_000, isPlaying = true, positionMs = 155_000, accentColor = 0xFFD3A774.toInt(), canSeek = true,
)

@Composable
internal fun ScenePreview(np: NowPlaying, retro: RetroSettings, knob: KnobState? = null, tone: RoomTone = retro.room) {
    val k = knob ?: remember { KnobState() }
    Box(Modifier.fillMaxSize().background(SceneTheme.of(tone).wallEdge)) {
        RoomScene(
            np = np, retro = retro, tone = tone, tvSize = 0.85f, accent = Color(0xFFD3A774), motionAllowed = false,
            editing = false,
            status = SceneStatus("10:09 AM", "Buds Pro", listOf(if (np.hasTrack) "PLAY" else "STANDBY", "OUT 48 kHz", "BUDS PRO")),
            positionMs = np.positionMs, titleScrimAlpha = 0.25f, knobState = k, volume = 0.45f, onVolume = {},
            onPrevious = {}, onRewind = {}, onPlayPause = {}, onNext = {}, onSeek = {}, onTick = {}, onOpenLyrics = {},
        )
    }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RoomShots {
    @get:Rule
    val compose = createComposeRule()

    private fun shot(name: String) = compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")

    /** Ukuran persis gambar referensi (1312 x 816) agar bisa dibandingkan berdampingan. */
    @Test
    @Config(qualifiers = "w1312dp-h816dp-land-mdpi")
    fun referenceSize() {
        compose.setContent { ScenePreview(playing(fakeCover()), RetroSettings()) }
        shot("ref_tablet_1312x816")
    }

    @Test
    @Config(qualifiers = "w1312dp-h816dp-land-mdpi")
    fun referenceSizeStandby() {
        compose.setContent { ScenePreview(NowPlaying(), RetroSettings()) }
        shot("ref_tablet_standby")
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp-land-mdpi")
    fun tabletKnobMode() {
        val k = KnobState().apply { active = true; turns = 0.12f; previewMs = 158_600L }
        compose.setContent { ScenePreview(playing(fakeCover()), RetroSettings(), k) }
        shot("ref_tablet_mode_knob")
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xxhdpi")
    fun phonePortrait() {
        compose.setContent { ScenePreview(playing(fakeCover()), RetroSettings()) }
        shot("ref_ponsel_portrait")
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp-land-mdpi")
    fun tabletLightRoomWalnut() {
        compose.setContent { ScenePreview(playing(fakeCover()), RetroSettings(tvBody = TvBody.WALNUT, room = RoomTone.LIGHT)) }
        shot("ref_tablet_terang_walnut")
    }
}
