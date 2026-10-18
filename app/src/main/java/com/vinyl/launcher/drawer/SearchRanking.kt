package com.vinyl.launcher.drawer

import java.text.Normalizer
import java.util.Locale

/** Normalisasi teks untuk pencarian: huruf kecil, tanpa tanda diakritik, spasi dirapikan. */
object SearchText {
    private val COMBINING = Regex("\\p{Mn}+")
    private val SPACES = Regex("\\s+")

    fun normalize(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD).replace(COMBINING, "").lowercase(Locale.ROOT).trim().replace(SPACES, " ")
}

/**
 * Pemeringkat pencarian sederhana yang dapat diprediksi (bukan fuzzy-search penuh):
 * persis > awalan > awalan kata > inisial > mengandung > urutan huruf (hanya untuk kueri ≥ 3 huruf).
 * Kueri berkata banyak: setiap kata harus cocok; skor = skor kata terburuk.
 */
object SearchRanking {
    const val EXACT = 100
    const val PREFIX = 90
    const val WORD_PREFIX = 75
    const val INITIALS = 65
    const val CONTAINS = 50
    const val SUBSEQUENCE = 20

    /** Skor 0..100; 0 = tidak cocok. */
    fun score(query: String, text: String): Int {
        val q = SearchText.normalize(query)
        if (q.isEmpty()) return 0
        return scoreNormalized(q, q.split(' '), SearchText.normalize(text))
    }

    /** Sama dengan [score] tetapi masukannya sudah dinormalisasi ([tokens] = [q] dipecah per spasi), agar tidak diulang per item. */
    internal fun scoreNormalized(q: String, tokens: List<String>, t: String): Int {
        if (q.isEmpty() || t.isEmpty()) return 0
        if (t == q) return EXACT
        if (t.startsWith(q)) return PREFIX
        var worst = Int.MAX_VALUE
        for (tok in tokens) {
            val s = tokenScore(tok, t)
            if (s == 0) return 0
            worst = minOf(worst, s)
        }
        return worst
    }

    private fun tokenScore(tok: String, t: String): Int {
        if (t == tok) return EXACT
        if (t.startsWith(tok)) return PREFIX
        val words = t.split(' ', '-', '.', '_', '&')
        if (words.any { it.startsWith(tok) }) return WORD_PREFIX
        if (tok.length >= 2 && words.filter { it.isNotEmpty() }.map { it[0] }.joinToString("").startsWith(tok)) return INITIALS
        if (t.contains(tok)) return CONTAINS
        if (tok.length >= 3 && t.length <= 40 && isSubsequence(tok, t)) return SUBSEQUENCE
        return 0
    }

    private fun isSubsequence(needle: String, hay: String): Boolean {
        var i = 0
        for (c in hay) if (i < needle.length && c == needle[i]) i++
        return i == needle.length
    }

    /**
     * Urutkan [items] yang cocok dengan [query], skor tertinggi dulu; seri mempertahankan urutan masukan
     * (stabil). Yang tidak cocok dibuang. Teks kosong menghasilkan daftar kosong.
     * Untuk daftar yang dicari berulang kali (tiap ketikan), buat [SearchIndex] sekali saja.
     */
    fun <T> rank(query: String, items: List<T>, limit: Int = Int.MAX_VALUE, text: (T) -> String): List<T> =
        SearchIndex(items, text).rank(query, limit)
}

/** Daftar yang teksnya dinormalisasi sekali di muka sehingga setiap ketikan hanya membandingkan string. */
class SearchIndex<T>(private val items: List<T>, text: (T) -> String) {
    private val normalized: List<String> = items.map { SearchText.normalize(text(it)) }

    fun rank(query: String, limit: Int = Int.MAX_VALUE): List<T> {
        val q = SearchText.normalize(query)
        if (q.isEmpty()) return emptyList()
        val tokens = q.split(' ')
        return items.indices.asSequence()
            .map { it to SearchRanking.scoreNormalized(q, tokens, normalized[it]) }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }      // sortedBy pada Sequence stabil: seri tetap urut masukan
            .take(limit.coerceAtLeast(0))
            .map { items[it.first] }
            .toList()
    }
}

/**
 * Pintasan pengaturan sistem yang bisa dicari. Intent sebenarnya (Settings.ACTION_*) dipetakan di sisi Android dengan
 * `when` eksplisit sehingga kompilator memeriksa kelengkapannya.
 */
enum class SystemSetting(val title: String, val keywords: List<String>) {
    WIFI("Wi-Fi", listOf("wifi", "internet", "jaringan", "wireless")),
    BLUETOOTH("Bluetooth", listOf("bt", "perangkat", "headset")),
    DISPLAY("Layar", listOf("tampilan", "kecerahan", "brightness", "display", "mode gelap", "dark mode")),
    SOUND("Suara", listOf("volume", "getar", "dering", "sound", "nada")),
    BATTERY("Hemat baterai", listOf("battery", "baterai", "daya", "battery saver")),
    APPS("Aplikasi", listOf("apps", "kelola aplikasi", "izin aplikasi")),
    DEFAULT_APPS("Aplikasi default", listOf("default apps", "peluncur", "launcher", "browser default")),
    LOCATION("Lokasi", listOf("gps", "location")),
    SECURITY("Keamanan", listOf("kunci layar", "sidik jari", "security", "pin")),
    PRIVACY("Privasi", listOf("privacy", "izin")),
    LANGUAGE("Bahasa", listOf("locale", "language", "wilayah")),
    DATE_TIME("Tanggal & waktu", listOf("jam", "zona waktu", "date", "time")),
    ACCESSIBILITY("Aksesibilitas", listOf("accessibility", "talkback", "ukuran font", "teks besar")),
    STORAGE("Penyimpanan", listOf("storage", "memori", "ruang kosong")),
    AIRPLANE("Mode pesawat", listOf("airplane", "flight", "penerbangan")),
    NFC("NFC", listOf("nfc", "bayar")),
    CAST("Cast layar", listOf("cast", "cermin", "mirror", "miracast")),
    NOTIFICATION_ACCESS("Akses notifikasi", listOf("notification access", "notifikasi")),
    DEVELOPER("Opsi pengembang", listOf("developer", "debugging", "usb debugging", "pengembang")),
    ALL_SETTINGS("Pengaturan", listOf("settings", "setelan")),
}

/** Pencarian pengaturan sistem: judul diutamakan, lalu kata kunci. */
fun searchSystemSettings(query: String, limit: Int = 4): List<SystemSetting> =
    SystemSetting.entries
        .map { s -> s to maxOf(SearchRanking.score(query, s.title), s.keywords.maxOfOrNull { SearchRanking.score(query, it) - 5 } ?: 0) }
        .filter { it.second > 0 }
        .sortedByDescending { it.second }
        .take(limit)
        .map { it.first }
