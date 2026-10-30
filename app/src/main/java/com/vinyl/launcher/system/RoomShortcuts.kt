package com.vinyl.launcher.system

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.widget.Toast
import com.vinyl.launcher.data.PhoneFavorite
import com.vinyl.launcher.media.RadioPick
import com.vinyl.launcher.media.SleepTimer
import com.vinyl.launcher.player.LocalLibrary
import com.vinyl.launcher.player.LocalPlayer
import kotlin.random.Random

/** Apa yang terjadi saat angka diputar di telepon putar. */
sealed interface DialAction {
    /** Buka aplikasi telepon (papan tombol). */
    data object OpenPhone : DialAction
    /** Buka kartu kontak pintasan. */
    data class OpenContact(val name: String, val uri: String) : DialAction
    /** Angka 1–9 yang belum diisi kontak. */
    data class Empty(val digit: Int) : DialAction
}

/**
 * Pintasan benda-benda di ruangan: alarm/timer (jam dinding), lagu acak dan timer tidur (radio), telepon putar. Dipakai
 * beranda dan halaman Pengaturan → Benda ruangan, jadi fungsi yang sama tetap ada walau bendanya tidak muat di layar.
 * Tidak ada izin yang dipakai: semuanya membuka aplikasi sistem lewat Intent atau memakai pemutar dan timer bawaan.
 */
object RoomShortcuts {
    /** Keputusan angka [digit] terhadap pintasan [favorites]; angka 0 dan angka di luar 0–9 membuka aplikasi telepon. */
    fun dialAction(digit: Int, favorites: Map<Int, PhoneFavorite>): DialAction {
        if (digit !in 1..9) return DialAction.OpenPhone
        val f = favorites[digit] ?: return DialAction.Empty(digit)
        return DialAction.OpenContact(f.name, f.uri)
    }

    fun openAlarms(context: Context) = launch(context, Intent(AlarmClock.ACTION_SHOW_ALARMS), "Tidak ada aplikasi jam di perangkat ini")

    fun openTimers(context: Context) = launch(context, Intent(AlarmClock.ACTION_SHOW_TIMERS), "Tidak ada aplikasi timer di perangkat ini")

    fun openPhone(context: Context) = launch(context, Intent(Intent.ACTION_DIAL), "Tidak ada aplikasi telepon di perangkat ini")

    fun dial(context: Context, digit: Int, favorites: Map<Int, PhoneFavorite>) {
        when (val a = dialAction(digit, favorites)) {
            DialAction.OpenPhone -> openPhone(context)
            is DialAction.OpenContact -> launch(context, Intent(Intent.ACTION_VIEW, Uri.parse(a.uri)), "Kontak ${a.name} tidak bisa dibuka")
            is DialAction.Empty -> notify(context, "Angka ${a.digit} belum diisi. Pilih kontaknya di Pengaturan → Benda ruangan")
        }
    }

    /**
     * Putar lagu acak dari koleksi pemutar bawaan (folder musik yang Anda pilih di Pengaturan → Musik). Mengembalikan id lagu
     * pertama yang diputar, atau null bila koleksi kosong (dengan pesan). [lastId] = lagu pilihan radio sebelumnya.
     */
    suspend fun playRandom(context: Context, folderUri: String?, lastId: String?, rnd: Random = Random.Default): String? {
        var lib = LocalLibrary.state.value
        if (!lib.loaded && folderUri != null) {
            LocalLibrary.load(context.applicationContext, folderUri)
            lib = LocalLibrary.state.value
        }
        val ids = RadioPick.order(lib.tracks.map { it.id }, lastId, rnd)
        if (ids.isEmpty()) {
            notify(context, "Belum ada lagu di pemutar bawaan. Pilih folder musik di Pengaturan → Musik")
            return null
        }
        LocalPlayer.play(context.applicationContext, ids, 0)
        return ids.first()
    }

    /** Timer tidur dari radio: mulai [minutes] menit, atau batalkan bila sedang berjalan. Mengembalikan true bila sekarang berjalan. */
    fun toggleSleep(context: Context, minutes: Int): Boolean {
        return if (SleepTimer.state.value is SleepTimer.State.Running) {
            SleepTimer.cancel()
            notify(context, "Timer tidur dibatalkan")
            false
        } else {
            SleepTimer.start(minutes)
            notify(context, "Timer tidur $minutes menit")
            true
        }
    }

    private fun launch(context: Context, intent: Intent, missing: String) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
            notify(context, missing)
        } catch (_: SecurityException) {
            notify(context, missing)
        }
    }

    private fun notify(context: Context, text: String) {
        Toast.makeText(context.applicationContext, text, Toast.LENGTH_SHORT).show()
    }
}
