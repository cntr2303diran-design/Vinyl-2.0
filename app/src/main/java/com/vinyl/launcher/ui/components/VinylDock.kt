package com.vinyl.launcher.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vinyl.launcher.data.AppInfo
import kotlinx.coroutines.launch

/**
 * Dock vinyl mini (cadangan bila taskbar sistem tidak tersedia).
 * Tanpa panel latar: hanya deretan piringan kecil berlabel ikon aplikasi.
 */
@Composable
fun VinylDock(
    apps: List<AppInfo>,
    onLaunch: (AppInfo) -> Unit,
    onReplace: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    modifier: Modifier = Modifier,
    itemSize: Dp = 58.dp,
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        apps.forEachIndexed { index, app ->
            VinylAppIcon(
                app = app,
                size = itemSize,
                onClick = { onLaunch(app) },
                onReplace = { onReplace(index) },
                onRemove = { onRemove(index) },
            )
        }
    }
}

/** Satu ikon dock: piringan kecil yang berputar setengah putaran saat diketuk. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VinylAppIcon(app: AppInfo, size: Dp, onClick: () -> Unit, onReplace: () -> Unit, onRemove: () -> Unit) {
    val rotation = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    var menu by remember { mutableStateOf(false) }

    Box(
        Modifier
            .size(size)
            .semantics { contentDescription = app.label }
            .combinedClickable(
                onClick = {
                    scope.launch {
                        rotation.animateTo(rotation.value + 180f, tween(450, easing = FastOutSlowInEasing))
                    }
                    onClick()
                },
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    menu = true
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(size).graphicsLayer { rotationZ = rotation.value }, contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(size)) {
                val r = this.size.minDimension / 2f
                drawCircle(Color.Black.copy(alpha = 0.35f), r, center.copy(y = center.y + 2f))
                drawCircle(Color(0xFF0B0B0D), r)
                for (i in 0..6) drawCircle(Color.White.copy(alpha = 0.06f), r * (0.62f + i * 0.055f), style = Stroke(1f))
                drawCircle(Color.White.copy(alpha = 0.08f), r - 1f, style = Stroke(1.5f))
            }
            Image(app.icon, null, Modifier.size(size * 0.52f).clip(CircleShape))
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text("Ganti aplikasi") }, onClick = { menu = false; onReplace() })
            DropdownMenuItem(text = { Text("Hapus dari dock") }, onClick = { menu = false; onRemove() })
        }
    }
}
