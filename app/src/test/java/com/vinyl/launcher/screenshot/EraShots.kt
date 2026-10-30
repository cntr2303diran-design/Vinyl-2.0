package com.vinyl.launcher.screenshot

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.vinyl.launcher.data.RetroSettings
import com.vinyl.launcher.data.RoomEra
import com.vinyl.launcher.data.RoomTone
import com.vinyl.launcher.data.TvBody
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Tema era di beranda: tiga era pada tablet, ponsel tegak (terang dan gelap), huruf 200%, dan RTL. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EraShots {
    @get:Rule
    val compose = createComposeRule()

    private fun shot(name: String) = compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")

    private fun retro(era: RoomEra, room: RoomTone = RoomTone.DIM) = RetroSettings(era = era, room = room, tvBody = TvBody.CREAM)

    @Test
    @Config(qualifiers = "w1312dp-h816dp-land-mdpi")
    fun tablet70s() { compose.setContent { StagePreview(playing(fakeCover()), retro(RoomEra.SEVENTIES)) }; shot("ruanghidup_era70_tablet") }

    @Test
    @Config(qualifiers = "w1312dp-h816dp-land-mdpi")
    fun tablet80s() { compose.setContent { StagePreview(playing(fakeCover()), retro(RoomEra.EIGHTIES)) }; shot("ruanghidup_era80_tablet") }

    @Test
    @Config(qualifiers = "w1312dp-h816dp-land-mdpi")
    fun tablet90s() { compose.setContent { StagePreview(playing(fakeCover()), retro(RoomEra.NINETIES)) }; shot("ruanghidup_era90_tablet") }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xxhdpi")
    fun phone70sDim() { compose.setContent { StagePreview(playing(fakeCover()), retro(RoomEra.SEVENTIES), landscape = false) }; shot("ruanghidup_era70_ponsel_gelap") }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xxhdpi")
    fun phone80sDim() { compose.setContent { StagePreview(playing(fakeCover()), retro(RoomEra.EIGHTIES), landscape = false) }; shot("ruanghidup_era80_ponsel_gelap") }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xxhdpi")
    fun phone90sDim() { compose.setContent { StagePreview(playing(fakeCover()), retro(RoomEra.NINETIES), landscape = false) }; shot("ruanghidup_era90_ponsel_gelap") }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xxhdpi")
    fun phone70sLight() { compose.setContent { StagePreview(playing(fakeCover()), retro(RoomEra.SEVENTIES, RoomTone.LIGHT), landscape = false) }; shot("ruanghidup_era70_ponsel_terang") }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xxhdpi")
    fun phone80sLight() { compose.setContent { StagePreview(playing(fakeCover()), retro(RoomEra.EIGHTIES, RoomTone.LIGHT), landscape = false) }; shot("ruanghidup_era80_ponsel_terang") }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xxhdpi")
    fun phone90sLight() { compose.setContent { StagePreview(playing(fakeCover()), retro(RoomEra.NINETIES, RoomTone.LIGHT), landscape = false) }; shot("ruanghidup_era90_ponsel_terang") }

    @Test
    @Config(qualifiers = "w360dp-h780dp-xxhdpi", fontScale = 2.0f)
    fun smallPhone80sLargeFont() { compose.setContent { StagePreview(playing(fakeCover()), retro(RoomEra.EIGHTIES), landscape = false) }; shot("ruanghidup_era80_font200") }

    @Test
    @Config(qualifiers = "ar-w1280dp-h800dp-land-mdpi")
    fun tabletRtl70s() { compose.setContent { StagePreview(playing(fakeCover()), retro(RoomEra.SEVENTIES)) }; shot("ruanghidup_era70_rtl") }
}
