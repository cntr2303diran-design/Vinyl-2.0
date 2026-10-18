package com.vinyl.launcher.screenshot

import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Process
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.vinyl.launcher.data.AppInfo
import com.vinyl.launcher.drawer.AppCategory
import com.vinyl.launcher.ui.taskbar.NavButtons
import com.vinyl.launcher.ui.taskbar.TaskbarBar
import com.vinyl.launcher.ui.taskbar.TaskbarInput
import com.vinyl.launcher.ui.taskbar.TaskbarModel
import com.vinyl.launcher.ui.taskbar.TaskbarPlanner
import com.vinyl.launcher.ui.theme.VinylTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Ikon rekaan (kotak berwarna + huruf), bukan ikon aplikasi sungguhan. */
private fun icon(color: Int, letter: String) = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888).also { b ->
    val c = Canvas(b)
    c.drawRoundRect(RectF(4f, 4f, 92f, 92f), 24f, 24f, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color })
    c.drawText(letter, 48f, 64f, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = -1; textSize = 44f; textAlign = Paint.Align.CENTER })
}.asImageBitmap()

private fun app(i: Int, name: String, color: Long) = AppInfo(
    key = "p$i/c$i", packageName = "contoh.app$i", label = name, icon = icon(color.toInt(), name.take(1)),
    component = ComponentName("contoh.app$i", "c$i"), user = Process.myUserHandle(), category = AppCategory.OTHER,
)

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TaskbarShots {
    @get:Rule
    val compose = createComposeRule()

    /** Taskbar panel VCR (favorit, terakhir, jendela VFD lagu, jam VFD) dan tombol navigasi remote. */
    @Test
    @Config(qualifiers = "w1280dp-h400dp-land-mdpi")
    fun vcrPanelAndRemoteKeys() {
        val favorites = listOf(app(0, "Kamera", 0xFF2F6F73), app(1, "Pesan", 0xFF3E5C8A), app(2, "Peta", 0xFF5C7A3E))
        val recents = listOf(app(3, "Catatan", 0xFFB0782F), app(4, "Galeri", 0xFF7A3E5A))
        val plan = TaskbarPlanner.plan(
            TaskbarInput(
                vertical = false, availableMainDp = 1200f, favorites = favorites.size, recentsCandidates = recents.size,
                nowPlaying = true, tray = true, fontScale = 1f, collapsed = false,
            ),
        )
        val model = MutableStateFlow(TaskbarModel(plan, favorites, recents.take(plan.recents), windowed = false))
        // Jam di tray berdetak tiap detik (loop tanpa akhir); jam uji dimajukan manual agar Compose bisa dianggap idle
        compose.mainClock.autoAdvance = false
        compose.setContent {
            VinylTheme {
                Column(
                    Modifier.fillMaxSize().background(Color(0xFF5E6A73)).padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(Modifier.size(plan.windowWidthDp.dp, plan.windowHeightDp.dp)) { TaskbarBar(model) }
                    NavButtons()
                }
            }
        }
        repeat(3) { compose.mainClock.advanceTimeByFrame() }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/tahap9_taskbar_vcr.png")
    }
}
