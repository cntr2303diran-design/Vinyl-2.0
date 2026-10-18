package com.vinyl.launcher.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConsoleModelTest {
    private val speaker = OutputDevice(1, DeviceKind.SPEAKER, "Speaker")
    private val buds = OutputDevice(7, DeviceKind.BLUETOOTH, "Buds Pro", address = "AA:BB")
    private val sco = OutputDevice(8, DeviceKind.BLUETOOTH_CALL, "Buds Pro")
    private val ear = OutputDevice(2, DeviceKind.EARPIECE, "Earpiece")
    private val dac = OutputDevice(9, DeviceKind.USB, "USB DAC", sampleRates = listOf(96_000, 44_100, 0))

    private fun snap(active: OutputDevice?, source: Source = Source.REPORTED, vararg devices: OutputDevice) = AudioSnapshot(
        devices = devices.toList(),
        active = ActiveOutput(active, if (active == null) Reading.unavailable("x") else Reading(active.name, source, "n")),
    )

    @Test
    fun detectedDevicesAreNotCalledTheOutputAndCallDevicesAreHidden() {
        val rows = ConsoleRows.devices(snap(buds, Source.REPORTED, speaker, ear, buds, sco))
        assertEquals(listOf("Buds Pro", "Speaker"), rows.map { it.name })
        assertTrue(rows[0].active)
        assertEquals("Keluaran media", rows[0].status)
        assertFalse(rows[1].active)
        assertEquals("Terdeteksi", rows[1].status)
        // Rute tebakan diberi tanda
        assertEquals("Keluaran media (perkiraan)", ConsoleRows.devices(snap(buds, Source.ESTIMATED, buds))[0].status)
    }

    @Test
    fun qualitySeparatesSourceFromOutputAndNeverGuesses() {
        val q = ConsoleRows.quality(snap(speaker, Source.REPORTED, speaker), AdvancedReadings())
        val byLabel = q.associate { it.label to it.reading }
        assertEquals(Source.UNAVAILABLE, byLabel.getValue("Format sumber").source)
        assertEquals("Keluaran aktif bukan Bluetooth", byLabel.getValue("Codec Bluetooth").note)
        assertEquals(AdvancedReadings.NEEDS_ADVANCED, byLabel.getValue("Resampling"))
        // Bluetooth aktif: codec dari Akses lanjutan (di sini belum ada)
        val bt = ConsoleRows.quality(snap(buds, Source.REPORTED, buds), AdvancedReadings()).first { it.label == "Codec Bluetooth" }
        assertEquals(AdvancedReadings.NEEDS_ADVANCED, bt.reading)
        val withCodec = AdvancedReadings(codec = Reading.reported("AAC", "dumpsys"))
        assertEquals("AAC", ConsoleRows.quality(snap(buds, Source.REPORTED, buds), withCodec).first { it.label == "Codec Bluetooth" }.reading.text)
    }

    @Test
    fun batteryOnlyForBluetooth() {
        assertEquals("Keluaran aktif bukan perangkat Bluetooth", ConsoleRows.battery(snap(speaker, Source.REPORTED, speaker), AdvancedReadings()).note)
        assertEquals(AdvancedReadings.NEEDS_ADVANCED, ConsoleRows.battery(snap(buds, Source.REPORTED, buds), AdvancedReadings()))
    }

    @Test
    fun diagnosticsDescribeUsb() {
        val none = ConsoleRows.diagnostics(snap(speaker, Source.REPORTED, speaker)).first { it.label == "USB DAC" }.reading
        assertEquals(Source.UNAVAILABLE, none.source)
        val usb = ConsoleRows.diagnostics(snap(dac, Source.REPORTED, speaker, dac)).first { it.label == "USB DAC" }.reading
        assertEquals("USB DAC · 44,1 kHz/96 kHz", usb.text)
        assertEquals("Speaker perangkat · Speaker", ConsoleRows.diagnostics(snap(speaker, Source.REPORTED, speaker)).first().reading.text)
    }

    // --------------- sesi EQ
    @Test
    fun sessionsAreValidatedDedupedAndCapped() {
        var l = emptyList<EqSession>()
        l = EqSessions.open(l, -1, "x", SessionOrigin.APP)
        l = EqSessions.open(l, 0, "x", SessionOrigin.APP)          // sesi 0 hanya untuk GLOBAL
        assertTrue(l.isEmpty())
        l = EqSessions.open(l, 0, "", SessionOrigin.GLOBAL)
        l = EqSessions.open(l, 55, "det", SessionOrigin.DETECTED)
        l = EqSessions.open(l, 55, "com.music", SessionOrigin.APP) // sumber lebih andal menggantikan
        l = EqSessions.open(l, 55, "other", SessionOrigin.DETECTED) // yang kurang andal tidak
        assertEquals(2, l.size)
        assertEquals(SessionOrigin.APP, l.first { it.id == 55 }.origin)
        assertEquals("com.music", l.first { it.id == 55 }.packageName)
        repeat(40) { l = EqSessions.open(l, 100 + it, "p$it", SessionOrigin.APP) }
        assertEquals(EqSessions.MAX, l.size)
        assertEquals(139, l.last().id)
        assertEquals(EqSessions.MAX - 1, EqSessions.close(l, 139).size)
    }

    @Test
    fun theSummaryNamesWhatIsReallyProcessed() {
        assertEquals("EQ mati", EqSessions.summary(false, emptyList()).note)
        assertTrue(EqSessions.summary(true, emptyList()).note.startsWith("Belum ada sesi"))
        var l = EqSessions.open(emptyList(), 5, "com.music", SessionOrigin.APP)
        assertEquals("Memasang efek…", EqSessions.summary(true, l).note)
        l = EqSessions.withState(l, 5, SessionState.FAILED, "ditolak")
        assertTrue(EqSessions.summary(true, l).note.endsWith("ditolak"))
        l = EqSessions.withState(l, 5, SessionState.NO_CONTROL)
        assertEquals(Source.UNAVAILABLE, EqSessions.summary(true, l).source)
        l = EqSessions.withState(l, 5, SessionState.ACTIVE)
        val ok = EqSessions.summary(true, l)
        assertEquals("Memproses 1 sesi", ok.text)
        assertEquals("Sesi: com.music", ok.note)
        val g = EqSessions.withState(EqSessions.open(emptyList(), 0, "", SessionOrigin.GLOBAL), 0, SessionState.ACTIVE)
        assertTrue(EqSessions.summary(true, g).note.contains("eksperimental"))
    }
}
