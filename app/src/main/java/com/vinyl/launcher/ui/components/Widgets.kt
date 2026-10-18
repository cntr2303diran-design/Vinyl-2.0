package com.vinyl.launcher.ui.components

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.draw.shadow
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Thunderstorm
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.em
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.core.content.ContextCompat
import com.vinyl.launcher.data.CalendarRepository
import com.vinyl.launcher.data.WeatherInfo
import com.vinyl.launcher.data.WeatherRepository
import com.vinyl.launcher.data.WeatherSettings
import kotlinx.coroutines.delay
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

// ======================= Kalender =======================

/**
 * Kalender mini satu bulan. Hari ini ditandai lingkaran kecil bergaya vinyl
 * (piringan hitam beralur dengan label berwarna [accent]).
 */
@Composable
fun MiniCalendar(
    month: YearMonth,
    today: LocalDate,
    eventDays: Set<LocalDate>,
    color: Color,
    accent: Color,
    modifier: Modifier = Modifier,
    cell: Dp = 30.dp,
) {
    val locale = Locale.getDefault()
    val firstDow = WeekFields.of(locale).firstDayOfWeek
    val days = remember(firstDow) { (0..6).map { firstDow.plus(it.toLong()) } }
    val leading = (month.atDay(1).dayOfWeek.value - firstDow.value + 7) % 7
    val total = month.lengthOfMonth()
    val fs = (cell.value * 0.42f).sp

    Column(modifier) {
        // Judul bulan ala widget kalender iOS: huruf kapital kecil berwarna aksen
        Text(
            month.month.getDisplayName(TextStyle.FULL_STANDALONE, locale).uppercase(locale) +
                if (month.year != today.year) " ${month.year}" else "",
            color = accent,
            fontSize = (cell.value * 0.40f).sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.08.em,
            modifier = Modifier.padding(bottom = 8.dp, start = cell * 0.18f),
        )
        Row {
            days.forEach { d ->
                Text(
                    d.getDisplayName(TextStyle.NARROW_STANDALONE, locale),
                    color = color.copy(alpha = if (d == DayOfWeek.SUNDAY) 0.7f else 0.42f),
                    fontSize = fs * 0.8f,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(cell),
                )
            }
        }
        val rows = (leading + total + 6) / 7
        for (r in 0 until rows) {
            Row {
                for (c in 0 until 7) {
                    val dayNum = r * 7 + c - leading + 1
                    Box(Modifier.size(cell), contentAlignment = Alignment.Center) {
                        if (dayNum in 1..total) {
                            val date = month.atDay(dayNum)
                            val isToday = date == today
                            if (isToday) VinylDot(accent, Modifier.size(cell * 0.92f))
                            Text(
                                dayNum.toString(),
                                color = when {
                                    isToday -> Color.White
                                    date.isBefore(today) -> color.copy(alpha = 0.4f)
                                    else -> color.copy(alpha = 0.85f)
                                },
                                fontSize = fs,
                                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium,
                            )
                            if (date in eventDays) {
                                Box(
                                    Modifier.align(Alignment.BottomCenter).padding(bottom = cell * 0.06f).size(4.dp)
                                ) {
                                    Canvas(Modifier.size(4.dp)) { drawCircle(if (isToday) Color.White else accent) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Lingkaran kecil bergaya vinyl untuk menandai hari ini. */
@Composable
fun VinylDot(accent: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val r = size.minDimension / 2f
        // Piringan mini: badan hitam beralur, label aksen penuh di tengah
        drawCircle(Color(0xFF0B0B0D), r)
        for (i in 0..3) drawCircle(Color.White.copy(alpha = 0.09f), r * (0.70f + i * 0.08f), style = Stroke(1f))
        drawCircle(accent, r * 0.62f)
        drawCircle(Color.Black.copy(alpha = 0.18f), r * 0.62f, style = Stroke(r * 0.04f))
    }
}

/** Dua kalender mini berdampingan: bulan ini & bulan depan (otomatis bergeser tiap awal bulan). */
@Composable
fun TwoMonthCalendar(
    today: LocalDate,
    showEvents: Boolean,
    color: Color,
    accent: Color,
    modifier: Modifier = Modifier,
    cell: Dp = 28.dp,
    vertical: Boolean = false,
    card: Boolean = false,
) {
    val context = LocalContext.current
    val thisMonth = YearMonth.from(today)
    val nextMonth = thisMonth.plusMonths(1)
    val events by produceState(emptySet<LocalDate>(), thisMonth, showEvents) {
        value = if (showEvents) CalendarRepository.eventDays(context, thisMonth, nextMonth) else emptySet()
    }
    val content: @Composable () -> Unit = {
        MiniCalendar(thisMonth, today, events, color, accent, cell = cell)
        Spacer(Modifier.size(cell * 0.8f))
        MiniCalendar(nextMonth, today, events, color, accent, cell = cell)
    }
    val m = if (card) modifier.glass(RoundedCornerShape(26.dp)).padding(horizontal = 18.dp, vertical = 16.dp) else modifier
    if (vertical) Column(m) { content() } else Row(m) { content() }
}

// ======================= Baterai =======================

data class BatteryStatus(val level: Int, val charging: Boolean)

/** Status baterai via broadcast ACTION_BATTERY_CHANGED (sticky). */
@Composable
fun rememberBattery(): BatteryStatus {
    val context = LocalContext.current
    var status by remember { mutableStateOf(BatteryStatus(100, false)) }
    DisposableEffect(context) {
        fun parse(i: Intent?) {
            i ?: return
            val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
            val plugged = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
            if (level >= 0) status = BatteryStatus(level * 100 / scale.coerceAtLeast(1), plugged)
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) = parse(intent)
        }
        parse(
            ContextCompat.registerReceiver(
                context, receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED,
            )
        )
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }
    return status
}

/** Indikator baterai minimalis ala status bar iOS (ikon digambar Canvas + persen). */
@Composable
fun BatteryIndicator(color: Color, modifier: Modifier = Modifier, scale: Float = 1f) {
    val b = rememberBattery()
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp * scale)) {
        Text(
            "${b.level}%",
            color = color,
            fontSize = (15 * scale).sp,
            fontWeight = FontWeight.SemiBold,
            style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum"),
        )
        Canvas(Modifier.size(width = 27.dp * scale, height = 13.dp * scale)) {
            val bodyW = size.width * 0.9f
            val corner = CornerRadius(size.height * 0.32f)
            drawRoundRect(color.copy(alpha = 0.35f), size = Size(bodyW, size.height), cornerRadius = corner, style = Stroke(size.height * 0.09f))
            drawRoundRect(color.copy(alpha = 0.4f), topLeft = Offset(bodyW + size.width * 0.02f, size.height * 0.33f), size = Size(size.width * 0.06f, size.height * 0.34f), cornerRadius = CornerRadius(2f))
            val fill = when {
                b.charging -> Color(0xFF34C759)
                b.level <= 20 -> Color(0xFFFF453A)
                else -> color
            }
            val inset = size.height * 0.17f
            drawRoundRect(
                fill,
                topLeft = Offset(inset, inset),
                size = Size(((bodyW - inset * 2) * b.level / 100f).coerceAtLeast(2f), size.height - inset * 2),
                cornerRadius = CornerRadius(size.height * 0.18f),
            )
        }
    }
}

// ======================= Cuaca =======================

@Composable
fun WeatherWidget(settings: WeatherSettings, color: Color, modifier: Modifier = Modifier, scale: Float = 1f) {
    val weather by produceState<WeatherInfo?>(null, settings) {
        while (true) {
            value = WeatherRepository.current(settings) ?: value
            delay(30 * 60 * 1000L)
        }
    }
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp * scale)) {
        val icon = when (weather?.code ?: -1) {
            -1 -> Icons.Rounded.Cloud
            0, 1 -> Icons.Rounded.WbSunny
            in 51..67, in 80..82 -> Icons.Rounded.WaterDrop
            in 95..99 -> Icons.Rounded.Thunderstorm
            else -> Icons.Rounded.Cloud
        }
        Icon(icon, null, tint = color, modifier = Modifier.size(34.dp * scale))
        Column {
            Text(
                weather?.let { "${it.temperatureC.toInt()}°" } ?: "--°",
                color = color, fontSize = (34 * scale).sp, fontWeight = FontWeight.Light, letterSpacing = (-0.03).em,
            )
            Text(
                weather?.let { "${it.city} · ${it.description}" } ?: settings.cityName,
                color = color.copy(alpha = 0.65f), fontSize = (13 * scale).sp, fontWeight = FontWeight.Medium,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ======================= Info lagu =======================

/** Judul + artis (tanpa kontrol). */
@Composable
fun SongInfo(title: String, artist: String, color: Color, modifier: Modifier = Modifier, alignEnd: Boolean = false) {
    Column(modifier, horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start) {
        Text(
            title.ifBlank { "Tidak ada yang diputar" },
            color = color, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.01).em,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        if (artist.isNotBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(artist, color = color.copy(alpha = 0.62f), fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * Kartu "Now Playing" kaca — perpaduan widget musik layar kunci iOS dan Now Bar One UI:
 * thumbnail cover, judul/artis, progress tipis dengan waktu, dan kontrol putar.
 */
@Composable
fun NowPlayingCard(
    np: com.vinyl.launcher.media.NowPlaying,
    modifier: Modifier = Modifier,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onOpen: () -> Unit,
) {
    var progress by remember { mutableFloatStateOf(np.progress) }
    LaunchedEffect(np) {
        while (true) {
            progress = np.progress
            if (!np.isPlaying) break
            delay(500)
        }
    }
    Column(
        modifier
            .widthIn(max = 460.dp)
            .glass(RoundedCornerShape(28.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.08f))
                    .clickable(onClick = onOpen),
                contentAlignment = Alignment.Center,
            ) {
                if (np.art != null) Image(np.art, null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
                else Icon(Icons.Rounded.MusicNote, null, tint = Color.White.copy(alpha = 0.5f))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (np.hasTrack) np.title else "Tidak ada yang diputar",
                    color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (np.hasTrack) np.artist else "Ketuk untuk membuka musik",
                    color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            if (np.hasTrack) {
                ControlButton(Icons.Rounded.SkipPrevious, "Sebelumnya", 26.dp, onPrevious)
                ControlButton(if (np.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Putar/jeda", 34.dp, onTogglePlay)
                ControlButton(Icons.Rounded.SkipNext, "Berikutnya", 26.dp, onNext)
            }
        }
        if (np.hasTrack && np.durationMs > 0) {
            Spacer(Modifier.height(14.dp))
            Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.18f))) {
                Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).fillMaxHeight().background(Color.White.copy(alpha = 0.85f)))
            }
            Spacer(Modifier.height(6.dp))
            Row {
                Text(formatMs(np.positionAt()), color = Color.White.copy(alpha = 0.55f), fontSize = 11.sp, fontWeight = FontWeight.Medium, style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum"))
                Spacer(Modifier.weight(1f))
                Text("-" + formatMs(np.durationMs - np.positionAt()), color = Color.White.copy(alpha = 0.55f), fontSize = 11.sp, fontWeight = FontWeight.Medium, style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum"))
            }
        }
    }
}

@Composable
private fun ControlButton(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, size: Dp, onClick: () -> Unit) {
    Box(
        Modifier.size(size + 18.dp).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, desc, tint = Color.White, modifier = Modifier.size(size)) }
}

// ======================= Foto =======================

/**
 * Bingkai foto: sudut membulat, bayangan lembut, dan slideshow crossfade
 * (berganti setiap 8 detik) bila lebih dari satu foto.
 */
@Composable
fun PhotoWidget(images: List<String>, modifier: Modifier = Modifier, scale: Float = 1f) {
    var index by remember(images) { mutableIntStateOf(0) }
    LaunchedEffect(images) {
        if (images.size < 2) return@LaunchedEffect
        while (true) {
            delay(8000)
            index = (index + 1) % images.size
        }
    }
    val shape = RoundedCornerShape(24.dp * scale)
    Box(
        modifier
            .size(width = 220.dp * scale, height = 270.dp * scale)
            .shadow(24.dp, shape, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(shape)
            .background(Color.White.copy(alpha = 0.06f))
            .border(0.8.dp, Color.White.copy(alpha = 0.14f), shape),
        contentAlignment = Alignment.Center,
    ) {
        if (images.isEmpty()) {
            Icon(Icons.Rounded.Image, null, tint = Color.White.copy(alpha = 0.4f), modifier = Modifier.size(40.dp))
        } else {
            androidx.compose.animation.Crossfade(targetState = images[index % images.size], animationSpec = androidx.compose.animation.core.tween(1200), label = "photo") { path ->
                val img = rememberFileImage(path)
                if (img != null) Image(img, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
        }
    }
}
