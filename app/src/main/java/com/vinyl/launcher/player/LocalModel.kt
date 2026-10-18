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
        val n = Normalizer.normalize(s.trim(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT)
        return n.removePrefix("the ")
    }

    fun displayArtist(t: LocalTrack): String = t.albumArtist.ifBlank { t.artist }.ifBlank { "Artis tak dikenal" }

    fun displayAlbum(t: LocalTrack): String = t.album.ifBlank { "Album tak dikenal" }

    fun sorted(list: List<LocalTrack>, sort: LocalSort): List<LocalTrack> = when (sort) {
        LocalSort.ALBUM -> list.sortedWith(
            compareBy<LocalTrack>({ sortKey(displayAlbum(it)) }, { sortKey(displayArtist(it)) }, { it.discNo }, { it.trackNo }, { sortKey(it.title) }),
        )
        LocalSort.ARTIST -> list.sortedWith(
            compareBy<LocalTrack>({ sortKey(displayArtist(it)) }, { it.year }, { sortKey(displayAlbum(it)) }, { it.discNo }, { it.trackNo }),
        )
        LocalSort.TITLE -> list.sortedWith(compareBy<LocalTrack>({ sortKey(it.title) }, { sortKey(it.artist) }))
        LocalSort.RECENT -> list.sortedWith(compareByDescending<LocalTrack> { it.modified }.thenBy { sortKey(it.title) })
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
        val words = sortKey(query).split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return list
        return list.filter { t ->
            val hay = sortKey(listOf(t.title, t.artist, t.albumArtist, t.album, t.fileName).joinToString(" "))
            words.all { it in hay }
        }
    }

    /** Judul dari nama berkas bila tag kosong: "01 - Lagu Saya.flac" → "Lagu Saya". */
    fun titleFromFileName(name: String): String {
        val base = name.substringBeforeLast('.')
        return base.replace(Regex("^\\s*\\d{1,3}\\s*[-._)]\\s*"), "").trim().ifBlank { base }
    }

    /** Nomor lagu dari tag "3/12" atau "03". */
    fun parseTrackNo(raw: String?): Int = raw?.substringBefore('/')?.trim()?.toIntOrNull()?.coerceIn(0, 9999) ?: 0
}
