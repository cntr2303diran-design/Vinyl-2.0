package com.vinyl.launcher.ui.home

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vinyl.launcher.data.ClockSettings
import com.vinyl.launcher.data.OsdSettings
import com.vinyl.launcher.media.NowPlaying
import com.vinyl.launcher.media.SleepTimer
import com.vinyl.launcher.music.SleepPhase
import com.vinyl.launcher.player.HiFi
import com.vinyl.launcher.system.DeviceStatusMonitor
import com.vinyl.launcher.system.NotificationIcons
import com.vinyl.launcher.system.StatusText
import com.vinyl.launcher.ui.components.rememberNow
import com.vinyl.launcher.ui.room.stage.SeekState
import com.vinyl.launcher.ui.room.vhs.OsdActions
import com.vinyl.launcher.ui.room.vhs.OsdState
import com.vinyl.launcher.ui.room.vhs.VhsOsd
import com.vinyl.launcher.util.SystemActions

/**
 * Mode pita di OSD/deck: "HI-FI" untuk berkas lossless yang diputar pemutar bawaan (formatnya diketahui pasti), "SP"
 * untuk sumber lain (aplikasi musik lain tidak membagikan format sumbernya).
 */
fun tapeMode(np: NowPlaying, ownPackage: String, path: HiFi.PathState): String? = when {
    !np.hasTrack -> null
    np.packageName == ownPackage && path.active && path.lossless -> "HI-FI"
    else -> "SP"
}

/**
 * OSD beranda (pengganti status bar): jam & tanggal tiap detik, status perangkat dari [DeviceStatusMonitor],
 * notifikasi dari [NotificationIcons], timer tidur, dan penghitung pita dari lagu yang sedang diputar.
 */
@Composable
fun RoomOsd(
    np: NowPlaying,
    positionMs: Long,
    seek: SeekState,
    osd: OsdSettings,
    clock: ClockSettings,
    landscape: Boolean,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val now by rememberNow()
    val status by DeviceStatusMonitor.state.collectAsStateWithLifecycle()
    val icons by NotificationIcons.icons.collectAsStateWithLifecycle()
    val path by HiFi.path.collectAsStateWithLifecycle()
    val sleep by SleepTimer.state.collectAsStateWithLifecycle()
    val sleepMs = (sleep as? SleepTimer.State.Running)?.phase?.let {
        when (it) {
            is SleepPhase.Counting -> it.remainingMs
            is SleepPhase.Fading -> it.remainingMs
            else -> null
        }
    }
    val state = OsdState(
        now = now,
        use24h = clock.use24h,
        seconds = osd.seconds,
        showClock = clock.visible,
        status = status,
        notifications = if (osd.notifications) icons else emptyList(),
        showNotifications = osd.notifications,
        transport = StatusText.transport(np.hasTrack, np.isPlaying, seek.deltaMs?.takeIf { seek.touching }),
        tapeMode = tapeMode(np, context.packageName, path),
        sleepLine = StatusText.sleepLine(sleepMs),
        hasTrack = np.hasTrack,
        positionMs = seek.previewMs ?: positionMs,
        durationMs = np.durationMs,
        canSeek = np.canSeek,
    )
    val actions = remember(context) {
        OsdActions(
            onSeek = onSeek,
            onQuickSettings = { SystemActions.expandQuickSettings(context) },
            onNotifications = { SystemActions.expandNotifications(context) },
            onClock = { SystemActions.openAlarms(context) },
            onBattery = { SystemActions.openBatteryUsage(context) },
        )
    }
    VhsOsd(state, landscape, actions, modifier)
}

/**
 * Sembunyikan status bar sistem selama [hidden] (beranda tampil, tanpa drawer/lirik/editor di atasnya). Diusap dari atas,
 * status bar muncul sebentar lalu hilang lagi. Dipasang ulang setiap kali beranda kembali tampil ([reapplyKey]).
 */
@Composable
fun SystemStatusBar(hidden: Boolean, reapplyKey: Any?) {
    val view = LocalView.current
    DisposableEffect(hidden, reapplyKey) {
        val window = view.context.findActivity()?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        if (controller != null) {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (hidden) controller.hide(WindowInsetsCompat.Type.statusBars()) else controller.show(WindowInsetsCompat.Type.statusBars())
        }
        onDispose { controller?.show(WindowInsetsCompat.Type.statusBars()) }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
