package com.vinyl.launcher.service

import android.app.KeyguardManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.vinyl.launcher.R
import com.vinyl.launcher.data.NavSettings
import com.vinyl.launcher.data.SettingsRepository
import com.vinyl.launcher.ui.settings.SettingsActivity
import com.vinyl.launcher.ui.taskbar.NavButtons
import com.vinyl.launcher.ui.theme.VinylTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Tombol navigasi Vinyl: tiga tombol (Back, Home, Recents) melayang di atas semua aplikasi,
 * posisinya bebas diatur. Back & Recents memakai layanan aksesibilitas Vinyl.
 * Agar tombol bawaan sistem hilang, ubah navigasi sistem ke "Gestur geser".
 */
class NavButtonsService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var wm: WindowManager
    private var view: ComposeView? = null
    private var params: WindowManager.LayoutParams? = null
    private val owner = OverlayOwner()
    private val unlocked = MutableStateFlow(true)
    private var lastW = 0
    private var lastH = 0

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> unlocked.value = false
                Intent.ACTION_USER_PRESENT -> unlocked.value = true
                Intent.ACTION_SCREEN_ON -> unlocked.value = unlockedNow()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        startAsForeground()
        if (!Settings.canDrawOverlays(this)) { stopSelf(); return }
        wm = getSystemService(WindowManager::class.java)
        unlocked.value = getSystemService(KeyguardManager::class.java)?.isKeyguardLocked != true
        ContextCompat.registerReceiver(
            this, screenReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_USER_PRESENT)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        owner.start()
        addOverlay()

        val repo = SettingsRepository.get(this)
        scope.launch {
            // Pindahkan/ubah ukuran saat pengaturan berubah
            repo.settings.collect { reposition(it.nav) }
        }
        scope.launch {
            combine(unlocked, TaskbarState.suppressed) { u, sup -> u && !sup }
                .collect { show -> setShown(show) }
        }
        // Cocokkan status kunci dengan sistem secara berkala (USER_PRESENT kadang tidak datang)
        scope.launch {
            while (isActive) {
                unlocked.value = unlockedNow()
                delay(1500)
            }
        }
    }

    private fun unlockedNow() =
        getSystemService(android.os.PowerManager::class.java)?.isInteractive != false &&
            getSystemService(KeyguardManager::class.java)?.isKeyguardLocked != true

    /** Sembunyikan tanpa GONE (transparan + tidak menerima sentuhan) agar jendela tetap "hidup". */
    private fun setShown(show: Boolean) {
        val v = view ?: return
        val p = params ?: return
        v.alpha = if (show) 1f else 0f
        val flags = if (show) p.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        else p.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        if (flags != p.flags) {
            p.flags = flags
            runCatching { wm.updateViewLayout(v, p) }
        }
    }

    private fun addOverlay() {
        val v = ComposeView(this).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent { VinylTheme { NavButtons() } }
        }
        val p = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP or Gravity.START }
        params = p
        // Ukuran isi berubah (jarak/ukuran tombol) → hitung ulang posisi agar tetap di titik yang dipilih
        v.addOnLayoutChangeListener { _, l, t, r, b, _, _, _, _ ->
            val w = r - l; val h = b - t
            if (w != lastW || h != lastH) {
                lastW = w; lastH = h
                reposition(SettingsRepository.get(this).settings.value.nav)
            }
        }
        runCatching { wm.addView(v, p) }.onSuccess { view = v }
    }

    @Suppress("DEPRECATION")
    private fun screenBounds(): Rect =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) wm.maximumWindowMetrics.bounds
        else DisplayMetrics().also { wm.defaultDisplay.getRealMetrics(it) }.let { Rect(0, 0, it.widthPixels, it.heightPixels) }

    private fun reposition(nav: NavSettings) {
        val v = view ?: return
        val p = params ?: return
        val b = screenBounds()
        val w = if (lastW > 0) lastW else v.width
        val h = if (lastH > 0) lastH else v.height
        p.x = ((b.width() - w) * nav.x.coerceIn(0f, 1f)).toInt().coerceAtLeast(0)
        p.y = ((b.height() - h) * nav.y.coerceIn(0f, 1f)).toInt().coerceAtLeast(0)
        runCatching { wm.updateViewLayout(v, p) }
    }

    private fun startAsForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Tombol navigasi Vinyl", NotificationManager.IMPORTANCE_MIN).apply { setShowBadge(false) }
        )
        val pi = PendingIntent.getActivity(
            this, 2, Intent(this, SettingsActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notif = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Tombol navigasi Vinyl aktif")
            .setContentIntent(pi)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, NOTIF_ID, notif, type)
    }

    /** Layar diputar → ukuran layar berubah, hitung ulang posisi tombol. */
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        reposition(SettingsRepository.get(this).settings.value.nav)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        scope.cancel()
        runCatching { unregisterReceiver(screenReceiver) }
        view?.let { runCatching { wm.removeView(it) } }
        view = null
        owner.stop()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL = "navbuttons"
        private const val NOTIF_ID = 46

        fun start(context: Context) {
            if (!Settings.canDrawOverlays(context)) return
            runCatching { ContextCompat.startForegroundService(context, Intent(context, NavButtonsService::class.java)) }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, NavButtonsService::class.java))
        }
    }
}
