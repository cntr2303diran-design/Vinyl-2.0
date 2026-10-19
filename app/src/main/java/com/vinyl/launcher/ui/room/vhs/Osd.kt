package com.vinyl.launcher.ui.room.vhs

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vinyl.launcher.system.Bluetooth
import com.vinyl.launcher.system.DeviceStatus
import com.vinyl.launcher.system.NotificationIcons
import com.vinyl.launcher.system.Ringer
import com.vinyl.launcher.system.StatusText
import com.vinyl.launcher.system.Wifi
import com.vinyl.launcher.ui.theme.OsdFamily
import com.vinyl.launcher.ui.theme.Tokens
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

private val OsdWhite = Color(Tokens.Vhs.OSD_TEXT)
private val OsdAccent = Color(Tokens.Vhs.OSD_ACCENT)
private val OsdWarn = Color(Tokens.Vhs.OSD_WARN_FILL)

/** Teks OSD: huruf VCR putih dengan bayangan hitam keras (terbaca di atas apa pun, seperti tampilan kamera). */
private fun osdStyle(size: TextUnit, color: Color = OsdWhite) = TextStyle(
    fontFamily = OsdFamily,
    fontSize = size,
    lineHeight = size,
    color = color,
    shadow = Shadow(Color.Black, offset = Offset(2.5f, 2.5f), blurRadius = 0f),
)

/** Isi OSD saat ini (dihitung pemanggil dari status perangkat, lagu, dan pengaturan). */
data class OsdState(
    val now: LocalDateTime,
    val use24h: Boolean,
    val seconds: Boolean,
    val showClock: Boolean,
    val status: DeviceStatus,
    val notifications: List<NotificationIcons.Entry>,
    val showNotifications: Boolean,
    val transport: StatusText.Transport,
    /** "HI-FI" (sumber lossless pemutar bawaan) atau "SP"; null = tidak ada lagu. */
    val tapeMode: String?,
    val sleepLine: String?,
    val hasTrack: Boolean,
    val positionMs: Long,
    val durationMs: Long,
    val canSeek: Boolean,
)

class OsdActions(
    val onSeek: (Long) -> Unit,
    val onQuickSettings: () -> Unit,
    val onNotifications: () -> Unit,
    val onClock: () -> Unit,
)

/** Batas skala huruf OSD: teks dasarnya sudah besar (20–32sp); di atas 1,5× pita OSD akan menutupi TV. */
private const val MAX_FONT_SCALE = 1.5f

/**
 * Teks layar kamera VHS di atas beranda, pengganti status bar: mode pita (kiri atas), baterai & status jaringan,
 * Bluetooth, dering, Jangan Ganggu, alarm, notifikasi (kanan atas), penghitung pita yang bisa digeser, tanggal, dan jam
 * (bawah). Pita atas/bawah diberi peredam ([Tokens.Vhs.OSD_SCRIM_ALPHA]) agar teks putih selalu >= 4,5:1.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VhsOsd(
    state: OsdState,
    landscape: Boolean,
    actions: OsdActions,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val capped = Density(density.density, fontScale = density.fontScale.coerceAtMost(MAX_FONT_SCALE))
    CompositionLocalProvider(LocalDensity provides capped) {
        Box(modifier.fillMaxSize()) {
            // Peredam pita bawah (penghitung pita membentang selebar layar); teks atas memakai peredam sudutnya sendiri
            Canvas(Modifier.fillMaxSize().clearAndSetSemantics { }) {
                val a = Tokens.Vhs.OSD_SCRIM_ALPHA.toFloat()
                val bottomBand = (if (landscape) 88.dp else 136.dp).toPx()
                drawRect(
                    Brush.verticalGradient(0f to Color.Transparent, 0.45f to Color.Black.copy(alpha = a * 0.75f), 1f to Color.Black.copy(alpha = a), startY = size.height - bottomBand, endY = size.height),
                    topLeft = Offset(0f, size.height - bottomBand), size = Size(size.width, bottomBand),
                )
            }

            val top = Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            val bottom = Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))

            // ----- Kiri atas: mode pita
            Column(
                top.align(Alignment.TopStart).padding(start = 18.dp, top = 12.dp).osdScrim().clearAndSetSemantics { },
            ) {
                Text(StatusText.modeLine(state.transport), style = osdStyle(30.sp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    state.tapeMode?.let { Text(it, style = osdStyle(20.sp, if (it == "HI-FI") OsdAccent else OsdWhite)) }
                    state.sleepLine?.let { Text(it, style = osdStyle(20.sp, OsdAccent)) }
                }
            }

            // ----- Kanan atas: baterai & status
            Column(
                top.align(Alignment.TopEnd).padding(end = 14.dp, top = 8.dp).osdScrim(),
                horizontalAlignment = Alignment.End,
            ) {
                StatusCluster(state, actions)
                if (state.showNotifications && state.status.notifications > 0) {
                    NotificationRow(state, actions)
                }
            }

            // ----- Bawah: penghitung pita, tanggal, jam
            Column(
                bottom.align(Alignment.BottomCenter).fillMaxWidth().padding(start = 18.dp, end = 18.dp, bottom = 10.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (landscape) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        if (state.showClock) DateText(state, actions.onClock)
                        if (state.hasTrack) LtrCounter(state, actions.onSeek, Modifier.weight(1f).padding(horizontal = 24.dp))
                        else Spacer(Modifier.weight(1f))
                        if (state.showClock) TimeText(state, actions.onClock)
                    }
                } else {
                    if (state.hasTrack) LtrCounter(state, actions.onSeek, Modifier.fillMaxWidth())
                    // Tanggal & jam berbagi satu baris bila muat; dengan huruf besar jam turun ke baris berikutnya
                    if (state.showClock) FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        DateText(state, actions.onClock)
                        TimeText(state, actions.onClock)
                    }
                }
            }
        }
    }
}

@Composable
private fun DateText(state: OsdState, onClock: () -> Unit) {
    val spoken = remember(state.now.toLocalDate()) {
        state.now.toLocalDate().format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(Locale.forLanguageTag("id-ID")))
    }
    Text(
        StatusText.dateLine(state.now.toLocalDate()),
        style = osdStyle(24.sp),
        maxLines = 1,
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clickable(onClickLabel = "Buka alarm", role = Role.Button, onClick = onClock)
            .semantics { contentDescription = spoken }
            .padding(top = 18.dp),
    )
}

@Composable
private fun TimeText(state: OsdState, onClock: () -> Unit) {
    val t = state.now.toLocalTime()
    Text(
        StatusText.timeLine(t, state.use24h, state.seconds),
        style = osdStyle(32.sp),
        maxLines = 1,
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clickable(onClickLabel = "Buka alarm", role = Role.Button, onClick = onClock)
            // Pembaca layar mendengar jam & menit saja (detik berubah tiap detik)
            .semantics { contentDescription = "Pukul " + StatusText.timeLine(t, use24h = true, seconds = false) }
            .padding(top = 10.dp),
    )
}

/** Baterai + baris ikon status. Satu tombol: membuka pengaturan cepat. */
@Composable
private fun StatusCluster(state: OsdState, actions: OsdActions) {
    val s = state.status
    val spoken = remember(s) { StatusText.spoken(s.copy(notifications = -1)) }
    Column(
        Modifier
            .heightIn(min = 48.dp)
            .clickable(onClickLabel = "Buka pengaturan cepat", role = Role.Button, onClick = actions.onQuickSettings)
            .semantics(mergeDescendants = true) { contentDescription = spoken.ifBlank { "Status perangkat" } }
            .padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.End,
    ) {
        if (s.batteryLevel >= 0) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (s.charging) PixelGlyph(OsdGlyphs.BOLT, Modifier.padding(end = 4.dp))
                BatteryGlyph(s.batteryLevel, StatusText.batteryLow(s.batteryLevel, s.charging))
                Text(" ${s.batteryLevel}%", style = osdStyle(26.sp))
            }
        }
        Row(
            Modifier.padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (s.airplane) PixelGlyph(OsdGlyphs.AIRPLANE)
            if (s.wifi == Wifi.CONNECTED) PixelGlyph(OsdGlyphs.WIFI, level = s.wifiLevel)
            if (s.hasCellular && !s.airplane) {
                if (s.simAbsent) PixelGlyph(OsdGlyphs.NO_SIM) else if (s.cellLevel >= 0) CellBars(s.cellLevel, s.mobileData)
            }
            if (s.bluetooth == Bluetooth.CONNECTED || s.bluetooth == Bluetooth.ON) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PixelGlyph(OsdGlyphs.BLUETOOTH, dim = s.bluetooth != Bluetooth.CONNECTED)
                    // Baterai earphone (dari laporan sistem Bluetooth; "≈" = level terakhir yang diingat)
                    s.bluetoothBattery?.takeIf { s.bluetooth == Bluetooth.CONNECTED }?.let {
                        Text(it, style = osdStyle(18.sp), modifier = Modifier.padding(start = 3.dp))
                    }
                }
            }
            when (s.ringer) {
                Ringer.VIBRATE -> PixelGlyph(OsdGlyphs.VIBRATE)
                Ringer.SILENT -> PixelGlyph(OsdGlyphs.BELL_OFF)
                Ringer.NORMAL -> Unit
            }
            if (s.doNotDisturb) PixelGlyph(OsdGlyphs.DO_NOT_DISTURB)
            if (s.nextAlarm != null) PixelGlyph(OsdGlyphs.ALARM)
        }
    }
}

/** Ikon notifikasi (monokrom, seperti status bar) + jumlahnya. Ketuk = buka panel notifikasi. */
@Composable
private fun NotificationRow(state: OsdState, actions: OsdActions) {
    val count = state.status.notifications
    Row(
        Modifier
            .heightIn(min = 48.dp)
            .clickable(onClickLabel = "Buka notifikasi", role = Role.Button, onClick = actions.onNotifications)
            .semantics(mergeDescendants = true) { contentDescription = "$count notifikasi" }
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        state.notifications.forEach { e ->
            Image(e.icon, null, colorFilter = ColorFilter.tint(OsdWhite), modifier = Modifier.size(20.dp))
        }
        Text(if (count > 99) "99+" else "$count", style = osdStyle(24.sp))
    }
}

/**
 * Garis waktu pita selalu kiri-ke-kanan, juga pada bahasa kanan-ke-kiri: ia menggambarkan arah pita berjalan, seperti
 * tombol putar/maju/mundur (pedoman kontrol media).
 */
@Composable
private fun LtrCounter(state: OsdState, onSeek: (Long) -> Unit, modifier: Modifier) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) { TapeCounter(state, onSeek, modifier) }
}

/** Penghitung pita VCR + garis posisi. Geser/ketuk untuk pindah posisi; pembaca layar: kontrol yang bisa diatur. */
@Composable
private fun TapeCounter(state: OsdState, onSeek: (Long) -> Unit, modifier: Modifier) {
    val dur = state.durationMs
    val canSeek = state.canSeek && dur > 0
    var drag by remember { mutableStateOf<Float?>(null) }
    var barStart by remember { mutableFloatStateOf(0f) }
    var barWidth by remember { mutableFloatStateOf(1f) }
    val seek by rememberUpdatedState(onSeek)
    val frac = drag ?: if (dur > 0) (state.positionMs.toFloat() / dur).coerceIn(0f, 1f) else 0f
    val shown = drag?.let { (it * dur).toLong() } ?: state.positionMs
    Row(
        modifier
            .heightIn(min = 48.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = "Posisi lagu"
                stateDescription = StatusText.counter(state.positionMs) + if (dur > 0) " dari " + StatusText.counter(dur) else ""
                if (dur > 0) progressBarRangeInfo = ProgressBarRangeInfo(state.positionMs.toFloat(), 0f..dur.toFloat())
                if (canSeek) setProgress { v -> seek(v.toLong().coerceIn(0L, dur)); true }
            }
            .pointerInput(canSeek, dur) {
                if (!canSeek) return@pointerInput
                fun f(x: Float) = ((x - barStart) / barWidth).coerceIn(0f, 1f)
                detectHorizontalDragGestures(
                    onDragStart = { o -> drag = f(o.x) },
                    onDragEnd = { drag?.let { seek((it * dur).toLong()) }; drag = null },
                    onDragCancel = { drag = null },
                ) { ch, _ -> ch.consume(); drag = f(ch.position.x) }
            }
            .pointerInput(canSeek, dur) {
                if (!canSeek) return@pointerInput
                detectTapGestures { o -> seek((((o.x - barStart) / barWidth).coerceIn(0f, 1f) * dur).toLong()) }
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(StatusText.counter(shown), style = osdStyle(24.sp), modifier = Modifier.padding(end = 12.dp))
        Box(
            Modifier
                .weight(1f)
                .heightIn(min = 24.dp)
                .onSizeChanged { barWidth = it.width.toFloat().coerceAtLeast(1f) }
                // Posisi garis di dalam baris: sentuhan baris diubah menjadi pecahan garis
                .onPlaced { barStart = it.positionInParent().x },
        ) {
            Canvas(Modifier.fillMaxWidth().heightIn(min = 24.dp)) {
                val y = size.height / 2f
                val px = 3.dp.toPx()
                // Garis bertitik seperti OSD VCR: segmen kosong tipis, bagian terputar tebal
                drawRect(Color.Black.copy(alpha = 0.6f), topLeft = Offset(1.5f, y - px / 2f + 1.5f), size = Size(size.width, px))
                drawRect(OsdWhite.copy(alpha = 0.35f), topLeft = Offset(0f, y - px / 2f), size = Size(size.width, px))
                drawRect(OsdWhite, topLeft = Offset(0f, y - px), size = Size(size.width * frac, px * 2f))
                val tx = (size.width * frac).coerceIn(0f, size.width - px * 3f)
                drawRect(Color.Black, topLeft = Offset(tx + 1.5f, y - px * 2.5f + 1.5f), size = Size(px * 3f, px * 5f))
                drawRect(OsdWhite, topLeft = Offset(tx, y - px * 2.5f), size = Size(px * 3f, px * 5f))
            }
        }
        if (dur > 0) Text(StatusText.counter(dur), style = osdStyle(24.sp), modifier = Modifier.padding(start = 12.dp))
    }
}

/** Ikon baterai kamera: kerangka dengan empat ruas; merah bila lemah. */
@Composable
private fun BatteryGlyph(level: Int, low: Boolean) {
    val bars = StatusText.batteryBars(level)
    Canvas(Modifier.size(width = 34.dp, height = 18.dp).clearAndSetSemantics { }) {
        val p = size.height / 9f
        fun r(x: Float, y: Float, w: Float, h: Float, c: Color) {
            drawRect(Color.Black, Offset(x * p + p * 0.6f, y * p + p * 0.6f), Size(w * p, h * p))
            drawRect(c, Offset(x * p, y * p), Size(w * p, h * p))
        }
        val body = if (low) OsdWarn else Color.Transparent
        if (low) drawRect(body, Offset(p, p), Size(13f * p, 7f * p))
        // Kerangka 15 × 9 dengan tonjolan
        r(0f, 0f, 15f, 1f, OsdWhite); r(0f, 8f, 15f, 1f, OsdWhite); r(0f, 0f, 1f, 9f, OsdWhite); r(14f, 0f, 1f, 9f, OsdWhite)
        r(15f, 3f, 2f, 3f, OsdWhite)
        for (i in 0 until bars) r(2f + i * 3f, 2f, 2f, 5f, OsdWhite)
    }
}

/** Empat batang sinyal seluler (batang di atas level dibuat redup); titik kecil = data seluler aktif. */
@Composable
private fun CellBars(level: Int, data: Boolean) {
    Canvas(Modifier.size(width = 22.dp, height = 18.dp).clearAndSetSemantics { }) {
        val p = size.height / 9f
        for (i in 0 until 4) {
            val h = 2f + i * 2f
            val c = if (i < level) OsdWhite else OsdWhite.copy(alpha = 0.3f)
            val x = i * 3f * p
            drawRect(Color.Black, Offset(x + p * 0.6f, (9f - h) * p + p * 0.6f), Size(2f * p, h * p))
            drawRect(c, Offset(x, (9f - h) * p), Size(2f * p, h * p))
        }
        if (data) drawRect(OsdAccent, Offset(12f * p, 0f), Size(p * 1.5f, p * 1.5f))
    }
}

/** Glyph berpiksel. [level] untuk glyph bertingkat (piksel di atas level redup); [dim] = seluruhnya redup. */
@Composable
private fun PixelGlyph(g: Glyph, modifier: Modifier = Modifier, level: Int = Int.MAX_VALUE, dim: Boolean = false) {
    val pxDp = 18.dp / 9f
    Canvas(modifier.size(width = pxDp * g.width, height = pxDp * g.height).clearAndSetSemantics { }) { drawGlyph(g, level, dim) }
}

private fun DrawScope.drawGlyph(g: Glyph, level: Int, dim: Boolean) {
    val p = size.width / g.width
    val pixels = g.pixels()
    for ((x, y) in pixels) drawRect(Color.Black, Offset(x * p + p * 0.6f, y * p + p * 0.6f), Size(p, p))
    for ((x, y) in pixels) {
        val grp = g.groupOf(y)
        val lit = !dim && (grp < 0 || grp < level)
        drawRect(if (lit) OsdWhite else OsdWhite.copy(alpha = 0.3f), Offset(x * p, y * p), Size(p, p))
    }
}

/**
 * Peredam lembut di belakang satu blok OSD sudut: inti di bawah teks berlapis hingga alpha ≥ [Tokens.Vhs.OSD_SCRIM_ALPHA]
 * (teks putih tetap ≥ 4,5:1 di atas proyeksi putih), tepinya memudar 28 dp sehingga tidak tampak seperti kotak.
 */
private fun Modifier.osdScrim(): Modifier = drawBehind {
    val layers = Tokens.Vhs.SCRIM_LAYERS
    val feather = 28.dp.toPx()
    val c = Color.Black.copy(alpha = Tokens.Vhs.SCRIM_LAYER_ALPHA.toFloat())
    for (i in 0 until layers) {
        val grow = feather * (layers - 1 - i) / (layers - 1)
        drawRoundRect(
            c, topLeft = Offset(-grow, -grow), size = Size(size.width + 2 * grow, size.height + 2 * grow),
            cornerRadius = CornerRadius(grow + 8.dp.toPx()),
        )
    }
}
