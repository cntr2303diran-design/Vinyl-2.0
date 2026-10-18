package com.vinyl.launcher.ui.components

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.res.ResourcesCompat
import com.vinyl.launcher.R

/** Typeface Inter untuk teks yang digambar di Canvas native (mis. jam melingkar). */
fun interTypeface(context: Context): Typeface =
    runCatching { ResourcesCompat.getFont(context, R.font.inter) }.getOrNull() ?: Typeface.SANS_SERIF

/**
 * Menulis teks mengikuti lingkaran berjari-jari [radius].
 * @param startDeg sudut awal (0° = arah jam 3, searah jarum jam).
 * @param centered jika true, teks dipusatkan di sekitar [startDeg].
 */
fun DrawScope.drawCircularText(
    text: String,
    c: Offset,
    radius: Float,
    textSize: Float,
    color: Color,
    paint: Paint,
    startDeg: Float = 180f,
    centered: Boolean = false,
) {
    if (text.isBlank() || radius <= 0f) return
    paint.textSize = textSize
    paint.color = color.toArgb()
    val circumference = (2 * Math.PI * radius).toFloat()
    var t = text
    val maxLen = circumference * 0.9f
    while (paint.measureText(t) > maxLen && t.length > 2) t = t.dropLast(2) + "…"
    val textLen = paint.measureText(t)
    val startAngle = if (centered) startDeg - (textLen / circumference) * 180f else startDeg
    val path = android.graphics.Path().apply {
        addArc(c.x - radius, c.y - radius, c.x + radius, c.y + radius, startAngle, 359.9f)
    }
    drawIntoCanvas { canvas ->
        canvas.nativeCanvas.drawTextOnPath(t, path, 0f, textSize * 0.35f, paint)
    }
}

/** Durasi "m:ss". */
fun formatMs(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(totalSec / 60, totalSec % 60)
}
