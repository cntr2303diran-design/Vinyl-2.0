package com.vinyl.launcher.ui.room

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.vinyl.launcher.data.WeatherInfo
import com.vinyl.launcher.data.WeatherRepository
import com.vinyl.launcher.data.WeatherSettings
import com.vinyl.launcher.ui.components.rememberBattery
import com.vinyl.launcher.ui.theme.InterDisplay
import com.vinyl.launcher.ui.theme.MonoFamily
import com.vinyl.launcher.ui.theme.RetroColors
import com.vinyl.launcher.ui.components.rememberFileImage
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

/*
 * Widget beranda bertema ruang TV. Setiap widget membawa permukaannya sendiri (kertas krem, kaca VFD gelap, muka meter,
 * bingkai polaroid), sehingga kontras teksnya tidak bergantung pada warna dinding:
 *  - tinta #1E1D1B di atas kertas #F2EBDD: 14,2:1 (tinta lembut #4A463F: 7,9:1); teks putih di merah kepala #A3322B: 6,9:1
 *  - hijau VFD #9FC29A di atas kaca #141613: 9,2:1; krem di panel charcoal: 9,6:1
 * Ukuran mengikuti skala widget pengguna; posisi & ukuran tetap bisa diubah di mode edit.
 */

private val Paper = Color(0xFFF2EBDD)
private val PaperShade = Color(0xFFE2D8C4)
private val Ink = Color(0xFF1E1D1B)
private val InkSoft = Color(0xFF4A463F)
private val CalendarRed = Color(0xFFA3322B)

/** Kalender sobek: satu lembar per hari, tepi atas berlubang seperti blok kertas. */
@Composable
fun TearCalendarWidget(today: LocalDate, modifier: Modifier = Modifier, scale: Float = 1f) {
    val locale = Locale.getDefault()
    val s = scale.coerceIn(0.5f, 2f)
    val month = today.month.getDisplayName(TextStyle.FULL_STANDALONE, locale)
    val weekday = today.dayOfWeek.getDisplayName(TextStyle.FULL_STANDALONE, locale)
    val week = today.get(WeekFields.of(locale).weekOfWeekBasedYear())
    Column(
        modifier
            .width(150.dp * s)
            .shadow(10.dp, RoundedCornerShape(4.dp))
            .clip(RoundedCornerShape(4.dp))
            .background(Paper)
            .semantics(mergeDescendants = true) { contentDescription = "Kalender: $weekday, ${today.dayOfMonth} $month ${today.year}" },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Kepala blok (kertas merah) dengan lubang jilid
        Box(Modifier.fillMaxWidth().height(34.dp * s).background(CalendarRed)) {
            Canvas(Modifier.fillMaxSize().clearAndSetSemantics { }) {
                val n = 7
                for (i in 0 until n) {
                    val x = size.width * (i + 0.5f) / n
                    drawCircle(Color.Black.copy(alpha = 0.35f), size.height * 0.1f, Offset(x, size.height * 0.22f))
                }
            }
            Text(
                "${month.uppercase(locale)} ${today.year}",
                color = Color.White, fontSize = (12 * s).sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.08.em,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 5.dp * s),
            )
        }
        Text(
            today.dayOfMonth.toString(),
            color = Ink, fontFamily = InterDisplay, fontWeight = FontWeight.Bold, fontSize = (64 * s).sp, lineHeight = (66 * s).sp,
            modifier = Modifier.padding(top = 4.dp * s),
        )
        Text(weekday, color = Ink, fontSize = (15 * s).sp, fontWeight = FontWeight.SemiBold)
        Text("Minggu ke-$week", color = InkSoft, fontSize = (11 * s).sp, modifier = Modifier.padding(top = 2.dp * s, bottom = 6.dp * s))
        // Sisa sobekan lembar kemarin (tepi bergerigi)
        Canvas(Modifier.fillMaxWidth().height(8.dp * s).clearAndSetSemantics { }) {
            val teeth = 14
            val p = Path().apply {
                moveTo(0f, 0f)
                for (i in 0 until teeth) {
                    val x0 = size.width * i / teeth
                    lineTo(x0 + size.width / teeth / 2f, size.height)
                    lineTo(x0 + size.width / teeth, 0f)
                }
                close()
            }
            drawRect(PaperShade)
            drawPath(p, Paper)
        }
    }
}

/** Cuaca di layar VFD (memuat data sendiri, diperbarui tiap 30 menit). */
@Composable
fun VfdWeatherWidget(settings: WeatherSettings, modifier: Modifier = Modifier, scale: Float = 1f) {
    val weather by produceState<WeatherInfo?>(null, settings) {
        while (true) {
            value = WeatherRepository.current(settings) ?: value
            delay(30 * 60 * 1000L)
        }
    }
    VfdWeather(weather, settings.cityName, modifier, scale)
}

@Composable
fun VfdWeather(weather: WeatherInfo?, cityFallback: String, modifier: Modifier = Modifier, scale: Float = 1f) {
    val s = scale.coerceIn(0.5f, 2f)
    val temp = weather?.let { "${it.temperatureC.toInt()}°C" } ?: "--°C"
    val desc = weather?.description?.uppercase() ?: "MENUNGGU DATA"
    val city = (weather?.city ?: cityFallback).uppercase().ifBlank { "LOKASI BELUM DIATUR" }
    Column(
        modifier
            .clip(RoundedCornerShape(6.dp))
            .background(RetroColors.VfdGlass)
            .padding(horizontal = 14.dp * s, vertical = 10.dp * s)
            .semantics(mergeDescendants = true) {
                contentDescription = if (weather == null) "Cuaca belum tersedia, $city" else "Cuaca: $temp, ${weather.description}, ${weather.city}"
            },
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                temp, color = RetroColors.VfdGreen, fontFamily = MonoFamily, fontWeight = FontWeight.Medium,
                fontSize = (30 * s).sp, lineHeight = (30 * s).sp,
            )
            Text(
                "  TEMP", color = RetroColors.VfdGreen.copy(alpha = 0.7f), fontFamily = MonoFamily, fontSize = (10 * s).sp,
                modifier = Modifier.padding(bottom = 4.dp * s),
            )
        }
        Text(desc, color = RetroColors.VfdGreen, fontFamily = MonoFamily, fontSize = (12 * s).sp, maxLines = 1, overflow = TextOverflow.Clip)
        Text(city, color = RetroColors.VfdGreen, fontFamily = MonoFamily, fontSize = (12 * s).sp, maxLines = 1, overflow = TextOverflow.Clip)
    }
}

/** Baterai sebagai meter jarum analog (memuat status sendiri). */
@Composable
fun NeedleBatteryWidget(modifier: Modifier = Modifier, scale: Float = 1f) {
    val b = rememberBattery()
    NeedleBattery(b.level, b.charging, modifier, scale)
}

@Composable
fun NeedleBattery(level: Int, charging: Boolean, modifier: Modifier = Modifier, scale: Float = 1f) {
    val s = scale.coerceIn(0.5f, 2f)
    val lv = level.coerceIn(0, 100)
    Column(
        modifier
            .width(150.dp * s)
            .clip(RoundedCornerShape(10.dp))
            .background(RetroColors.Charcoal)
            .padding(8.dp * s)
            .semantics(mergeDescendants = true) { contentDescription = "Baterai $lv persen" + if (charging) ", mengisi daya" else "" },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.fillMaxWidth().height(78.dp * s).clip(RoundedCornerShape(6.dp)).background(Paper)) {
            Canvas(Modifier.fillMaxSize().clearAndSetSemantics { }) {
                val c = Offset(size.width / 2f, size.height * 0.92f)
                val r = size.height * 0.78f
                val start = 200f
                val sweep = 140f
                // Zona merah (0–20%)
                drawArc(CalendarRed, start, sweep * 0.2f, false, Offset(c.x - r, c.y - r), Size(r * 2, r * 2), style = Stroke(r * 0.07f))
                drawArc(Ink, start + sweep * 0.2f, sweep * 0.8f, false, Offset(c.x - r, c.y - r), Size(r * 2, r * 2), style = Stroke(r * 0.02f))
                for (i in 0..10) {
                    val a = Math.toRadians((start + sweep * i / 10f).toDouble())
                    val inner = if (i % 5 == 0) r * 0.80f else r * 0.88f
                    drawLine(
                        Ink, Offset(c.x + (inner * kotlin.math.cos(a)).toFloat(), c.y + (inner * kotlin.math.sin(a)).toFloat()),
                        Offset(c.x + (r * kotlin.math.cos(a)).toFloat(), c.y + (r * kotlin.math.sin(a)).toFloat()), r * 0.025f,
                    )
                }
                rotate(start + sweep * lv / 100f + 90f, c) {
                    drawLine(CalendarRed, c, Offset(c.x, c.y - r * 0.95f), r * 0.035f, cap = StrokeCap.Round)
                }
                drawCircle(Ink, r * 0.07f, c)
            }
            Text("BATT", color = InkSoft, fontSize = (9 * s).sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.1.em,
                modifier = Modifier.align(Alignment.Center).padding(top = 10.dp * s))
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp * s), verticalAlignment = Alignment.CenterVertically) {
            Text("$lv%", color = RetroColors.CreamText, fontFamily = MonoFamily, fontSize = (15 * s).sp, modifier = Modifier.weight(1f))
            Box(Modifier.size(7.dp * s).clip(RoundedCornerShape(50)).background(if (charging) RetroColors.MutedGreen else RetroColors.CharcoalRaised))
            Text(" CHG", color = if (charging) RetroColors.CreamText else RetroColors.CreamTextSecondary, fontFamily = MonoFamily, fontSize = (10 * s).sp)
        }
    }
}

/** Foto sebagai polaroid yang sedikit miring di dinding; beberapa foto berganti tiap 8 detik. */
@Composable
fun PolaroidWidget(images: List<String>, modifier: Modifier = Modifier, scale: Float = 1f) {
    val s = scale.coerceIn(0.5f, 2f)
    var index by remember(images) { mutableIntStateOf(0) }
    LaunchedEffect(images) {
        if (images.size < 2) return@LaunchedEffect
        while (true) {
            delay(8000)
            index = (index + 1) % images.size
        }
    }
    Column(
        modifier
            .rotate(-3f)
            .shadow(14.dp, RoundedCornerShape(2.dp))
            .background(Color(0xFFFAF8F3))
            .padding(start = 10.dp * s, end = 10.dp * s, top = 10.dp * s, bottom = 34.dp * s)
            .semantics { contentDescription = if (images.isEmpty()) "Bingkai foto kosong" else "Foto" },
    ) {
        Box(Modifier.size(170.dp * s, 170.dp * s).background(Color(0xFF2A2926)), contentAlignment = Alignment.Center) {
            if (images.isEmpty()) {
                Icon(Icons.Rounded.Image, null, tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(36.dp))
            } else {
                Crossfade(targetState = images[index % images.size], animationSpec = tween(1200), label = "polaroid") { path ->
                    val img = rememberFileImage(path)
                    if (img != null) Image(img, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
            }
        }
    }
}

/** Penanda hari ini di kalender layar kunci: lampu amber kecil (pengganti titik bergaya piringan). */
@Composable
fun TodayLamp(accent: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.clearAndSetSemantics { }) {
        val r = size.minDimension / 2f
        drawCircle(accent.copy(alpha = 0.25f), r)
        drawCircle(accent, r * 0.78f)
    }
}
