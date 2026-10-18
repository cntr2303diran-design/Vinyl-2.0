package com.vinyl.launcher.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.vinyl.launcher.data.BadgeRepository
import com.vinyl.launcher.data.SettingsRepository
import com.vinyl.launcher.drawer.BadgeCounter
import com.vinyl.launcher.drawer.NotificationMeta
import com.vinyl.launcher.media.MediaRepository
import com.vinyl.launcher.system.NotificationIcons
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * NotificationListenerService berfungsi sebagai "kunci" agar launcher boleh membaca sesi media aktif lewat
 * MediaSessionManager. Bila Lencana notifikasi di app drawer menyala, layanan ini juga menghitung notifikasi
 * per aplikasi; bila notifikasi di OSD beranda menyala, ia juga mengambil ikon kecil (monokrom, sama dengan status bar)
 * per aplikasi. Yang dibaca HANYA nama paket, dua penanda (berkelanjutan / ringkasan grup), waktu kirim, dan ikon kecil;
 * judul, isi, dan ekstra notifikasi tidak pernah diakses. Semuanya hanya di memori ([BadgeRepository], [NotificationIcons]).
 */
class MediaListenerService : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    /** Permintaan hitung ulang digabung: puluhan notifikasi sekaligus cukup menghasilkan satu-dua hitungan. */
    private val recountRequests = Channel<Unit>(Channel.CONFLATED)
    private var worker: Job? = null
    @Volatile private var badgesEnabled = true
    @Volatile private var osdEnabled = true

    override fun onListenerConnected() {
        super.onListenerConnected()
        MediaRepository.init(this)
        MediaRepository.connect()
        startBadgeWorker()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        MediaRepository.disconnect()
        worker?.cancel()
        BadgeRepository.clear()
        NotificationIcons.clear(accessGranted = false)
    }

    override fun onDestroy() {
        scope.cancel()
        BadgeRepository.clear()
        NotificationIcons.clear(accessGranted = false)
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) { recountRequests.trySend(Unit) }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) { recountRequests.trySend(Unit) }

    private fun startBadgeWorker() {
        worker?.cancel()
        worker = scope.launch {
            // Ikuti pengaturan: saat lencana dimatikan, tidak ada notifikasi yang dibaca sama sekali
            launch {
                SettingsRepository.get(applicationContext).settings
                    .map { it.drawer.showBadges to (it.retro.osd.visible && it.retro.osd.notifications) }
                    .distinctUntilChanged()
                    .collect { (badges, osd) ->
                        badgesEnabled = badges
                        osdEnabled = osd
                        recountRequests.trySend(Unit)
                    }
            }
            while (true) {
                recountRequests.receive()
                recount()
            }
        }
    }

    private fun recount() {
        if (!badgesEnabled) BadgeRepository.clear()
        if (!osdEnabled) NotificationIcons.clear(accessGranted = true)
        if (!badgesEnabled && !osdEnabled) return
        val active = try {
            activeNotifications
        } catch (e: CancellationException) {
            throw e
        } catch (e: RuntimeException) {
            null   // belum/tidak lagi terhubung ke sistem
        } ?: return
        val metas = active.map { n ->
            NotificationMeta(
                packageName = n.packageName.orEmpty(),
                ongoing = n.isOngoing,
                groupSummary = n.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0,
            )
        }
        if (badgesEnabled) BadgeRepository.publish(BadgeCounter.count(metas))
        if (osdEnabled) {
            // Seperti status bar: satu ikon per aplikasi, yang terbaru dulu; ringkasan grup tidak dihitung dua kali
            val shown = active.filter { it.notification.flags and Notification.FLAG_GROUP_SUMMARY == 0 }
            val icons = shown.sortedByDescending { it.postTime }
                .distinctBy { it.packageName }
                .map { it.packageName.orEmpty() to it.notification.smallIcon }
            NotificationIcons.publish(applicationContext, shown.size, icons)
        }
    }
}
