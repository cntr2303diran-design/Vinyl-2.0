package com.vinyl.launcher.ui.taskbar

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vinyl.launcher.data.NavOrder
import com.vinyl.launcher.data.SettingsRepository
import com.vinyl.launcher.util.SystemActions

private enum class NavKey { BACK, HOME, RECENTS }

/**
 * Tombol navigasi Vinyl: Back (segitiga), Home (lingkaran), Recents (kotak) — bentuk khas Android/Samsung.
 * Posisi, jarak, ukuran, urutan & arah susun diatur dari Pengaturan.
 */
@Composable
fun NavButtons() {
    val context = LocalContext.current
    val nav by SettingsRepository.get(context).settings.collectAsStateWithLifecycle()
    val s = nav.nav
    val keys = if (s.order == NavOrder.BACK_HOME_RECENTS) listOf(NavKey.BACK, NavKey.HOME, NavKey.RECENTS)
    else listOf(NavKey.RECENTS, NavKey.HOME, NavKey.BACK)

    val content: @Composable () -> Unit = {
        keys.forEach { key -> NavButton(key, s.size, s.opacity) { press(context, key) } }
    }
    if (s.vertical) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(s.spacing.dp)) { content() }
    } else {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(s.spacing.dp)) { content() }
    }
}

@Composable
private fun NavButton(key: NavKey, sizeDp: Int, opacity: Float, onClick: () -> Unit) {
    val desc = when (key) { NavKey.BACK -> "Kembali"; NavKey.HOME -> "Layar utama"; NavKey.RECENTS -> "Aplikasi terbaru" }
    Box(
        Modifier
            .size(sizeDp.dp)
            .alpha(opacity)
            .clip(CircleShape)
            .background(Color(0xCC151517))
            .border(0.8.dp, Color.White.copy(alpha = 0.22f), CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = desc },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size((sizeDp * 0.4f).dp)) {
            val w = size.minDimension
            val stroke = Stroke(width = w * 0.11f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            val c = Color.White
            when (key) {
                NavKey.BACK -> {
                    val p = Path().apply {
                        moveTo(w * 0.78f, w * 0.12f); lineTo(w * 0.18f, w * 0.5f); lineTo(w * 0.78f, w * 0.88f); close()
                    }
                    drawPath(p, c, style = stroke)
                }
                NavKey.HOME -> drawCircle(c, radius = w * 0.46f, center = Offset(w / 2, w / 2), style = stroke)
                NavKey.RECENTS -> drawRoundRect(
                    c, topLeft = Offset(w * 0.1f, w * 0.1f), size = Size(w * 0.8f, w * 0.8f),
                    cornerRadius = CornerRadius(w * 0.16f), style = stroke,
                )
            }
        }
    }
}

private fun press(context: Context, key: NavKey) {
    when (key) {
        NavKey.BACK -> SystemActions.global(context, AccessibilityService.GLOBAL_ACTION_BACK)
        NavKey.RECENTS -> SystemActions.global(context, AccessibilityService.GLOBAL_ACTION_RECENTS)
        NavKey.HOME -> runCatching {
            context.startActivity(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            )
        }
    }
}
