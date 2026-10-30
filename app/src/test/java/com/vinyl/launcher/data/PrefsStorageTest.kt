package com.vinyl.launcher.data

import android.app.Application
import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vinyl.launcher.data.secure.FakeKeys
import com.vinyl.launcher.data.secure.PrefsSlots
import com.vinyl.launcher.data.secure.SealedText
import com.vinyl.launcher.data.secure.SerialWorker
import com.vinyl.launcher.data.secure.Vault
import com.vinyl.launcher.data.secure.VaultPurpose
import com.vinyl.launcher.data.secure.drain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Sambungan ke SharedPreferences sungguhan: kunci lama, kunci baru, dan isi yang benar-benar tersimpan di berkas. Memakai
 * Application polos (bukan VinylApp) supaya penyimpanan singleton milik aplikasi tidak ikut membaca berkas yang sama.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class PrefsStorageTest {
    private val context: Context = RuntimeEnvironment.getApplication()
    private val keys = FakeKeys()
    private val vault = Vault(keys)

    @Before
    fun clean() {
        for (name in listOf("retro_diary", "retro_phone", "uji_slots")) context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun prefs(name: String) = context.getSharedPreferences(name, Context.MODE_PRIVATE)

    @Test
    fun prefsSlotsReadWriteAndRemoveManyKeysAtOnce() {
        val slots = PrefsSlots { prefs("uji_slots") }
        assertNull(slots.get("a"))
        slots.change(mapOf("a" to "1", "b" to "2"))
        assertEquals("1", slots.get("a"))
        assertEquals("2", slots.get("b"))
        slots.change(mapOf("a" to null, "c" to "3"))
        assertNull(slots.get("a"))
        assertEquals("2", slots.get("b"))
        assertEquals("3", slots.get("c"))
    }

    @Test
    fun theDiaryFlagsDefaultToOffWithEveryKindOn() {
        val flags = PrefsDiaryFlags { prefs("retro_diary") }.read()
        assertEquals(DiaryFlags(on = false, music = true, film = true, games = true), flags)
    }

    @Test
    fun anOldRecordingSwitchStaysOnAfterTheUpdate() {
        // Versi lama menyimpan satu saklar bernama "recording"; pengguna yang sudah menyalakannya tetap merekam
        prefs("retro_diary").edit().putBoolean("recording", true).commit()
        val flags = PrefsDiaryFlags { prefs("retro_diary") }.read()
        assertTrue(flags.on)
        assertTrue(DiaryKind.entries.all { flags.accepts(it) })
        prefs("retro_diary").edit().putBoolean("recording", false).commit()
        assertFalse(PrefsDiaryFlags { prefs("retro_diary") }.read().on)
    }

    @Test
    fun flagsRoundTripThroughThePreferences() {
        val store = PrefsDiaryFlags { prefs("retro_diary") }
        val mine = DiaryFlags(on = true, music = false, film = true, games = false)
        store.write(mine)
        assertEquals(mine, PrefsDiaryFlags { prefs("retro_diary") }.read())
        assertEquals(true, prefs("retro_diary").getBoolean("recording", false))
        assertEquals(false, prefs("retro_diary").getBoolean("rec_music", true))
        assertEquals(true, prefs("retro_diary").getBoolean("rec_film", false))
        assertEquals(false, prefs("retro_diary").getBoolean("rec_games", true))
    }

    @Test
    fun theDiaryFileOnDiskHoldsOnlyTheSealedValueAndTheSwitches() {
        val worker = SerialWorker()
        val diary = DiaryStore(
            SealedText(PrefsSlots { prefs("retro_diary") }, vault, VaultPurpose.DIARY, "entries_sealed", "entries"),
            PrefsDiaryFlags { prefs("retro_diary") },
            worker,
        )
        diary.setRecording(true)
        diary.record(DiaryKind.MUSIC, "Judul Rahasia", "Artis Rahasia", at = 5_000)
        diary.record(DiaryKind.GAME, "Game Ular", "Skor 41", at = 6_000)
        worker.drain()
        val all = prefs("retro_diary").all
        assertEquals(setOf("entries_sealed", "recording", "rec_music", "rec_film", "rec_games"), all.keys)
        val sealed = all["entries_sealed"] as String
        assertTrue(!sealed.contains("Judul Rahasia") && !sealed.contains("Skor 41") && !sealed.contains("Game Ular"))
        // Proses baru membaca hal yang sama dari berkas
        val worker2 = SerialWorker()
        val again = DiaryStore(
            SealedText(PrefsSlots { prefs("retro_diary") }, vault, VaultPurpose.DIARY, "entries_sealed", "entries"),
            PrefsDiaryFlags { prefs("retro_diary") },
            worker2,
        )
        worker2.drain()
        assertEquals(listOf("Judul Rahasia", "Game Ular"), again.entries.value.map { it.title })
        assertTrue(again.flags.value.on)
    }

    @Test
    fun anOldPlaintextDiaryFileIsSealedAndTheOldKeyIsGone() {
        prefs("retro_diary").edit()
            .putString("entries", """[{"at":1000,"title":"Lagu lama","artist":"Artis lama"}]""")
            .putBoolean("recording", true)
            .commit()
        val worker = SerialWorker()
        val diary = DiaryStore(
            SealedText(PrefsSlots { prefs("retro_diary") }, vault, VaultPurpose.DIARY, "entries_sealed", "entries"),
            PrefsDiaryFlags { prefs("retro_diary") },
            worker,
        )
        worker.drain()
        assertEquals(listOf("Lagu lama"), diary.entries.value.map { it.title })
        assertTrue(diary.flags.value.on)
        val all = prefs("retro_diary").all
        assertFalse("kunci polos lama harus hilang", all.containsKey("entries"))
        assertTrue(all.containsKey("entries_sealed"))
        assertTrue((all["entries_sealed"] as String).let { !it.contains("Lagu lama") })
    }

    @Test
    fun anOldPlaintextPhoneFileIsSealedAndTheOldKeyIsGone() {
        val legacy = PhoneBookRules.encode(listOf(PhoneFavorite(2, "Dewi Lama", "content://com.android.contacts/contacts/lookup/k/2")))
        prefs("retro_phone").edit().putString("favorites", legacy).commit()
        val worker = SerialWorker()
        val book = PhoneBook(SealedText(PrefsSlots { prefs("retro_phone") }, vault, VaultPurpose.PHONE, "favorites_sealed", "favorites"), worker)
        worker.drain()
        assertEquals("Dewi Lama", book.favorites.value[2]?.name)
        val all = prefs("retro_phone").all
        assertEquals(setOf("favorites_sealed"), all.keys)
        assertTrue(!(all["favorites_sealed"] as String).contains("Dewi Lama"))
    }
}
