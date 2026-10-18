package com.vinyl.launcher.screenshot

import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Process
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.vinyl.launcher.data.AppInfo
import com.vinyl.launcher.data.DrawerSettings
import com.vinyl.launcher.drawer.AppCategory
import com.vinyl.launcher.drawer.UsageData
import com.vinyl.launcher.media.NowPlaying
import com.vinyl.launcher.ui.drawer.AppDrawer
import com.vinyl.launcher.ui.drawer.DrawerActions
import com.vinyl.launcher.ui.drawer.DrawerData
import com.vinyl.launcher.ui.theme.VinylTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Ikon rekaan: kotak membulat berwarna dengan huruf awal (bukan ikon aplikasi sungguhan). */
private fun fakeIcon(color: Int, letter: String) = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888).also { b ->
    val c = Canvas(b)
    c.drawRoundRect(RectF(4f, 4f, 92f, 92f), 24f, 24f, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color })
    c.drawText(letter, 48f, 64f, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = -1; textSize = 44f; textAlign = Paint.Align.CENTER })
}.asImageBitmap()

private val names = listOf(
    "Kalender", "Kamera", "Galeri", "Musik Pita", "Peta", "Catatan", "Cuaca", "Pesan", "Telepon", "Jam", "Kalkulator",
    "Berkas", "Perekam Suara", "Toko Buku", "Aplikasi Dengan Nama Sangat Panjang Sekali", "Podcast", "Radio", "Kontak",
)
private val colors = listOf(0xFF8C5A2B, 0xFF2F6F73, 0xFF7A3E5A, 0xFFB0782F, 0xFF3E5C8A, 0xFF5C7A3E).map { it.toInt() }

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DrawerShots {
    @get:Rule
    val compose = createComposeRule()

    @Test
    @Config(qualifiers = "w1280dp-h800dp-land-mdpi")
    fun drawerTablet() {
        val apps = names.mapIndexed { i, n ->
            AppInfo(
                key = "p$i/c$i", packageName = "contoh.app$i", label = n, icon = fakeIcon(colors[i % colors.size], n.take(1)),
                component = ComponentName("contoh.app$i", "c$i"), user = Process.myUserHandle(),
                category = AppCategory.entries[i % AppCategory.entries.size],
            )
        }
        val data = DrawerData(apps, DrawerSettings(), UsageData(), emptyMap(), emptyList(), false, emptyList())
        val actions = DrawerActions(
            {}, {}, {}, {}, {}, { _, _ -> }, { emptyList() }, {}, {}, {}, { emptyList() }, {}, {},
        )
        compose.setContent { VinylTheme { AppDrawer(data, Color(0xFF1A1A18), NowPlaying(), actions) } }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/tahap7_drawer_tablet.png")
    }
}
