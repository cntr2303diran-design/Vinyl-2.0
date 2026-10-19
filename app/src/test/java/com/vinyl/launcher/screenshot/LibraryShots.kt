package com.vinyl.launcher.screenshot

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.vinyl.launcher.data.MusicSettings
import com.vinyl.launcher.data.PlayerSettings
import com.vinyl.launcher.media.NowPlaying
import com.vinyl.launcher.player.HiFi
import com.vinyl.launcher.player.LocalLibrary
import com.vinyl.launcher.player.LocalTrack
import com.vinyl.launcher.player.PlaybackErrors
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

private const val FAKE_FOLDER = "content://contoh/tree/musik"

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

    private fun content(select: String? = null, tab: Int = 0, np: NowPlaying = playing(null).copy(title = "Lagu Percobaan", artist = "Artis Contoh")) {
        compose.setContent {
            val state = rememberLibraryState()
            LaunchedEffect(select) { state.selected = select }
            LaunchedEffect(tab) { state.tab = tab }
            CrateSheet(
                np = np,
                queue = emptyList(), history = fakeHistory,
                music = MusicSettings(shelfStyle = ShelfStyle.FLOW, favorites = setOf(LibraryCatalog.songKey("Gelombang", "Citra Laut"))),
                onMusicChange = {}, onClose = {}, onClearHistory = {},
                state = state, onToggleFavorite = {},
                player = PlayerSettings(folderUri = FAKE_FOLDER, folderName = "Musik"),
                videoFolder = null,
                onPlayer = {},
            )
        }
    }

    /** Tab Lokal dengan berkas rekaan (judul, artis, dan format buatan; tidak membaca perangkat). */
    private fun localContent() {
        val tracks = listOf(
            LocalTrack("1", "Pagi di Dermaga", "Arunika", "Pelabuhan", trackNo = 1, mime = "audio/flac", fileName = "01.flac", sampleRate = 96_000, bitsPerSample = 24, durationMs = 245_000),
            LocalTrack("2", "Hujan Bulan Juni", "Arunika", "Pelabuhan", trackNo = 2, mime = "audio/flac", fileName = "02.flac", sampleRate = 96_000, bitsPerSample = 24, durationMs = 312_000),
            LocalTrack("3", "Kota Tua", "Arunika", "Pelabuhan", trackNo = 3, mime = "audio/flac", fileName = "03.flac", sampleRate = 96_000, bitsPerSample = 24, durationMs = 198_000),
            LocalTrack("4", "Neon Lama", "Band Kertas", "Radio Malam", trackNo = 1, mime = "audio/mpeg", fileName = "neon.mp3", bitrate = 320_000, durationMs = 221_000),
            LocalTrack("5", "Lagu dengan judul yang sangat panjang sekali untuk menguji pemotongan teks", "Band Kertas", "Radio Malam", trackNo = 2, mime = "audio/wav", fileName = "b.wav", sampleRate = 44_100, bitsPerSample = 16, durationMs = 3_725_000),
            // Format yang belum didukung pemutar bawaan: tetap tampil dengan keterangannya, tanpa aksi putar
            LocalTrack("6", "Rekaman Studio", "Band Kertas", "Radio Malam", trackNo = 3, mime = "audio/x-aiff", fileName = "c.aiff", sampleRate = 48_000, bitsPerSample = 24, durationMs = 254_000),
        )
        LocalLibrary.preview(FAKE_FOLDER, tracks)
        HiFi.updatePath {
            HiFi.PathState(
                active = true, trackId = "2", codec = "FLAC", sourceRate = 96_000, sourceBits = 24, channels = 2, lossless = true,
                // Keterangan lagu yang dilewati pemutar (kode 4005 = dekoder tidak mendukung format)
                skipped = PlaybackErrors.message("Arsip Lama", 4005, skipped = true),
            )
        }
        content(tab = 4, np = playing(null).copy(title = "Hujan Bulan Juni", artist = "Arunika", packageName = "com.vinyl.launcher"))
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xxhdpi")
    fun phoneLocal() {
        localContent()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/tahap11_lokal_ponsel.png")
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp-land-mdpi")
    fun tabletLocal() {
        localContent()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/tahap11_lokal_tablet.png")
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
