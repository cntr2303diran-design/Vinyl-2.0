package com.vinyl.launcher.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.media.AudioManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.vinyl.launcher.R
import com.vinyl.launcher.ui.cover.CoverActivity
import com.vinyl.launcher.ui.settings.SettingsActivity

/**
 * Foreground service ringan yang memantau ACTION_SCREEN_OFF.
 * Saat layar dimatikan, CoverActivity dibuka sehingga ketika layar menyala lagi
 * yang terlihat adalah layar kunci vinyl (di atas keyguard sistem).
 *
 * Catatan Android 10+: membuka activity dari latar belakang membutuhkan izin
 * "Tampilkan di atas aplikasi lain" (SYSTEM_ALERT_WINDOW) atau layanan aksesibilitas aktif.
 */
class CoverService : Service() {

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_SCREEN_OFF && !inCall()) {
                CoverActivity.show(context)
            }
        }
    }

    /** Jangan menutupi layar saat telepon/panggilan video berlangsung (tanpa izin telepon). */
    private fun inCall(): Boolean {
        val am = getSystemService(AudioManager::class.java) ?: return false
        return am.mode == AudioManager.MODE_IN_CALL || am.mode == AudioManager.MODE_IN_COMMUNICATION
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, NOTIF_ID, buildNotification(), type)
        ContextCompat.registerReceiver(
            this, screenReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        runCatching { unregisterReceiver(screenReceiver) }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.cover_channel), NotificationManager.IMPORTANCE_MIN)
                .apply { setShowBadge(false) }
        )
    }

    private fun buildNotification(): Notification {
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, SettingsActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.cover_notification))
            .setContentIntent(pi)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    companion object {
        private const val CHANNEL = "cover"
        private const val NOTIF_ID = 33

        fun start(context: Context) {
            runCatching {
                ContextCompat.startForegroundService(context, Intent(context, CoverService::class.java))
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, CoverService::class.java))
        }
    }
}
