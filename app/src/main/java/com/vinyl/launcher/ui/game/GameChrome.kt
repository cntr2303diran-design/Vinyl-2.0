package com.vinyl.launcher.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vinyl.launcher.ui.theme.OsdFamily
import com.vinyl.launcher.ui.theme.RetroColors
import com.vinyl.launcher.ui.theme.Tokens
import kotlin.math.max

/*
 * Bagian layar yang sama di semua game di TV tabung: pita skor di atas papan dan panel pesan di tengahnya. Huruf bagian
 * dari gambar TV: ukurannya mengikuti lebar TV, bukan skala huruf sistem (dibagi [fontScale] agar hasil akhirnya tetap).
 */

/** Isi panel pesan: judul, baris keterangan, dan (opsional) lencana seperti "REKOR BARU!". */
internal class GamePanelText(val title: String, val lines: List<String>, val badge: String? = null)

/**
 * Pita skor setinggi [heightDp] di atas papan: [start] di kiri (putih), [end] di kanan (amber). Pita ini tidak pernah
 * menutupi petak papan, jadi tidak ada yang bisa tertutup tulisan.
 */
@Composable
internal fun GameHudBar(widthDp: Float, heightDp: Float, fontScale: Float, start: String, end: String) {
    val hudSp = (max(heightDp * 0.62f, 8f) / fontScale).sp
    Row(
        Modifier.fillMaxWidth().height(heightDp.dp).padding(horizontal = (widthDp * 0.04f).dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(start, color = Color.White, fontFamily = OsdFamily, fontSize = hudSp, lineHeight = hudSp, maxLines = 1)
        Text(end, color = RetroColors.Amber, fontFamily = OsdFamily, fontSize = hudSp, lineHeight = hudSp, maxLines = 1)
    }
}

/**
 * Panel pesan di atas papan (hitam [Tokens.Game.PANEL_ALPHA]). [compact] = judul lebih kecil, untuk pesan singkat yang
 * tidak boleh menutupi permainan.
 */
@Composable
internal fun GamePanel(panel: GamePanelText, widthDp: Float, fontScale: Float, modifier: Modifier = Modifier, compact: Boolean = false) {
    val titleSp = ((widthDp * (if (compact) 0.055f else 0.085f)).coerceIn(if (compact) 11f else 14f, 40f) / fontScale).sp
    val lineSp = ((widthDp * 0.046f).coerceIn(9f, 22f) / fontScale).sp
    Column(
        modifier
            .background(Color.Black.copy(alpha = Tokens.Game.PANEL_ALPHA.toFloat()), RoundedCornerShape(4.dp))
            .padding(horizontal = (widthDp * 0.05f).dp, vertical = (widthDp * (if (compact) 0.02f else 0.03f)).dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(panel.title, color = Color.White, fontFamily = OsdFamily, fontSize = titleSp, lineHeight = titleSp, maxLines = 1, textAlign = TextAlign.Center)
        if (panel.badge != null) {
            Text(panel.badge, color = RetroColors.Amber, fontFamily = OsdFamily, fontSize = lineSp, lineHeight = lineSp, maxLines = 1, textAlign = TextAlign.Center, modifier = Modifier.padding(top = (widthDp * 0.012f).dp))
        }
        for (line in panel.lines) {
            Text(line, color = RetroColors.CreamText, fontFamily = OsdFamily, fontSize = lineSp, lineHeight = lineSp, maxLines = 1, textAlign = TextAlign.Center, modifier = Modifier.padding(top = (widthDp * 0.012f).dp))
        }
    }
}
