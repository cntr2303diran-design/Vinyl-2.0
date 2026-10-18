package com.vinyl.launcher.music

/** Satu baris lirik yang mulai dinyanyikan pada [timeMs]. Teks kosong = jeda instrumental. */
data class LyricLine(val timeMs: Long, val text: String)

/**
 * Lirik sebuah lagu: [synced] berisi baris bertanda waktu (kosong jika hanya ada lirik biasa).
 * [instrumental] = lagu tanpa vokal (kedua teks kosong); ditampilkan sebagai "Instrumental", bukan "tidak ditemukan".
 */
data class Lyrics(val synced: List<LyricLine>, val plain: String?, val instrumental: Boolean = false) {
    val hasSynced get() = synced.isNotEmpty()

    /** Lirik biasa per baris, tanpa baris kosong di awal/akhir. Kosong bila tidak ada. */
    val plainLines: List<String>
        get() = plain.orEmpty().lines().map { it.trimEnd() }.dropWhile { it.isBlank() }.dropLastWhile { it.isBlank() }

    /** Indeks baris yang sedang dinyanyikan pada posisi [ms], atau -1 sebelum baris pertama. */
    fun indexAt(ms: Long): Int {
        if (synced.isEmpty() || ms < synced.first().timeMs) return -1
        var lo = 0
        var hi = synced.lastIndex
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (synced[mid].timeMs <= ms) lo = mid else hi = mid - 1
        }
        return lo
    }
}

/**
 * Parser format LRC. Sengaja ketat dan terbatas karena masukannya berasal dari internet:
 * - tanda waktu `[mm:ss.xx]` (menit 1-3 digit, detik 00-59, pecahan 1-3 digit; pemisah pecahan `.` atau `:`),
 *   satu baris boleh punya beberapa tanda waktu;
 * - tag metadata (`[ar:..]`, `[ti:..]`, `[offset:..]`, dll.) tidak menjadi baris lirik. `offset` TIDAK diterapkan:
 *   artinya (maju/mundur) berbeda antar pemutar dan LRCLIB tidak memakainya;
 * - tanda waktu per kata ala "enhanced LRC" (`<00:12.34>`) dibuang dari teks;
 * - jumlah baris dibatasi [MAX_LINES] dan panjang teks per baris [MAX_LINE_CHARS].
 */
object Lrc {
    const val MAX_LINES = 5000
    const val MAX_LINE_CHARS = 300

    private val TIME_TAG = Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?]""")
    private val WORD_TAG = Regex("""<\d{1,3}:\d{2}(?:[.:]\d{1,3})?>""")

    fun parse(lrc: String): List<LyricLine> {
        val out = ArrayList<LyricLine>()
        for (line in lrc.lineSequence()) {
            if (out.size >= MAX_LINES) break
            val tags = TIME_TAG.findAll(line).toList()
            if (tags.isEmpty()) continue
            val text = line.substring(tags.last().range.last + 1)
                .replace(WORD_TAG, "")
                .filter { !it.isISOControl() }
                .trim()
                .take(MAX_LINE_CHARS)
            for (m in tags) {
                val time = timeOf(m) ?: continue
                out += LyricLine(time, text)
                if (out.size >= MAX_LINES) break
            }
        }
        return out.sortedBy { it.timeMs }   // sortedBy stabil: baris dengan waktu sama tetap berurutan
    }

    private fun timeOf(m: MatchResult): Long? {
        val (mm, ss, frac) = m.destructured
        val seconds = ss.toInt()
        if (seconds > 59) return null        // "[00:99.00]" bukan waktu yang sah
        val fracMs = when (frac.length) {
            0 -> 0
            1 -> frac.toInt() * 100
            2 -> frac.toInt() * 10
            else -> frac.toInt()
        }
        return mm.toLong() * 60_000L + seconds * 1000L + fracMs
    }
}

/** Pembersih judul/artis sebelum dicari ke LRCLIB: buang "(feat. …)", "- Remastered 2011", dll. yang membuat pencarian gagal. */
object TitleCleaner {
    private const val MAX_INPUT = 200
    private val BRACKETED = Regex(
        """\s*[(\[](feat|ft|with|remaster|live|official|lyric|audio|video)[^)\]]*[)\]]""",
        RegexOption.IGNORE_CASE,
    )
    private val DASH_SUFFIX = Regex("""\s+-\s+[^-]*(remaster|version|edit|mix|live)[^-]*$""", RegexOption.IGNORE_CASE)

    /** Hasil tidak pernah kosong bila masukan tidak kosong: bila pembersihan menghabiskan teks, masukan dikembalikan apa adanya. */
    fun clean(s: String): String {
        val input = s.take(MAX_INPUT).trim()
        val cleaned = input.replace(BRACKETED, "").replace(DASH_SUFFIX, "").trim()
        return cleaned.ifBlank { input }
    }
}
