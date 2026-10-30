package com.vinyl.launcher.screenshot

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.vinyl.launcher.data.AchievementInput
import com.vinyl.launcher.data.Achievements
import com.vinyl.launcher.data.RetroSettings
import com.vinyl.launcher.data.RoomEra
import com.vinyl.launcher.media.NowPlaying
import com.vinyl.launcher.ui.room.stage.PropActions
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Benda ruangan: jam 07.05 (tetap), poster berisi cover, rak piala berisi tiga piala, buku harian merekam, radio menyala, telepon. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PropShots {
    @get:Rule
    val compose = createComposeRule()

    private fun shot(name: String) = compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")

    private val props = PropActions(
        radioSleepActive = true,
        phoneFavorites = mapOf(1 to "Ibu", 5 to "Kantor"),
        trophies = Achievements.compute(AchievementInput(snakeBest = 30, breakoutBest = 160, listenedMs = 12 * Achievements.HOUR_MS, songs = 60)),
        diaryRecording = true, diaryCount = 14,
    )

    @Test
    @Config(qualifiers = "w1312dp-h816dp-land-mdpi")
    fun tabletAllProps() { compose.setContent { StagePreview(playing(fakeCover()), RetroSettings(), props = props) }; shot("ruanghidup_benda_tablet") }

    @Test
    @Config(qualifiers = "w1312dp-h816dp-land-mdpi")
    fun tablet90s() { compose.setContent { StagePreview(playing(fakeCover()), RetroSettings(era = RoomEra.NINETIES), props = props) }; shot("ruanghidup_benda_tablet_era90") }

    @Test
    @Config(qualifiers = "w1312dp-h816dp-land-mdpi")
    fun tabletEmptyShelfAndNoCover() { compose.setContent { StagePreview(NowPlaying(), RetroSettings(), props = PropActions()) }; shot("ruanghidup_benda_tablet_kosong") }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xxhdpi")
    fun phonePortrait() { compose.setContent { StagePreview(playing(fakeCover()), RetroSettings(), landscape = false, props = props) }; shot("ruanghidup_benda_ponsel") }

    @Test
    @Config(qualifiers = "w915dp-h412dp-land-xxhdpi")
    fun phoneLandscape() { compose.setContent { StagePreview(playing(fakeCover()), RetroSettings(), props = props) }; shot("ruanghidup_benda_ponsel_mendatar") }
}
