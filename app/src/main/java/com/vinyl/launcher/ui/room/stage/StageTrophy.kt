package com.vinyl.launcher.ui.room.stage

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.vinyl.launcher.data.Trophy
import com.vinyl.launcher.data.TrophyTier
import com.vinyl.launcher.ui.theme.Tokens

/** Jumlah piala yang muat dipajang di rak dinding. */
internal const val TROPHY_SLOTS = 3

private fun TrophyTier.color(): Color = Color(
    when (this) {
        TrophyTier.GOLD -> Tokens.Props.CUP_GOLD
        TrophyTier.SILVER -> Tokens.Props.CUP_SILVER
        TrophyTier.BRONZE -> Tokens.Props.CUP_BRONZE
    },
)

/**
 * Rak piala di dinding: papan kayu dengan hingga [TROPHY_SLOTS] piala yang sudah diraih (tertinggi dulu); piala yang belum
 * diraih tampak sebagai siluet pudar. Ketuk = buka daftar semua piala beserta kemajuannya.
 */
@Composable
internal fun StageTrophy(
    spot: PropSpot,
    look: PropLook,
    shown: List<Trophy>,
    earned: Int,
    total: Int,
    enabled: Boolean,
    onOpen: () -> Unit,
) {
    val b = spot.bounds
    Canvas(Modifier.offset(b.left.dp, b.top.dp).size(b.width.dp, b.height.dp)) {
        drawTrophyShelf(look, shown)
        if (look.aged) drawWear(seed = 5)
    }
    val h = spot.hit
    Box(
        Modifier
            .offset(h.left.dp, h.top.dp)
            .size(h.width.dp, h.height.dp)
            .semantics {
                contentDescription = "Rak piala"
                stateDescription = "$earned dari $total piala diraih"
            }
            .clickable(enabled = enabled, onClickLabel = "Lihat semua piala", role = Role.Button, onClick = onOpen),
    )
}

private fun DrawScope.drawTrophyShelf(look: PropLook, shown: List<Trophy>) {
    val d = density
    val w = size.width
    val h = size.height
    val plankH = (h * 0.16f).coerceAtLeast(4f * d)
    val plankTop = h - plankH
    // Dua siku penyangga, lalu papan
    val bracket = Color.Black.copy(alpha = 0.35f)
    drawLine(bracket, Offset(w * 0.18f, plankTop + plankH), Offset(w * 0.18f, plankTop + plankH + 3f * d), strokeWidth = 2f * d, cap = StrokeCap.Round)
    drawLine(bracket, Offset(w * 0.82f, plankTop + plankH), Offset(w * 0.82f, plankTop + plankH + 3f * d), strokeWidth = 2f * d, cap = StrokeCap.Round)
    drawRect(Color.Black.copy(alpha = 0.25f), Offset(0f, plankTop + 1.5f * d), Size(w, plankH))
    drawRect(Brush.verticalGradient(listOf(look.wood, look.woodDark), startY = plankTop, endY = h), Offset(0f, plankTop), Size(w, plankH))
    drawLine(Color.White.copy(alpha = 0.18f), Offset(0f, plankTop + 0.5f * d), Offset(w, plankTop + 0.5f * d), strokeWidth = 1f * d)
    // Tiga tempat piala; yang belum diraih berupa siluet
    val cupH = plankTop - 2f * d
    val slotW = w / TROPHY_SLOTS
    for (i in 0 until TROPHY_SLOTS) {
        val cx = slotW * (i + 0.5f)
        val t = shown.getOrNull(i)
        val cupColor = if (t != null) androidx.compose.ui.graphics.lerp(t.tier.color(), Color(0xFF6E6A58), look.tarnish) else Color.White.copy(alpha = 0.16f)
        drawCup(cx, plankTop, cupH, slotW * 0.72f, cupColor, lit = t != null)
    }
}

/** Piala bergagang dua: mangkuk, tangkai, dan alas; [baseY] garis papan, [h] tinggi total. */
private fun DrawScope.drawCup(cx: Float, baseY: Float, h: Float, maxW: Float, color: Color, lit: Boolean) {
    val d = density
    val bowlW = minOf(maxW, h * 0.95f)
    val bowlH = h * 0.46f
    val bowlTop = baseY - h
    val stemH = h * 0.26f
    val baseH = h * 0.14f
    val dark = Color.Black.copy(alpha = if (lit) 0.28f else 0.0f)
    // Gagang
    val handle = Stroke(width = (1.6f * d).coerceAtLeast(1f), cap = StrokeCap.Round)
    drawArc(color, 90f, 180f, false, Offset(cx - bowlW / 2f - bowlH * 0.30f, bowlTop + bowlH * 0.10f), Size(bowlH * 0.6f, bowlH * 0.55f), style = handle)
    drawArc(color, -90f, 180f, false, Offset(cx + bowlW / 2f - bowlH * 0.30f, bowlTop + bowlH * 0.10f), Size(bowlH * 0.6f, bowlH * 0.55f), style = handle)
    // Mangkuk
    val bowl = Path().apply {
        moveTo(cx - bowlW / 2f, bowlTop)
        lineTo(cx + bowlW / 2f, bowlTop)
        quadraticTo(cx + bowlW * 0.46f, bowlTop + bowlH, cx, bowlTop + bowlH)
        quadraticTo(cx - bowlW * 0.46f, bowlTop + bowlH, cx - bowlW / 2f, bowlTop)
        close()
    }
    drawPath(bowl, color)
    // Tangkai dan alas
    val stemW = bowlW * 0.16f
    drawRect(color, Offset(cx - stemW / 2f, bowlTop + bowlH), Size(stemW, stemH + 1f))
    val baseW = bowlW * 0.62f
    drawRect(color, Offset(cx - baseW / 2f, baseY - baseH), Size(baseW, baseH))
    if (lit) {
        drawPath(bowl, Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.35f), Color.Transparent, dark), startX = cx - bowlW / 2f, endX = cx + bowlW / 2f))
    }
}
