package com.vinyl.launcher.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Siaran radio bukan lagu: tidak punya kunci riwayat, jadi tidak masuk riwayat, piala, atau buku harian. */
class HistoryKeyTest {
    @Test
    fun aSongGetsANormalizedKey() {
        val np = NowPlaying(hasSession = true, title = "  Judul Lagu ", artist = "ARTIS")
        assertEquals("judul lagu|artis", np.historyKey)
    }

    @Test
    fun aRadioBroadcastGetsNoKey() {
        val np = NowPlaying(hasSession = true, title = "Radio Rimba", artist = "Radio", live = true)
        assertNull(np.historyKey)
    }

    @Test
    fun nothingPlayingGetsNoKey() {
        assertNull(NowPlaying().historyKey)
        assertNull(NowPlaying(hasSession = true, title = "   ").historyKey)
    }
}
