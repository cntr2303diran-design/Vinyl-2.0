package com.vinyl.launcher.screenshot

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.vinyl.launcher.data.DiaryEntry
import com.vinyl.launcher.data.DiaryFlags
import com.vinyl.launcher.data.DiaryKind
import com.vinyl.launcher.data.TrophyGoal
import com.vinyl.launcher.data.TrophyGoals
import com.vinyl.launcher.ui.settings.DiaryList
import com.vinyl.launcher.ui.settings.DiaryRecordingPanel
import com.vinyl.launcher.ui.settings.TrophyGoalsEditor
import com.vinyl.launcher.ui.theme.VinylTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Pengaturan → Benda ruangan: saklar buku harian (musik, film, game), isi buku harian bercampur jenis, dan editor ambang
 * piala. Dirender di ponsel, ponsel kecil dengan font 200%, dan tablet RTL (hanya direkam, tidak dibandingkan). Layar
 * Pengaturan memakai skema warna gelap saja, jadi tidak ada varian terang.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PrivacyShots {
    @get:Rule
    val compose = createComposeRule()

    private fun shot(name: String) = compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")

    private val base = 1_760_000_000_000L
    private val entries = listOf(
        DiaryEntry(base, "Blue in Green", "Miles Davis"),
        DiaryEntry(base + 600_000, "Kaset film dengan nama yang sangat panjang sekali untuk menguji pemotongan teks di baris judul", kind = DiaryKind.FILM),
        DiaryEntry(base + 1_200_000, "Ular", "Skor 41 · rekor baru", DiaryKind.GAME),
        DiaryEntry(base + 90_000_000, "Bata", "Skor 120", DiaryKind.GAME),
    )
    private val custom = TrophyGoals(snake = TrophyGoal(5, 12, 30), listen = TrophyGoal(2, 5, 20))

    private fun full() {
        compose.setContent {
            VinylTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
                        DiaryRecordingPanel(DiaryFlags(on = true, film = false), saveFailed = true, onRecording = {}, onKind = { _, _ -> })
                        DiaryList(entries, onDelete = {})
                        TrophyGoalsEditor(custom) {}
                    }
                }
            }
        }
    }

    private fun empty() {
        compose.setContent {
            VinylTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
                        DiaryRecordingPanel(DiaryFlags(), saveFailed = false, onRecording = {}, onKind = { _, _ -> })
                        DiaryList(emptyList(), onDelete = {})
                        TrophyGoalsEditor(TrophyGoals()) {}
                    }
                }
            }
        }
    }

    @Test
    @Config(qualifiers = "w411dp-h2000dp-mdpi")
    fun phone() { full(); shot("privasi_ponsel") }

    @Test
    @Config(qualifiers = "w411dp-h1200dp-mdpi")
    fun phoneEverythingOff() { empty(); shot("privasi_ponsel_kosong") }

    @Test
    @Config(qualifiers = "w360dp-h2800dp-mdpi", fontScale = 2.0f)
    fun smallPhoneLargeFont() { full(); shot("privasi_ponsel_kecil_font200") }

    @Test
    @Config(qualifiers = "ar-w800dp-h1800dp-mdpi")
    fun tabletRtl() { full(); shot("privasi_tablet_rtl") }
}
