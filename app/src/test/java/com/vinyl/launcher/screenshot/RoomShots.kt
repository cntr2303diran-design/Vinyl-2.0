package com.vinyl.launcher.screenshot

import android.graphics.Bitmap
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.vinyl.launcher.data.RetroSettings
import com.vinyl.launcher.data.RoomTone
import com.vinyl.launcher.data.TvBody
import com.vinyl.launcher.media.NowPlaying
import com.vinyl.launcher.data.CassetteShell
import com.vinyl.launcher.system.Bluetooth
import com.vinyl.launcher.system.DeviceStatus
import com.vinyl.launcher.system.NotificationIcons
import com.vinyl.launcher.system.Ringer
import com.vinyl.launcher.system.StatusText
import com.vinyl.launcher.system.Wifi
import com.vinyl.launcher.ui.room.stage.DeckDisplay
import com.vinyl.launcher.ui.room.stage.ProjectionContent
import com.vinyl.launcher.ui.room.stage.RoomStage
import com.vinyl.launcher.ui.room.stage.SeekState
import com.vinyl.launcher.ui.room.stage.StageActions
import com.vinyl.launcher.ui.room.stage.StageInfo
import com.vinyl.launcher.ui.room.vhs.OsdActions
import com.vinyl.launcher.ui.room.vhs.OsdState
import com.vinyl.launcher.ui.room.vhs.VhsFilm
import com.vinyl.launcher.ui.room.vhs.VhsOsd
import com.vinyl.launcher.ui.theme.VinylTheme
import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Cover buatan untuk tes: langit senja, bulan sabit, bukit pasir (bukan gambar berhak cipta). */
internal fun fakeCover(): ImageBitmap {
    val s = 600f
    val bmp = Bitmap.createBitmap(600, 600, Bitmap.Config.ARGB_8888)
    val c = android.graphics.Canvas(bmp)
    c.drawRect(0f, 0f, s, s, Paint().apply { shader = LinearGradient(0f, 0f, 0f, s * 0.7f, 0xFF5E7FA8.toInt(), 0xFFC9C6C0.toInt(), Shader.TileMode.CLAMP) })
    c.drawCircle(s * 0.55f, s * 0.12f, s * 0.035f, Paint().apply { color = 0xFFF4F1E8.toInt(); isAntiAlias = true })
    c.drawCircle(s * 0.565f, s * 0.11f, s * 0.033f, Paint().apply { color = 0xFF7E97B5.toInt(); isAntiAlias = true })
    val dune = Path().apply { moveTo(0f, s * 0.75f); quadTo(s * 0.45f, s * 0.22f, s * 0.7f, s * 0.5f); quadTo(s * 0.85f, s * 0.62f, s, s * 0.6f); lineTo(s, s); lineTo(0f, s); close() }
    c.drawPath(dune, Paint().apply { shader = LinearGradient(0f, s * 0.3f, s * 0.6f, s, 0xFFD3A774.toInt(), 0xFF7A5634.toInt(), Shader.TileMode.CLAMP); isAntiAlias = true })
    val dune2 = Path().apply { moveTo(0f, s * 0.86f); quadTo(s * 0.5f, s * 0.68f, s, s * 0.84f); lineTo(s, s); lineTo(0f, s); close() }
    c.drawPath(dune2, Paint().apply { color = 0xFF5B3E26.toInt(); isAntiAlias = true })
    return bmp.asImageBitmap()
}

internal fun playing(art: ImageBitmap?) = NowPlaying(
    hasSession = true, title = "Desert Moon", artist = "Dune Wanderer", album = "Album", art = art,
    durationMs = 258_000, isPlaying = true, positionMs = 155_000, accentColor = 0xFFD3A774.toInt(), canSeek = true,
)

/** Status perangkat rekaan untuk OSD (tidak membaca perangkat sungguhan). */
internal fun fakeStatus() = DeviceStatus(
    batteryLevel = 72, charging = false, wifi = Wifi.CONNECTED, wifiLevel = 3, hasCellular = true, cellLevel = 2,
    bluetooth = Bluetooth.CONNECTED, bluetoothDevice = "Buds Pro", bluetoothBattery = "70%", ringer = Ringer.VIBRATE, nextAlarm = LocalTime.of(6, 30),
    notifications = 3, vpn = true,
)

/** Ikon notifikasi rekaan (lonceng putih), bukan ikon aplikasi sungguhan. */
internal fun fakeNotificationIcon(): ImageBitmap {
    val b = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888)
    val c = android.graphics.Canvas(b)
    val p = Paint().apply { color = 0xFFFFFFFF.toInt(); isAntiAlias = true }
    c.drawRoundRect(8f, 8f, 40f, 40f, 8f, 8f, p)
    return b.asImageBitmap()
}

/** Beranda lengkap (panggung + film + OSD) dengan data rekaan dan waktu tetap, tanpa animasi. */
@Composable
internal fun StagePreview(np: NowPlaying, retro: RetroSettings, seek: SeekState? = null, tone: RoomTone = retro.room, landscape: Boolean = true) {
    val s = seek ?: remember { SeekState() }
    VinylTheme {
        Box(Modifier.fillMaxSize().background(Color(0xFF07080B))) {
            val transport = StatusText.transport(np.hasTrack, np.isPlaying, s.deltaMs?.takeIf { s.touching })
            RoomStage(
                np = np, retro = retro, tone = tone, tvSize = 0.85f, accent = Color(0xFFD3A774), motionAllowed = false,
                editing = false, positionMs = np.positionMs, titleScrimAlpha = 0.25f, seek = s,
                info = StageInfo(
                    tvVfd = if (np.hasTrack) "PLAY" else "STANDBY",
                    deck = DeckDisplay(StatusText.modeLine(transport) + if (np.hasTrack) " SP" else "", "48K BUDS PRO"),
                    tapeTime = if (np.durationMs > 0) "4:18" else "",
                ),
                actions = StageActions({}, {}, {}, {}, {}, {}, {}),
                projection = np.art?.let { ProjectionContent.Picture(it) },
            )
            VhsFilm(animate = false, modifier = Modifier.fillMaxSize())
            VhsOsd(
                OsdState(
                    now = LocalDateTime.of(2026, 10, 18, 19, 21, 7), use24h = true, seconds = true, showClock = true,
                    status = fakeStatus(), notifications = List(2) { NotificationIcons.Entry("contoh.app$it", fakeNotificationIcon()) },
                    showNotifications = true, transport = transport, tapeMode = if (np.hasTrack) "SP" else null, sleepLine = null,
                    hasTrack = np.hasTrack, positionMs = s.previewMs ?: np.positionMs, durationMs = np.durationMs, canSeek = np.canSeek,
                ),
                landscape = landscape,
                actions = OsdActions({}, {}, {}, {}),
            )
        }
    }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RoomShots {
    @get:Rule
    val compose = createComposeRule()

    private fun shot(name: String) = compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")

    @Test
    @Config(qualifiers = "w1312dp-h816dp-land-mdpi")
    fun tablet() {
        compose.setContent { StagePreview(playing(fakeCover()), RetroSettings()) }
        shot("tahap10_vhs_tablet")
    }

    @Test
    @Config(qualifiers = "w1312dp-h816dp-land-mdpi")
    fun tabletStandby() {
        compose.setContent { StagePreview(NowPlaying(), RetroSettings()) }
        shot("tahap10_vhs_tablet_standby")
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp-land-mdpi")
    fun tabletSeekingWithTheKnob() {
        val k = SeekState().apply { touching = true; turns = 0.4f; startMs = 155_000L; previewMs = 171_000L }
        compose.setContent { StagePreview(playing(fakeCover()), RetroSettings(tvBody = TvBody.WALNUT), k) }
        shot("tahap10_vhs_knob_seek")
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xxhdpi")
    fun phonePortrait() {
        compose.setContent { StagePreview(playing(fakeCover()), RetroSettings(tvBody = TvBody.WALNUT), landscape = false) }
        shot("tahap10_vhs_ponsel")
    }

    @Test
    @Config(qualifiers = "w360dp-h780dp-xxhdpi", fontScale = 2.0f)
    fun smallPhoneLargeFont() {
        compose.setContent { StagePreview(playing(fakeCover()), RetroSettings(tvBody = TvBody.CHARCOAL, cassetteShell = CassetteShell.BLACK), landscape = false) }
        shot("tahap10_vhs_ponsel_kecil_font200")
    }

    @Test
    @Config(qualifiers = "w915dp-h412dp-land-xxhdpi")
    fun phoneLandscape() {
        compose.setContent { StagePreview(playing(fakeCover()), RetroSettings(tvBody = TvBody.SPACE_AGE, cassetteShell = CassetteShell.COLORED)) }
        shot("tahap10_vhs_ponsel_mendatar")
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp-land-mdpi")
    fun tabletLightRoomPictureTape() {
        compose.setContent { StagePreview(playing(fakeCover()), RetroSettings(room = RoomTone.LIGHT, cassetteShell = CassetteShell.PICTURE)) }
        shot("tahap10_vhs_terang")
    }

    @Test
    @Config(qualifiers = "ar-w1280dp-h800dp-land-mdpi")
    fun tabletRtl() {
        compose.setContent { StagePreview(playing(fakeCover()), RetroSettings()) }
        shot("tahap10_vhs_rtl")
    }
}
