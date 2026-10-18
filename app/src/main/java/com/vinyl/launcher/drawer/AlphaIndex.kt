package com.vinyl.launcher.drawer

import java.text.Normalizer

/**
 * Indeks abjad untuk app drawer: pengelompokan per huruf dan penentuan posisi lompat.
 *
 * Huruf Latin A–Z (tanpa tanda diakritik: "Ä" → A). Angka, simbol, dan aksara non-Latin dikelompokkan di '#'.
 * Keterbatasan yang disengaja: label berhuruf non-Latin (Arab, Cina, Cyrillic, dll.) tidak punya huruf sendiri.
 */
object AlphaIndex {
    const val OTHER = '#'

    /** Urutan bagian: A–Z lalu '#'. */
    val LETTERS: List<Char> = ('A'..'Z').toList() + OTHER

    data class Section<T>(val letter: Char, val items: List<T>)

    /** Huruf indeks untuk [label]. Karakter awal yang bukan huruf/angka (tanda kurung, spasi) dilewati. */
    fun letterOf(label: String): Char {
        val first = label.firstOrNull { it.isLetterOrDigit() } ?: return OTHER
        val base = Normalizer.normalize(first.toString(), Normalizer.Form.NFD).firstOrNull() ?: return OTHER
        val up = base.uppercaseChar()
        return if (up in 'A'..'Z') up else OTHER
    }

    /**
     * Kelompokkan [items] per huruf. Urutan bagian mengikuti [LETTERS]; urutan item dalam bagian dipertahankan
     * (stabil), jadi masukan yang sudah diurut abjad tetap urut.
     */
    fun <T> group(items: List<T>, label: (T) -> String): List<Section<T>> {
        val buckets = LinkedHashMap<Char, MutableList<T>>()
        for (item in items) buckets.getOrPut(letterOf(label(item))) { mutableListOf() }.add(item)
        return LETTERS.mapNotNull { l -> buckets[l]?.let { Section(l, it) } }
    }

    /**
     * Indeks item pertama (pada daftar datar: tiap bagian = 1 header + jumlah itemnya) untuk melompat ke [letter].
     * Bila huruf tidak punya bagian, melompat ke bagian berikutnya yang ada; bila tidak ada lagi, ke bagian terakhir.
     * Mengembalikan null bila tidak ada bagian sama sekali.
     */
    fun <T> flatIndexOf(sections: List<Section<T>>, letter: Char): Int? {
        if (sections.isEmpty()) return null
        val wanted = LETTERS.indexOf(letter.uppercaseChar()).let { if (it < 0) LETTERS.lastIndex else it }
        var index = 0
        var lastStart = 0
        for (s in sections) {
            lastStart = index
            if (LETTERS.indexOf(s.letter) >= wanted) return index
            index += 1 + s.items.size
        }
        return lastStart
    }

    /** Kebalikannya: huruf bagian yang memuat indeks datar [flatIndex] (untuk menyorot huruf aktif saat menggulir). */
    fun <T> letterAt(sections: List<Section<T>>, flatIndex: Int): Char? {
        var index = 0
        for (s in sections) {
            val end = index + 1 + s.items.size
            if (flatIndex < end) return s.letter
            index = end
        }
        return sections.lastOrNull()?.letter
    }
}
