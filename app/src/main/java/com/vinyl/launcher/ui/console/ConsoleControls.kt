package com.vinyl.launcher.ui.console

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.em
import com.vinyl.launcher.audio.Reading
import com.vinyl.launcher.audio.Source
import com.vinyl.launcher.ui.theme.MonoFamily
import com.vinyl.launcher.ui.theme.RetroColors
import kotlin.math.roundToInt

/*
 * Komponen Audio Console bergaya receiver stereo: panel charcoal, label krem, indikator amber/hijau.
 * Kontras teks di atas panel (#343633): krem 9,6:1, krem sekunder 6,6:1, amber 5,5:1, hijau VFD 6,2:1.
 */

internal object ConsoleColors {
    val Page = RetroColors.CharcoalDeep
    val Panel = RetroColors.Charcoal
    val Line = RetroColors.CharcoalRaised
    val Text = RetroColors.CreamText
    val TextSecondary = RetroColors.CreamTextSecondary
    val Amber = RetroColors.Amber
    val Green = RetroColors.VfdGreen
}

/** Panel berjudul dengan lampu indikator kecil. */
@Composable
internal fun ConsolePanel(title: String, modifier: Modifier = Modifier, lit: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(ConsoleColors.Panel)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(7.dp).clip(CircleShape)
                    .background(if (lit) ConsoleColors.Amber else ConsoleColors.Line)
                    .clearAndSetSemantics { },
            )
            Text(
                title.uppercase(),
                color = ConsoleColors.Text,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.12.em,
                modifier = Modifier.padding(start = 10.dp).semantics { heading() },
            )
        }
        content()
    }
}

/** Warna & teks label sumber nilai. */
private fun sourceColor(s: Source): Color = when (s) {
    Source.MEASURED -> ConsoleColors.Green
    Source.REPORTED -> ConsoleColors.Green
    Source.ESTIMATED -> ConsoleColors.Amber
    Source.UNAVAILABLE -> ConsoleColors.TextSecondary
}

/**
 * Satu nilai: label, nilai, label sumber, dan (bila perlu) alasan/asal. Disusun bertumpuk agar tetap terbaca pada
 * ukuran huruf 200%. TalkBack membacanya sebagai satu kalimat.
 */
@Composable
internal fun ReadingRow(label: String, reading: Reading, modifier: Modifier = Modifier) {
    val showNote = reading.note.isNotBlank() && reading.note != reading.sourceLabel
    Column(
        modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = "$label: ${reading.display}. ${reading.sourceLabel}" + if (showNote) ". ${reading.note}" else ""
            },
    ) {
        Text(label, color = ConsoleColors.TextSecondary, style = MaterialTheme.typography.labelMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                reading.display,
                color = if (reading.source == Source.UNAVAILABLE) ConsoleColors.TextSecondary else ConsoleColors.Text,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = if (reading.source == Source.UNAVAILABLE) null else MonoFamily,
                modifier = Modifier.weight(1f, fill = false),
            )
            // "Tidak tersedia" sudah tertulis sebagai nilainya; label sumber hanya untuk nilai yang ada
            if (reading.source != Source.UNAVAILABLE) {
                Text(
                    reading.sourceLabel,
                    color = sourceColor(reading.source),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
        if (showNote) {
            Text(reading.note, color = ConsoleColors.TextSecondary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

/** Keterangan kecil (peringatan, penjelasan batas kemampuan). */
@Composable
internal fun ConsoleNote(text: String, modifier: Modifier = Modifier, warn: Boolean = false) {
    Text(
        text,
        color = if (warn) ConsoleColors.Amber else ConsoleColors.TextSecondary,
        style = MaterialTheme.typography.bodySmall,
        modifier = modifier,
    )
}

/** Baris sakelar: seluruh baris bisa diketuk, minimal 48dp. */
@Composable
internal fun ConsoleSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier, detail: String? = null, enabled: Boolean = true) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(label, color = ConsoleColors.Text, style = MaterialTheme.typography.bodyMedium)
            if (detail != null) Text(detail, color = ConsoleColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
        }
        Switch(
            checked = checked, onCheckedChange = null, enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = ConsoleColors.Page, checkedTrackColor = ConsoleColors.Amber,
                uncheckedThumbColor = ConsoleColors.TextSecondary, uncheckedTrackColor = ConsoleColors.Line,
                uncheckedBorderColor = ConsoleColors.TextSecondary,
            ),
        )
    }
}

/** Tombol garis tipis bergaya tombol receiver. [selected] = diisi amber. */
@Composable
internal fun ConsoleButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, selected: Boolean = false, enabled: Boolean = true, description: String? = null) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier
            .heightIn(min = 48.dp)
            .widthIn(min = 48.dp)
            .clip(shape)
            .then(if (selected) Modifier.background(ConsoleColors.Amber) else Modifier.border(1.dp, ConsoleColors.TextSecondary.copy(alpha = if (enabled) 1f else 0.4f), shape))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = when {
                selected -> ConsoleColors.Page
                enabled -> ConsoleColors.Text
                else -> ConsoleColors.TextSecondary.copy(alpha = 0.6f)
            },
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

private fun snap(v: Float, step: Float, range: ClosedFloatingPointRange<Float>): Float =
    ((v / step).roundToInt() * step).coerceIn(range.start, range.endInclusive)

/**
 * Fader EQ vertikal. Seret atau ketuk untuk mengatur, ketuk dua kali untuk 0. TalkBack: penggeser dengan langkah [step]
 * dan aksi "Naik/Turun 1 dB" serta "Atur ke 0".
 */
@Composable
internal fun Fader(
    label: String,
    sub: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    valueText: (Float) -> String,
    onChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    step: Float = 0.5f,
) {
    var hPx by remember { mutableFloatStateOf(1f) }
    val set by rememberUpdatedState(onChange)
    val span = range.endInclusive - range.start
    fun fromY(y: Float) = snap(range.endInclusive - (y / hPx).coerceIn(0f, 1f) * span, step, range)
    Column(modifier.width(72.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(valueText(value), color = ConsoleColors.Text, style = MaterialTheme.typography.labelMedium, fontFamily = MonoFamily, maxLines = 1)
        Box(
            Modifier
                .padding(vertical = 6.dp)
                .width(48.dp)
                .height(150.dp)
                .onSizeChanged { hPx = it.height.toFloat().coerceAtLeast(1f) }
                .semantics {
                    contentDescription = "$label, $sub"
                    stateDescription = valueText(value)
                    progressBarRangeInfo = ProgressBarRangeInfo(value, range, steps = (span / step).roundToInt() - 1)
                    setProgress { v -> set(snap(v, step, range)); true }
                    customActions = listOf(
                        CustomAccessibilityAction("Naik 1 dB") { set(snap(value + 1f, step, range)); true },
                        CustomAccessibilityAction("Turun 1 dB") { set(snap(value - 1f, step, range)); true },
                        CustomAccessibilityAction("Atur ke 0 dB") { set(snap(0f, step, range)); true },
                    )
                }
                .pointerInput(range, step) {
                    detectVerticalDragGestures(onDragStart = { set(fromY(it.y)) }) { ch, _ -> ch.consume(); set(fromY(ch.position.y)) }
                }
                .pointerInput(range, step) {
                    detectTapGestures(onDoubleTap = { set(snap(0f, step, range)) }) { set(fromY(it.y)) }
                },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val cx = size.width / 2f
                val pad = 8.dp.toPx()
                val top = pad
                val bottom = size.height - pad
                fun yOf(v: Float) = bottom - (v - range.start) / span * (bottom - top)
                // Alur fader
                drawRoundRect(Color.Black.copy(alpha = 0.45f), Offset(cx - 2.dp.toPx(), top), Size(4.dp.toPx(), bottom - top), CornerRadius(2.dp.toPx()))
                // Skala: ujung dan titik nol
                for (v in listOf(range.start, range.endInclusive)) {
                    drawLine(ConsoleColors.TextSecondary.copy(alpha = 0.6f), Offset(cx - 14.dp.toPx(), yOf(v)), Offset(cx - 8.dp.toPx(), yOf(v)), 1.dp.toPx())
                }
                if (range.start < 0f && range.endInclusive > 0f) {
                    drawLine(ConsoleColors.Text, Offset(cx - 16.dp.toPx(), yOf(0f)), Offset(cx + 16.dp.toPx(), yOf(0f)), 1.dp.toPx())
                }
                // Tutup fader
                val y = yOf(value.coerceIn(range.start, range.endInclusive))
                val capW = 30.dp.toPx()
                val capH = 16.dp.toPx()
                drawRoundRect(Color.Black.copy(alpha = 0.35f), Offset(cx - capW / 2f, y - capH / 2f + 2.dp.toPx()), Size(capW, capH), CornerRadius(3.dp.toPx()))
                drawRoundRect(ConsoleColors.Text, Offset(cx - capW / 2f, y - capH / 2f), Size(capW, capH), CornerRadius(3.dp.toPx()))
                drawLine(ConsoleColors.Amber, Offset(cx - capW / 2f + 3.dp.toPx(), y), Offset(cx + capW / 2f - 3.dp.toPx(), y), 2.dp.toPx())
            }
        }
        Text(label, color = ConsoleColors.Text, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        Text(sub, color = ConsoleColors.TextSecondary, style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}

/**
 * Penggeser horizontal (balance). Kiri/kanan di sini adalah kanal FISIK, jadi tata letaknya selalu kiri→kanan, juga
 * pada bahasa RTL.
 */
@Composable
internal fun LineSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    valueText: String,
    startLabel: String,
    endLabel: String,
    onChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    step: Float = 0.05f,
) {
    var wPx by remember { mutableFloatStateOf(1f) }
    val set by rememberUpdatedState(onChange)
    val span = range.endInclusive - range.start
    fun fromX(x: Float): Float = snap(range.start + (x / wPx).coerceIn(0f, 1f) * span, step, range)
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) { Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = ConsoleColors.Text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(valueText, color = ConsoleColors.Text, style = MaterialTheme.typography.labelMedium, fontFamily = MonoFamily)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(startLabel, color = ConsoleColors.TextSecondary, style = MaterialTheme.typography.labelSmall)
            Box(
                Modifier
                    .weight(1f)
                    .height(48.dp)
                    .padding(horizontal = 10.dp)
                    .onSizeChanged { wPx = it.width.toFloat().coerceAtLeast(1f) }
                    .semantics {
                        contentDescription = label
                        stateDescription = valueText
                        progressBarRangeInfo = ProgressBarRangeInfo(value, range, steps = (span / step).roundToInt() - 1)
                        setProgress { v -> set(snap(v, step, range)); true }
                        customActions = listOf(CustomAccessibilityAction("Tengah") { set(snap((range.start + range.endInclusive) / 2f, step, range)); true })
                    }
                    .pointerInput(range) {
                        detectHorizontalDragGestures(onDragStart = { set(fromX(it.x)) }) { ch, _ -> ch.consume(); set(fromX(ch.position.x)) }
                    }
                    .pointerInput(range) {
                        detectTapGestures(onDoubleTap = { set(snap((range.start + range.endInclusive) / 2f, step, range)) }) { set(fromX(it.x)) }
                    },
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val y = size.height / 2f
                    drawLine(Color.Black.copy(alpha = 0.45f), Offset(0f, y), Offset(size.width, y), 4.dp.toPx())
                    drawLine(ConsoleColors.TextSecondary, Offset(size.width / 2f, y - 8.dp.toPx()), Offset(size.width / 2f, y + 8.dp.toPx()), 1.dp.toPx())
                    val f = ((value - range.start) / span).coerceIn(0f, 1f)
                    drawCircle(ConsoleColors.Text, 9.dp.toPx(), Offset(size.width * f, y))
                    drawCircle(ConsoleColors.Amber, 3.dp.toPx(), Offset(size.width * f, y))
                }
            }
            Text(endLabel, color = ConsoleColors.TextSecondary, style = MaterialTheme.typography.labelSmall)
        }
    } }
}

/** Garis pemisah tipis antarbagian panel. */
@Composable
internal fun ConsoleDivider() {
    Spacer(Modifier.fillMaxWidth().height(1.dp).background(ConsoleColors.Line))
}
