package com.vinyl.launcher.music

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import java.net.URLEncoder
import kotlin.math.abs

/**
 * Klien murni untuk LRCLIB (lrclib.net) — database lirik terbuka, tanpa API key. Hanya membangun URL dan
 * menafsirkan jawaban; pengiriman permintaan ada di [com.vinyl.launcher.util.Http] dan LyricsRepository.
 *
 * Semua isi jawaban dianggap tidak tepercaya: tipe field diperiksa satu-satu (tidak ada cast yang bisa melempar),
 * jumlah kandidat dan panjang lirik dibatasi.
 */
object Lrclib {
    private const val BASE = "https://lrclib.net/api"

    /** Respons LRCLIB normal < 100 KB; batas 512 KB menahan respons berlebihan. */
    const val MAX_RESPONSE = 512 * 1024
    const val MAX_CANDIDATES = 20
    const val MAX_PLAIN_CHARS = 20_000

    /**
     * Selisih durasi terbesar yang masih dianggap versi lagu yang sama. Lebih jauh dari ini (mis. versi live yang
     * lebih panjang) lirik tersinkronnya pasti melenceng, jadi bagian tersinkron dibuang dan hanya lirik biasa dipakai.
     */
    const val DURATION_TOLERANCE_MS = 8_000L

    private val json = Json { ignoreUnknownKeys = true }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    fun getUrl(title: String, artist: String, album: String, durationMs: Long): String {
        var url = "$BASE/get?track_name=${enc(title)}&artist_name=${enc(artist)}"
        if (album.isNotBlank()) url += "&album_name=${enc(album)}"
        if (durationMs > 0) url += "&duration=${durationMs / 1000}"
        return url
    }

    fun searchUrl(title: String, artist: String): String =
        "$BASE/search?track_name=${enc(title)}&artist_name=${enc(artist)}"

    /**
     * Arti status HTTP selain 200. 404 (dan 400/410) = server menjawab bahwa lirik tidak ada; sisanya
     * (429 dibatasi, 5xx server rusak, dll.) berarti jawabannya belum diketahui, bukan "tidak ada".
     */
    fun outcomeForStatus(code: Int): LyricsOutcome = when (code) {
        400, 404, 410 -> LyricsOutcome.NotFound
        else -> LyricsOutcome.Failed
    }

    /** Jawaban `/get`: satu objek. JSON rusak/bukan objek = [LyricsOutcome.Failed]; objek tanpa lirik = NotFound. */
    fun parseExact(body: String): LyricsOutcome {
        val obj = try { json.parseToJsonElement(body) as? JsonObject } catch (e: IllegalArgumentException) { null }
            ?: return LyricsOutcome.Failed
        return lyricsOf(obj, trackDurationMs = 0)?.let { LyricsOutcome.Found(it) } ?: LyricsOutcome.NotFound
    }

    /** Jawaban `/search`: larik kandidat. Memilih yang bertanda waktu, bukan instrumental, dan durasinya terdekat. */
    fun parseSearch(body: String, trackDurationMs: Long): LyricsOutcome {
        val arr = try { json.parseToJsonElement(body) as? JsonArray } catch (e: IllegalArgumentException) { null }
            ?: return LyricsOutcome.Failed
        val best = arr.asSequence()
            .take(MAX_CANDIDATES)
            .mapNotNull { it as? JsonObject }
            .mapNotNull { o -> lyricsOf(o, trackDurationMs)?.let { it to durationGap(o, trackDurationMs) } }
            .sortedWith(compareBy<Pair<Lyrics, Double>>({ !it.first.hasSynced }, { it.first.instrumental }, { it.second }))
            .firstOrNull()
            ?: return LyricsOutcome.NotFound
        return LyricsOutcome.Found(best.first)
    }

    /**
     * Gabungkan tahap `/get` dan `/search`:
     * - exact ketemu → selesai;
     * - exact GAGAL (jaringan) → berhenti; mencoba tahap kedua hanya melipatgandakan waktu tunggu;
     * - exact "tidak ada" → coba pencarian, dan hasil pencarian yang menentukan.
     */
    inline fun combine(exact: LyricsOutcome, search: () -> LyricsOutcome): LyricsOutcome = when (exact) {
        is LyricsOutcome.Found -> exact
        LyricsOutcome.Failed -> exact
        else -> search()
    }

    // ---------------------------------------------------------------- internal

    /** Selisih durasi (ms) kandidat dengan lagu; 0 bila salah satunya tidak diketahui, supaya tidak menghukum data kosong. */
    private fun durationGap(o: JsonObject, trackDurationMs: Long): Double {
        val seconds = (o["duration"] as? JsonPrimitive)?.doubleOrNull ?: return 0.0
        return if (trackDurationMs > 0) abs(seconds * 1000 - trackDurationMs) else 0.0
    }

    private fun text(o: JsonObject, key: String): String? =
        (o[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    /** Lirik dari satu objek LRCLIB, atau null bila tidak ada isi yang bisa ditampilkan. */
    private fun lyricsOf(o: JsonObject, trackDurationMs: Long): Lyrics? {
        if ((o["instrumental"] as? JsonPrimitive)?.booleanOrNull == true) return Lyrics(emptyList(), null, instrumental = true)
        val plain = text(o, "plainLyrics")?.take(MAX_PLAIN_CHARS)?.takeIf { it.isNotBlank() }
        val sameVersion = trackDurationMs <= 0 || (o["duration"] as? JsonPrimitive)?.doubleOrNull
            ?.let { abs(it * 1000 - trackDurationMs) <= DURATION_TOLERANCE_MS } ?: true
        val synced = if (sameVersion) text(o, "syncedLyrics")?.let { Lrc.parse(it) }.orEmpty() else emptyList()
        if (synced.isEmpty() && plain == null) return null
        return Lyrics(synced, plain)
    }
}
