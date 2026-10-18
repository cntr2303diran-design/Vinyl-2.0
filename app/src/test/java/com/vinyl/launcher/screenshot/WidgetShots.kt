package com.vinyl.launcher.screenshot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.vinyl.launcher.data.WeatherInfo
import com.vinyl.launcher.ui.room.NeedleBattery
import com.vinyl.launcher.ui.room.PolaroidWidget
import com.vinyl.launcher.ui.room.TearCalendarWidget
import com.vinyl.launcher.ui.room.VfdWeather
import com.vinyl.launcher.ui.theme.VinylTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WidgetShots {
    @get:Rule
    val compose = createComposeRule()

    /** Keempat widget beranda di atas dinding ruang gelap (data rekaan). */
    @Test
    @Config(qualifiers = "w1100dp-h420dp-land-mdpi")
    fun roomWidgets() {
        compose.setContent {
            VinylTheme {
                Row(
                    Modifier.fillMaxSize().background(Color(0xFF1F2228)).padding(32.dp),
                    horizontalArrangement = Arrangement.spacedBy(36.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TearCalendarWidget(LocalDate.of(2026, 10, 18))
                    VfdWeather(WeatherInfo(28.4, 1, "Jakarta"), "Jakarta")
                    NeedleBattery(72, charging = true)
                    PolaroidWidget(emptyList())
                }
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/tahap9_widget.png")
    }
}
