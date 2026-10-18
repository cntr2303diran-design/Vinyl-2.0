package com.vinyl.launcher.ui.room.stage

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vinyl.launcher.data.VfdColor
import com.vinyl.launcher.ui.theme.MonoFamily
import com.vinyl.launcher.ui.theme.RetroColors
import com.vinyl.launcher.ui.theme.Tokens

/** Isi VFD deck: baris status (mis. "▶ PLAY   HI-FI") dan baris keluaran audio (mis. "BUDS PRO · 48 kHz"). */
data class DeckDisplay(val status: String, val output: String)

/**
 * Deck kaset hitam di rak meja: jendela VFD (status & keluaran audio) dan empat tombol perak mekanis — sebelumnya,
 * mundur 10 detik, putar/jeda, berikutnya. Tombol turun saat ditekan; tombol putar "terkunci" setengah masuk selama
 * lagu berjalan. Setiap tombol ≥ 48 dp.
 */
@Composable
fun TapeDeck(
    rect: SRect,
    display: DeckDisplay,
    vfdColor: VfdColor,
    hasSession: Boolean,
    playing: Boolean,
    canSeek: Boolean,
    enabled: Boolean,
    onPrevious: () -> Unit,
    onRewind: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
) {
    BoxWithConstraints(
        Modifier
            .offset(rect.left.dp, rect.top.dp)
            .size(rect.width.dp, rect.height.dp)
            .clip(RoundedCornerShape(5.dp)),
    ) {
        val h = maxHeight
        // Badan logam hitam bersikat dengan tepi atas tersorot
        Canvas(Modifier.fillMaxSize().clearAndSetSemantics { }) {
            drawRect(Brush.verticalGradient(listOf(Color(Tokens.Vhs.DECK_FACE_LIGHT), Color(Tokens.Vhs.DECK_FACE), Color(0xFF0E0E0F))))
            var y = 0f
            while (y < size.height) {
                drawLine(Color.White.copy(alpha = 0.025f), Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                y += 2.dp.toPx()
            }
            drawRect(Color.White.copy(alpha = 0.14f), size = Size(size.width, 1.5.dp.toPx()))
            drawRect(Color.Black.copy(alpha = 0.6f), topLeft = Offset(0f, size.height - 2.dp.toPx()), size = Size(size.width, 2.dp.toPx()))
            // Sekrup di sudut
            for (x in listOf(7.dp.toPx(), size.width - 7.dp.toPx())) for (yy in listOf(7.dp.toPx(), size.height - 7.dp.toPx())) {
                drawCircle(Color(0xFF55565A), 2.4.dp.toPx(), Offset(x, yy))
                drawLine(Color.Black, Offset(x - 1.6.dp.toPx(), yy), Offset(x + 1.6.dp.toPx(), yy), strokeWidth = 0.8.dp.toPx())
            }
        }
        // Tombol ≥ 48 dp; jendela VFD mendapat sisa lebar (paling sedikit ±38%) agar dua barisnya terbaca
        val inner = maxWidth - 28.dp - 10.dp
        val keyH = (h * 0.58f).coerceIn(48.dp, 58.dp)
        val keyW = (keyH * 1.05f).coerceAtMost(((inner * 0.62f) - 15.dp) / 4.25f).coerceAtLeast(48.dp)
        Row(
            Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            DeckVfd(display, vfdColor, Modifier.weight(1f).fillMaxHeight().padding(vertical = 4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                DeckKey(Icons.Rounded.SkipPrevious, "Lagu sebelumnya", enabled && hasSession, keyW, keyH, onClick = onPrevious)
                DeckKey(Icons.Rounded.FastRewind, "Mundur 10 detik", enabled && hasSession && canSeek, keyW, keyH, onClick = onRewind)
                DeckKey(
                    if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    if (!hasSession) "Buka aplikasi musik" else if (playing) "Jeda" else "Putar",
                    enabled, keyW * 1.25f, keyH, latched = playing, onClick = onPlayPause,
                )
                DeckKey(Icons.Rounded.SkipNext, "Lagu berikutnya", enabled && hasSession, keyW, keyH, onClick = onNext)
            }
        }
    }
}

@Composable
private fun DeckVfd(display: DeckDisplay, vfdColor: VfdColor, modifier: Modifier) {
    val color = if (vfdColor == VfdColor.GREEN) RetroColors.VfdGreen else RetroColors.VfdAmber
    BoxWithConstraints(
        modifier
            .clip(RoundedCornerShape(3.dp))
            .background(RetroColors.VfdGlass)
            .clearAndSetSemantics { },
    ) {
        // Ukuran huruf mengikuti tinggi jendela; skala huruf sistem ikut sampai 1,3× (lebih dari itu teks keluar jendela)
        val density = LocalDensity.current
        val scale = density.fontScale.coerceAtMost(1.3f) / density.fontScale
        // …dan tidak lebih besar dari yang muat untuk ±11 karakter huruf monospace selebar jendela
        val byWidth = ((maxWidth.value - 16f) / (0.62f * 11f)).coerceAtLeast(7f)
        val textSize = (minOf((maxHeight.value * 0.27f).coerceIn(9f, 17f), byWidth / density.fontScale) * scale).sp
        // Kilau kaca & ruas mati samar (VFD sungguhan tetap memperlihatkan segmen yang tidak menyala)
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.06f), Color.Transparent), endY = size.height * 0.5f))
            drawRoundRect(Color.Black.copy(alpha = 0.5f), cornerRadius = CornerRadius(3.dp.toPx()), style = Stroke(width = 1.5.dp.toPx()))
        }
        Column(
            Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 3.dp),
            verticalArrangement = Arrangement.SpaceEvenly,
        ) {
            Text(display.status, color = color, fontFamily = MonoFamily, fontWeight = FontWeight.Medium, fontSize = textSize, lineHeight = textSize, maxLines = 1, softWrap = false, overflow = TextOverflow.Clip)
            Text(display.output, color = color.copy(alpha = 0.92f), fontFamily = MonoFamily, fontSize = textSize * 0.86f, lineHeight = textSize * 0.86f, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Tombol perak mekanis: turun saat ditekan, setengah masuk saat "terkunci" (lagu berjalan). */
@Composable
private fun DeckKey(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    width: Dp,
    height: Dp,
    latched: Boolean = false,
    onClick: () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val depth by animateFloatAsState(
        when { pressed -> 1f; latched -> 0.5f; else -> 0f },
        spring(dampingRatio = 0.6f, stiffness = 900f), label = "deckKey",
    )
    Box(
        Modifier
            .size(width, height)
            .clip(RoundedCornerShape(4.dp))
            .background(Color.Black)
            .clickable(interactionSource = source, indication = null, enabled = enabled, onClickLabel = label, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .padding(start = 2.dp, end = 2.dp, top = 2.dp, bottom = 4.dp)
                .graphicsLayer { translationY = depth * 2.5.dp.toPx() }
                .clip(RoundedCornerShape(3.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(
                            if (depth > 0.4f) Color(Tokens.Vhs.KEY_FACE_DOWN) else Color(0xFFE6E3DA),
                            Color(Tokens.Vhs.KEY_FACE),
                            Color(0xFF8F8C84),
                        ),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            // Garis tekstur sikat pada muka tombol
            Canvas(Modifier.fillMaxSize()) {
                drawRect(Color.White.copy(alpha = 0.35f), size = Size(size.width, 1.dp.toPx()))
            }
            Icon(
                icon, null,
                tint = Color(Tokens.Vhs.KEY_INK).copy(alpha = if (enabled) 1f else 0.35f),
                modifier = Modifier.size((height.value * 0.42f).coerceIn(18f, 28f).dp),
            )
        }
    }
}
