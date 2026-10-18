package com.vinyl.launcher.screenshot

import android.content.Intent
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.vinyl.launcher.audio.ActiveOutput
import com.vinyl.launcher.audio.AdvancedReadings
import com.vinyl.launcher.audio.AudioSnapshot
import com.vinyl.launcher.audio.Automation
import com.vinyl.launcher.audio.DeviceKind
import com.vinyl.launcher.audio.EqSession
import com.vinyl.launcher.audio.OutputDevice
import com.vinyl.launcher.audio.Reading
import com.vinyl.launcher.audio.SessionOrigin
import com.vinyl.launcher.audio.SessionState
import com.vinyl.launcher.audio.SystemAudioShortcuts
import com.vinyl.launcher.data.AudioConsoleRules
import com.vinyl.launcher.data.AudioConsoleSettings
import com.vinyl.launcher.ui.console.ConsoleActions
import com.vinyl.launcher.ui.console.ConsoleScreen
import com.vinyl.launcher.ui.console.ConsoleUi
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private val buds = OutputDevice(7, DeviceKind.BLUETOOTH, "Buds Pro", address = "AA:BB")
private val speaker = OutputDevice(1, DeviceKind.SPEAKER, "Speaker perangkat")

/** Data rekaan untuk tangkapan layar (bukan hasil baca perangkat). */
internal fun consoleUi(eqOn: Boolean = true): ConsoleUi {
    var audio = AudioConsoleRules.applyPreset(AudioConsoleSettings(eqEnabled = eqOn), "Bass hangat")
    audio = AudioConsoleRules.setProfile(audio, buds, "Bass hangat")
    return ConsoleUi(
        snapshot = AudioSnapshot(
            devices = listOf(speaker, buds),
            inputs = listOf("Mikrofon bawaan"),
            active = ActiveOutput(buds, Reading.reported("Buds Pro", "Rute media dilaporkan sistem")),
            nativeRate = Reading.reported("48 kHz", "Sample rate keluaran bawaan sistem"),
            latency = Reading.estimated("≥ 5,0 ms", "Satu buffer keluaran (bukan latensi ujung-ke-ujung)"),
            capability = Reading.reported("Format apa saja", "Perangkat tidak membatasi daftar format (biasanya di-mix sistem)"),
            bitPerfect = Reading.unavailable("Hanya berlaku untuk perangkat USB"),
            spatial = Reading.reported("Tersedia, mati", "Nyalakan di pengaturan suara sistem"),
            headTracking = Reading.reported("Tidak tersedia"),
            musicActive = true,
        ),
        advanced = AdvancedReadings(),
        audio = audio,
        sessions = listOf(EqSession(4321, "com.contoh.musik", SessionOrigin.APP, SessionState.ACTIVE)),
        log = listOf(Automation.Result("Profil Buds Pro", Automation.Outcome.DONE, "Profil Buds Pro diterapkan: \"Bass hangat\"", 0L)),
        shortcuts = listOf(
            SystemAudioShortcuts.Shortcut(SystemAudioShortcuts.Kind.SOUND_SETTINGS, "Pengaturan suara", "Setelan", Intent("a"), false),
            SystemAudioShortcuts.Shortcut(SystemAudioShortcuts.Kind.VOLUME_PANEL, "Panel volume & keluaran", "Setelan", Intent("b"), false),
            SystemAudioShortcuts.Shortcut(SystemAudioShortcuts.Kind.BLUETOOTH, "Bluetooth", "Setelan", Intent("c"), false),
            SystemAudioShortcuts.Shortcut(SystemAudioShortcuts.Kind.EFFECTS_PANEL, "Panel efek audio sistem", "Efek suara", Intent("d"), true),
        ),
        tone = null,
        micGranted = false,
        advancedAccess = Reading.unavailable("Belum diberikan"),
        isSamsung = true,
    )
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ConsoleShots {
    @get:Rule
    val compose = createComposeRule()

    private val actions = ConsoleActions({}, {}, {}, {}, {})

    private fun shot(name: String) = compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")

    @Test
    @Config(qualifiers = "w1280dp-h800dp-land-mdpi")
    fun tablet() {
        compose.setContent { ConsoleScreen(consoleUi(), actions) }
        shot("tahap6_console_tablet")
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xxhdpi")
    fun phone() {
        compose.setContent { ConsoleScreen(consoleUi(eqOn = false), actions) }
        shot("tahap6_console_ponsel")
    }

    /** Seluruh isi halaman ponsel dalam satu gambar (layar sangat tinggi agar semua panel terlihat). */
    @Test
    @Config(qualifiers = "w411dp-h3000dp-mdpi")
    fun phoneFullPage() {
        compose.setContent { ConsoleScreen(consoleUi(), actions) }
        shot("tahap6_console_ponsel_penuh")
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xxhdpi", fontScale = 2.0f)
    fun phoneLargeFont() {
        compose.setContent { ConsoleScreen(consoleUi(), actions) }
        shot("tahap6_console_ponsel_font200")
    }

    @Test
    @Config(qualifiers = "ar-w1280dp-h800dp-land-mdpi")
    fun tabletRtl() {
        compose.setContent { ConsoleScreen(consoleUi(), actions) }
        shot("tahap6_console_rtl")
    }
}
