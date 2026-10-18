package com.vinyl.launcher.data

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.graphics.Rect
import android.net.Uri
import android.os.UserHandle
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import com.vinyl.launcher.drawer.AppCategorizer
import com.vinyl.launcher.drawer.AppCategory
import com.vinyl.launcher.drawer.PlatformCategory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.Collator
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/** Satu aplikasi yang bisa diluncurkan. [key] = komponen yang di-flatten (stabil untuk disimpan). */
data class AppInfo(
    val key: String,
    val packageName: String,
    val label: String,
    val icon: ImageBitmap,
    val component: ComponentName,
    val user: UserHandle,
    /** Kategori otomatis (dari pengembang aplikasi, lalu tebakan nama). Pilihan pengguna ditimpa di tingkat tampilan. */
    val category: AppCategory = AppCategory.OTHER,
)

/** Pintasan aplikasi (menu tekan-lama): "Selfie", "Pesan baru", dll. Objek [info] hanya berlaku selama proses hidup. */
class AppShortcut(val appKey: String, val id: String, val label: String, val icon: ImageBitmap?, internal val info: ShortcutInfo)

/**
 * Daftar aplikasi via LauncherApps (mendukung profil kerja),
 * diperbarui otomatis saat aplikasi dipasang/dihapus/diperbarui.
 */
class AppsRepository private constructor(private val context: Context) {

    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val iconCache = ConcurrentHashMap<String, ImageBitmap>()
    private val iconSizePx = (context.resources.displayMetrics.density * 56).toInt()
    private val usage = UsageStore.get(context)

    /** Permintaan muat ulang digabung (CONFLATED) dan diproses satu per satu agar hasil lama tidak menimpa yang baru. */
    private val refreshRequests = Channel<Unit>(Channel.CONFLATED)

    private val _apps = MutableStateFlow<List<AppInfo>>(emptyList())
    /** Semua aplikasi, urut abjad (sadar locale). */
    val apps: StateFlow<List<AppInfo>> = _apps.asStateFlow()

    private val callback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String, user: UserHandle) {
            forgetIfGone(packageName)
            invalidate(packageName)
        }
        override fun onPackageAdded(packageName: String, user: UserHandle) = invalidate(packageName)
        override fun onPackageChanged(packageName: String, user: UserHandle) = invalidate(packageName)
        override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = refresh()
        override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = refresh()
    }

    init {
        scope.launch {
            while (true) {
                refreshRequests.receive()
                try {
                    _apps.value = load()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: RuntimeException) {
                    // Profil terkunci / layanan sistem sedang tidak siap: pertahankan daftar sebelumnya, coba lagi di pemicu berikutnya
                }
            }
        }
        launcherApps.registerCallback(callback)
        refresh()
    }

    /**
     * Hapus riwayat pemakaian paket yang sudah tidak ada. Ditunda dan diperiksa ulang karena sistem bisa memanggil
     * onPackageRemoved sesaat saat aplikasi sekadar diperbarui; bila pengecekan gagal, riwayat dipertahankan.
     */
    private fun forgetIfGone(pkg: String) {
        scope.launch {
            delay(FORGET_DELAY_MS)
            val stillThere = launcherApps.profiles.any { user ->
                try { launcherApps.getActivityList(pkg, user).isNotEmpty() } catch (e: RuntimeException) { true }
            }
            if (!stillThere) usage.forgetPackage(pkg)
        }
    }

    private fun invalidate(pkg: String) {
        iconCache.keys.filter { it.startsWith("$pkg/") }.forEach { iconCache.remove(it) }
        refresh()
    }

    fun refresh() { refreshRequests.trySend(Unit) }

    private fun load(): List<AppInfo> {
        val collator = Collator.getInstance(Locale.getDefault())
        val previous = _apps.value
        return launcherApps.profiles.flatMap { user ->
            val activities = try {
                launcherApps.getActivityList(null, user)
            } catch (e: RuntimeException) {
                // Satu profil (mis. profil kerja terkunci) gagal: jangan sampai menghapus aplikasi profil lain.
                // Aplikasi profil itu yang sudah dikenal dipertahankan; yang lain dimuat normal.
                return@flatMap previous.filter { it.user == user }
            }
            activities
                .filter { it.componentName.packageName != context.packageName || it.componentName.className.endsWith("OnboardingActivity") }
                .map { it.toAppInfo() }
        }.sortedWith { a, b -> collator.compare(a.label, b.label) }
    }

    private fun LauncherActivityInfo.toAppInfo(): AppInfo {
        val key = componentName.flattenToString() + if (user != android.os.Process.myUserHandle()) "#${user.hashCode()}" else ""
        val icon = iconCache.getOrPut(key) {
            getBadgedIcon(0).toBitmap(iconSizePx, iconSizePx).asImageBitmap()
        }
        val appLabel = label.toString()
        val category = AppCategorizer.categorize(platformCategory(applicationInfo), componentName.packageName, appLabel)
        return AppInfo(key, componentName.packageName, appLabel, icon, componentName, user, category)
    }

    private fun platformCategory(info: ApplicationInfo): PlatformCategory = when (info.category) {
        ApplicationInfo.CATEGORY_GAME -> PlatformCategory.GAME
        ApplicationInfo.CATEGORY_AUDIO -> PlatformCategory.AUDIO
        ApplicationInfo.CATEGORY_VIDEO -> PlatformCategory.VIDEO
        ApplicationInfo.CATEGORY_IMAGE -> PlatformCategory.IMAGE
        ApplicationInfo.CATEGORY_SOCIAL -> PlatformCategory.SOCIAL
        ApplicationInfo.CATEGORY_NEWS -> PlatformCategory.NEWS
        ApplicationInfo.CATEGORY_MAPS -> PlatformCategory.MAPS
        ApplicationInfo.CATEGORY_PRODUCTIVITY -> PlatformCategory.PRODUCTIVITY
        else -> PlatformCategory.UNDEFINED
    }

    fun find(key: String): AppInfo? = _apps.value.firstOrNull { it.key == key }
    fun findByPackage(pkg: String): AppInfo? = _apps.value.firstOrNull { it.packageName == pkg }

    fun launch(app: AppInfo, bounds: Rect? = null) {
        if (runCatching { launcherApps.startMainActivity(app.component, app.user, bounds, null) }.isSuccess) usage.record(app.key)
    }

    /** Buka sebagai jendela di tengah layar (±62% × 70%); jika sistem tidak mendukung, terbuka biasa. */
    fun open(app: AppInfo, windowed: Boolean) {
        if (!windowed) { launch(app); return }
        val dm = context.resources.displayMetrics
        val w = (dm.widthPixels * 0.62f).toInt()
        val h = (dm.heightPixels * 0.7f).toInt()
        val l = (dm.widthPixels - w) / 2
        val t = (dm.heightPixels - h) / 2
        val opts = android.app.ActivityOptions.makeBasic().setLaunchBounds(Rect(l, t, l + w, t + h)).toBundle()
        if (runCatching { launcherApps.startMainActivity(app.component, app.user, null, opts) }.isSuccess) usage.record(app.key)
        else launch(app)
    }

    fun launchPackage(pkg: String) {
        findByPackage(pkg)?.let { launch(it) } ?: run {
            context.packageManager.getLaunchIntentForPackage(pkg)?.let {
                it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { context.startActivity(it) }
            }
        }
    }

    /**
     * Pintasan aplikasi untuk menu tekan-lama. Sistem hanya mengizinkan launcher default membacanya; di luar itu
     * (atau profil terkunci) hasilnya kosong, bukan error.
     */
    suspend fun shortcuts(app: AppInfo, max: Int = MAX_SHORTCUTS): List<AppShortcut> = withContext(Dispatchers.IO) {
        try {
            if (!launcherApps.hasShortcutHostPermission()) return@withContext emptyList()
            val query = LauncherApps.ShortcutQuery()
                .setPackage(app.packageName)
                .setActivity(app.component)
                .setQueryFlags(
                    LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                        LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                        LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED,
                )
            val density = context.resources.displayMetrics.densityDpi
            launcherApps.getShortcuts(query, app.user).orEmpty()
                .filter { it.isEnabled }
                .sortedBy { it.rank }
                .take(max)
                .mapNotNull { si ->
                    val label = (si.shortLabel ?: si.longLabel)?.toString()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    val icon = runCatching {
                        launcherApps.getShortcutIconDrawable(si, density)?.toBitmap(iconSizePx, iconSizePx)?.asImageBitmap()
                    }.getOrNull()
                    AppShortcut(app.key, si.id, label, icon, si)
                }
        } catch (e: CancellationException) {
            throw e
        } catch (e: RuntimeException) {
            emptyList()   // SecurityException (bukan launcher default) atau IllegalStateException (profil terkunci)
        }
    }

    fun startShortcut(shortcut: AppShortcut) {
        if (runCatching { launcherApps.startShortcut(shortcut.info, null, null) }.isSuccess) usage.record(shortcut.appKey)
    }

    fun openAppInfo(app: AppInfo) {
        runCatching { launcherApps.startAppDetailsActivity(app.component, app.user, null, null) }
    }

    fun uninstall(app: AppInfo) {
        val intent = Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    companion object {
        const val MAX_SHORTCUTS = 4
        private const val FORGET_DELAY_MS = 60_000L
        // Yang disimpan adalah applicationContext (hidup sepanjang proses), bukan Activity: tidak bocor
        @SuppressLint("StaticFieldLeak")
        @Volatile private var instance: AppsRepository? = null
        fun get(context: Context): AppsRepository =
            instance ?: synchronized(this) {
                instance ?: AppsRepository(context.applicationContext).also { instance = it }
            }
    }
}
