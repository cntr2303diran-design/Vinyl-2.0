package com.vinyl.launcher.ui.room.vhs

/*
 * Ikon status berpiksel untuk OSD bergaya layar kamera/VCR. Murni (tanpa Compose) agar bentuknya diuji. '#' = piksel
 * menyala. Ikon bertingkat (Wi-Fi) membagi pikselnya ke beberapa "grup" yang menyala sesuai level.
 */
class Glyph(rows: List<String>, private val groups: List<Set<Int>> = emptyList()) {
    val width: Int = rows.maxOf { it.length }
    val height: Int = rows.size
    private val on: List<BooleanArray> = rows.map { r -> BooleanArray(width) { x -> x < r.length && r[x] == '#' } }

    fun isOn(x: Int, y: Int): Boolean = y in 0 until height && x in 0 until width && on[y][x]

    /** Grup baris (0 = paling dalam/kecil) tempat piksel di baris [y] berada; -1 bila glyph tidak bertingkat. */
    fun groupOf(y: Int): Int = groups.indexOfFirst { y in it }

    val levels: Int get() = groups.size

    /** Semua piksel menyala sebagai pasangan (x, y). */
    fun pixels(): List<Pair<Int, Int>> = (0 until height).flatMap { y -> (0 until width).filter { x -> on[y][x] }.map { x -> x to y } }
}

object OsdGlyphs {
    /** Kipas Wi-Fi: grup 0 = titik, 1 = busur dalam, 2 = tengah, 3 = luar (menyala bila level >= grup + 1). */
    val WIFI = Glyph(
        listOf(
            ".#######.",
            "#.......#",
            "..#####..",
            ".#.....#.",
            "...###...",
            "..#...#..",
            "....#....",
        ),
        groups = listOf(setOf(6), setOf(4, 5), setOf(2, 3), setOf(0, 1)),
    )

    val BLUETOOTH = Glyph(
        listOf(
            "..#..",
            "..##.",
            "#.#.#",
            ".###.",
            "..#..",
            ".###.",
            "#.#.#",
            "..##.",
            "..#..",
        ),
    )

    val BELL = Glyph(
        listOf(
            "...#...",
            "..###..",
            ".#####.",
            ".#####.",
            ".#####.",
            "#######",
            ".......",
            "...#...",
        ),
    )

    val BELL_OFF = Glyph(
        listOf(
            "#..#...",
            ".####..",
            ".##.##.",
            ".###.#.",
            ".####..",
            "######.",
            "......#",
            "...#...",
        ),
    )

    val VIBRATE = Glyph(
        listOf(
            "..#####..",
            "#.#...#.#",
            ".##...##.",
            "#.#...#.#",
            ".##...##.",
            "#.#...#.#",
            "..#####..",
        ),
    )

    val DO_NOT_DISTURB = Glyph(
        listOf(
            "..###..",
            ".#...#.",
            "#.....#",
            "#.###.#",
            "#.....#",
            ".#...#.",
            "..###..",
        ),
    )

    val ALARM = Glyph(
        listOf(
            "#.....#",
            ".#####.",
            "#..#..#",
            "#..#..#",
            "#..##.#",
            "#.....#",
            ".#####.",
        ),
    )

    val AIRPLANE = Glyph(
        listOf(
            "...#...",
            "...#...",
            ".#####.",
            "#######",
            "...#...",
            "..###..",
            "..#.#..",
        ),
    )

    val BOLT = Glyph(
        listOf(
            "...#.",
            "..#..",
            ".#...",
            "#####",
            "...#.",
            "..#..",
            ".#...",
        ),
    )

    val NO_SIM = Glyph(
        listOf(
            "####.",
            "#..##",
            "#.#.#",
            "#..##",
            "#.#.#",
            "#####",
        ),
    )

    /** Semua glyph (untuk pengujian). */
    val ALL = listOf(WIFI, BLUETOOTH, BELL, BELL_OFF, VIBRATE, DO_NOT_DISTURB, ALARM, AIRPLANE, BOLT, NO_SIM)
}
