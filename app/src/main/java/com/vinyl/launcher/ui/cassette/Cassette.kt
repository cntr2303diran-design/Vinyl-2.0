package com.vinyl.launcher.ui.cassette

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vinyl.launcher.data.CassetteShell
import com.vinyl.launcher.data.ReelSpeed
import com.vinyl.launcher.ui.theme.MonoFamily
import com.vinyl.launcher.ui.theme.RetroColors
import com.vinyl.launcher.ui.theme.Tokens
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Isi label satu kaset (disimpan agar kaset LAMA tetap berlabel lagu lama saat keluar). */
data class CassetteLabel(val key: String?, val title: String, val artist: String, val art: ImageBitmap?)

/**
 * Kaset sebagai kontrol pemutaran: label lagu, dua gulungan yang berputar hanya saat musik benar-benar berjalan, pita
 * yang berpindah dari kiri ke kanan mengikuti progres, dan tiga tombol mekanis (sebelumnya, putar/jeda, berikutnya).
 *
 * Saat lagu berganti, kaset lama keluar dan kaset baru masuk ([CassetteSwap]). Perintah ganti lagu dikirim pemanggil
 * seketika; animasi ini tidak pernah menundanya.
 */
@Composable
fun Cassette(
    label: CassetteLabel,
    hasSession: Boolean,
    playing: Boolean,
    progress: Float,
    timeText: String,
    shell: CassetteShell,
    speed: ReelSpeed,
    accent: Color,
    motionAllowed: Boolean,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // --- Ganti kaset
    val swap = remember { CassetteSwap(label.key) }
    var labels by remember { mutableStateOf(mapOf(label.key to label)) }
    var frame by remember { mutableStateOf(swap.frame(0L)) }
    LaunchedEffect(label) {
        labels = (labels + (label.key to label)).entries.toList().takeLast(4).associate { it.key to it.value }
    }
    LaunchedEffect(label.key, motionAllowed) {
        val start = withFrameNanos { it / 1_000_000L }
        swap.onTrack(label.key, start, animate = motionAllowed)
        frame = swap.frame(start)
        while (swap.running) {
            val now = withFrameNanos { it / 1_000_000L }
            frame = swap.frame(now)
        }
        frame = swap.frame(Long.MAX_VALUE / 2)
    }
    val shown = labels[frame.shownKey] ?: label

    // --- Putaran gulungan: hanya saat musik berjalan dan animasi diizinkan
    var angleL by remember { mutableFloatStateOf(0f) }
    var angleR by remember { mutableFloatStateOf(0f) }
    val (rl, rr) = Reels.radii(progress)
    val currentProgress by rememberUpdatedState(progress)
    LaunchedEffect(playing && motionAllowed, speed) {
        if (!(playing && motionAllowed)) return@LaunchedEffect
        var last = withFrameNanos { it }
        val rpm = Reels.rpm(speed)
        while (true) {
            val now = withFrameNanos { it }
            val dt = (now - last) / 1e9f
            last = now
            val (l, r) = Reels.radii(currentProgress)
            angleL = (angleL + Reels.turnsPerSecond(l, rpm) * dt * 360f) % 360f
            angleR = (angleR + Reels.turnsPerSecond(r, rpm) * dt * 360f) % 360f
        }
    }

    BoxWithConstraints(modifier) {
        val keysH = maxOf(maxHeight * 0.30f, 48.dp)
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            // Cangkang kaset (yang keluar/masuk)
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .graphicsLayer {
                        val p = frame.progress
                        when (frame.phase) {
                            CassetteSwap.Phase.EJECT -> { translationY = -p * size.height * 0.55f; alpha = 1f - p }
                            CassetteSwap.Phase.INSERT -> { translationY = (1f - p) * size.height * 0.55f; alpha = p }
                            CassetteSwap.Phase.IDLE -> Unit
                        }
                    }
                    .semantics(mergeDescendants = true) {
                        contentDescription = if (shown.key == null) "Kaset kosong" else "Kaset: ${shown.title}" +
                            (if (shown.artist.isNotBlank()) ", ${shown.artist}" else "") + ". $timeText"
                    },
            ) {
                Shell(shown, shell, accent, rl, rr, angleL, angleR, timeText)
            }
            // Tombol mekanis ala deck kaset
            Row(
                Modifier.fillMaxWidth().height(keysH),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DeckKey(Icons.Rounded.SkipPrevious, "Lagu sebelumnya", enabled = hasSession, onClick = onPrevious, modifier = Modifier.weight(1f))
                DeckKey(
                    if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    if (!hasSession) "Buka aplikasi musik" else if (playing) "Jeda" else "Putar",
                    enabled = true, latched = playing, onClick = onPlayPause, modifier = Modifier.weight(1.4f),
                )
                DeckKey(Icons.Rounded.SkipNext, "Lagu berikutnya", enabled = hasSession, onClick = onNext, modifier = Modifier.weight(1f))
            }
        }
    }
}

private data class ShellPalette(val body: Color, val edge: Color, val see: Boolean)

private fun shellPalette(shell: CassetteShell, accent: Color): ShellPalette = when (shell) {
    CassetteShell.BLACK -> ShellPalette(Color(0xFF232323), Color(0xFF3A3A3A), see = false)
    CassetteShell.TRANSPARENT -> ShellPalette(Color(0x8C9AA3A6), Color(0xFF7F8A8D), see = true)
    CassetteShell.COLORED -> ShellPalette(accent, accent.copy(red = accent.red * 0.7f, green = accent.green * 0.7f, blue = accent.blue * 0.7f), see = false)
    CassetteShell.PICTURE -> ShellPalette(Color(0xFF2A2A2A), Color(0xFF444444), see = false)
}

@Composable
private fun Shell(label: CassetteLabel, shell: CassetteShell, accent: Color, rl: Float, rr: Float, angleL: Float, angleR: Float, timeText: String) {
    val pal = shellPalette(shell, accent)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = maxWidth
        val h = maxHeight
        Canvas(Modifier.fillMaxSize().clearAndSetSemantics { }) { drawShell(pal, rl, rr, angleL, angleR) }

        // Label (atas): kertas krem dengan judul & artis, atau cover penuh untuk cangkang bergambar
        val labelTop = h * 0.08f
        val labelH = h * 0.36f
        Box(
            Modifier
                .offset(w * 0.08f, labelTop)
                .size(w * 0.84f, labelH)
                .clip(RoundedCornerShape(4.dp))
                .background(RetroColors.CreamBeige.copy(alpha = 1f))
                .clearAndSetSemantics { },
        ) {
            if (shell == CassetteShell.PICTURE && label.art != null) {
                Image(label.art, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                // Garis label klasik
                Box(Modifier.fillMaxWidth().height(3.dp).offset(y = labelH * 0.18f).background(accent.copy(alpha = 0.85f)))
                Column(Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 2.dp), verticalArrangement = Arrangement.Center) {
                    val big = (w.value * 0.062f).coerceIn(9f, 20f).sp
                    Text(
                        label.title.ifBlank { "—" }, color = RetroColors.Ink, fontSize = big, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Row {
                        Text(
                            label.artist, color = RetroColors.Ink, fontSize = big * 0.78f,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                        )
                        Text(timeText, color = RetroColors.Ink, fontSize = big * 0.74f, fontFamily = MonoFamily, maxLines = 1)
                    }
                }
            }
        }
    }
}

/** Cangkang, jendela, dua gulungan berpita, sekrup. Semua dalam satuan kotak cangkang. */
private fun DrawScope.drawShell(p: ShellPalette, rl: Float, rr: Float, angleL: Float, angleR: Float) {
    val w = size.width
    val h = size.height
    // Bayangan kontak
    drawOval(Color(Tokens.Retro.CONTACT_SHADOW), topLeft = Offset(w * 0.02f, h * 0.93f), size = Size(w * 0.96f, h * 0.10f))
    // Cangkang
    drawRoundRect(p.body, cornerRadius = CornerRadius(w * 0.045f))
    drawRoundRect(p.edge, cornerRadius = CornerRadius(w * 0.045f), style = Stroke(width = w * 0.008f))
    drawRoundRect(
        Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.14f), Color.Transparent), endY = h * 0.3f),
        cornerRadius = CornerRadius(w * 0.045f), size = Size(w, h * 0.3f),
    )
    // Trapesium bawah (bagian kepala pita)
    val trapTop = h * 0.74f
    drawRoundRect(p.edge.copy(alpha = 0.55f), topLeft = Offset(w * 0.2f, trapTop), size = Size(w * 0.6f, h * 0.22f), cornerRadius = CornerRadius(w * 0.02f))

    // Jendela & gulungan
    val winTop = h * 0.48f
    val winH = h * 0.24f
    val winLeft = w * 0.22f
    val winW = w * 0.56f
    drawRoundRect(Color(0xFF14110E).copy(alpha = if (p.see) 0.55f else 0.85f), topLeft = Offset(winLeft, winTop), size = Size(winW, winH), cornerRadius = CornerRadius(winH * 0.25f))
    val cy = winTop + winH / 2f
    val maxR = winH * 0.95f
    val cxL = winLeft + winW * 0.22f
    val cxR = winLeft + winW * 0.78f
    drawReel(Offset(cxL, cy), rl * maxR, maxR * Reels.HUB, angleL)
    drawReel(Offset(cxR, cy), rr * maxR, maxR * Reels.HUB, angleR)
    // Pita yang melintas di bawah jendela
    drawLine(Color(0xFF3B2A1E), Offset(cxL, cy + rl * maxR), Offset(cxR, cy + rr * maxR), strokeWidth = h * 0.012f)

    // Sekrup di sudut
    for ((x, y) in listOf(0.05f to 0.08f, 0.95f to 0.08f, 0.05f to 0.9f, 0.95f to 0.9f, 0.5f to 0.88f)) {
        drawCircle(Color(0xFF8A8A8A), w * 0.014f, Offset(w * x, h * y))
        drawLine(Color(0xFF4A4A4A), Offset(w * x - w * 0.009f, h * y), Offset(w * x + w * 0.009f, h * y), strokeWidth = w * 0.003f)
    }
}

/** Satu gulungan: cincin pita cokelat (jari-jari [tape]) di sekitar hub putih bergigi yang berputar [deg] derajat. */
private fun DrawScope.drawReel(c: Offset, tape: Float, hub: Float, deg: Float) {
    drawCircle(Color(0xFF3B2A1E), tape, c)
    // Alur pita
    drawCircle(Color.Black.copy(alpha = 0.18f), tape * 0.75f, c, style = Stroke(width = 1f))
    drawCircle(Color(0xFFEDE7DB), hub * 0.82f, c)
    rotate(deg, c) {
        for (i in 0 until 6) {
            val a = i / 6f * 2 * PI
            val e = Offset(c.x + cos(a).toFloat() * hub * 0.62f, c.y + sin(a).toFloat() * hub * 0.62f)
            drawLine(Color(0xFF3A3A3A), c, e, strokeWidth = hub * 0.16f)
        }
    }
    drawCircle(Color(0xFF3A3A3A), hub * 0.24f, c)
}

/** Tombol mekanis: turun saat ditekan, tombol putar "terkunci" (sedikit masuk) selama lagu berjalan. */
@Composable
private fun DeckKey(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    latched: Boolean = false,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val depth by animateFloatAsState(
        when { pressed -> 1f; latched -> 0.45f; else -> 0f },
        spring(dampingRatio = 0.6f, stiffness = 900f), label = "key",
    )
    Box(
        modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(10.dp))
            .background(RetroColors.Charcoal)
            .clickable(interactionSource = source, indication = null, enabled = enabled, onClickLabel = label, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .padding(start = 3.dp, end = 3.dp, top = 3.dp, bottom = 3.dp)
                .graphicsLayer { translationY = depth * 3.dp.toPx() }
                .clip(RoundedCornerShape(8.dp))
                .background(
                    Brush.verticalGradient(
                        if (latched) listOf(Color(0xFF4A4C48), Color(0xFF3A3C39)) else listOf(Color(0xFF5A5C57), Color(0xFF41433F)),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = if (enabled) RetroColors.CreamText else RetroColors.CreamText.copy(alpha = 0.4f), modifier = Modifier.size(24.dp))
        }
    }
}
