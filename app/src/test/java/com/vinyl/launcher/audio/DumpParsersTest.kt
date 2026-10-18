package com.vinyl.launcher.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Fixture: contoh sintetis dari format AOSP (lihat src/test/resources/dumpsys/README.md). */
class DumpParsersTest {
    private fun fixture(name: String): String =
        listOf("src/test/resources/dumpsys/$name", "app/src/test/resources/dumpsys/$name").map(::File).first { it.isFile }.readText()

    private val audio by lazy { fixture("audio_aosp.txt") }
    private val bt by lazy { fixture("bluetooth_aosp.txt") }
    private val flinger by lazy { fixture("audio_flinger_aosp.txt") }
    private val oem by lazy { fixture("unknown_oem.txt") }

    private val buds = OutputDevice(7, DeviceKind.BLUETOOTH, "Buds Pro")
    private val speaker = OutputDevice(1, DeviceKind.SPEAKER, "Speaker")

    @Test
    fun playersAreReadFromThePlaybackMonitor() {
        val p = DumpParsers.players(audio)
        assertEquals(5, p.size)
        val music = p.first { it.sessionId == 4321 }
        assertEquals(10234, music.uid)
        assertEquals("started", music.state)
        assertEquals("USAGE_MEDIA", music.usage)
        assertEquals(44_100, music.sampleRate)
    }

    @Test
    fun onlyLiveMediaSessionsAreOfferedToTheEq() {
        val s = DumpParsers.musicSessions(DumpParsers.players(audio))
        // Sonifikasi, sesi yang sudah dilepas, dan duplikat (dua pemutar satu sesi) disaring
        assertEquals(listOf(4321, 5577), s.map { it.sessionId })
    }

    @Test
    fun theActiveA2dpCodecIsTakenFromTheActiveSectionOnly() {
        val c = DumpParsers.activeA2dpCodec(bt)!!
        assertEquals("AAC", c.name)
        assertEquals(listOf(44_100), c.sampleRates)
        assertEquals(listOf(16), c.bitsPerSample)
    }

    @Test
    fun withoutAnActiveMarkerSeveralDevicesAreNotGuessed() {
        val two = bt.replace(" (Active)", "")
        assertNull(DumpParsers.activeA2dpCodec(two))
        // Satu perangkat saja tanpa penanda: boleh dipakai
        val one = "=== A2dpStateMachine for XX ===\n mCodecConfig: {codecName:LDAC,mCodecType:4,mCodecPriority:0,mSampleRate:0x8(96000),mBitsPerSample:0x2(24),mChannelMode:0x2(STEREO)}"
        assertEquals("LDAC", DumpParsers.activeA2dpCodec(one)!!.name)
        // Codec tidak sah tidak dilaporkan
        assertNull(DumpParsers.activeA2dpCodec("=== A2dpStateMachine for X (Active) ===\n mCodecConfig: {codecName:INVALID CODEC,mCodecType:1000000}"))
    }

    @Test
    fun outputThreadsAndTheRunningOneForTheActiveDevice() {
        val t = DumpParsers.outputThreads(flinger)
        assertEquals(2, t.size) // utas input tidak ikut
        assertEquals("AudioOut_D", t[0].name)
        assertEquals("MIXER", t[0].type)
        assertEquals(true, t[0].standby)
        val a2dp = DumpParsers.activeThread(t, DeviceKind.BLUETOOTH)!!
        assertEquals("AudioOut_2D", a2dp.name)
        assertEquals(48_000, a2dp.sampleRate)
        assertEquals("AUDIO_FORMAT_PCM_16_BIT", a2dp.halFormat)
        // Speaker sedang standby → tidak ada utas berjalan, tidak memilih utas lain
        assertNull(DumpParsers.activeThread(t, DeviceKind.SPEAKER))
        assertNull(DumpParsers.activeThread(t, null))
        assertEquals("PCM 16-bit", DumpParsers.formatName("AUDIO_FORMAT_PCM_16_BIT"))
        assertEquals("AAC LC", DumpParsers.formatName("AUDIO_FORMAT_AAC_LC"))
    }

    @Test
    fun unknownFormatsGiveNothingInsteadOfGuesses() {
        assertTrue(DumpParsers.players(oem).isEmpty())
        assertNull(DumpParsers.activeA2dpCodec(oem))
        assertTrue(DumpParsers.outputThreads(oem).isEmpty())
        assertTrue(DumpParsers.players("").isEmpty())
        // Baris rusak tidak membuat crash
        assertTrue(DumpParsers.players("AudioPlaybackConfiguration piid:1 u/pid:x/y sessionId:").isEmpty())
    }

    @Test
    fun theReportLabelsEverythingAndDetectsResampling() {
        val players = DumpParsers.players(audio)
        val thread = DumpParsers.activeThread(DumpParsers.outputThreads(flinger), DeviceKind.BLUETOOTH)
        val r = AdvancedReport.build(buds, DumpParsers.activeA2dpCodec(bt), thread, players, musicUid = 10234)
        assertEquals("AAC · 44,1 kHz · 16-bit", r.codec.text)
        assertEquals(Source.REPORTED, r.codec.source)
        assertEquals("48 kHz · PCM 16-bit", r.outputFormat.text)
        assertEquals("Ya · 44,1 kHz → 48 kHz", r.resampling.text)
        assertEquals(Source.UNAVAILABLE, r.battery.source)

        // Keluaran bukan Bluetooth: codec tidak berlaku; tidak ada utas speaker berjalan
        val s = AdvancedReport.build(speaker, null, null, players, null)
        assertEquals("Keluaran aktif bukan Bluetooth A2DP", s.codec.note)
        assertEquals(Source.UNAVAILABLE, s.outputFormat.source)
        assertEquals(Source.UNAVAILABLE, s.resampling.source)

        // Sample rate sama → tidak ada resampling
        val same = thread!!.copy(sampleRate = 44_100)
        assertEquals("Tidak · 44,1 kHz", AdvancedReport.build(buds, null, same, players, 10234).resampling.text)
        // Pemutar tanpa sample rate (Android < 14) → tidak tersedia, bukan "Tidak"
        val noRate = players.map { it.copy(sampleRate = 0) }
        assertEquals(Source.UNAVAILABLE, AdvancedReport.build(buds, null, thread, noRate, 10234).resampling.source)
    }
}
