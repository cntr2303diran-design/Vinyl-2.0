package com.vinyl.launcher.system

import java.time.LocalDate
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale

/*
 * Status perangkat yang ditampilkan di OSD beranda (pengganti status bar Samsung saat beranda tampil) dan semua
 * keputusan teksnya. Murni (tanpa Android) agar diuji. Pengumpulnya: DeviceStatusMonitor.
 */

enum class Wifi { OFF, ON_NOT_CONNECTED, CONNECTED, UNKNOWN }

enum class Bluetooth { OFF, ON, CONNECTED, UNKNOWN }

enum class Ringer { NORMAL, VIBRATE, SILENT }

data class DeviceStatus(
    /** 0..100; -1 = belum terbaca. */
    val batteryLevel: Int = -1,
    val charging: Boolean = false,
    val powerSave: Boolean = false,
    val wifi: Wifi = Wifi.UNKNOWN,
    /** 0..4 batang; hanya berarti bila [wifi] CONNECTED. */
    val wifiLevel: Int = 0,
    /** Perangkat punya radio seluler. */
    val hasCellular: Boolean = false,
    /** 0..4 batang; -1 = tidak ada sinyal / tidak terbaca. */
    val cellLevel: Int = -1,
    val simAbsent: Boolean = false,
    /** Internet sedang lewat data seluler. */
    val mobileData: Boolean = false,
    val airplane: Boolean = false,
    val bluetooth: Bluetooth = Bluetooth.UNKNOWN,
    /** Nama perangkat audio Bluetooth yang tersambung (dari daftar keluaran audio), bila ada. */
    val bluetoothDevice: String? = null,
    val ringer: Ringer = Ringer.NORMAL,
    val doNotDisturb: Boolean = false,
    /** Alarm berikutnya sebagai jam lokal, null = tidak ada. */
    val nextAlarm: LocalTime? = null,
    /** Jumlah notifikasi (tanpa ringkasan grup); -1 = akses notifikasi tidak diberikan. */
    val notifications: Int = -1,
)

object StatusText {
    private val ID = Locale.forLanguageTag("id-ID")

    /** RSSI Wi-Fi (dBm) → 0..4 batang. Ambang umum Android (−55 / −66 / −77 / −88). */
    fun wifiLevel(rssi: Int?): Int = when {
        rssi == null || rssi >= 0 || rssi < -127 -> 0
        rssi >= -55 -> 4
        rssi >= -66 -> 3
        rssi >= -77 -> 2
        rssi >= -88 -> 1
        else -> 0
    }

    /** Batang baterai (0..4) untuk ikon OSD; level tak terbaca = 0. */
    fun batteryBars(level: Int): Int = if (level < 0) 0 else ((level.coerceIn(0, 100) + 12) / 25).coerceIn(0, 4)

    /** Baterai lemah: ≤ 15% dan tidak sedang diisi. */
    fun batteryLow(level: Int, charging: Boolean): Boolean = level in 0..15 && !charging

    /** "MIN 18 OKT 2026" — tanggal ala kamera, huruf kapital, bahasa Indonesia. */
    fun dateLine(date: LocalDate): String {
        val day = date.dayOfWeek.getDisplayName(TextStyle.SHORT, ID).uppercase(ID).replace(".", "")
        val month = date.month.getDisplayName(TextStyle.SHORT, ID).uppercase(ID).replace(".", "")
        return "$day ${date.dayOfMonth} $month ${date.year}"
    }

    /** "19:21:07" atau "7:21:07 PM" (12 jam); detik opsional. */
    fun timeLine(time: LocalTime, use24h: Boolean, seconds: Boolean): String {
        val m = "%02d".format(time.minute)
        val s = if (seconds) ":%02d".format(time.second) else ""
        return if (use24h) "%02d:%s%s".format(time.hour, m, s)
        else {
            val h12 = (time.hour % 12).let { if (it == 0) 12 else it }
            "$h12:$m$s ${if (time.hour < 12) "AM" else "PM"}"
        }
    }

    /** Arah gerak pita untuk teks mode. */
    enum class Transport { STOP, PLAY, PAUSE, FORWARD, REWIND }

    /** Teks mode di kiri atas OSD, gaya VCR. */
    fun modeLine(t: Transport): String = when (t) {
        Transport.STOP -> "■ STOP"
        Transport.PLAY -> "▶ PLAY"
        Transport.PAUSE -> "❚❚ PAUSE"
        Transport.FORWARD -> "▶▶ FF"
        Transport.REWIND -> "◀◀ REW"
    }

    fun transport(hasTrack: Boolean, playing: Boolean, seekDeltaMs: Long?): Transport = when {
        !hasTrack -> Transport.STOP
        seekDeltaMs != null && seekDeltaMs > 0 -> Transport.FORWARD
        seekDeltaMs != null && seekDeltaMs < 0 -> Transport.REWIND
        playing -> Transport.PLAY
        else -> Transport.PAUSE
    }

    /** Penghitung pita "0:02:35" (selalu jam:menit:detik, seperti VCR). */
    fun counter(ms: Long): String {
        val s = (ms.coerceAtLeast(0L) / 1000L)
        return "%d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60)
    }

    /** Sisa timer tidur "SLEEP 25:00"; null bila tidak aktif. */
    fun sleepLine(remainingMs: Long?): String? {
        if (remainingMs == null || remainingMs <= 0L) return null
        val s = (remainingMs + 999L) / 1000L
        return "SLEEP %d:%02d".format(s / 60, s % 60)
    }

    /** Kalimat lengkap untuk pembaca layar (bagian yang tidak terbaca dilewati, tidak ditebak). */
    fun spoken(s: DeviceStatus): String = buildList {
        if (s.batteryLevel >= 0) add("Baterai ${s.batteryLevel} persen" + if (s.charging) ", mengisi daya" else "")
        if (s.powerSave) add("Hemat daya aktif")
        if (s.airplane) add("Mode pesawat")
        when (s.wifi) {
            Wifi.CONNECTED -> add("Wi-Fi tersambung, sinyal ${s.wifiLevel} dari 4")
            Wifi.ON_NOT_CONNECTED -> add("Wi-Fi menyala, tidak tersambung")
            Wifi.OFF -> add("Wi-Fi mati")
            Wifi.UNKNOWN -> Unit
        }
        if (s.hasCellular) when {
            s.simAbsent -> add("Tidak ada kartu SIM")
            s.cellLevel >= 0 -> add("Sinyal seluler ${s.cellLevel} dari 4" + if (s.mobileData) ", data seluler dipakai" else "")
        }
        when (s.bluetooth) {
            Bluetooth.CONNECTED -> add("Bluetooth tersambung" + (s.bluetoothDevice?.let { " ke $it" } ?: ""))
            Bluetooth.ON -> add("Bluetooth menyala")
            Bluetooth.OFF -> add("Bluetooth mati")
            Bluetooth.UNKNOWN -> Unit
        }
        when (s.ringer) {
            Ringer.VIBRATE -> add("Mode getar")
            Ringer.SILENT -> add("Mode senyap")
            Ringer.NORMAL -> Unit
        }
        if (s.doNotDisturb) add("Jangan Ganggu aktif")
        s.nextAlarm?.let { add("Alarm %02d:%02d".format(it.hour, it.minute)) }
        if (s.notifications > 0) add("${s.notifications} notifikasi")
    }.joinToString(". ")
}
