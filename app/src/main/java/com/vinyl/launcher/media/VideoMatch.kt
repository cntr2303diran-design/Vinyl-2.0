package com.vinyl.launcher.media

import java.text.Normalizer
import java.util.Locale

/** Satu berkas video di folder pilihan pengguna. [documentId] = id dokumen SAF (stabil selama berkas tidak dipindah). */
data class VideoFile(val documentId: String, val name: String)

/**
 * Mencocokkan lagu yang sedang diputar dengan video milik pengguna. Murni agar aturannya diuji. Urutan:
 *  1. tautan manual pengguna untuk album (atau judul, bila album kosong);
 *  2. nama berkas "Artis - Album";
 *  3. nama berkas "Artis - Judul";
 *  4. nama berkas yang sama persis dengan nama album (minimal 3 huruf, agar "1" atau "Hits" tidak cocok ke mana-mana).
 * Perbandingan mengabaikan huruf besar/kecil, aksen, tanda baca, dan ekstensi.
 */
object VideoMatch {
    enum class Reason { LINKED, ARTIST_ALBUM, ARTIST_TITLE, ALBUM }

    data class Match(val file: VideoFile, val reason: Reason)

    private val MARKS = Regex("\\p{Mn}+")
    private val NON_WORD = Regex("[^\\p{L}\\p{N}]+")
    private val SEPARATOR = Regex("\\s+[-–—]\\s+")

    fun normalize(s: String): String =
        Normalizer.normalize(s.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(MARKS, "")
            .replace(NON_WORD, " ")
            .trim()

    fun stripExtension(name: String): String {
        val dot = name.lastIndexOf('.')
        return if (dot > 0 && name.length - dot <= 6) name.substring(0, dot) else name
    }

    /** "Artis - Album.mp4" → ("artis", "album"); tanpa pemisah → null. */
    fun parse(name: String): Pair<String, String>? {
        val base = stripExtension(name)
        val parts = base.split(SEPARATOR, limit = 2)
        if (parts.size < 2) return null
        val artist = normalize(parts[0])
        val rest = normalize(parts[1])
        return if (artist.isEmpty() || rest.isEmpty()) null else artist to rest
    }

    /** Kunci tautan manual: per album, atau per judul bila lagu tidak punya album. */
    fun linkKey(artist: String, album: String, title: String): String? {
        val a = normalize(artist)
        val al = normalize(album)
        val t = normalize(title)
        return when {
            al.isNotEmpty() -> "a:$a|$al"
            t.isNotEmpty() -> "t:$a|$t"
            else -> null
        }
    }

    fun find(files: List<VideoFile>, links: Map<String, String>, artist: String, album: String, title: String): Match? {
        linkKey(artist, album, title)?.let { key ->
            links[key]?.let { id -> files.firstOrNull { it.documentId == id }?.let { return Match(it, Reason.LINKED) } }
        }
        val a = normalize(artist)
        val al = normalize(album)
        val t = normalize(title)
        val parsed = files.map { it to parse(it.name) }
        if (a.isNotEmpty() && al.isNotEmpty()) {
            parsed.firstOrNull { (_, p) -> p != null && p.first == a && p.second == al }?.let { return Match(it.first, Reason.ARTIST_ALBUM) }
        }
        if (a.isNotEmpty() && t.isNotEmpty()) {
            parsed.firstOrNull { (_, p) -> p != null && p.first == a && p.second == t }?.let { return Match(it.first, Reason.ARTIST_TITLE) }
        }
        if (al.length >= 3) {
            files.firstOrNull { normalize(stripExtension(it.name)) == al }?.let { return Match(it, Reason.ALBUM) }
        }
        return null
    }

    fun reasonLabel(r: Reason): String = when (r) {
        Reason.LINKED -> "ditautkan manual"
        Reason.ARTIST_ALBUM -> "nama berkas Artis - Album"
        Reason.ARTIST_TITLE -> "nama berkas Artis - Judul"
        Reason.ALBUM -> "nama berkas sama dengan album"
    }
}
