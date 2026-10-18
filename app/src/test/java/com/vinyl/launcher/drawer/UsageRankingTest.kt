package com.vinyl.launcher.drawer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageRankingTest {
    private val day = 86_400_000L
    private val now = 1_800_000_000_000L

    @Test fun scoreHalvesEveryHalfLife() {
        assertEquals(1.0, UsageRanking.score(listOf(now), now), 1e-9)
        assertEquals(0.5, UsageRanking.score(listOf(now - 10 * day), now), 1e-9)
        assertEquals(0.25, UsageRanking.score(listOf(now - 20 * day), now), 1e-9)
    }

    @Test fun futureTimestampsCountAsNow() {
        // Jam perangkat dimundurkan: cap di masa depan tidak boleh menghasilkan skor > 1
        assertEquals(1.0, UsageRanking.score(listOf(now + 5 * day), now), 1e-9)
    }

    @Test fun recentFrequentAppsBeatOldOnes() {
        val data = UsageData(
            mapOf(
                "old" to List(5) { now - 30 * day },     // 5 × 0.125 = 0.625 → di bawah ambang
                "stale" to List(3) { now - 20 * day },   // 3 × 0.25 = 0.75 → di bawah ambang
                "hot" to listOf(now - day, now - 2 * day, now - 3 * day),
                "warm" to listOf(now - day, now - 2 * day),
            ),
        )
        assertEquals(listOf("hot", "warm"), UsageRanking.top(data, now, 10))
    }

    @Test fun singleLaunchYesterdayIsNotEnough() {
        val data = UsageData(mapOf("once" to listOf(now - day)))
        assertTrue(UsageRanking.top(data, now, 10).isEmpty())
        val twice = UsageData(mapOf("twice" to listOf(now - day, now - day)))
        assertEquals(listOf("twice"), UsageRanking.top(twice, now, 10))
    }

    @Test fun topHonorsLimitAndFilter() {
        val data = UsageData((1..6).associate { "app$it" to List(it + 1) { now - day } })
        assertEquals(3, UsageRanking.top(data, now, 3).size)
        assertEquals("app6", UsageRanking.top(data, now, 3).first())
        // aplikasi yang disembunyikan/tidak terpasang disaring
        assertFalse("app6" in UsageRanking.top(data, now, 10) { it != "app6" })
        assertTrue(UsageRanking.top(data, now, 0).isEmpty())
        assertTrue(UsageRanking.top(data, now, -3).isEmpty())
    }

    @Test fun tieBreaksByKeyForStableOrder() {
        val stamps = listOf(now - day, now - day)
        val data = UsageData(mapOf("b" to stamps, "a" to stamps, "c" to stamps))
        assertEquals(listOf("a", "b", "c"), UsageRanking.top(data, now, 10))
    }

    @Test fun recordKeepsOnlyNewestStamps() {
        var d = UsageData()
        repeat(UsageRanking.MAX_STAMPS + 15) { i -> d = UsageRanking.record(d, "app", now + i) }
        val stamps = d.launches.getValue("app")
        assertEquals(UsageRanking.MAX_STAMPS, stamps.size)
        assertEquals(now + UsageRanking.MAX_STAMPS + 14, stamps.last())       // yang terbaru dipertahankan
        assertEquals(now + 15, stamps.first())                                // yang terlama dibuang
    }

    @Test fun recordPrunesToMaxKeysKeepingBestScores() {
        var d = UsageData((0 until UsageRanking.MAX_KEYS).associate { "k$it" to listOf(now - 40 * day) })
        d = UsageRanking.record(d, "fresh", now)
        assertEquals(UsageRanking.MAX_KEYS, d.launches.size)
        assertTrue("fresh" in d.launches)
    }

    @Test fun forgetPackageDropsOnlyThatPackage() {
        val d = UsageData(
            mapOf(
                "com.a/.Main" to listOf(now), "com.a/.Other" to listOf(now), "com.a/.Work#10" to listOf(now),
                "com.ab/.Main" to listOf(now),          // awalan nama yang mirip, bukan paket yang sama
                "com.b/.Main" to listOf(now),
            ),
        )
        assertEquals(setOf("com.ab/.Main", "com.b/.Main"), UsageRanking.forgetPackage(d, "com.a").launches.keys)
        assertTrue(UsageRanking.forgetPackage(d, "com.zzz") === d)     // tak ada yang cocok → objek sama (tanpa tulis ulang)
    }

    @Test fun sanitizeRemovesGarbage() {
        val d = UsageData(
            mapOf(
                "" to listOf(now),                                   // kunci kosong
                "x".repeat(600) to listOf(now),                      // kunci kepanjangan
                "future" to listOf(now + 400 * day),                 // jauh di masa depan
                "zero" to listOf(0L, -5L),                           // cap tidak valid
                "ok" to listOf(now - day, now - 2 * day, now + day / 2),
                "many" to List(500) { now - it * 1000L },
            ),
        )
        val clean = UsageRanking.sanitize(d, now)
        assertEquals(setOf("ok", "many"), clean.launches.keys)
        assertEquals(3, clean.launches.getValue("ok").size)
        assertEquals(UsageRanking.MAX_STAMPS, clean.launches.getValue("many").size)
        assertEquals(clean.launches.getValue("ok").sorted(), clean.launches.getValue("ok"))
    }

    @Test fun sanitizeCapsKeyCount() {
        val d = UsageData((0 until UsageRanking.MAX_KEYS + 200).associate { "k$it" to listOf(now - it * day / 10) })
        val clean = UsageRanking.sanitize(d, now)
        assertEquals(UsageRanking.MAX_KEYS, clean.launches.size)
        assertTrue("k0" in clean.launches)                           // yang paling baru bertahan
        assertFalse("k${UsageRanking.MAX_KEYS + 199}" in clean.launches)
    }
}
