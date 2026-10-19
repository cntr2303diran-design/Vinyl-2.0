package com.vinyl.launcher.player

import kotlinx.serialization.Serializable
import java.text.Normalizer
import java.util.Locale

/*
 * Model pustaka musik lokal (berkas di folder yang dipilih pengguna) untuk pemutar bawaan. Murni (tanpa Android) agar
 * pengurutan, pengelompokan, dan label kualitas diuji.
 */

/** Satu berkas audio di indeks. [id] = ID dokumen SAF (stabil selama berkas tidak dipindah). */
@Serializable
data class LocalTrack(
    val id: String,
    val title: String,
    val artist: String = "",
    val album: String = "",
    val albumArtist: String = "",
    val trackNo: Int = 0,
    val discNo: Int = 0,
    val year: Int = 0,
    val durationMs: Long = 0,
    val mime: String = "",
    val fileName: String = "",
    /** Hz; 0 = tidak diketahui (dibaca dari berkas di Android 12+). */
    val sampleRate: Int = 0,
    /** Bit per sampel (16/24/32); 0 = tidak diketahui atau format lossy. */
    val bitsPerSample: Int = 0,
    /** Bit per detik rata-rata; 0 = tidak diketahui. */
    val bitrate: Int = 0,
    val size: Long = 0,
    /** Waktu ubah berkas (ms) untuk mendeteksi perubahan saat memindai ulang. */
    val modified: Long = 0,
)

@Serializable
enum class LocalSort { ALBUM, ARTIST, TITLE, RECENT }

object LocalFormat {
    /** Nama format pendek dari jenis MIME / ekstensi: FLAC, ALAC, WAV, AIFF, DSD, MP3, AAC, OGG, OPUS, … */
    fun codec(mime: String, fileName: String): String {
        val ext = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        val m = mime.lowercase(Locale.ROOT)
        return when {
            m.contains("flac") || ext == "flac" -> "FLAC"
            m.contains("alac") || ext == "alac" -> "ALAC"
            m.contains("wav") || ext == "wav" -> "WAV"
            m.contains("aiff") || ext == "aif" || ext == "aiff" -> "AIFF"
            ext == "dsf" || ext == "dff" || m.contains("dsd") -> "DSD"
            m.contains("mpeg") && (ext == "mp3" || m.contains("mp3")) || ext == "mp3" -> "MP3"
            m.contains("opus") || ext == "opus" -> "OPUS"
            m.contains("ogg") || m.contains("vorbis") || ext == "ogg" || ext == "oga" -> "OGG"
            m.contains("mp4") || m.contains("aac") || m.contains("m4a") || ext == "m4a" || ext == "aac" || ext == "mp4" -> "AAC"
            m.contains("ape") || ext == "ape" -> "APE"
            m.contains("wma") || ext == "wma" -> "WMA"
            else -> ext.uppercase(Locale.ROOT).ifBlank { "AUDIO" }
        }
    }

    /** Format tanpa kompresi data hilang. M4A bisa AAC atau ALAC: [bitsPerSample] > 0 berarti ALAC (lossless). */
    fun lossless(codec: String, bitsPerSample: Int = 0): Boolean =
        codec in setOf("FLAC", "ALAC", "WAV", "AIFF", "APE", "DSD") || (codec == "AAC" && bitsPerSample > 0)

    /** "FLAC 24/96", "WAV 16/44,1", "MP3 320k", "AAC" — label kecil di daftar & VFD. */
    fun quality(t: LocalTrack): String {
        var codec = codec(t.mime, t.fileName)
        if (codec == "AAC" && t.bitsPerSample > 0) codec = "ALAC"
        return if (lossless(codec, t.bitsPerSample)) {
            val bits = if (t.bitsPerSample > 0) "${t.bitsPerSample}" else null
            val rate = if (t.sampleRate > 0) khz(t.sampleRate) else null
            listOfNotNull(codec, if (bits != null && rate != null) "$bits/$rate" else rate?.let { "$it kHz" }).joinToString(" ")
        } else {
            val kbps = if (t.bitrate > 0) "${(t.bitrate + 500) / 1000}k" else null
            listOfNotNull(codec, kbps).joinToString(" ")
        }
    }

    /**
     * Format yang wadahnya tidak bisa dibaca pemutar bawaan (Media3 tanpa ekstensi FFmpeg tidak punya pengurai DSF/DFF,
     * APE, dan WMA; AIFF dibaca oleh [AiffExtractor] buatan sendiri). Tetap tampil di daftar dengan tanda "belum didukung" dan dilewati saat membuat antrean. Format lain
     * dicoba; bila dekoder perangkat menolaknya (mis. ALAC di sebagian perangkat), pemutar melompat ke lagu berikutnya.
     */
    val UNSUPPORTED = setOf("DSD", "APE", "WMA")

    fun playable(t: LocalTrack): Boolean = codec(t.mime, t.fileName) !in UNSUPPORTED

    /** Resolusi tinggi: lebih dari 16-bit atau lebih dari 48 kHz. */
    fun hiRes(t: LocalTrack): Boolean = lossless(codec(t.mime, t.fileName), t.bitsPerSample) && (t.bitsPerSample > 16 || t.sampleRate > 48_000)

    /** 44100 → "44,1"; 96000 → "96" (gaya Indonesia, koma desimal). */
    fun khz(hz: Int): String {
        val k = hz / 1000.0
        return if (hz % 1000 == 0) "${hz / 1000}" else "%.1f".format(Locale.ROOT, k).replace('.', ',')
    }

    /** Ekstensi berkas audio yang dipindai (juga dipakai bila penyedia dokumen tidak memberi MIME). */
    val AUDIO_EXTENSIONS = setOf("flac", "wav", "aif", "aiff", "mp3", "m4a", "aac", "mp4", "ogg", "oga", "opus", "alac", "wma", "ape", "dsf", "dff")

    fun isAudio(mime: String?, fileName: String): Boolean {
        val ext = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        if (mime != null && mime.startsWith("audio/")) return true
        // Beberapa penyedia memberi application/octet-stream untuk FLAC/Opus
        return ext in AUDIO_EXTENSIONS && (mime == null || mime == "application/octet-stream" || mime == "application/ogg")
    }
}

object LocalLibraryModel {
    /** Kunci pengurutan & pencarian: tanpa aksen, huruf kecil, "The " di depan diabaikan. */
    fun sortKey(s: String): String {
        val t = s.trim()
        // Jalur cepat untuk teks ASCII (sebagian besar tag): normalisasi Unicode hanya bila perlu
        val base = if (t.all { it.code < 0x80 }) t else {
            val n = Normalizer.normalize(t, Normalizer.Form.NFD)
            buildString(n.length) {
                for (c in n) {
                    when (Character.getType(c).toByte()) {
                        Character.NON_SPACING_MARK, Character.ENCLOSING_MARK, Character.COMBINING_SPACING_MARK -> Unit
                        else -> append(c)
                    }
                }
            }
        }
        return base.lowercase(Locale.ROOT).removePrefix("the ")
    }

    fun displayArtist(t: LocalTrack): String = t.albumArtist.ifBlank { t.artist }.ifBlank { "Artis tak dikenal" }

    fun displayAlbum(t: LocalTrack): String = t.album.ifBlank { "Album tak dikenal" }

    /** Lagu dengan kunci urut yang dihitung SEKALI (bukan di setiap perbandingan: ribuan lagu × log n perbandingan). */
    private class Keyed(val t: LocalTrack) {
        val album = sortKey(displayAlbum(t))
        val artist = sortKey(displayArtist(t))
        val title = sortKey(t.title)
        val trackArtist = sortKey(t.artist)
    }

    fun sorted(list: List<LocalTrack>, sort: LocalSort): List<LocalTrack> {
        val cmp: Comparator<Keyed> = when (sort) {
            LocalSort.ALBUM -> compareBy({ it.album }, { it.artist }, { it.t.discNo }, { it.t.trackNo }, { it.title })
            LocalSort.ARTIST -> compareBy({ it.artist }, { it.t.year }, { it.album }, { it.t.discNo }, { it.t.trackNo })
            LocalSort.TITLE -> compareBy({ it.title }, { it.trackArtist })
            LocalSort.RECENT -> compareByDescending<Keyed> { it.t.modified }.thenBy { it.title }
        }
        return list.map(::Keyed).sortedWith(cmp).map { it.t }
    }

    /**
     * Daftar terurut beserta teks cari ternormalisasi per lagu (sejajar dengan [tracks]); dibuat di thread latar.
     * [source] & [sort] = masukannya, agar layar tahu hasil ini sudah sesuai daftar terbaru atau belum.
     */
    class Prepared(val source: List<LocalTrack>, val sort: LocalSort, val tracks: List<LocalTrack>, val hay: List<String>)

    fun prepare(list: List<LocalTrack>, sort: LocalSort): Prepared {
        val s = sorted(list, sort)
        return Prepared(list, sort, s, s.map(::haystack))
    }

    private fun haystack(t: LocalTrack): String = sortKey(listOf(t.title, t.artist, t.albumArtist, t.album, t.fileName).joinToString(" "))

    /** Cari di [p] memakai teks yang sudah dinormalisasi: tiap ketikan hanya membandingkan string. */
    fun search(p: Prepared, query: String): List<LocalTrack> {
        val words = queryWords(query)
        if (words.isEmpty()) return p.tracks
        return p.tracks.filterIndexed { i, _ -> words.all { it in p.hay[i] } }
    }

    private fun queryWords(query: String): List<String> = sortKey(query).split(' ', '\t', '\n').filter { it.isNotEmpty() }

    /**
     * Antrean "putar dari sini": id lagu yang bisa diputar dari daftar yang tampil, dan posisi lagu yang diketuk di
     * antrean itu. null = lagu itu tidak bisa diputar pemutar bawaan.
     */
    fun queue(shown: List<LocalTrack>, tapped: LocalTrack): Pair<List<String>, Int>? {
        if (!LocalFormat.playable(tapped)) return null
        val ids = shown.filter(LocalFormat::playable).map { it.id }
        val start = ids.indexOf(tapped.id)
        return if (start < 0) null else ids to start
    }

    /** Judul bagian untuk lagu pada urutan [sort] (lagu berurutan dengan judul yang sama digabung di bawahnya). */
    fun section(t: LocalTrack, sort: LocalSort): String = when (sort) {
        LocalSort.ALBUM -> displayAlbum(t) + " — " + displayArtist(t)
        LocalSort.ARTIST -> displayArtist(t)
        LocalSort.TITLE -> sortKey(t.title).firstOrNull()?.takeIf { it.isLetter() }?.uppercase() ?: "#"
        LocalSort.RECENT -> ""
    }

    /** Cari di judul, artis, album, dan nama berkas; semua kata harus ada (urutan bebas). */
    fun search(list: List<LocalTrack>, query: String): List<LocalTrack> {
        val words = queryWords(query)
        if (words.isEmpty()) return list
        return list.filter { t -> haystack(t).let { hay -> words.all { it in hay } } }
    }

    /** Judul dari nama berkas bila tag kosong: "01 - Lagu Saya.flac" → "Lagu Saya". */
    fun titleFromFileName(name: String): String {
        val base = name.substringBeforeLast('.')
        return base.replace(Regex("^\\s*\\d{1,3}\\s*[-._)]\\s*"), "").trim().ifBlank { base }
    }

    /** Nomor lagu dari tag "3/12" atau "03". */
    fun parseTrackNo(raw: String?): Int = raw?.substringBefore('/')?.trim()?.toIntOrNull()?.coerceIn(0, 9999) ?: 0
}

/**
 * Alasan galat pemutaran yang bisa dimengerti pengguna, dari kode galat Media3 (PlaybackException: 2xxx berkas/I-O,
 * 3xxx wadah berkas, 4xxx dekoder, 5xxx keluaran audio).
 */
object PlaybackErrors {
    private const val IO_FILE_NOT_FOUND = 2005
    private const val IO_NO_PERMISSION = 2006

    fun reason(code: Int): String = when {
        code == IO_FILE_NOT_FOUND -> "berkas tidak ditemukan (dipindah atau dihapus)"
        code == IO_NO_PERMISSION -> "izin folder sudah tidak berlaku"
        code in 2000..2999 -> "berkas tidak bisa dibaca"
        code in 3000..3999 -> "format berkas tidak dikenali atau rusak"
        code in 4000..4999 -> "dekoder perangkat ini tidak mendukung formatnya"
        code in 5000..5999 -> "keluaran audio menolak formatnya"
        else -> "galat pemutaran (kode $code)"
    }

    /** @param skipped pemutar melompat ke lagu berikutnya (false = antrean berhenti di lagu ini) */
    fun message(title: String, code: Int, skipped: Boolean): String =
        (if (title.isBlank()) "Satu lagu" else "\"$title\"") + (if (skipped) " dilewati: " else " tidak bisa diputar: ") + reason(code)
}
