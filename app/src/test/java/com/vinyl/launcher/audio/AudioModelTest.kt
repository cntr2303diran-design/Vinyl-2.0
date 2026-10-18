package com.vinyl.launcher.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioModelTest {
    private val speaker = OutputDevice(1, DeviceKind.SPEAKER, "Speaker")
    private val buds = OutputDevice(7, DeviceKind.BLUETOOTH, "Buds", address = "AA:BB")
    private val dac = OutputDevice(9, DeviceKind.USB, "USB DAC")

    @Test
    fun theReportedRouteWinsAndIsLabelledReported() {
        val a = AudioRoutes.active(listOf(speaker, buds, dac), routedIds = listOf(9))
        assertEquals(dac, a.device)
        assertEquals(Source.REPORTED, a.reading.source)
    }

    @Test
    fun aDetectedDeviceIsNotClaimedAsActiveWhenTheSystemSaysOtherwise() {
        // USB DAC tersambung tetapi sistem melaporkan speaker sebagai keluaran media
        val a = AudioRoutes.active(listOf(speaker, dac), routedIds = listOf(1))
        assertEquals(speaker, a.device)
    }

    @Test
    fun withoutAReportedRouteTheGuessIsLabelledAnEstimate() {
        val a = AudioRoutes.active(listOf(speaker, dac, buds), routedIds = null)
        assertEquals(buds, a.device)
        assertEquals(Source.ESTIMATED, a.reading.source)
        val none = AudioRoutes.active(emptyList(), routedIds = null)
        assertNull(none.device)
        assertEquals(Source.UNAVAILABLE, none.reading.source)
        assertEquals("Tidak tersedia", none.reading.display)
        // Rute dilaporkan tetapi tak cocok dengan perangkat yang dikenal: tidak menebak
        assertEquals(Source.UNAVAILABLE, AudioRoutes.active(listOf(speaker), routedIds = listOf(42)).reading.source)
    }

    @Test
    fun callDevicesAreNeverGuessedAsMediaOutput() {
        val sco = OutputDevice(3, DeviceKind.BLUETOOTH_CALL, "Headset")
        val ear = OutputDevice(4, DeviceKind.EARPIECE, "Earpiece")
        assertNull(AudioRoutes.active(listOf(sco, ear), routedIds = null).device)
    }

    @Test
    fun formattingAndLatencyAreHonest() {
        assertEquals("48 kHz", AudioRoutes.khz(48_000))
        assertEquals("44,1 kHz", AudioRoutes.khz(44_100))
        val l = AudioRoutes.bufferLatency(240, 48_000)
        assertEquals(Source.ESTIMATED, l.source)
        assertEquals("≥ 5,0 ms", l.text)
        assertEquals(Source.UNAVAILABLE, AudioRoutes.bufferLatency(null, 48_000).source)
        assertEquals(Source.UNAVAILABLE, AudioRoutes.bufferLatency(0, 48_000).source)
    }

    @Test
    fun profileKeysAreStableAndPreferTheAddressWithoutStoringIt() {
        assertTrue(buds.profileKey.startsWith("BLUETOOTH:"))
        assertTrue(dac.profileKey.startsWith("USB:"))
        // Stabil saat id/nama berubah (alamat dipakai), dan alamatnya sendiri tidak terbaca dari kunci
        assertEquals(buds.profileKey, buds.copy(id = 99, name = "Nama baru").profileKey)
        assertTrue("aa:bb" !in buds.profileKey.lowercase())
        assertTrue(buds.profileKey != buds.copy(address = "AA:BC").profileKey)
        // Tanpa alamat: nama (tanpa beda huruf besar/kecil)
        assertEquals(dac.profileKey, dac.copy(id = 2, name = "usb dac").profileKey)
        assertEquals("USB:".length + 16, dac.profileKey.length)
    }

    @Test
    fun safeHeadroomLowersTheInputByTheLargestBoost() {
        val v = EqValues(lowDb = 6f, midDb = -2f, highDb = 3f, preampDb = -1f)
        assertEquals(-7f to -7f, Eq.inputGains(v, safeHeadroom = true))
        assertEquals(-1f to -1f, Eq.inputGains(v, safeHeadroom = false))
        // Hanya pemotongan: tidak ada penurunan tambahan
        assertEquals(0f to 0f, Eq.inputGains(EqValues(lowDb = -3f), safeHeadroom = true))
    }

    // ---------------- EQ
    @Test
    fun valuesAreClamped() {
        val c = Eq.clamp(EqValues(lowDb = 40f, midDb = Float.NaN, highDb = -99f, preampDb = 20f, balance = 3f))
        assertEquals(EqValues(12f, 0f, -12f, 6f, 1f), c)
    }

    @Test
    fun balanceOnlyAttenuatesTheOppositeSide() {
        assertEquals(0f to 0f, Eq.channelGains(EqValues()))
        val (l, r) = Eq.channelGains(EqValues(balance = 0.5f))
        assertEquals(-6.02f, l, 0.01f)
        assertEquals(0f, r, 0f)
        val (l2, r2) = Eq.channelGains(EqValues(preampDb = -3f, balance = -1f))
        assertEquals(-3f, l2, 0f)
        assertEquals(-63f, r2, 0.001f)
        // Tidak pernah ada penguatan dari balance
        for (b in listOf(-1f, -0.3f, 0f, 0.3f, 1f)) {
            val (a, c) = Eq.channelGains(EqValues(balance = b))
            assertTrue(a <= 0f && c <= 0f)
        }
    }

    @Test
    fun builtInPresetsLeaveHeadroomWhenTheyBoost() {
        for ((name, v) in Eq.BUILT_IN) {
            val maxBoost = Eq.bands(v).maxOrNull()!!.coerceAtLeast(0f)
            assertTrue("$name: preamp ${v.preampDb} vs boost $maxBoost", v.preampDb <= -maxBoost + 1f)
            assertEquals(v, Eq.clamp(v))
        }
        assertTrue(Eq.isFlat(Eq.BUILT_IN.first().second))
        assertEquals(Eq.BUILT_IN.size, Eq.BUILT_IN.map { it.first }.toSet().size)
    }

    @Test
    fun dbText() {
        assertEquals("+3 dB", Eq.dbText(3f))
        assertEquals("−4,5 dB", Eq.dbText(-4.5f))
        assertEquals("0 dB", Eq.dbText(0.1f))
    }

    // ---------------- Otomasi
    private val profiles = mapOf(buds.profileKey to "Earphone Bluetooth", dac.profileKey to "Headphone kabel")

    @Test
    fun connectingAProfiledDeviceAppliesItsPresetAndRemembersTheOldOne() {
        val d = Automation.onOutputChanged(speaker, buds, profiles, current = "Datar", previous = null, restoreOnDisconnect = true)
        assertEquals("Earphone Bluetooth", d.apply)
        assertEquals("Datar", d.previous)
    }

    @Test
    fun disconnectingRestoresThePreviousPresetOnlyWhenAsked() {
        val back = Automation.onOutputChanged(buds, speaker, profiles, current = "Earphone Bluetooth", previous = "Datar", restoreOnDisconnect = true)
        assertEquals("Datar", back.apply)
        val stay = Automation.onOutputChanged(buds, speaker, profiles, current = "Earphone Bluetooth", previous = "Datar", restoreOnDisconnect = false)
        assertNull(stay.apply)
    }

    @Test
    fun switchingBetweenTwoProfiledDevicesKeepsTheOriginalPresetToRestore() {
        val d = Automation.onOutputChanged(buds, dac, profiles, current = "Earphone Bluetooth", previous = "Datar", restoreOnDisconnect = true)
        assertEquals("Headphone kabel", d.apply)
        assertEquals("Datar", d.previous)
    }

    @Test
    fun noChangeAndUnknownDevicesDoNothing() {
        assertNull(Automation.onOutputChanged(buds, buds.copy(id = 3), profiles, "x", "y", true).apply)
        assertNull(Automation.onOutputChanged(speaker, OutputDevice(5, DeviceKind.WIRED, "Kabel"), profiles, "x", null, true).apply)
        assertTrue(Automation.isUsbDac(dac))
        assertTrue(!Automation.isUsbDac(buds) && !Automation.isUsbDac(null))
    }
}
