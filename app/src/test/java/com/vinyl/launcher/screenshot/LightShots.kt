package com.vinyl.launcher.screenshot

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.vinyl.launcher.data.LightLevel
import com.vinyl.launcher.data.ProjectorSettings
import com.vinyl.launcher.data.RetroSettings
import com.vinyl.launcher.data.RoomEra
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Cahaya proyektor yang lebih hidup: berkas cahaya, kilau kaca TV, pantulan meja, dan debu (diam, fase tetap). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LightShots {
    @get:Rule
    val compose = createComposeRule()

    private fun shot(name: String) = compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")

    private fun retro(level: LightLevel, era: RoomEra = RoomEra.DEFAULT) =
        RetroSettings(era = era, projector = ProjectorSettings(light = level))

    @Test
    @Config(qualifiers = "w1312dp-h816dp-land-mdpi")
    fun tabletNormal() { compose.setContent { StagePreview(playing(fakeCover()), retro(LightLevel.NORMAL)) }; shot("ruanghidup_cahaya_tablet_normal") }

    @Test
    @Config(qualifiers = "w1312dp-h816dp-land-mdpi")
    fun tabletSoft() { compose.setContent { StagePreview(playing(fakeCover()), retro(LightLevel.SOFT)) }; shot("ruanghidup_cahaya_tablet_lembut") }

    @Test
    @Config(qualifiers = "w1312dp-h816dp-land-mdpi")
    fun tabletOff() { compose.setContent { StagePreview(playing(fakeCover()), retro(LightLevel.OFF)) }; shot("ruanghidup_cahaya_tablet_mati") }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xxhdpi")
    fun phoneNormal() { compose.setContent { StagePreview(playing(fakeCover()), retro(LightLevel.NORMAL), landscape = false) }; shot("ruanghidup_cahaya_ponsel_normal") }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xxhdpi")
    fun phoneNormal80s() { compose.setContent { StagePreview(playing(fakeCover()), retro(LightLevel.NORMAL, RoomEra.EIGHTIES), landscape = false) }; shot("ruanghidup_cahaya_ponsel_era80") }

    @Test
    @Config(qualifiers = "w915dp-h412dp-land-xxhdpi")
    fun phoneLandscapeNormal() { compose.setContent { StagePreview(playing(fakeCover()), retro(LightLevel.NORMAL)) }; shot("ruanghidup_cahaya_ponsel_mendatar") }
}
