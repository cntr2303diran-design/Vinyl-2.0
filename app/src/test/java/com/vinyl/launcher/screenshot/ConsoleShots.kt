package com.vinyl.launcher.screenshot

import android.content.Intent
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.vinyl.launcher.advanced.AdvancedAccess
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
import com.vinyl.launcher.data.PlayerSettings
import com.vinyl.launcher.audio.EqMode
import com.vinyl.launcher.audio.dsp.CrossfeedLevel
import com.vinyl.launcher.audio.dsp.EqBand
import com.vinyl.launcher.audio.dsp.FilterType
import com.vinyl.launcher.audio.dsp.ParametricProfile
import com.vinyl.launcher.player.HiFi
import com.vinyl.launcher.ui.console.ConsoleActions
import com.vinyl.launcher.ui.console.ConsoleScreen
import com.vinyl.launcher.ui.console.ConsoleUi
import com.vinyl.launcher.ui.theme.VinylTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private val buds = OutputDevice(7, DeviceKind.BLUETOOTH, "Buds Pro", address = "AA:BB")
private val speaker = OutputDevice(1, DeviceKind.SPEAKER, "Speaker perangkat")

/** Data rekaan untuk tangkapan layar (bukan hasil baca perangkat). */
internal fun consoleUi(eqOn: Boolean = true, granted: Boolean = false): ConsoleUi {
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
            headTracking = Reading.reported("Tidak didukung"),
            musicActive = true,
        ),
        advanced = if (!granted) AdvancedReadings() else AdvancedReadings(
            codec = Reading.reported("AAC · 44,1 kHz · 16-bit", "Dari laporan diagnostik sistem (Bluetooth)"),
            outputFormat = Reading.reported("48 kHz · PCM 16-bit", "Dari laporan diagnostik sistem (utas AudioOut_2D)"),
            resampling = Reading.reported("Ya · 44,1 kHz → 48 kHz", "Dari laporan diagnostik sistem: pemutar dan keluaran berbeda sample rate"),
            battery = Reading.unavailable("Laporan diagnostik sistem tidak memuat baterai perangkat Bluetooth"),
        ),
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
        access = if (granted) AdvancedAccess.State(dumpGranted = true, shizuku = AdvancedAccess.ShizukuState.READY,
            lastGrant = Reading.reported("Izin DUMP diberikan", "Lewat Shizuku"))
        else AdvancedAccess.State(shizuku = AdvancedAccess.ShizukuState.NOT_RUNNING),
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
        compose.setContent { VinylTheme { ConsoleScreen(consoleUi(), actions) } }
        shot("tahap6_console_tablet")
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xxhdpi")
    fun phone() {
        compose.setContent { VinylTheme { ConsoleScreen(consoleUi(eqOn = false), actions) } }
        shot("tahap6_console_ponsel")
    }

    /** Seluruh isi halaman ponsel dalam satu gambar (layar sangat tinggi agar semua panel terlihat). */
    @Test
    @Config(qualifiers = "w411dp-h4400dp-mdpi")
    fun phoneFullPage() {
        compose.setContent { VinylTheme { ConsoleScreen(consoleUi(), actions) } }
        shot("tahap6_console_ponsel_penuh")
    }

    /** Akses lanjutan aktif: nilai codec/format/resampling terisi (data rekaan), layar tinggi agar semua panel terlihat. */
    @Test
    @Config(qualifiers = "w1280dp-h1900dp-mdpi")
    fun tabletAdvancedFull() {
        compose.setContent { VinylTheme { ConsoleScreen(consoleUi(granted = true), actions) } }
        shot("tahap6_console_tablet_akses_lanjutan")
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xxhdpi", fontScale = 2.0f)
    fun phoneLargeFont() {
        compose.setContent { VinylTheme { ConsoleScreen(consoleUi(), actions) } }
        shot("tahap6_console_ponsel_font200")
    }

    /** Pemutar bawaan memutar FLAC 24/96 dengan profil EQ hasil impor & ReplayGain (data rekaan). */
    @Test
    @Config(qualifiers = "w1280dp-h1900dp-mdpi")
    fun tabletPlayerChain() {
        val profile = ParametricProfile(
            "Headphone uji", preampDb = -6.2f,
            bands = listOf(
                EqBand(FilterType.LOW_SHELF, 105f, 6.2f, 0.7f), EqBand(FilterType.PEAK, 2245f, -3.4f, 1.29f),
                EqBand(FilterType.PEAK, 5600f, 4.1f, 3f), EqBand(FilterType.HIGH_SHELF, 10_000f, -2f, 0.7f),
            ),
        )
        val ui = consoleUi().let { base ->
            base.copy(
                audio = base.audio.copy(eqMode = EqMode.PROFILE, profile = profile),
                player = PlayerSettings(eqEnabled = true, eq = profile, savedEqs = listOf(profile), crossfeed = CrossfeedLevel.NATURAL),
                path = HiFi.PathState(
                    active = true, trackId = "x", codec = "FLAC", sourceRate = 96_000, sourceBits = 24, channels = 2, lossless = true,
                    decoded = "float 32-bit", output = "float 32-bit", replayGainDb = -7.2f, eqName = profile.name,
                    crossfeed = CrossfeedLevel.NATURAL, limiter = false, transparent = false,
                ),
            )
        }
        compose.setContent { VinylTheme { ConsoleScreen(ui, actions) } }
        shot("tahap11_console_pemutar")
    }

    @Test
    @Config(qualifiers = "ar-w1280dp-h800dp-land-mdpi")
    fun tabletRtl() {
        compose.setContent { VinylTheme { ConsoleScreen(consoleUi(), actions) } }
        shot("tahap6_console_rtl")
    }
}
