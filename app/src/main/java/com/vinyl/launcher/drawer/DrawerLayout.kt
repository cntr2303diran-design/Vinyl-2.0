package com.vinyl.launcher.drawer

/** Satu baris pada daftar app drawer. [id] unik dan stabil (dipakai sebagai key LazyGrid; aplikasi yang sama bisa muncul dua kali). */
sealed class DrawerRow<out T>(val id: String) {
    /** Judul bagian: "Sering dipakai" ([letter] null) atau huruf abjad. */
    class Header(val title: String, val letter: Char? = null) : DrawerRow<Nothing>("header:$title")

    class App<T>(val item: T, key: String, val frequent: Boolean) : DrawerRow<T>((if (frequent) "frequent:" else "app:") + key)
}

/**
 * Menyusun isi app drawer dari daftar aplikasi. Murni dan generik ([T] = AppInfo di aplikasi) sehingga bisa diuji.
 * Urutan masukan [apps] dianggap sudah urut abjad sesuai locale; di sini hanya dikelompokkan, tidak diurutkan ulang.
 */
object DrawerLayout {
    const val FREQUENT_TITLE = "Sering dipakai"

    class Result<T>(
        val rows: List<DrawerRow<T>>,
        private val sections: List<AlphaIndex.Section<T>>,
        /** Jumlah baris sebelum bagian abjad pertama (judul + baris "Sering dipakai"). */
        private val alphaOffset: Int,
    ) {
        /** Posisi baris judul huruf [letter] (atau bagian terdekat sesudahnya) untuk jump bar; null bila tak ada aplikasi. */
        fun jumpIndex(letter: Char): Int? = AlphaIndex.flatIndexOf(sections, letter)?.plus(alphaOffset)

        /** Huruf bagian yang sedang terlihat pada baris [rowIndex]; baris "Sering dipakai" dianggap huruf pertama. */
        fun letterAt(rowIndex: Int): Char? =
            if (sections.isEmpty()) null else AlphaIndex.letterAt(sections, (rowIndex - alphaOffset).coerceAtLeast(0))

        /** Huruf yang punya aplikasi, untuk menyorot jump bar. */
        val lettersPresent: Set<Char> get() = sections.map { it.letter }.toSet()
    }

    /** Kategori efektif: pilihan pengguna bila ada dan sah, selain itu kategori otomatis. */
    fun effectiveCategory(auto: AppCategory, key: String, overrides: Map<String, String>): AppCategory =
        AppCategorizer.parse(overrides[key]) ?: auto

    /** Kategori yang punya aplikasi beserta jumlahnya, urut sesuai enum. Kosong bila hanya ada satu kategori (chip tak berguna). */
    fun <T> availableCategories(
        apps: List<T>,
        key: (T) -> String,
        auto: (T) -> AppCategory,
        overrides: Map<String, String>,
    ): List<Pair<AppCategory, Int>> {
        val counts = apps.groupingBy { effectiveCategory(auto(it), key(it), overrides) }.eachCount()
        val list = AppCategory.entries.mapNotNull { c -> counts[c]?.let { c to it } }
        return if (list.size < 2) emptyList() else list
    }

    /**
     * @param selected null = semua aplikasi; selain itu hanya kategori tersebut
     * @param frequentKeys kunci aplikasi terurut dari yang paling sering; hanya dipakai pada "semua"
     * @param frequentCount batas jumlah ikon di bagian "Sering dipakai"
     */
    fun <T> build(
        apps: List<T>,
        key: (T) -> String,
        label: (T) -> String,
        auto: (T) -> AppCategory,
        overrides: Map<String, String>,
        selected: AppCategory?,
        frequentKeys: List<String>,
        frequentCount: Int,
    ): Result<T> {
        val shown = if (selected == null) apps else apps.filter { effectiveCategory(auto(it), key(it), overrides) == selected }
        val rows = ArrayList<DrawerRow<T>>(shown.size + 40)

        if (selected == null && frequentCount > 0) {
            val byKey = apps.associateBy(key)
            val frequent = frequentKeys.asSequence().mapNotNull { byKey[it] }.distinctBy(key).take(frequentCount).toList()
            if (frequent.isNotEmpty()) {
                rows += DrawerRow.Header(FREQUENT_TITLE)
                frequent.forEach { rows += DrawerRow.App(it, key(it), frequent = true) }
            }
        }
        val alphaOffset = rows.size

        val sections = AlphaIndex.group(shown, label)
        for (s in sections) {
            rows += DrawerRow.Header(s.letter.toString(), s.letter)
            s.items.forEach { rows += DrawerRow.App(it, key(it), frequent = false) }
        }
        return Result(rows, sections, alphaOffset)
    }
}
