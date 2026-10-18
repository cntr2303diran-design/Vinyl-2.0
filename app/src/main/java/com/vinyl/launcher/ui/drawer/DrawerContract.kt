package com.vinyl.launcher.ui.drawer

import com.vinyl.launcher.data.AppInfo
import com.vinyl.launcher.data.AppShortcut
import com.vinyl.launcher.data.ContactHit
import com.vinyl.launcher.data.DrawerSettings
import com.vinyl.launcher.data.PlayRecord
import com.vinyl.launcher.drawer.AppCategory
import com.vinyl.launcher.drawer.SystemSetting
import com.vinyl.launcher.drawer.UsageData

/** Semua yang dibaca app drawer. [apps] sudah tanpa aplikasi yang disembunyikan dan urut abjad. */
data class DrawerData(
    val apps: List<AppInfo>,
    val settings: DrawerSettings,
    val usage: UsageData,
    val badges: Map<String, Int>,
    val songs: List<PlayRecord>,
    val dockEnabled: Boolean,
    val dockKeys: List<String>,
)

/** Semua yang bisa dilakukan app drawer; diimplementasikan oleh HomeViewModel. */
class DrawerActions(
    val launch: (AppInfo) -> Unit,
    val info: (AppInfo) -> Unit,
    val uninstall: (AppInfo) -> Unit,
    val addToDock: (AppInfo) -> Unit,
    val hide: (AppInfo) -> Unit,
    /** null = kembali ke kategori otomatis. */
    val setCategory: (AppInfo, AppCategory?) -> Unit,
    val shortcuts: suspend (AppInfo) -> List<AppShortcut>,
    val startShortcut: (AppShortcut) -> Unit,
    val playSong: (PlayRecord) -> Unit,
    val openSetting: (SystemSetting) -> Unit,
    val searchContacts: suspend (String) -> List<ContactHit>,
    val openContact: (ContactHit) -> Unit,
    val close: () -> Unit,
)
