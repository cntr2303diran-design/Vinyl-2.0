package com.vinyl.launcher.data

import com.vinyl.launcher.data.secure.FakeKeys
import com.vinyl.launcher.data.secure.MapSlots
import com.vinyl.launcher.data.secure.SealedText
import com.vinyl.launcher.data.secure.SerialWorker
import com.vinyl.launcher.data.secure.Vault
import com.vinyl.launcher.data.secure.VaultPurpose
import com.vinyl.launcher.data.secure.drain
import com.vinyl.launcher.data.secure.softwareKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Buku harian: merekam musik, film, dan game sesuai saklar; tersimpan terenkripsi; gagal-tertutup. */
class DiaryStoreTest {
    private class FakeFlags(var flags: DiaryFlags = DiaryFlags()) : DiaryFlagStore {
        var writes = 0
        override fun read() = flags
        override fun write(flags: DiaryFlags) {
            this.flags = flags
            writes++
        }
    }

    private val keys = FakeKeys()
    private val vault = Vault(keys)
    private val slots = MapSlots()
    private val flagStore = FakeFlags()

    private fun open(over: MapSlots = slots): Pair<DiaryStore, SerialWorker> {
        val worker = SerialWorker()
        val store = DiaryStore(SealedText(over, vault, VaultPurpose.DIARY, "entries_sealed", "entries"), flagStore, worker)
        worker.drain()
        return store to worker
    }

    private fun titles(store: DiaryStore) = store.entries.value.map { it.title }

    @Test
    fun recordingIsOffByDefaultSoNothingIsKeptOrWritten() {
        val (diary, worker) = open()
        diary.record(DiaryKind.MUSIC, "Lagu", "Artis", at = 1_000)
        diary.record(DiaryKind.FILM, "Film", at = 2_000)
        diary.record(DiaryKind.GAME, "Game Ular", "Skor 9", at = 3_000)
        worker.drain()
        assertTrue(diary.entries.value.isEmpty())
        assertTrue(slots.keys().isEmpty())
        assertFalse(diary.flags.value.on)
    }

    @Test
    fun theMasterSwitchRecordsMusicFilmAndGamesTogether() {
        val (diary, worker) = open()
        diary.setRecording(true)
        diary.record(DiaryKind.MUSIC, "Lagu A", "Artis A", at = 1_000)
        diary.record(DiaryKind.FILM, "Kaset Film", at = 2_000)
        diary.record(DiaryKind.GAME, "Game Ular", "Skor 12 · rekor baru", at = 3_000)
        worker.drain()
        assertEquals(listOf(DiaryKind.MUSIC, DiaryKind.FILM, DiaryKind.GAME), diary.entries.value.map { it.kind })
        assertEquals(listOf("Lagu A", "Kaset Film", "Game Ular"), titles(diary))
        assertEquals(listOf("Artis A", "", "Skor 12 · rekor baru"), diary.entries.value.map { it.artist })
    }

    @Test
    fun eachKindHasItsOwnSwitchThatOnlyWorksWhileTheMasterIsOn() {
        val (diary, worker) = open()
        diary.setRecording(true)
        diary.setKind(DiaryKind.FILM, false)
        diary.record(DiaryKind.MUSIC, "Musik", "A", at = 1_000)
        diary.record(DiaryKind.FILM, "Film", at = 2_000)
        diary.record(DiaryKind.GAME, "Game", "Skor 1", at = 3_000)
        worker.drain()
        assertEquals(listOf("Musik", "Game"), titles(diary))

        diary.setKind(DiaryKind.FILM, true)
        diary.setKind(DiaryKind.GAME, false)
        diary.record(DiaryKind.FILM, "Film lagi", at = 4_000)
        diary.record(DiaryKind.GAME, "Game lagi", "Skor 2", at = 5_000)
        worker.drain()
        assertEquals(listOf("Musik", "Game", "Film lagi"), titles(diary))

        // Saklar utama mati: tidak ada yang dicatat walau saklar jenisnya menyala
        diary.setRecording(false)
        diary.record(DiaryKind.MUSIC, "Tidak", "X", at = 6_000)
        worker.drain()
        assertEquals(3, diary.entries.value.size)
        // Mematikan perekaman tidak menghapus isi
        assertEquals(listOf("Musik", "Game", "Film lagi"), titles(diary))
    }

    @Test
    fun theSwitchesArePersistedThroughTheFlagStore() {
        val (diary, _) = open()
        diary.setRecording(true)
        diary.setKind(DiaryKind.GAME, false)
        assertEquals(DiaryFlags(on = true, music = true, film = true, games = false), flagStore.flags)
        val (again, _) = open()
        assertEquals(flagStore.flags, again.flags.value)
        val writes = flagStore.writes
        again.setRecording(true)
        assertEquals("tidak menulis bila tidak berubah", writes, flagStore.writes)
    }

    @Test
    fun theSameSongOrFilmTwiceInAMinuteIsOneButEveryGameRoundCounts() {
        val (diary, worker) = open()
        diary.setRecording(true)
        diary.record(DiaryKind.MUSIC, "Lagu", "A", at = 100_000)
        diary.record(DiaryKind.MUSIC, "Lagu", "A", at = 100_500)
        diary.record(DiaryKind.FILM, "Film", at = 200_000)
        diary.record(DiaryKind.FILM, "Film", at = 200_500)
        diary.record(DiaryKind.GAME, "Game Ular", "Skor 5", at = 300_000)
        diary.record(DiaryKind.GAME, "Game Ular", "Skor 5", at = 300_500)
        worker.drain()
        assertEquals(listOf("Lagu", "Film", "Game Ular", "Game Ular"), titles(diary))
    }

    @Test
    fun entriesAreEncryptedAtRestAndComeBackAfterARestart() {
        val (diary, worker) = open()
        diary.setRecording(true)
        diary.record(DiaryKind.MUSIC, "Judul Rahasia", "Artis Rahasia", at = 1_000)
        diary.record(DiaryKind.GAME, "Game Bata", "Skor 77", at = 2_000)
        worker.drain()
        assertEquals(setOf("entries_sealed"), slots.keys())
        assertTrue(slots.values().none { it.contains("Judul Rahasia") || it.contains("Artis Rahasia") || it.contains("Game Bata") || it.contains("Skor 77") })
        val (again, _) = open()
        assertEquals(diary.entries.value, again.entries.value)
        assertEquals(2, again.entries.value.size)
    }

    @Test
    fun anOldPlaintextDiaryWithoutKindsIsReadAsMusicThenSealed() {
        val oldJson = """[{"at":1000,"title":"Lagu lama","artist":"Artis lama"},{"at":2000,"title":"Lagu dua","artist":""}]"""
        val old = MapSlots(mapOf("entries" to oldJson))
        val (diary, _) = open(old)
        assertEquals(listOf("Lagu lama", "Lagu dua"), titles(diary))
        assertTrue(diary.entries.value.all { it.kind == DiaryKind.MUSIC })
        assertEquals(setOf("entries_sealed"), old.keys())
        assertTrue(old.values().none { it.contains("Lagu lama") || it.contains("Artis lama") })
        val (again, _) = open(old)
        assertEquals(diary.entries.value, again.entries.value)
    }

    @Test
    fun withoutEncryptionNothingIsKeptAndTheUserIsTold() {
        val (diary, worker) = open()
        diary.setRecording(true)
        keys.key = null
        diary.record(DiaryKind.MUSIC, "Lagu", "A", at = 1_000)
        worker.drain()
        assertTrue(diary.saveFailed.value)
        assertTrue(diary.entries.value.isEmpty())
        assertTrue(slots.keys().isEmpty())
        keys.key = softwareKey()
        diary.record(DiaryKind.MUSIC, "Lagu", "A", at = 2_000)
        worker.drain()
        assertFalse(diary.saveFailed.value)
        assertEquals(1, diary.entries.value.size)
    }

    @Test
    fun deleteRemovesOneEntryAndClearAllRemovesTheStoredValue() {
        val (diary, worker) = open()
        diary.setRecording(true)
        diary.record(DiaryKind.MUSIC, "A", "x", at = 1_000)
        diary.record(DiaryKind.FILM, "B", at = 2_000)
        diary.record(DiaryKind.GAME, "C", "Skor 1", at = 3_000)
        worker.drain()
        diary.delete(diary.entries.value[1])
        worker.drain()
        assertEquals(listOf("A", "C"), titles(diary))
        val (again, _) = open()
        assertEquals(listOf("A", "C"), titles(again))

        val writesBefore = slots.changeCalls
        diary.delete(DiaryEntry(99, "tidak ada"))
        worker.drain()
        assertEquals("menghapus yang tidak ada tidak menulis apa pun", writesBefore, slots.changeCalls)

        diary.clearAll()
        worker.drain()
        assertTrue(diary.entries.value.isEmpty())
        assertTrue(slots.keys().isEmpty())
    }

    @Test
    fun clearingAllWorksEvenWhenEncryptionIsUnavailable() {
        val (diary, worker) = open()
        diary.setRecording(true)
        diary.record(DiaryKind.MUSIC, "A", "x", at = 1_000)
        worker.drain()
        keys.key = null
        diary.clearAll()
        worker.drain()
        assertTrue(slots.keys().isEmpty())
        assertTrue(diary.entries.value.isEmpty())
    }
}
