package com.vinyl.launcher.ui.room.stage

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.vinyl.launcher.ui.theme.Tokens

/**
 * Buku harian kaset di meja: buku tertutup dengan pita pembatas; titik merah kecil di sampulnya menyala bila perekaman aktif.
 * Ketuk = buka isi buku (daftar lagu per hari) beserta saklar perekaman dan tombol hapus.
 */
@Composable
internal fun StageDiary(
    spot: PropSpot,
    look: PropLook,
    recording: Boolean,
    count: Int,
    enabled: Boolean,
    onOpen: () -> Unit,
) {
    val b = spot.bounds
    Canvas(Modifier.offset(b.left.dp, b.top.dp).size(b.width.dp, b.height.dp)) { drawDiary(look, recording) }
    val h = spot.hit
    Box(
        Modifier
            .offset(h.left.dp, h.top.dp)
            .size(h.width.dp, h.height.dp)
            .semantics {
                contentDescription = "Buku harian kaset"
                stateDescription = (if (recording) "Merekam" else "Perekaman mati") + ", $count catatan"
            }
            .clickable(enabled = enabled, onClickLabel = "Buka buku harian", role = Role.Button, onClick = onOpen),
    )
}

private fun DrawScope.drawDiary(look: PropLook, recording: Boolean) {
    val d = density
    val w = size.width
    val h = size.height
    val cover = Color(Tokens.Props.DIARY_COVER)
    val pages = Color(Tokens.Props.DIARY_PAGES)
    val r = CornerRadius(2f * d, 2f * d)
    // Bayangan di meja, halaman yang mengintip di bawah, lalu sampul
    drawRoundRect(Color.Black.copy(alpha = 0.30f), Offset(0.5f * d, h * 0.86f), Size(w, h * 0.16f), r)
    drawRoundRect(pages, Offset(w * 0.04f, h * 0.80f), Size(w * 0.92f, h * 0.18f), r)
    drawRoundRect(Brush.verticalGradient(listOf(cover, Color(Tokens.Props.BAKELITE_DARK).copy(alpha = 0.6f).compositeOver(cover))), Offset.Zero, Size(w, h * 0.86f), r)
    // Punggung buku dan garis hias
    drawRect(Color.Black.copy(alpha = 0.25f), Offset.Zero, Size(w * 0.10f, h * 0.86f))
    val trim = look.rim
    drawRect(trim, Offset(w * 0.18f, h * 0.14f), Size(w * 0.64f, 1.2f * d))
    drawRect(trim, Offset(w * 0.18f, h * 0.60f), Size(w * 0.64f, 1.2f * d))
    // Label kecil berbentuk kaset di sampul
    val lw = w * 0.46f
    val lh = h * 0.24f
    val lx = (w - lw) / 2f + w * 0.04f
    val ly = h * 0.28f
    drawRoundRect(pages, Offset(lx, ly), Size(lw, lh), CornerRadius(1.5f * d, 1.5f * d))
    drawCircle(cover, lh * 0.20f, Offset(lx + lw * 0.28f, ly + lh * 0.5f))
    drawCircle(cover, lh * 0.20f, Offset(lx + lw * 0.72f, ly + lh * 0.5f))
    // Pita pembatas
    drawRect(Color(Tokens.Props.NEEDLE), Offset(w * 0.78f, h * 0.86f), Size(2.2f * d, h * 0.12f))
    // Titik perekaman
    if (recording) drawCircle(Color(Tokens.Props.DIARY_REC), 2.6f * d, Offset(w * 0.90f, h * 0.10f))
}
