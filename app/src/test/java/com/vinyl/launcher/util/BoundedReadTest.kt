package com.vinyl.launcher.util

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream

class BoundedReadTest {

    private fun bytes(n: Int) = ByteArray(n) { (it % 251).toByte() }

    /** Stream yang hanya mengembalikan potongan kecil per read(), seperti jaringan sungguhan. */
    private class Trickle(private val data: ByteArray, private val chunk: Int) : InputStream() {
        private var pos = 0
        override fun read(): Int = if (pos < data.size) data[pos++].toInt() and 0xFF else -1
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (pos >= data.size) return -1
            val n = minOf(chunk, len, data.size - pos)
            System.arraycopy(data, pos, b, off, n)
            pos += n
            return n
        }
    }

    @Test fun `isi lebih kecil dari batas dikembalikan utuh`() {
        val d = bytes(1000)
        assertArrayEquals(d, ByteArrayInputStream(d).readAtMost(2000))
    }

    @Test fun `tepat di batas masih diterima`() {
        val d = bytes(20_000)
        assertArrayEquals(d, ByteArrayInputStream(d).readAtMost(20_000))
    }

    @Test fun `satu byte di atas batas ditolak`() {
        assertNull(ByteArrayInputStream(bytes(20_001)).readAtMost(20_000))
    }

    @Test fun `stream kosong menghasilkan array kosong`() {
        val r = ByteArrayInputStream(ByteArray(0)).readAtMost(10)
        assertNotNull(r)
        assertEquals(0, r!!.size)
    }

    @Test fun `batas nol hanya menerima stream kosong`() {
        assertEquals(0, ByteArrayInputStream(ByteArray(0)).readAtMost(0)!!.size)
        assertNull(ByteArrayInputStream(bytes(1)).readAtMost(0))
    }

    @Test fun `potongan kecil tetap terbaca lengkap dan batas tetap berlaku`() {
        val d = bytes(100_000)
        assertArrayEquals(d, Trickle(d, 7).readAtMost(100_000))
        assertNull(Trickle(d, 7).readAtMost(99_999))
    }

    @Test fun `batas negatif ditolak`() {
        try {
            ByteArrayInputStream(bytes(1)).readAtMost(-1)
            error("seharusnya melempar")
        } catch (e: IllegalArgumentException) {
            // diharapkan
        }
    }

    @Test fun `teks utf8 dibaca benar termasuk karakter non-ASCII`() {
        val s = "Lagu — 夜に駆ける ♪"
        assertEquals(s, ByteArrayInputStream(s.toByteArray(Charsets.UTF_8)).readTextAtMost(1000))
    }

    @Test fun `hanya https dan berhost yang diizinkan`() {
        assertTrue(Http.isAllowed("https://lrclib.net/api/get?x=1"))
        assertTrue(Http.isAllowed("HTTPS://example.com/a"))
        assertFalse(Http.isAllowed("http://example.com/a"))
        assertFalse(Http.isAllowed("file:///data/data/com.vinyl.launcher/files/x"))
        assertFalse(Http.isAllowed("ftp://example.com/a"))
        assertFalse(Http.isAllowed("content://media/external/images/1"))
        assertFalse(Http.isAllowed("https://"))
        assertFalse(Http.isAllowed("bukan url"))
        assertFalse(Http.isAllowed(""))
    }

    @Test fun `get menolak skema terlarang tanpa menyentuh jaringan`() {
        assertNull(Http.get("http://127.0.0.1:1/", 100))
        assertNull(Http.get("file:///etc/hostname", 100))
        assertNull(Http.getText("javascript:alert(1)", 100))
    }

    @Test fun `faktor sampling memperkecil hanya selama sisi terpanjang masih cukup besar`() {
        assertEquals(1, sampleSizeFor(800, 600, 900))      // sudah lebih kecil dari target
        assertEquals(1, sampleSizeFor(1799, 1000, 900))    // tidak bisa dibagi 2 tanpa jatuh di bawah target
        assertEquals(2, sampleSizeFor(1800, 1000, 900))
        assertEquals(4, sampleSizeFor(4000, 3000, 900))
        assertEquals(8, sampleSizeFor(8000, 8000, 900))
    }

    @Test fun `faktor sampling aman untuk ukuran tidak valid dan ekstrem`() {
        assertEquals(1, sampleSizeFor(0, 0, 900))
        assertEquals(1, sampleSizeFor(-5, 100, 0))
        assertEquals(1, sampleSizeFor(100, 100, -1))
        val huge = sampleSizeFor(Int.MAX_VALUE, Int.MAX_VALUE, 1)
        assertTrue("sampling harus berbatas, dapat $huge", huge in 1..1024)
    }
}
