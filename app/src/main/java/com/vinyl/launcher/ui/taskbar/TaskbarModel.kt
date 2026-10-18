package com.vinyl.launcher.ui.taskbar

import com.vinyl.launcher.data.AppInfo
import com.vinyl.launcher.data.DockPosition
import com.vinyl.launcher.data.DockSettings

/** Info layar yang dibutuhkan untuk merencanakan bar (diperbarui saat rotasi / ganti skala font). */
data class DisplayInfo(
    val widthDp: Float,
    val heightDp: Float,
    val density: Float,
    val fontScale: Float,
)

/**
 * Seluruh isi taskbar yang SUDAH diputuskan: daftar aplikasi + rencana ukuran.
 * Dibuat sekali di TaskbarService; dipakai TaskbarBar (isi) dan service (ukuran jendela).
 */
data class TaskbarModel(
    val plan: TaskbarPlan,
    val favorites: List<AppInfo>,
    val recents: List<AppInfo>,
    val windowed: Boolean,
) {
    companion object {
        /** Aplikasi terakhir yang dipertimbangkan (dikurangi bila layar sempit). */
        const val MAX_RECENTS = 4

        /** Jarak aman di kedua ujung sumbu bar agar tidak menempel ke tepi layar (dp, total). */
        private const val EDGE_GUTTER_DP = 16f

        fun build(
            dock: DockSettings,
            apps: List<AppInfo>,
            recents: List<AppInfo>,
            hasTrack: Boolean,
            collapsed: Boolean,
            display: DisplayInfo,
        ): TaskbarModel {
            val favorites = dock.apps.mapNotNull { key -> apps.firstOrNull { it.key == key } }
            val favPkgs = favorites.map { it.packageName }.toSet()
            val recentCandidates =
                if (dock.showRecents) recents.filter { it.packageName !in favPkgs }.take(MAX_RECENTS) else emptyList()
            val vertical = dock.position == DockPosition.LEFT || dock.position == DockPosition.RIGHT
            val screenMain = if (vertical) display.heightDp else display.widthDp
            val plan = TaskbarPlanner.plan(
                TaskbarInput(
                    vertical = vertical,
                    availableMainDp = (screenMain - EDGE_GUTTER_DP).coerceAtLeast(0f),
                    favorites = favorites.size,
                    recentsCandidates = recentCandidates.size,
                    nowPlaying = dock.showNowPlaying && hasTrack,
                    tray = dock.pcStyle,
                    fontScale = display.fontScale,
                    collapsed = collapsed,
                ),
            )
            return TaskbarModel(plan, favorites, recentCandidates.take(plan.recents), dock.windowed)
        }
    }
}
