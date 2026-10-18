package com.vinyl.launcher.ui.taskbar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.vinyl.launcher.ui.theme.RetroColors

/*
 * Permukaan tombol mekanis untuk panel depan VCR (taskbar) dan remote (tombol navigasi): plastik arang dengan sorot
 * tipis di tepi atas dan bayangan di tepi bawah. Saat ditekan tombol "turun" 1dp dan sorotnya hilang, jadi umpan balik
 * tekan terlihat tanpa ripple. Hanya warna token RetroColors; simbol di atasnya memakai CreamText (kontras ≥ 4,5:1,
 * diuji RetroPaletteTest untuk CREAM_TEXT di atas ketiga warna arang).
 */

private val FaceUp = Brush.verticalGradient(listOf(RetroColors.CharcoalRaised, RetroColors.Charcoal))
private val FaceDown = Brush.verticalGradient(listOf(RetroColors.CharcoalDeep, RetroColors.Charcoal))
private val EdgeUp = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.18f), Color.Black.copy(alpha = 0.45f)))
private val EdgeDown = Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.45f), Color.White.copy(alpha = 0.06f)))

/** Muka tombol mekanis. [pressed] dari interaction source tombol induknya. */
internal fun Modifier.keyFace(pressed: Boolean, shape: Shape): Modifier = this
    .graphicsLayer { translationY = if (pressed) 1.dp.toPx() else 0f }
    .clip(shape)
    .background(if (pressed) FaceDown else FaceUp, shape)
    .border(0.8.dp, if (pressed) EdgeDown else EdgeUp, shape)

/** Badan panel depan VCR: arang dengan gradasi halus, tepi atas sedikit tersorot. */
internal val PanelBody = Brush.verticalGradient(
    listOf(RetroColors.CharcoalRaised.copy(alpha = 0.97f), RetroColors.CharcoalDeep.copy(alpha = 0.97f)),
)
internal val PanelEdge = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.16f), Color.Black.copy(alpha = 0.5f)))
