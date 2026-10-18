package com.vinyl.launcher.drawer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BadgeCounterTest {
    private fun n(pkg: String, ongoing: Boolean = false, summary: Boolean = false) = NotificationMeta(pkg, ongoing, summary)

    @Test fun countsPerPackage() {
        val counts = BadgeCounter.count(listOf(n("a"), n("a"), n("b")))
        assertEquals(mapOf("a" to 2, "b" to 1), counts)
    }

    @Test fun ongoingAndGroupSummariesAreNotCounted() {
        val counts = BadgeCounter.count(listOf(n("music", ongoing = true), n("chat"), n("chat"), n("chat", summary = true)))
        assertEquals(mapOf("chat" to 2), counts)
    }

    @Test fun emptyListAndEmptyPackageProduceNothing() {
        assertTrue(BadgeCounter.count(emptyList()).isEmpty())
        assertTrue(BadgeCounter.count(listOf(n(""))).isEmpty())
    }

    @Test fun countIsCapped() {
        val many = List(BadgeCounter.MAX + 50) { n("spam") }
        assertEquals(BadgeCounter.MAX, BadgeCounter.count(many)["spam"])
    }

    @Test fun formatShowsPlusAboveNinetyNine() {
        assertEquals("1", BadgeCounter.format(1))
        assertEquals("99", BadgeCounter.format(99))
        assertEquals("99+", BadgeCounter.format(100))
        assertEquals("99+", BadgeCounter.format(BadgeCounter.MAX))
    }
}
