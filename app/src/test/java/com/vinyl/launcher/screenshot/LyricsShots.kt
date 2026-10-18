package com.vinyl.launcher.screenshot

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.vinyl.launcher.data.MusicSettings
import com.vinyl.launcher.media.NowPlaying
import com.vinyl.launcher.ui.lyrics.LyricsActions
import com.vinyl.launcher.ui.lyrics.LyricsScreen
import com.vinyl.launcher.ui.theme.VinylTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LyricsShots {
    @get:Rule
    val compose = createComposeRule()

    private val actions = LyricsActions({}, {}, {}, {}, {}, {}, {})

    /** Status "pencarian lirik mati" (tanpa jaringan) bergaya kartu teletext. */
    @Test
    @Config(qualifiers = "w1280dp-h800dp-land-mdpi")
    fun disabledState() {
        compose.setContent {
            // Dijeda: latar ambient beranimasi tanpa henti saat memutar, sehingga Compose tidak pernah idle untuk diambil gambarnya
            VinylTheme { LyricsScreen(playing(fakeCover()).copy(isPlaying = false), Color(0xFF3A2E22), MusicSettings(lyricsOnline = false), actions) }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/tahap9_lirik_teletext.png")
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xxhdpi")
    fun standbyPhone() {
        compose.setContent {
            VinylTheme { LyricsScreen(NowPlaying(), Color(0xFF1C1B19), MusicSettings(), actions) }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/tahap9_lirik_standby.png")
    }
}
