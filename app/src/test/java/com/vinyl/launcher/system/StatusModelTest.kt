package com.vinyl.launcher.system

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class StatusModelTest {

    @Test
    fun wifiBarsFollowRssi() {
        assertEquals(4, StatusText.wifiLevel(-40))
        assertEquals(3, StatusText.wifiLevel(-60))
        assertEquals(2, StatusText.wifiLevel(-70))
        assertEquals(1, StatusText.wifiLevel(-85))
        assertEquals(0, StatusText.wifiLevel(-95))
        // Nilai tidak sah (sistem melaporkan "tidak ditentukan") → tanpa batang, bukan tebakan
        assertEquals(0, StatusText.wifiLevel(null))
        assertEquals(0, StatusText.wifiLevel(Int.MIN_VALUE))
        assertEquals(0, StatusText.wifiLevel(5))
    }

    @Test
    fun batteryBarsAndLowWarning() {
        assertEquals(0, StatusText.batteryBars(-1))
        assertEquals(0, StatusText.batteryBars(5))
        assertEquals(1, StatusText.batteryBars(20))
        assertEquals(3, StatusText.batteryBars(72))
        assertEquals(4, StatusText.batteryBars(100))
        assertTrue(StatusText.batteryLow(15, charging = false))
        assertFalse(StatusText.batteryLow(15, charging = true))
        assertFalse(StatusText.batteryLow(16, charging = false))
        assertFalse(StatusText.batteryLow(-1, charging = false))
    }

    @Test
    fun dateIsUppercaseIndonesianWithoutDots() {
        assertEquals("MIN 18 OKT 2026", StatusText.dateLine(LocalDate.of(2026, 10, 18)))
        assertEquals("SEN 2 FEB 2026", StatusText.dateLine(LocalDate.of(2026, 2, 2)))
    }

    @Test
    fun timeFormats() {
        val t = LocalTime.of(19, 21, 7)
        assertEquals("19:21:07", StatusText.timeLine(t, use24h = true, seconds = true))
        assertEquals("19:21", StatusText.timeLine(t, use24h = true, seconds = false))
        assertEquals("7:21:07 PM", StatusText.timeLine(t, use24h = false, seconds = true))
        assertEquals("12:05 AM", StatusText.timeLine(LocalTime.of(0, 5), use24h = false, seconds = false))
        assertEquals("12:00 PM", StatusText.timeLine(LocalTime.NOON, use24h = false, seconds = false))
    }

    @Test
    fun transportFollowsPlaybackAndKnob() {
        assertEquals(StatusText.Transport.STOP, StatusText.transport(hasTrack = false, playing = true, seekDeltaMs = 5000))
        assertEquals(StatusText.Transport.PLAY, StatusText.transport(true, true, null))
        assertEquals(StatusText.Transport.PAUSE, StatusText.transport(true, false, null))
        assertEquals(StatusText.Transport.FORWARD, StatusText.transport(true, false, 1000))
        assertEquals(StatusText.Transport.REWIND, StatusText.transport(true, true, -1))
        assertEquals("▶ PLAY", StatusText.modeLine(StatusText.Transport.PLAY))
    }

    @Test
    fun counterAndSleep() {
        assertEquals("0:02:35", StatusText.counter(155_000))
        assertEquals("1:00:00", StatusText.counter(3_600_000))
        assertEquals("0:00:00", StatusText.counter(-5))
        assertEquals("SLEEP 25:00", StatusText.sleepLine(25 * 60_000L))
        assertEquals("SLEEP 0:01", StatusText.sleepLine(200))
        assertNull(StatusText.sleepLine(0))
        assertNull(StatusText.sleepLine(null))
    }

    @Test
    fun spokenSummarySkipsUnknownParts() {
        assertEquals("", StatusText.spoken(DeviceStatus()))
        val s = DeviceStatus(
            batteryLevel = 72, charging = true, wifi = Wifi.CONNECTED, wifiLevel = 3, hasCellular = true, cellLevel = 2,
            bluetooth = Bluetooth.CONNECTED, bluetoothDevice = "Buds Pro", ringer = Ringer.VIBRATE, doNotDisturb = true,
            nextAlarm = LocalTime.of(6, 30), notifications = 3,
        )
        assertEquals(
            "Baterai 72 persen, mengisi daya. Wi-Fi tersambung, sinyal 3 dari 4. Sinyal seluler 2 dari 4. " +
                "Bluetooth tersambung ke Buds Pro. Mode getar. Jangan Ganggu aktif. Alarm 06:30. 3 notifikasi",
            StatusText.spoken(s),
        )
        assertTrue(StatusText.spoken(DeviceStatus(hasCellular = true, simAbsent = true)).contains("Tidak ada kartu SIM"))
        assertEquals("VPN aktif", StatusText.spoken(DeviceStatus(vpn = true)))
        assertEquals("Wi-Fi tersambung, sinyal 2 dari 4. VPN aktif. Bluetooth menyala", StatusText.spoken(DeviceStatus(wifi = Wifi.CONNECTED, wifiLevel = 2, vpn = true, bluetooth = Bluetooth.ON)))
    }

    @Test
    fun earphoneBatteryPrefersLiveReportsAndMarksRememberedOnes() {
        val a = "AA:BB:CC:DD:EE:FF"
        val now = 1_000_000_000L
        val remembered = mapOf(com.vinyl.launcher.audio.BatteryReports.storeKey(a) to com.vinyl.launcher.audio.RememberedBattery(70, now - 60_000))
        assertEquals("55%", StatusText.earphoneBattery(a, mapOf(a to 55), remembered, now))
        assertEquals("≈70%", StatusText.earphoneBattery(a, emptyMap(), remembered, now))
        assertEquals(null, StatusText.earphoneBattery(null, mapOf(a to 55), remembered, now))
        assertEquals(null, StatusText.earphoneBattery(a, emptyMap(), remembered, now + com.vinyl.launcher.audio.BatteryReports.MAX_AGE_MS + 60_000))
        val spoken = StatusText.spoken(DeviceStatus(bluetooth = Bluetooth.CONNECTED, bluetoothDevice = "Buds", bluetoothBattery = "≈70%"))
        assertTrue(spoken, spoken.contains("baterai 70 persen (perkiraan)"))
    }
}
