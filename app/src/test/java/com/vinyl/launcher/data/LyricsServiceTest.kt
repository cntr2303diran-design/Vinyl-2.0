package com.vinyl.launcher.data

import com.vinyl.launcher.music.LyricsCache
import com.vinyl.launcher.music.LyricsOutcome
import com.vinyl.launcher.util.HttpResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

class LyricsServiceTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var clock = 0L
    private val cache = LyricsCache(now = { clock })
    private val urls = CopyOnWriteArrayList<String>()

    @After fun tearDown() { scope.coroutineContext[kotlinx.coroutines.Job]?.cancel() }

    private val goodExact = """{"duration":200,"syncedLyrics":"[00:01.00]satu","plainLyrics":"satu"}"""
    private val goodSearch = """[{"duration":200,"syncedLyrics":"[00:01.00]hasil cari","plainLyrics":"hasil cari"}]"""

    private fun ok(body: String) = HttpResult.Ok(body.toByteArray())

    /** Pengambil palsu: [onUrl] memilih jawaban per URL; semua URL tercatat di [urls]. */
    private fun service(onUrl: (String) -> HttpResult) =
        LyricsService(fetch = { u -> urls += u; onUrl(u) }, cache = cache, scope = scope)

    private fun isSearch(u: String) = "/api/search" in u

    private fun LyricsService.lyrics(allow: Boolean = true) =
        runBlocking { withTimeout(5_000) { get("Judul", "Artis", "Album", 200_000, allow) } }

    @Test fun exactHitStopsAtOneRequest() {
        val s = service { ok(goodExact) }
        assertTrue(s.lyrics() is LyricsOutcome.Found)
        assertEquals(1, urls.size)
    }

    @Test fun exactMissFallsBackToSearch() {
        val s = service { u -> if (isSearch(u)) ok(goodSearch) else HttpResult.Status(404) }
        val r = s.lyrics() as LyricsOutcome.Found
        assertEquals("hasil cari", r.lyrics.synced.single().text)
        assertEquals(2, urls.size)
    }

    @Test fun networkFailureDoesNotTryTheSecondStage() {
        val s = service { HttpResult.Failed }
        assertSame(LyricsOutcome.Failed, s.lyrics())
        assertEquals("tahap pencarian tidak boleh menggandakan waktu tunggu", 1, urls.size)
    }

    // ---- REGRESI bug utama: kegagalan sementara dulu tersimpan permanen sebagai "tidak ada lirik" ----

    @Test fun aTransientFailureIsNotRememberedAsNoLyrics() {
        var online = false
        val s = service { if (online) ok(goodExact) else HttpResult.Failed }
        assertSame(LyricsOutcome.Failed, s.lyrics())
        online = true
        clock += 20_000                 // lewat masa redam kegagalan
        assertTrue("sinyal pulih → lirik harus ditemukan", s.lyrics() is LyricsOutcome.Found)
    }

    @Test fun serverErrorsAreNotTreatedAsNoLyricsEither() {
        for (code in listOf(429, 500, 503)) {
            urls.clear(); cache.clear()
            val s = service { HttpResult.Status(code) }
            assertSame("status $code", LyricsOutcome.Failed, s.lyrics())
        }
    }

    @Test fun failureCooldownStopsHammeringButRetryBypassesIt() {
        val s = service { HttpResult.Failed }
        s.lyrics(); s.lyrics(); s.lyrics()
        assertEquals("tiga pemanggilan dalam masa redam = satu permintaan", 1, urls.size)
        s.forget("Judul", "Artis")
        s.lyrics()
        assertEquals("'Coba lagi' langsung bertanya ke server", 2, urls.size)
    }

    @Test fun trueNotFoundIsRememberedForTheTtlThenAskedAgain() {
        val s = service { u -> if (isSearch(u)) ok("[]") else HttpResult.Status(404) }
        assertSame(LyricsOutcome.NotFound, s.lyrics())
        val first = urls.size
        s.lyrics()
        assertEquals("masih dalam TTL → tidak bertanya lagi", first, urls.size)
        clock += 6 * 3_600_000L
        s.lyrics()
        assertTrue("TTL lewat → bertanya lagi", urls.size > first)
    }

    @Test fun foundIsServedFromCacheWithoutAnotherRequest() {
        val s = service { ok(goodExact) }
        s.lyrics(); s.lyrics()
        clock += 7L * 24 * 3_600_000
        s.lyrics()
        assertEquals(1, urls.size)
    }

    // ---- saklar online ----

    @Test fun disabledNeverTouchesTheNetworkEvenWithACachedHit() {
        val s = service { ok(goodExact) }
        s.lyrics()                      // mengisi cache
        urls.clear()
        assertSame(LyricsOutcome.Disabled, s.lyrics(allow = false))
        assertTrue("saklar mati = nol permintaan", urls.isEmpty())
    }

    @Test fun disabledIsNotCached() {
        val s = service { ok(goodExact) }
        assertSame(LyricsOutcome.Disabled, s.lyrics(allow = false))
        assertEquals(0, cache.size)
        assertTrue(s.lyrics(allow = true) is LyricsOutcome.Found)
    }

    @Test fun blankTitleIsNotFoundWithoutRequest() {
        val s = service { ok(goodExact) }
        val r = runBlocking { s.get("   ", "x", "", 0, true) }
        assertSame(LyricsOutcome.NotFound, r)
        assertTrue(urls.isEmpty())
    }

    // ---- permintaan kembar & pembatalan ----

    @Test fun simultaneousCallersShareOneRequest() {
        val gate = CompletableDeferred<Unit>()
        val calls = AtomicInteger()
        val s = service {
            calls.incrementAndGet()
            runBlocking { gate.await() }        // tahan jawaban sampai semua pemanggil menunggu
            ok(goodExact)
        }
        val results = runBlocking {
            withTimeout(5_000) {
                val jobs = (1..6).map { async(Dispatchers.Default) { s.get("Judul", "Artis", "Album", 200_000, true) } }
                delay(150)
                gate.complete(Unit)
                jobs.awaitAll()
            }
        }
        assertTrue(results.all { it is LyricsOutcome.Found })
        assertEquals("6 pemanggil, 1 permintaan jaringan", 1, calls.get())
    }

    @Test fun cancellingTheWaiterStillLetsTheResultLandInTheCache() {
        val gate = CompletableDeferred<Unit>()
        val s = service { runBlocking { gate.await() }; ok(goodExact) }
        runBlocking {
            withTimeout(5_000) {
                val waiter = launch(Dispatchers.Default) { s.get("Judul", "Artis", "Album", 200_000, true) }
                delay(100)
                waiter.cancelAndJoin()                       // pengguna ganti lagu
                gate.complete(Unit)
                var tries = 0
                while (cache.get(LyricsCache.key("Judul", "Artis")) == null && tries++ < 100) delay(20)
            }
        }
        assertTrue("hasil tetap tersimpan walau penunggunya dibatalkan", cache.get(LyricsCache.key("Judul", "Artis")) is LyricsOutcome.Found)
    }

    @Test fun inFlightEntryIsCleanedUpSoALaterRetryRunsAgain() {
        var fail = true
        val s = service { if (fail) HttpResult.Failed else ok(goodExact) }
        assertSame(LyricsOutcome.Failed, s.lyrics())
        fail = false
        s.forget("Judul", "Artis")
        assertTrue(s.lyrics() is LyricsOutcome.Found)
    }

    @Test fun searchStageUsesCleanedTitleButExactStageUsesTheRawOne() {
        val s = service { HttpResult.Status(404) }
        runBlocking { s.get("Lagu (feat. X)", "Artis", "", 0, true) }
        assertTrue(urls[0].contains("Lagu+%28feat.+X%29"))      // exact: judul apa adanya
        assertTrue(urls[1].contains("track_name=Lagu&"))        // search: sudah dibersihkan
    }
}
