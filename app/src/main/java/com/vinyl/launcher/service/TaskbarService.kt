package com.vinyl.launcher.service

import android.app.AppOpsManager
import android.app.KeyguardManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.os.Process
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.vinyl.launcher.R
import com.vinyl.launcher.data.AppInfo
import com.vinyl.launcher.data.AppsRepository
import com.vinyl.launcher.data.DockPosition
import com.vinyl.launcher.data.DockSettings
import com.vinyl.launcher.data.SettingsRepository
import com.vinyl.launcher.media.MediaRepository
import com.vinyl.launcher.ui.settings.SettingsActivity
import com.vinyl.launcher.ui.taskbar.AppPanel
import com.vinyl.launcher.ui.taskbar.DisplayInfo
import com.vinyl.launcher.ui.taskbar.TaskbarBar
import com.vinyl.launcher.ui.taskbar.TaskbarModel
import com.vinyl.launcher.ui.taskbar.TaskbarPlan
import com.vinyl.launcher.ui.theme.VinylTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.ceil

/**
 * Taskbar Vinyl: jendela overlay (TYPE_APPLICATION_OVERLAY) di bagian bawah layar yang tampil
 * di atas semua aplikasi — pengganti taskbar Samsung yang dimatikan sistem saat memakai
 * launcher pihak ketiga. Membutuhkan izin "Tampilkan di atas aplikasi lain".
 */
class TaskbarService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var wm: WindowManager
    private var view: ComposeView? = null
    private val owner = OverlayOwner()

    /** Aplikasi terakhir dibuka (dari UsageStats). */
    private val recents = MutableStateFlow<List<AppInfo>>(emptyList())
    private val screenUnlocked = MutableStateFlow(true)
    private val systemBarsVisible = MutableStateFlow(true)

    /** Ukuran layar saat ini; diperbarui saat rotasi atau skala font berubah. */
    private val displayInfo = MutableStateFlow(DisplayInfo(0f, 0f, 1f, 1f))

    /** Keputusan isi + ukuran taskbar (lihat TaskbarPlanner). Dibuat di onCreate. */
    private lateinit var model: StateFlow<TaskbarModel>

    private data class ModelInputs(
        val dock: DockSettings,
        val apps: List<AppInfo>,
        val recents: List<AppInfo>,
        val hasTrack: Boolean,
        val collapsed: Boolean,
    )

    /**
     * Ukuran layar sebenarnya (ikut rotasi & lipat). Android 11+ memakai WindowMetrics dari WindowManager;
     * displayMetrics milik Service tidak dijamin akurat per jendela. Android 10 memakai displayMetrics.
     */
    private fun readDisplay(): DisplayInfo {
        val dm = resources.displayMetrics
        val (wPx, hPx) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && ::wm.isInitialized) {
            wm.maximumWindowMetrics.bounds.let { it.width() to it.height() }
        } else {
            dm.widthPixels to dm.heightPixels
        }
        return DisplayInfo(
            widthDp = wPx / dm.density,
            heightDp = hPx / dm.density,
            density = dm.density,
            fontScale = resources.configuration.fontScale,
        )
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Bila izin overlay ditolak, service sudah berhenti sendiri dan tidak ada yang perlu diperbarui
        if (::wm.isInitialized) displayInfo.value = readDisplay()
    }

    /**
     * Ukuran jendela dibuat PASTI (bukan WRAP_CONTENT) dari rencana. Jendela WRAP_CONTENT berisi Compose tidak
     * melebar mengikuti isi sehingga elemen paling kanan (jam, tombol ciutkan) terpotong — itulah bug aslinya.
     * Selisih kecil (SLACK_PX) menampung pembulatan piksel tiap elemen.
     */
    private fun applySize(p: WindowManager.LayoutParams, plan: TaskbarPlan) {
        val d = resources.displayMetrics.density
        p.width = ceil(plan.windowWidthDp * d).toInt() + SLACK_PX
        p.height = ceil(plan.windowHeightDp * d).toInt() + SLACK_PX
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> screenUnlocked.value = false
                Intent.ACTION_USER_PRESENT -> screenUnlocked.value = true
                Intent.ACTION_SCREEN_ON -> screenUnlocked.value = unlockedNow()
            }
        }
    }

    private fun isLocked() = getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true

    /** Layar menyala & tidak terkunci — dicek langsung ke sistem, tidak hanya dari broadcast. */
    private fun unlockedNow() = getSystemService(android.os.PowerManager::class.java)?.isInteractive != false && !isLocked()

    /**
     * Tampilkan/sembunyikan TANPA View.GONE: jendela yang GONE berhenti menerima info inset sistem,
     * sehingga status "layar penuh" bisa macet setelah layar kunci dan taskbar tidak muncul lagi.
     * Saat disembunyikan, isi dibuat transparan & jendela tidak menerima sentuhan.
     */
    private fun setShown(show: Boolean) {
        val v = view ?: return
        val p = layoutParams ?: return
        v.alpha = if (show) 1f else 0f
        val flags = if (show) p.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        else p.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        if (flags != p.flags) {
            p.flags = flags
            runCatching { wm.updateViewLayout(v, p) }
        }
    }

    override fun onCreate() {
        super.onCreate()
        startAsForeground()
        if (!Settings.canDrawOverlays(this)) { stopSelf(); return }
        wm = getSystemService(WindowManager::class.java)
        screenUnlocked.value = !isLocked()
        displayInfo.value = readDisplay()
        model = buildModel()
        ContextCompat.registerReceiver(
            this, screenReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_USER_PRESENT)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        owner.start()
        addOverlay()

        // Cocokkan status kunci dengan sistem secara berkala (broadcast USER_PRESENT kadang tidak datang,
        // mis. setelah layar kunci vinyl ditutup), supaya taskbar selalu kembali muncul
        scope.launch {
            while (isActive) {
                screenUnlocked.value = unlockedNow()
                delay(1500)
            }
        }
        // Perbarui daftar aplikasi terakhir secara berkala selama taskbar tampil
        scope.launch {
            while (isActive) {
                if (screenUnlocked.value) refreshRecents()
                delay(4000)
            }
        }
        // Terapkan posisi + ukuran jendela setiap kali rencana berubah (pengaturan, favorit, aplikasi terakhir,
        // lagu, ciutkan/buka, rotasi layar, skala font)
        scope.launch {
            model.collect { m ->
                val v = view; val p = layoutParams
                if (v != null && p != null) {
                    applyPosition(p, SettingsRepository.get(this@TaskbarService).settings.value.dock)
                    applySize(p, m.plan)
                    runCatching { wm.updateViewLayout(v, p) }
                }
                TaskbarState.setThickness(m.plan.windowCrossDp)
            }
        }
        // Tampilkan/sembunyikan sesuai: layar terkunci, layar kunci vinyl, layar penuh
        scope.launch {
            combine(
                screenUnlocked, systemBarsVisible, TaskbarState.suppressed,
                SettingsRepository.get(this@TaskbarService).settings,
            ) { unlocked, barsVisible, suppressed, s ->
                unlocked && !suppressed && (barsVisible || !s.dock.hideInFullscreen)
            }.collect { show ->
                setShown(show)
                if (!show) TaskbarState.setAppPanel(false)
            }
        }
        // Panel semua aplikasi: jendela layar penuh transparan, ada hanya saat dibuka
        scope.launch {
            TaskbarState.appPanelOpen.collect { open -> if (open) showPanel() else hidePanel() }
        }
    }

    private var layoutParams: WindowManager.LayoutParams? = null
    private var panel: ComposeView? = null

    private fun buildModel(): StateFlow<TaskbarModel> {
        val settingsRepo = SettingsRepository.get(this)
        val appsRepo = AppsRepository.get(this)
        val inputs = combine(
            settingsRepo.settings.map { it.dock }.distinctUntilChanged(),
            appsRepo.apps,
            recents,
            MediaRepository.state.map { it.hasTrack }.distinctUntilChanged(),
            TaskbarState.collapsed,
        ) { dock, apps, rec, hasTrack, collapsed -> ModelInputs(dock, apps, rec, hasTrack, collapsed) }
        val initial = TaskbarModel.build(
            settingsRepo.settings.value.dock, appsRepo.apps.value, recents.value,
            MediaRepository.state.value.hasTrack, TaskbarState.collapsed.value, displayInfo.value,
        )
        return combine(inputs, displayInfo) { i, d ->
            TaskbarModel.build(i.dock, i.apps, i.recents, i.hasTrack, i.collapsed, d)
        }.stateIn(scope, SharingStarted.Eagerly, initial)
    }

    private fun showPanel() {
        if (panel != null || !::wm.isInitialized) return
        val v = ComposeView(this).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent { VinylTheme { AppPanel(onClose = { TaskbarState.setAppPanel(false) }) } }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        )
        runCatching { wm.addView(v, params) }.onSuccess { panel = v }
    }

    private fun hidePanel() {
        panel?.let { runCatching { wm.removeView(it) } }
        panel = null
    }

    /** Tempelkan jendela ke sisi pilihan pengguna (bawah/atas/kiri/kanan) + jarak dari tepi. */
    private fun applyPosition(p: WindowManager.LayoutParams, dock: DockSettings) {
        val margin = (resources.displayMetrics.density * dock.margin).toInt()
        p.x = 0; p.y = 0
        when (dock.position) {
            DockPosition.BOTTOM -> { p.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL; p.y = margin }
            DockPosition.TOP -> { p.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL; p.y = margin }
            DockPosition.LEFT -> { p.gravity = Gravity.START or Gravity.CENTER_VERTICAL; p.x = margin }
            DockPosition.RIGHT -> { p.gravity = Gravity.END or Gravity.CENTER_VERTICAL; p.x = margin }
        }
    }

    private fun addOverlay() {
        val v = ComposeView(this).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                VinylTheme {
                    TaskbarBar(modelFlow = model)
                }
            }
        }
        // Deteksi aplikasi layar penuh: jika bilah sistem disembunyikan, taskbar ikut sembunyi
        ViewCompat.setOnApplyWindowInsetsListener(v) { _, insets ->
            systemBarsVisible.value = insets.isVisible(WindowInsetsCompat.Type.navigationBars()) ||
                insets.isVisible(WindowInsetsCompat.Type.statusBars())
            insets
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        )
        applyPosition(params, SettingsRepository.get(this).settings.value.dock)
        applySize(params, model.value.plan)
        layoutParams = params
        runCatching { wm.addView(v, params) }.onSuccess { view = v }
    }

    /** Aplikasi yang terakhir dibuka (tanpa launcher & aplikasi ini), terbaru dulu. */
    private suspend fun refreshRecents() {
        if (!hasUsageAccess(this)) { recents.value = emptyList(); return }
        val list = withContext(Dispatchers.IO) {
            runCatching {
                val usm = getSystemService(UsageStatsManager::class.java)
                val now = System.currentTimeMillis()
                val events = usm.queryEvents(now - 6 * 60 * 60 * 1000L, now)
                val e = UsageEvents.Event()
                val lastUsed = LinkedHashMap<String, Long>()
                while (events.hasNextEvent()) {
                    events.getNextEvent(e)
                    if (e.eventType == UsageEvents.Event.ACTIVITY_RESUMED) lastUsed[e.packageName] = e.timeStamp
                }
                val home = packageManager.resolveActivity(
                    Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0,
                )?.activityInfo?.packageName
                val apps = AppsRepository.get(this@TaskbarService)
                lastUsed.entries
                    .sortedByDescending { it.value }
                    .map { it.key }
                    .filter { it != packageName && it != home && it != "com.android.systemui" }
                    .mapNotNull { apps.findByPackage(it) }
                    .take(4)
            }.getOrDefault(emptyList())
        }
        recents.value = list
    }

    private fun startAsForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Taskbar Vinyl", NotificationManager.IMPORTANCE_MIN).apply { setShowBadge(false) }
        )
        val pi = PendingIntent.getActivity(
            this, 1, Intent(this, SettingsActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notif = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Taskbar Vinyl aktif")
            .setContentIntent(pi)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, NOTIF_ID, notif, type)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        scope.cancel()
        runCatching { unregisterReceiver(screenReceiver) }
        hidePanel()
        view?.let { runCatching { wm.removeView(it) } }
        view = null
        owner.stop()
        TaskbarState.setCollapsed(false)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL = "taskbar"
        private const val NOTIF_ID = 45

        /** Piksel cadangan agar pembulatan tiap elemen (dp → px) tidak memotong elemen paling kanan. */
        private const val SLACK_PX = 12

        fun start(context: Context) {
            if (!Settings.canDrawOverlays(context)) return
            runCatching { ContextCompat.startForegroundService(context, Intent(context, TaskbarService::class.java)) }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, TaskbarService::class.java))
        }

        /** Apakah izin "Akses penggunaan" sudah diberikan (untuk daftar aplikasi terakhir). */
        fun hasUsageAccess(context: Context): Boolean {
            val ops = context.getSystemService(AppOpsManager::class.java) ?: return false
            val mode = ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
            return mode == AppOpsManager.MODE_ALLOWED
        }
    }
}

/** Status global yang memengaruhi taskbar (mis. disembunyikan saat layar kunci vinyl tampil). */
object TaskbarState {
    private val _suppressed = MutableStateFlow(false)
    val suppressed: StateFlow<Boolean> = _suppressed.asStateFlow()
    fun setSuppressed(value: Boolean) { _suppressed.value = value }

    /** Panel daftar aplikasi (tombol 9 titik) sedang terbuka. */
    private val _appPanel = MutableStateFlow(false)
    val appPanelOpen: StateFlow<Boolean> = _appPanel.asStateFlow()
    fun setAppPanel(open: Boolean) { _appPanel.value = open }
    fun toggleAppPanel() { _appPanel.value = !_appPanel.value }

    /** Taskbar diciutkan menjadi pegangan kecil. Disimpan di sini agar rencana ukuran jendela ikut berubah. */
    private val _collapsed = MutableStateFlow(false)
    val collapsed: StateFlow<Boolean> = _collapsed.asStateFlow()
    fun setCollapsed(value: Boolean) {
        _collapsed.value = value
        if (value) _appPanel.value = false
    }

    /** Tebal jendela taskbar (dp) agar panel semua aplikasi bisa menempel tepat di atasnya. */
    private val _thickness = MutableStateFlow(DEFAULT_THICKNESS_DP)
    val thicknessDp: StateFlow<Float> = _thickness.asStateFlow()
    fun setThickness(dp: Float) { _thickness.value = dp }
    const val DEFAULT_THICKNESS_DP = 72f
}

/** Pemilik lifecycle untuk ComposeView yang hidup di luar Activity (jendela overlay). */
internal class OverlayOwner : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
    private val registry = LifecycleRegistry(this)
    private val savedState = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry
    override val viewModelStore = ViewModelStore()

    fun start() {
        savedState.performRestore(null)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    fun stop() {
        if (registry.currentState == Lifecycle.State.INITIALIZED) return
        registry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        viewModelStore.clear()
    }
}
