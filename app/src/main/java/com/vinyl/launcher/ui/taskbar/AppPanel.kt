package com.vinyl.launcher.ui.taskbar

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vinyl.launcher.data.AppsRepository
import com.vinyl.launcher.data.DockPosition
import com.vinyl.launcher.data.SettingsRepository
import com.vinyl.launcher.service.TaskbarState
import com.vinyl.launcher.ui.theme.Tokens
import com.vinyl.launcher.ui.theme.Corner

private val PanelShape = Corner.l

/**
 * Panel kecil berisi semua aplikasi, muncul di dekat taskbar saat tombol 9 titik ditekan.
 * Ketuk di luar panel untuk menutup; ketuk aplikasi untuk membukanya.
 */
@Composable
fun AppPanel(onClose: () -> Unit) {
    val context = LocalContext.current
    val settings by SettingsRepository.get(context).settings.collectAsStateWithLifecycle()
    val apps by AppsRepository.get(context).apps.collectAsStateWithLifecycle()
    val shown = remember(apps, settings.hiddenApps) { apps.filter { it.key !in settings.hiddenApps } }
    val dock = settings.dock
    // Ruang yang ditempati taskbar di sisi tempatnya menempel (tebal jendela taskbar + sedikit jarak)
    val thickness by TaskbarState.thicknessDp.collectAsStateWithLifecycle()
    val gap = (thickness + 12f + dock.margin).dp
    val align = when (dock.position) {
        DockPosition.BOTTOM -> Alignment.BottomCenter
        DockPosition.TOP -> Alignment.TopCenter
        DockPosition.LEFT -> Alignment.CenterStart
        DockPosition.RIGHT -> Alignment.CenterEnd
    }
    val pad = when (dock.position) {
        DockPosition.BOTTOM -> Modifier.padding(bottom = gap)
        DockPosition.TOP -> Modifier.padding(top = gap)
        DockPosition.LEFT -> Modifier.padding(start = gap)
        DockPosition.RIGHT -> Modifier.padding(end = gap)
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(Tokens.Argb.SCRIM))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClose() },
    ) {
        Column(
            Modifier
                .align(align)
                .then(pad)
                .padding(12.dp)
                .width(400.dp)
                .heightIn(max = 480.dp)
                .clip(PanelShape)
                .background(Color(Tokens.Argb.PANEL))
                .border(0.8.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.22f), Color.White.copy(alpha = 0.06f))), PanelShape)
                // Ketukan di dalam panel tidak ikut menutup
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .padding(14.dp),
        ) {
            Text(
                "Semua aplikasi",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 6.dp, bottom = 10.dp),
            )
            LazyVerticalGrid(
                columns = GridCells.Adaptive(80.dp),
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(shown, key = { it.key }) { app ->
                    Column(
                        Modifier
                            .clip(Corner.m)
                            .clickable {
                                onClose()
                                AppsRepository.get(context).open(app, dock.windowed)
                            }
                            .padding(vertical = 8.dp, horizontal = 2.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Image(app.icon, app.label, Modifier.size(46.dp))
                        Text(
                            app.label,
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }
    }
}
