package com.vinyl.launcher.screenshot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.vinyl.launcher.data.CassetteShell
import com.vinyl.launcher.data.FilmRules
import com.vinyl.launcher.data.RetroSettings
import com.vinyl.launcher.data.TvBody
import com.vinyl.launcher.media.NowPlaying
import com.vinyl.launcher.ui.cassette.CassetteLabel
import com.vinyl.launcher.ui.film.FilmTray
import com.vinyl.launcher.ui.film.Tape
import com.vinyl.launcher.ui.film.TrayItem
import com.vinyl.launcher.ui.room.stage.DeckTape
import com.vinyl.launcher.ui.room.stage.StageDrawerInfo
import com.vinyl.launcher.ui.theme.OsdFamily
import com.vinyl.launcher.ui.theme.VinylTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Isi laci rekaan: tiga kaset bawaan. */
private fun trayItems() = FilmRules.defaults().map { c ->
    TrayItem(Tape.Film(c.id), c.name, FilmRules.hostOf(c.url), Color(c.color), c)
}

private fun drawerInfo(open: Boolean) = StageDrawerInfo(open, trayItems().map { it.color }, trayItems().size)

/** Film rekaan di TV: kaset "Domain Publik" sedang diputar 20:50 dari 1:30:00. */
private fun filmPlaying(): NowPlaying {
    val c = FilmRules.defaults().first().color
    return NowPlaying(
        hasSession = true, title = "Domain Publik", artist = "archive.org", durationMs = 5_400_000, isPlaying = true,
        positionMs = 1_250_000, dominantColor = c, accentColor = c, canSeek = true,
    )
}

private fun filmLabel(): CassetteLabel {
    val c = FilmRules.defaults().first()
    return CassetteLabel("film\u0000${c.id}", c.name, FilmRules.hostOf(c.url), null, CassetteShell.COLORED, Color(c.color))
}

/** Halaman web rekaan di layar TV (tanpa WebView sungguhan). */
@androidx.compose.runtime.Composable
private fun fakePage() {
    Box(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF22344F), Color(0xFF0B1019)))),
        contentAlignment = Alignment.Center,
    ) {
        Text("FILM DOMAIN PUBLIK", color = Color.White, fontFamily = OsdFamily)
    }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FilmShots {
    @get:Rule
    val compose = createComposeRule()

    private fun shot(name: String) = compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")

    @Test
    @Config(qualifiers = "w411dp-h891dp-xxhdpi")
    fun phoneDrawerClosed() {
        compose.setContent {
            StagePreview(playing(fakeCover()), RetroSettings(tvBody = TvBody.WALNUT), landscape = false, drawer = drawerInfo(false))
        }
        shot("tahap13_laci_ponsel_tertutup")
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xxhdpi")
    fun phoneDrawerOpen() {
        compose.setContent {
            StagePreview(playing(fakeCover()), RetroSettings(tvBody = TvBody.WALNUT), landscape = false, drawer = drawerInfo(true))
        }
        shot("tahap13_laci_ponsel_terbuka")
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp-land-mdpi")
    fun tabletDrawerOpen() {
        compose.setContent { StagePreview(playing(fakeCover()), RetroSettings(), drawer = drawerInfo(true)) }
        shot("tahap13_laci_tablet_terbuka")
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xxhdpi")
    fun phoneFilmTapeInTheDeck() {
        compose.setContent {
            StagePreview(
                filmPlaying(), RetroSettings(tvBody = TvBody.WALNUT), landscape = false,
                tape = DeckTape.FILM, tapeLabel = filmLabel(), tvScreen = { fakePage() }, drawer = drawerInfo(false),
            )
        }
        shot("tahap13_film_ponsel")
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xxhdpi")
    fun phoneTray() {
        compose.setContent {
            Box(Modifier.fillMaxSize()) {
                StagePreview(
                    filmPlaying(), RetroSettings(tvBody = TvBody.WALNUT), landscape = false,
                    tape = DeckTape.FILM, tapeLabel = filmLabel(), tvScreen = { fakePage() }, drawer = drawerInfo(true),
                )
                VinylTheme {
                    FilmTray(
                        items = trayItems(), current = trayItems().first().tape, canAdd = true,
                        onPress = {}, onAdd = {}, onEdit = {}, onRestoreDefaults = {}, onClose = {},
                    )
                }
            }
        }
        shot("tahap13_baki_ponsel")
    }

    @Test
    @Config(qualifiers = "w360dp-h780dp-xxhdpi", fontScale = 2.0f)
    fun smallPhoneTrayLargeFont() {
        compose.setContent {
            Box(Modifier.fillMaxSize()) {
                StagePreview(playing(fakeCover()), RetroSettings(tvBody = TvBody.CHARCOAL), landscape = false, drawer = drawerInfo(true))
                VinylTheme {
                    FilmTray(
                        items = trayItems(), current = Tape.Music, canAdd = true,
                        onPress = {}, onAdd = {}, onEdit = {}, onRestoreDefaults = {}, onClose = {},
                    )
                }
            }
        }
        shot("tahap13_baki_ponsel_kecil_font200")
    }

    @Test
    @Config(qualifiers = "ar-w1280dp-h800dp-land-mdpi")
    fun tabletTrayRtl() {
        compose.setContent {
            Box(Modifier.fillMaxSize()) {
                StagePreview(playing(fakeCover()), RetroSettings(), drawer = drawerInfo(true))
                VinylTheme {
                    FilmTray(
                        items = emptyList(), current = Tape.Music, canAdd = true,
                        onPress = {}, onAdd = {}, onEdit = {}, onRestoreDefaults = {}, onClose = {},
                    )
                }
            }
        }
        shot("tahap13_baki_kosong_rtl")
    }
}
