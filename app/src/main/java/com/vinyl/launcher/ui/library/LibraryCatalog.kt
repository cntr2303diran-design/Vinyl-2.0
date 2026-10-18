package com.vinyl.launcher.ui.library

import com.vinyl.launcher.data.PlayRecord
import java.text.Normalizer
import java.util.Locale

/*
 * Katalog rak kaset, murni (tanpa Compose) agar urutan, pencarian, dan pembagian kompartemen bisa diuji.
 *
 * Sumber jujur: koleksi = lagu yang benar-benar didengarkan (riwayat, aturan >= 30 detik). Launcher tidak punya akses
 * ke perpustakaan aplikasi streaming, jadi rak tidak berpura-pura berisi album yang belum pernah diputar. "Penanda
 * playlist" diwujudkan sebagai penanda DECK: lagu yang juga ada di antrean aplikasi musik saat ini.
 */

/** Cara rak disusun / disaring. */
enum class RackFilter { ALL, ARTIST, ALBUM, RECENT, FAVORITE }

/** Satu kaset di rak. */
data class RackEntry(
    val record: PlayRecord,
    /** Nomor katalog tetap (urutan pertama kali didengar), mis. 7 → "No. 007". */
    val number: Int,
    val favorite: Boolean,
    /** Lagu yang sedang diputar. */
    val active: Boolean,
    /** Lagu ini juga ada di antrean aplikasi musik (penanda "DECK"). */
    val inDeck: Boolean,
) {
    val key: String get() = record.key
}

/** Satu kompartemen kayu berisi beberapa kaset; [section] judul bagian rak (huruf, artis, atau album). */
data class Compartment(val section: String, val entries: List<RackEntry>)

object LibraryCatalog {
    /** Kaset per kompartemen. */
    const val PER_COMPARTMENT = 4
    const val MAX_FAVORITES = 1000

    /** Kunci lagu yang sama dengan HistoryRepository: "judul|artis" huruf kecil tanpa spasi tepi. */
    fun songKey(title: String, artist: String): String = "${title.trim().lowercase()}|${artist.trim().lowercase()}"

    /** Teks pembanding: huruf kecil, tanpa diakritik ("Beyoncé" cocok dengan "beyonce"). */
    fun fold(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT).trim()

    fun entries(
        history: List<PlayRecord>,
        favorites: Set<String>,
        activeKey: String?,
        deckKeys: Set<String>,
    ): List<RackEntry> {
        // Nomor katalog dari urutan pertama didengar: stabil walau urutan tampilan berubah
        val numbers = history.sortedWith(compareBy({ it.firstPlayed }, { it.key })).mapIndexed { i, r -> r.key to i + 1 }.toMap()
        return history.map { r ->
            RackEntry(r, numbers.getValue(r.key), r.key in favorites, r.key == activeKey, r.key in deckKeys)
        }
    }

    /** Saring dengan [query] (judul/artis/album) lalu susun sesuai [filter]. */
    fun arrange(entries: List<RackEntry>, filter: RackFilter, query: String): List<RackEntry> {
        val q = fold(query)
        val matched = if (q.isEmpty()) entries else entries.filter { e ->
            val r = e.record
            fold(r.title).contains(q) || fold(r.artist).contains(q) || fold(r.album).contains(q)
        }
        val alpha = compareBy<RackEntry>({ fold(it.record.artist) }, { fold(it.record.title) }, { it.key })
        return when (filter) {
            RackFilter.ALL -> matched.sortedWith(compareBy<RackEntry>({ sectionLetter(it.record.artist) }).then(alpha))
            RackFilter.ARTIST -> matched.sortedWith(alpha)
            RackFilter.ALBUM -> matched.sortedWith(compareBy<RackEntry>({ fold(it.record.album).ifEmpty { "￿" } }).then(alpha))
            RackFilter.RECENT -> matched.sortedWith(compareByDescending<RackEntry> { it.record.lastPlayed }.thenBy { it.key })
            RackFilter.FAVORITE -> matched.filter { it.favorite }.sortedWith(alpha)
        }
    }

    /**
     * Judul bagian rak untuk [e] sesuai [filter]. Rak dinding "Semua" sengaja TIDAK dibagi per huruf: kompartemen
     * diisi penuh berurutan A–Z (rak terlihat penuh) dan tiap kompartemen diberi pelat huruf ([plate]).
     */
    fun section(e: RackEntry, filter: RackFilter, now: Long): String = when (filter) {
        RackFilter.ALL -> ""
        RackFilter.FAVORITE -> sectionLetter(e.record.artist)
        RackFilter.ARTIST -> e.record.artist.trim().ifEmpty { "Tanpa artis" }
        RackFilter.ALBUM -> e.record.album.trim().ifEmpty { "Tanpa album" }
        RackFilter.RECENT -> recency(e.record.lastPlayed, now)
    }

    /** Huruf rak: A–Z, "#" untuk angka/simbol. */
    fun sectionLetter(artist: String): String {
        val c = fold(artist).firstOrNull() ?: return "#"
        return if (c in 'a'..'z') c.uppercaseChar().toString() else "#"
    }

    fun recency(at: Long, now: Long): String {
        val day = 86_400_000L
        val age = now - at
        return when {
            age < day -> "Hari ini"
            age < 7 * day -> "Minggu ini"
            age < 30 * day -> "Bulan ini"
            else -> "Lebih lama"
        }
    }

    /**
     * Bagi kaset yang sudah tersusun menjadi kompartemen: kompartemen tidak pernah mencampur dua bagian rak, dan
     * berisi paling banyak [PER_COMPARTMENT] kaset.
     */
    fun compartments(arranged: List<RackEntry>, filter: RackFilter, now: Long): List<Compartment> {
        val out = mutableListOf<Compartment>()
        var current: String? = null
        var bucket = mutableListOf<RackEntry>()
        fun flush() { if (bucket.isNotEmpty()) out += Compartment(current!!, bucket); bucket = mutableListOf() }
        for (e in arranged) {
            val s = section(e, filter, now)
            if (s != current || bucket.size == PER_COMPARTMENT) { flush(); current = s }
            bucket += e
        }
        flush()
        return out
    }

    /** Pelat huruf kompartemen, mis. "A" atau "A–D", dari artis kaset pertama dan terakhir. */
    fun plate(c: Compartment): String {
        val a = sectionLetter(c.entries.first().record.artist)
        val b = sectionLetter(c.entries.last().record.artist)
        return if (a == b) a else "$a–$b"
    }

    /** Lagu lain dari album yang sama di koleksi (pengganti "daftar lagu album": launcher tidak bisa membaca isi album). */
    fun sameAlbum(entries: List<RackEntry>, of: RackEntry): List<RackEntry> {
        val album = fold(of.record.album)
        if (album.isEmpty()) return emptyList()
        val artist = fold(of.record.artist)
        return entries.filter { it.key != of.key && fold(it.record.album) == album && fold(it.record.artist) == artist }
            .sortedBy { fold(it.record.title) }
    }

    /** Favorit setelah [key] di-tandai/dibuang; ukuran dibatasi. */
    fun toggleFavorite(favorites: Set<String>, key: String): Set<String> =
        if (key in favorites) favorites - key else (favorites + key).let { if (it.size > MAX_FAVORITES) it.drop(it.size - MAX_FAVORITES).toSet() else it }

    fun catalogNumber(n: Int): String = "No. %03d".format(n)
}
