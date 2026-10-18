package com.vinyl.launcher.screenshot

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.vinyl.launcher.data.MusicSettings
import com.vinyl.launcher.data.PlayRecord
import com.vinyl.launcher.data.ShelfStyle
import com.vinyl.launcher.ui.crate.CrateSheet
import com.vinyl.launcher.ui.library.LibraryCatalog
import com.vinyl.launcher.ui.library.rememberLibraryState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Koleksi buatan (judul & artis rekaan) untuk tangkapan layar rak kaset. */
private val fakeHistory: List<PlayRecord> = listOf(
    "Senja di Pelabuhan" to "Arunika", "Hujan Bulan Juni" to "Arunika", "Kota Tua" to "Arunika",
    "Neon Lama" to "Band Kertas", "Radio Malam" to "Band Kertas",
    "Lagu Percobaan" to "Artis Contoh", "Gelombang" to "Citra Laut", "Pasir Putih" to "Citra Laut",
    "Balada Kaset" to "Dewa Pita", "Putar Ulang" to "Dewa Pita", "Sisi B" to "Dewa Pita",
    "Lampu Kota" to "Elang Senja", "Tanpa Judul" to "Fajar", "1999" to "99 Kelana",
).mapIndexed { i, (t, a) ->
    PlayRecord(
        key = LibraryCatalog.songKey(t, a), title = t, artist = a, album = if (a == "Arunika") "Pelabuhan" else "",
        firstPlayed = i.toLong(), lastPlayed = 1_000L * i, plays = 1 + i % 4,
    )
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LibraryShots {
    @get:Rule
    val compose = createComposeRule()

    private fun content(select: String? = null) {
        compose.setContent {
            val state = rememberLibraryState()
            LaunchedEffect(select) { state.selected = select }
            CrateSheet(
                np = playing(null).copy(title = "Lagu Percobaan", artist = "Artis Contoh"),
                queue = emptyList(), history = fakeHistory,
                music = MusicSettings(shelfStyle = ShelfStyle.FLOW, favorites = setOf(LibraryCatalog.songKey("Gelombang", "Citra Laut"))),
                onMusicChange = {}, onClose = {}, onClearHistory = {},
                state = state, onToggleFavorite = {},
            )
        }
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xxhdpi")
    fun phoneRack() {
        content()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/tahap5_ponsel_rak.png")
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp-land-mdpi")
    fun tabletRack() {
        content()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/tahap5_tablet_rak.png")
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xxhdpi")
    fun phoneDetail() {
        content(select = LibraryCatalog.songKey("Hujan Bulan Juni", "Arunika"))
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/tahap5_ponsel_detail.png")
    }

}
