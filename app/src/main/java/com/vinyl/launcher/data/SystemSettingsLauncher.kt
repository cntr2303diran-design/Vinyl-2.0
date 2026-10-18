package com.vinyl.launcher.data

import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.vinyl.launcher.drawer.SystemSetting

/** Membuka layar pengaturan sistem. `when` tanpa else: menambah [SystemSetting] tanpa memetakan intent-nya tidak akan lolos kompilasi. */
object SystemSettingsLauncher {

    fun action(setting: SystemSetting): String = when (setting) {
        SystemSetting.WIFI -> Settings.ACTION_WIFI_SETTINGS
        SystemSetting.BLUETOOTH -> Settings.ACTION_BLUETOOTH_SETTINGS
        SystemSetting.DISPLAY -> Settings.ACTION_DISPLAY_SETTINGS
        SystemSetting.SOUND -> Settings.ACTION_SOUND_SETTINGS
        SystemSetting.BATTERY -> Settings.ACTION_BATTERY_SAVER_SETTINGS
        SystemSetting.APPS -> Settings.ACTION_APPLICATION_SETTINGS
        SystemSetting.DEFAULT_APPS -> Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS
        SystemSetting.LOCATION -> Settings.ACTION_LOCATION_SOURCE_SETTINGS
        SystemSetting.SECURITY -> Settings.ACTION_SECURITY_SETTINGS
        SystemSetting.PRIVACY -> Settings.ACTION_PRIVACY_SETTINGS
        SystemSetting.LANGUAGE -> Settings.ACTION_LOCALE_SETTINGS
        SystemSetting.DATE_TIME -> Settings.ACTION_DATE_SETTINGS
        SystemSetting.ACCESSIBILITY -> Settings.ACTION_ACCESSIBILITY_SETTINGS
        SystemSetting.STORAGE -> Settings.ACTION_INTERNAL_STORAGE_SETTINGS
        SystemSetting.AIRPLANE -> Settings.ACTION_AIRPLANE_MODE_SETTINGS
        SystemSetting.NFC -> Settings.ACTION_NFC_SETTINGS
        SystemSetting.CAST -> Settings.ACTION_CAST_SETTINGS
        SystemSetting.NOTIFICATION_ACCESS -> Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS
        SystemSetting.DEVELOPER -> Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS
        SystemSetting.ALL_SETTINGS -> Settings.ACTION_SETTINGS
    }

    /** Beberapa pabrikan tidak menyediakan semua layar; bila gagal, buka halaman Pengaturan utama. */
    fun open(context: Context, setting: SystemSetting) {
        fun start(action: String) = runCatching { context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
        if (!start(action(setting))) start(Settings.ACTION_SETTINGS)
    }
}
