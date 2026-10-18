package com.vinyl.launcher.ui.components

import android.graphics.Paint
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.vinyl.launcher.data.ClockSettings
import com.vinyl.launcher.data.ClockType
import com.vinyl.launcher.data.FontChoice
import com.vinyl.launcher.ui.theme.Accent
import com.vinyl.launcher.ui.theme.InterText
import com.vinyl.launcher.ui.theme.toColor
import com.vinyl.launcher.ui.theme.toFamily
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Waktu sekarang, diperbarui tepat di pergantian detik. */
@Composable
fun rememberNow(): State<LocalDateTime> = produceState(LocalDateTime.now()) {
    while (true) {
        value = LocalDateTime.now()
        delay(1000L - System.currentTimeMillis() % 1000L)
    }
}

/** Teks jam sesuai format 12/24 jam. Mengembalikan (jam:menit, AM/PM atau null). */
fun timeParts(now: LocalDateTime, use24h: Boolean): Pair<String, String?> =
    if (use24h) now.format(DateTimeFormatter.ofPattern("HH:mm")) to null
    else now.format(DateTimeFormatter.ofPattern("h:mm")) to now.format(DateTimeFormatter.ofPattern("a", Locale.getDefault()))

fun formatDate(now: LocalDateTime): String =
    now.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault()))
        .replaceFirstChar { it.titlecase(Locale.getDefault()) }

/** Gaya teks angka besar: tracking rapat & angka tabular agar tidak "goyang" saat berganti. */
private fun clockStyle(family: FontFamily, weight: FontWeight, sizeDp: Float, color: Color, serif: Boolean) = TextStyle(
    fontFamily = family,
    fontWeight = weight,
    fontSize = sizeDp.sp,
    lineHeight = (sizeDp * 1.0f).sp,
    letterSpacing = if (serif) (-0.01).em else (-0.045).em,
    color = color,
    fontFeatureSettings = "tnum, ss01",
)

/**
 * Jam bergaya layar kunci iOS: tanggal kecil di atas, waktu besar di bawah.
 * [baseSize] adalah ukuran angka pada skala 1.0 (dp).
 */
@Composable
fun ClockView(
    settings: ClockSettings,
    now: LocalDateTime,
    modifier: Modifier = Modifier,
    baseSize: Dp = 96.dp,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
) {
    val color = settings.color.toColor()
    val family = settings.font.toFamily()
    val sizeDp = baseSize.value * settings.scale
    val (hm, ampm) = timeParts(now, settings.use24h)
    val textAlign = when (horizontalAlignment) {
        Alignment.End -> TextAlign.End
        Alignment.CenterHorizontally -> TextAlign.Center
        else -> TextAlign.Start
    }

    Column(modifier, horizontalAlignment = horizontalAlignment) {
        if (settings.showDate) {
            Text(
                formatDate(now),
                style = TextStyle(
                    fontFamily = InterText,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = (sizeDp * 0.19f).coerceIn(13f, 30f).sp,
                    letterSpacing = 0.01.em,
                    color = color.copy(alpha = 0.78f),
                ),
                textAlign = textAlign,
                modifier = Modifier.padding(bottom = (sizeDp * 0.02f).dp, start = (sizeDp * 0.03f).dp),
            )
        }
        when (settings.type) {
            ClockType.THIN, ClockType.BOLD -> Row(verticalAlignment = Alignment.Bottom) {
                val weight = when {
                    settings.font == FontChoice.SERIF -> FontWeight.Normal
                    settings.type == ClockType.THIN -> FontWeight.ExtraLight
                    else -> FontWeight.SemiBold
                }
                Text(hm, style = clockStyle(family, weight, sizeDp, color, settings.font == FontChoice.SERIF))
                if (ampm != null) Text(
                    ampm,
                    style = TextStyle(fontFamily = InterText, fontWeight = FontWeight.SemiBold, fontSize = (sizeDp * 0.2f).sp, color = color.copy(alpha = 0.7f)),
                    modifier = Modifier.padding(start = (sizeDp * 0.06f).dp, bottom = (sizeDp * 0.14f).dp),
                )
            }
            ClockType.FLIP -> FlipClock(hm, ampm, color, family, (sizeDp * 0.9f).dp)
            ClockType.ANALOG -> AnalogClock(now, color, (sizeDp * 1.75f).dp)
            ClockType.CIRCULAR -> CircularClock(hm + (ampm?.let { " $it" } ?: ""), color, (sizeDp * 1.9f).dp)
        }
    }
}

/** Flip clock: setiap digit dalam kartu kaca; digit baru "jatuh" dari atas. */
@Composable
private fun FlipClock(hm: String, ampm: String?, color: Color, family: FontFamily, size: Dp) {
    val fs = size.value * 0.78f
    Row(horizontalArrangement = Arrangement.spacedBy(size * 0.05f), verticalAlignment = Alignment.CenterVertically) {
        hm.forEach { ch ->
            if (ch == ':') {
                Text(":", style = clockStyle(family, FontWeight.Medium, fs * 0.7f, color.copy(alpha = 0.5f), false))
            } else {
                Box(
                    Modifier
                        .glass(RoundedCornerShape(size * 0.14f), fillAlpha = 0.12f)
                        .clipToBounds()
                        .drawWithContent {
                            drawContent()
                            drawLine(
                                Color.Black.copy(alpha = 0.45f),
                                Offset(0f, this.size.height / 2f),
                                Offset(this.size.width, this.size.height / 2f),
                                strokeWidth = 2f,
                            )
                        }
                        .padding(horizontal = size * 0.1f, vertical = size * 0.04f),
                    contentAlignment = Alignment.Center,
                ) {
                    AnimatedContent(
                        targetState = ch,
                        transitionSpec = {
                            (slideInVertically(tween(420)) { -it } + fadeIn(tween(420))) togetherWith
                                (slideOutVertically(tween(420)) { it } + fadeOut(tween(260)))
                        },
                        label = "flip",
                    ) { c ->
                        Text(c.toString(), style = clockStyle(family, FontWeight.SemiBold, fs, color, false))
                    }
                }
            }
        }
        if (ampm != null) Text(ampm, style = TextStyle(fontFamily = InterText, fontWeight = FontWeight.SemiBold, fontSize = (fs * 0.25f).sp, color = color.copy(alpha = 0.7f)))
    }
}

/** Jam analog minimalis ala jam tangan: indeks tipis, jarum ramping, detik oranye. */
@Composable
fun AnalogClock(now: LocalDateTime, color: Color, size: Dp) {
    Canvas(Modifier.size(size)) {
        val r = this.size.minDimension / 2f
        val c = center
        drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.10f), Color.White.copy(alpha = 0.03f)), c, r), r, c)
        drawCircle(Color.White.copy(alpha = 0.18f), r, c, style = Stroke(r * 0.008f))
        for (i in 0 until 60) {
            val major = i % 5 == 0
            rotate(i * 6f, c) {
                drawLine(
                    color.copy(alpha = if (major) 0.9f else 0.28f),
                    Offset(c.x, c.y - r * 0.93f),
                    Offset(c.x, c.y - r * (if (major) 0.80f else 0.88f)),
                    strokeWidth = r * (if (major) 0.022f else 0.008f),
                    cap = StrokeCap.Round,
                )
            }
        }
        val sec = now.second
        val min = now.minute + sec / 60f
        val hr = (now.hour % 12) + min / 60f
        rotate(hr * 30f, c) { drawLine(color, Offset(c.x, c.y + r * 0.08f), Offset(c.x, c.y - r * 0.50f), r * 0.045f, StrokeCap.Round) }
        rotate(min * 6f, c) { drawLine(color, Offset(c.x, c.y + r * 0.08f), Offset(c.x, c.y - r * 0.76f), r * 0.03f, StrokeCap.Round) }
        rotate(sec * 6f, c) { drawLine(Accent, Offset(c.x, c.y + r * 0.16f), Offset(c.x, c.y - r * 0.84f), r * 0.012f, StrokeCap.Round) }
        drawCircle(Accent, r * 0.035f, c)
        drawCircle(Color.Black, r * 0.012f, c)
    }
}

/** Jam melingkar mandiri: piringan kecil dengan waktu tertulis di tepi. */
@Composable
fun CircularClock(text: String, color: Color, size: Dp) {
    val context = LocalContext.current
    val paint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = interTypeface(context)
            fontVariationSettings = "'wght' 350, 'opsz' 32"
            letterSpacing = 0.3f
        }
    }
    Canvas(Modifier.size(size)) {
        val r = this.size.minDimension / 2f
        drawCircle(Color(0xFF0B0B0D), r * 0.64f, center)
        for (i in 1..10) drawCircle(Color.White.copy(alpha = 0.045f), r * (0.28f + i * 0.033f), center, style = Stroke(1f))
        drawCircle(Accent, r * 0.2f, center)
        drawCircle(Color(0xFFE5E5E8), r * 0.02f, center)
        drawCircularText("$text  •  $text  •", center, r * 0.83f, r * 0.15f, color, paint, startDeg = -90f, centered = true)
    }
}

/** Tanggal sebagai teks terpisah (dipakai layar kunci). */
@Composable
fun DateText(now: LocalDateTime, settings: ClockSettings, modifier: Modifier = Modifier, baseSize: Dp = 96.dp, textAlign: TextAlign = TextAlign.Start) {
    Text(
        formatDate(now),
        style = TextStyle(
            fontFamily = InterText,
            fontWeight = FontWeight.SemiBold,
            fontSize = (baseSize.value * 0.2f).coerceIn(14f, 28f).sp,
            color = settings.color.toColor().copy(alpha = 0.8f),
        ),
        textAlign = textAlign,
        modifier = modifier,
    )
}
