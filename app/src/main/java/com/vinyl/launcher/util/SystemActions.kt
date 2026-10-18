package com.vinyl.launcher.util

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import com.vinyl.launcher.data.SettingsRepository
import com.vinyl.launcher.media.MediaRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import com.vinyl.launcher.data.AppsRepository
import com.vinyl.launcher.data.GestureAction
import com.vinyl.launcher.data.GestureBinding
import com.vinyl.launcher.service.VinylAccessibilityService
import com.vinyl.launcher.ui.settings.SettingsActivity

/** Aksi sistem yang bisa dipicu gestur home screen. */
object SystemActions {

    /**
     * Menjalankan gestur. [openDrawer] dipanggil untuk aksi APP_DRAWER
     * karena drawer adalah bagian UI home screen.
     */
    fun perform(
        context: Context, binding: GestureBinding,
        openCrate: () -> Unit = {}, startEdit: () -> Unit = {}, openDrawer: () -> Unit,
    ) {
        when (binding.action) {
            GestureAction.NONE -> Unit
            GestureAction.APP_DRAWER -> openDrawer()
            GestureAction.NOTIFICATIONS -> expandNotifications(context)
            GestureAction.LOCK_SCREEN -> lockScreen(context)
            GestureAction.OPEN_APP -> binding.packageName?.let { AppsRepository.get(context).launchPackage(it) }
            GestureAction.CRATE -> openCrate()
            GestureAction.PLAY_PAUSE -> MediaRepository.togglePlayPause("Gestur")
            GestureAction.NEXT_TRACK -> MediaRepository.next()
            GestureAction.PREV_TRACK -> MediaRepository.previous()
            GestureAction.BACK -> global(context, AccessibilityService.GLOBAL_ACTION_BACK)
            GestureAction.RECENTS -> global(context, AccessibilityService.GLOBAL_ACTION_RECENTS)
            GestureAction.QUICK_SETTINGS -> global(context, AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)
            GestureAction.SCREENSHOT ->
                if (Build.VERSION.SDK_INT >= 28) global(context, AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT)
            GestureAction.EDIT_HOME -> startEdit()
            GestureAction.TASKBAR_TOGGLE -> scope.launch {
                SettingsRepository.get(context).update { it.copy(dock = it.dock.copy(enabled = !it.dock.enabled)) }
            }
            GestureAction.SETTINGS -> context.startActivity(
                Intent(context, SettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun lockScreen(context: Context) = global(context, AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)

    /** Aksi global sistem (Back, Recents, kunci layar…) lewat layanan aksesibilitas Vinyl. */
    fun global(context: Context, action: Int) {
        val svc = VinylAccessibilityService.instance
        if (svc != null) {
            svc.performGlobalAction(action)
        } else {
            Toast.makeText(context, "Aktifkan layanan aksesibilitas Vinyl Launcher dulu", Toast.LENGTH_LONG).show()
            openAccessibilitySettings(context)
        }
    }

    @SuppressLint("WrongConstant")
    fun expandNotifications(context: Context) {
        val svc = VinylAccessibilityService.instance
        if (svc != null) {
            svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)
            return
        }
        // Cadangan: API tersembunyi StatusBarManager (izin EXPAND_STATUS_BAR, berfungsi di sebagian besar perangkat)
        runCatching {
            val sbm = context.getSystemService("statusbar")
            Class.forName("android.app.StatusBarManager")
                .getMethod("expandNotificationsPanel")
                .invoke(sbm)
        }.onFailure {
            Toast.makeText(context, "Aktifkan layanan aksesibilitas Vinyl Launcher", Toast.LENGTH_SHORT).show()
        }
    }

    fun openAccessibilitySettings(context: Context) = startSafe(context, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))

    fun openNotificationAccessSettings(context: Context) =
        startSafe(context, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))

    fun openHomeSettings(context: Context) = startSafe(context, Intent(Settings.ACTION_HOME_SETTINGS))

    fun openUsageAccessSettings(context: Context) = startSafe(context, Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))

    fun openOverlaySettings(context: Context) = startSafe(
        context,
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")),
    )

    fun openAppSettings(context: Context) = startSafe(
        context,
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")),
    )

    fun isAccessibilityEnabled(): Boolean = VinylAccessibilityService.instance != null

    private fun startSafe(context: Context, intent: Intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }
}
