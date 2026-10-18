package com.vinyl.launcher.screenshot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.vinyl.launcher.data.HomeSettings
import com.vinyl.launcher.data.RetroSettings
import com.vinyl.launcher.ui.settings.HomeSection
import com.vinyl.launcher.ui.settings.HomeSectionControls
import com.vinyl.launcher.ui.theme.VinylTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsShots {
    @get:Rule
    val compose = createComposeRule()

    /** Bagian "TV & kaset" di Pengaturan → Layar utama (layar tinggi agar seluruh bagian terlihat). */
    @Test
    @Config(qualifiers = "w411dp-h2000dp-mdpi")
    fun tvSection() {
        compose.setContent {
            VinylTheme {
                Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState()).padding(16.dp)) {
                    HomeSectionControls(HomeSection.TV, HomeSettings(), RetroSettings(), {}, {})
                    HomeSectionControls(HomeSection.CLOCK, HomeSettings(), RetroSettings(), {}, {})
                }
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/tahap7_pengaturan_tv.png")
    }
}
