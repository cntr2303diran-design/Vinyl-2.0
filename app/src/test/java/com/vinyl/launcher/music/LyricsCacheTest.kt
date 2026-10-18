package com.vinyl.launcher.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.Callable
import java.util.concurrent.Executors

class LyricsCacheTest {
    private var clock = 1_000L
    private val found = LyricsOutcome.Found(Lyrics(listOf(LyricLine(0, "x")), null))

    private fun cache(max: Int = 60) = LyricsCache(now = { clock }, maxEntries = max)

    @Test fun foundNeverExpires() {
        val c = cache()
        c.put("a", found)
        clock += 365L * 24 * 3_600_000
        assertSame(found, c.get("a"))
    }

    @Test fun notFoundIsRememberedOnlyForTheTtl() {
        val c = cache()
        c.put("a", LyricsOutcome.NotFound)
        clock += 6 * 3_600_000L - 1
        assertSame(LyricsOutcome.NotFound, c.get("a"))
        clock += 1
        assertNull("setelah TTL harus bertanya lagi ke server", c.get("a"))
    }

    @Test fun failureIsOnlyACooldownNotAVerdict() {
        val c = cache()
        c.put("a", LyricsOutcome.Failed)
        clock += 19_999
        assertSame(LyricsOutcome.Failed, c.get("a"))     // meredam permintaan beruntun
        clock += 1
        assertNull("kegagalan sementara tidak boleh dianggap 'tidak ada lirik'", c.get("a"))
    }

    @Test fun invalidateClearsEvenAFreshFailureSoRetryWorksAtOnce() {
        val c = cache()
        c.put("a", LyricsOutcome.Failed)
        c.invalidate("a")
        assertNull(c.get("a"))
    }

    @Test fun laterResultReplacesEarlierOne() {
        val c = cache()
        c.put("a", LyricsOutcome.Failed)
        c.put("a", found)
        assertSame(found, c.get("a"))
    }

    @Test fun evictsLeastRecentlyUsedBeyondCapacity() {
        val c = cache(max = 3)
        c.put("a", found); c.put("b", found); c.put("c", found)
        assertNotNull(c.get("a"))            // 'a' jadi paling baru dipakai
        c.put("d", found)                    // 'b' paling lama tak dipakai → terusir
        assertNull(c.get("b"))
        assertNotNull(c.get("a")); assertNotNull(c.get("c")); assertNotNull(c.get("d"))
        assertEquals(3, c.size)
    }

    @Test fun neverGrowsPastCapacity() {
        val c = cache(max = 10)
        repeat(1000) { c.put("k$it", found) }
        assertEquals(10, c.size)
    }

    @Test fun clockNearTheEndOfLongDoesNotOverflow() {
        val big = LyricsCache(now = { Long.MAX_VALUE - 5 })
        big.put("a", LyricsOutcome.NotFound)
        assertNotNull("kedaluwarsa tidak boleh meluap menjadi negatif", big.get("a"))
    }

    @Test fun keyIgnoresCaseAndOuterSpaces() {
        assertEquals(LyricsCache.key("Song ", " ARTIS"), LyricsCache.key("song", "artis"))
        assertTrue(LyricsCache.key("a", "bc") != LyricsCache.key("ab", "c"))     // pemisah mencegah tabrakan
    }

    @Test fun clearEmptiesEverything() {
        val c = cache()
        c.put("a", found)
        c.clear()
        assertEquals(0, c.size)
        assertNull(c.get("a"))
    }

    @Test fun survivesConcurrentUse() {
        val c = cache(max = 20)
        val pool = Executors.newFixedThreadPool(8)
        try {
            val jobs = (0 until 8).map { t ->
                Callable {
                    repeat(5000) { i ->
                        val k = "k${(i * 7 + t) % 50}"
                        when (i % 4) {
                            0 -> c.put(k, found)
                            1 -> c.get(k)
                            2 -> c.invalidate(k)
                            else -> c.put(k, LyricsOutcome.NotFound)
                        }
                    }
                    true
                }
            }
            pool.invokeAll(jobs).forEach { assertTrue(it.get()) }   // get() melempar bila ada ConcurrentModificationException
        } finally {
            pool.shutdownNow()
        }
        assertTrue(c.size <= 20)
    }
}
