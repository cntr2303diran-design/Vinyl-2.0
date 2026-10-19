package com.vinyl.launcher.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QueueRulesTest {
    private val all: (String) -> Boolean = { true }

    @Test
    fun resumesAtTheSavedTrackAndPosition() {
        val r = QueueRules.resolve(QueueSnapshot(listOf("a", "b", "c"), currentId = "b", positionMs = 42_000), all)
        assertEquals(QueueResume(listOf("a", "b", "c"), 1, 42_000), r)
    }

    @Test
    fun withoutASavedPositionItStartsAtTheFirstTrack() {
        assertEquals(QueueResume(listOf("a", "b"), 0, 0), QueueRules.resolve(QueueSnapshot(listOf("a", "b")), all))
        // id yang tersimpan tidak ada di daftar → awal antrean
        assertEquals(QueueResume(listOf("a", "b"), 0, 0), QueueRules.resolve(QueueSnapshot(listOf("a", "b"), "zzz", 9_000), all))
    }

    @Test
    fun missingTracksAreDroppedAndTheIndexFollowsTheCurrentTrack() {
        val alive = setOf("b", "d", "e")
        val r = QueueRules.resolve(QueueSnapshot(listOf("a", "b", "c", "d", "e"), currentId = "d", positionMs = 5_000)) { it in alive }
        assertEquals(QueueResume(listOf("b", "d", "e"), 1, 5_000), r)
    }

    @Test
    fun whenTheCurrentTrackIsGoneItContinuesWithTheNextOneFromTheStart() {
        val alive = setOf("a", "d", "e")
        val r = QueueRules.resolve(QueueSnapshot(listOf("a", "b", "c", "d", "e"), currentId = "c", positionMs = 77_000)) { it in alive }
        assertEquals(QueueResume(listOf("a", "d", "e"), 1, 0), r)
        // lagu terakhir di antrean hilang → jatuh ke awal antrean, bukan melewati ujung
        val last = QueueRules.resolve(QueueSnapshot(listOf("a", "b", "c"), currentId = "c", positionMs = 1_000)) { it != "c" }
        assertEquals(QueueResume(listOf("a", "b"), 0, 0), last)
    }

    @Test
    fun nothingLeftMeansNothingToResume() {
        assertNull(QueueRules.resolve(QueueSnapshot(emptyList()), all))
        assertNull(QueueRules.resolve(QueueSnapshot(listOf("a", "b"), "a", 1_000)) { false })
    }

    @Test
    fun badIdsAndNegativePositionsAreSanitized() {
        val huge = "x".repeat(QueueRules.MAX_ID_LENGTH + 1)
        val r = QueueRules.resolve(QueueSnapshot(listOf("", huge, "ok"), "ok", -5), all)
        assertEquals(QueueResume(listOf("ok"), 0, 0), r)
        val many = List(QueueRules.MAX_IDS + 50) { "t$it" }
        assertEquals(QueueRules.MAX_IDS, QueueRules.resolve(QueueSnapshot(many), all)!!.ids.size)
    }

    @Test
    fun idsSurviveEncodingIncludingOddCharactersAndCorruptTextIsIgnored() {
        val ids = listOf("primary:Music/Éé 東京.flac", "msf:1234", "a\"b\\c", "tab\there")
        assertEquals(ids, QueueRules.decodeIds(QueueRules.encodeIds(ids)))
        assertEquals(emptyList<String>(), QueueRules.decodeIds("[]"))
        assertNull(QueueRules.decodeIds(null))
        assertNull(QueueRules.decodeIds(""))
        assertNull(QueueRules.decodeIds("[\"a\","))
        assertNull(QueueRules.decodeIds("{\"x\":1}"))
    }
}
