package com.vinyl.launcher.screenshot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.vinyl.launcher.ui.theme.RetroColors
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Tangkapan layar palet tema TV tabung (Tahap 1). Tujuan utamanya membuktikan bahwa jalur render tanpa HP bekerja
 * di CI sebelum layar sungguhan dibuat; sekaligus menunjukkan pasangan teks/latar yang dijaga RetroPaletteTest.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class RetroPaletteShot {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun palette() {
        compose.setContent {
            Column(Modifier.background(RetroColors.WarmWhite).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Ruang putih — tinta utama", color = RetroColors.Ink, fontSize = 18.sp)
                Text("Tinta sekunder", color = RetroColors.InkSecondary)
                Text("Tinta tersier", color = RetroColors.InkTertiary)
                Swatch("Krem", RetroColors.CreamBeige, RetroColors.Ink)
                Swatch("Walnut", RetroColors.Walnut, RetroColors.CreamText)
                Swatch("Charcoal", RetroColors.Charcoal, RetroColors.CreamText)
                Swatch("Amber di charcoal", RetroColors.Charcoal, RetroColors.Amber)
                Swatch("VFD amber 12:34", RetroColors.VfdGlass, RetroColors.VfdAmber)
                Swatch("VFD hijau 44.1 kHz", RetroColors.VfdGlass, RetroColors.VfdGreen)
                Row(Modifier.fillMaxWidth().height(40.dp)) {
                    listOf(RetroColors.WallTop, RetroColors.WarmWhite, RetroColors.Floor, RetroColors.Corner).forEach {
                        Box(Modifier.weight(1f).height(40.dp).background(it))
                    }
                }
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/tahap1_palet.png")
    }

    @androidx.compose.runtime.Composable
    private fun Swatch(name: String, bg: Color, fg: Color) {
        Box(Modifier.width(360.dp).height(44.dp).background(bg).padding(12.dp)) { Text(name, color = fg) }
    }
}
