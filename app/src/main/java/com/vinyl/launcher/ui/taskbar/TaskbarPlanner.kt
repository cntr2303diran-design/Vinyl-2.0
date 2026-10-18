package com.vinyl.launcher.ui.taskbar

/*
 * Perencana tata letak Taskbar Vinyl.
 *
 * MASALAH LAMA: jendela overlay memakai WRAP_CONTENT dan isinya (Row) mengukur anak berurutan.
 * Jendela WRAP_CONTENT berbasis Compose tidak pernah memberi tahu sistem "ruang kurang", sehingga
 * saat favorit + aplikasi terakhir + kartu lagu + jam melebihi lebar layar, elemen paling kanan
 * (jam dan tombol ciutkan) kehabisan ruang dan terpotong.
 *
 * SOLUSI: ukuran bar dihitung di sini (fungsi murni, tanpa Android, mudah diuji) dari lebar layar
 * yang tersedia. Bila tidak muat, elemen dibuang menurut prioritas — aplikasi terakhir dulu, lalu
 * teks lagu, lalu tanggal — dan bila masih sempit daftar favorit dibuat bisa digulir. Jam dan tombol
 * ciutkan TIDAK PERNAH dibuang. Hasilnya dipakai dua pihak sekaligus: TaskbarService (ukuran jendela
 * yang pasti) dan TaskbarBar (isi), sehingga keduanya selalu sepakat.
 *
 * Semua ukuran dalam dp.
 */
object TaskbarMetrics {
    const val ICON = 48f
    const val GAP = 6f
    /** Pembatas: garis 1dp + 4dp ruang di kedua sisi. */
    const val DIVIDER = 9f
    /** Jarak kapsul ke tepi jendela (tiap sisi). */
    const val OUTER_PAD = 4f

    /** Padding dalam kapsul: horizontal 10dp, vertikal 8dp (tidak bergantung orientasi). */
    private const val PAD_H = 10f
    private const val PAD_V = 8f

    // Kartu lagu: padding awal 6 + cover 36 (+ judul: 10 + 140 + 2) + tombol putar 48 (target sentuh minimum)
    const val CHIP_FULL = 242f
    const val CHIP_COMPACT = 90f
    // Vertikal: padding 6 + cover 36 + jarak 2 + tombol putar 48 + padding 6
    const val CHIP_VERTICAL = 98f
    const val CHIP_CROSS = 48f
    /** Lebar tetap kolom judul/artis pada kartu lagu penuh. */
    const val NP_TEXT = 140f

    const val TRAY_TIME_W = 48f
    const val TRAY_DATE_W = 128f
    const val TRAY_PAD = 16f
    /** Tinggi tray (dua baris teks + padding) pada skala font 1×. */
    const val TRAY_H = 48f   // target sentuh minimum 48dp

    /** Skala font di dalam bar dibatasi agar kapsul tidak melebar tanpa batas. */
    const val MAX_FONT_SCALE = 1.5f
    /** Viewport minimum daftar favorit agar masih berguna; di bawah ini daftar disembunyikan. */
    const val MIN_VIEWPORT = 56f

    const val HANDLE_LONG = 120f
    const val HANDLE_SHORT = 40f

    fun padMain(vertical: Boolean) = if (vertical) 2 * PAD_V else 2 * PAD_H
    fun padCross(vertical: Boolean) = if (vertical) 2 * PAD_H else 2 * PAD_V
}

enum class ChipMode { NONE, COMPACT, FULL }

/** Masukan perencana. [availableMainDp] = ukuran layar searah bar (lebar bila horizontal) dikurangi jarak tepi. */
data class TaskbarInput(
    val vertical: Boolean,
    val availableMainDp: Float,
    /** Jumlah favorit yang berhasil dipetakan ke aplikasi terpasang. */
    val favorites: Int,
    /** Jumlah aplikasi terakhir yang tersedia (sudah tanpa duplikat favorit). */
    val recentsCandidates: Int,
    /** Kartu lagu aktif (pengaturan menyala DAN ada lagu). */
    val nowPlaying: Boolean,
    /** Gaya PC: jam/baterai (dan tanggal) ditampilkan. */
    val tray: Boolean,
    /** Skala font sistem. */
    val fontScale: Float,
    val collapsed: Boolean,
)

data class TaskbarPlan(
    val vertical: Boolean,
    val collapsed: Boolean,
    val recents: Int,
    val favorites: Int,
    val chip: ChipMode,
    val showTray: Boolean,
    val showDate: Boolean,
    /** Pintasan Pengaturan tampil bila tidak ada favorit maupun aplikasi terakhir. */
    val showSettingsShortcut: Boolean,
    val showMiddle: Boolean,
    val middleScrolls: Boolean,
    /** Ukuran grup tengah (favorit + terakhir) searah bar: viewport bila bisa digulir, jika tidak ukuran alami. */
    val middleMainDp: Float,
    /** Ukuran tray searah bar. */
    val trayMainDp: Float,
    /** Skala font efektif di dalam bar (sudah dibatasi). */
    val fontScale: Float,
    /** Ukuran kapsul, searah dan melintang sumbu bar. */
    val barMainDp: Float,
    val barCrossDp: Float,
    /** Ukuran jendela = kapsul + jarak tepi. */
    val windowMainDp: Float,
    val windowCrossDp: Float,
    /** false hanya bila layar lebih sempit dari bagian esensial (Home, Semua aplikasi, jam, ciutkan). */
    val fits: Boolean,
) {
    val windowWidthDp: Float get() = if (vertical) windowCrossDp else windowMainDp
    val windowHeightDp: Float get() = if (vertical) windowMainDp else windowCrossDp
}

object TaskbarPlanner {
    private const val ICON = TaskbarMetrics.ICON
    private const val GAP = TaskbarMetrics.GAP
    private const val DIV = TaskbarMetrics.DIVIDER
    private const val OUT = TaskbarMetrics.OUTER_PAD

    fun plan(i: TaskbarInput): TaskbarPlan {
        val fs = i.fontScale.coerceIn(1f, TaskbarMetrics.MAX_FONT_SCALE)
        if (i.collapsed) return collapsedPlan(i, fs)

        var rec = i.recentsCandidates.coerceAtLeast(0)
        var chip = when {
            !i.nowPlaying -> ChipMode.NONE
            i.vertical -> ChipMode.COMPACT
            else -> ChipMode.FULL
        }
        var date = i.tray && !i.vertical

        // 1) Buang elemen berprioritas rendah sampai muat
        while (true) {
            val total = windowMain(i, fs, chip, date, middle = middleNatural(i.favorites, rec))
            if (total <= i.availableMainDp) return build(i, fs, rec, chip, date, scrolls = false, showMiddle = true, viewport = middleNatural(i.favorites, rec))
            when {
                rec > 0 -> rec--
                chip == ChipMode.FULL -> chip = ChipMode.COMPACT
                date -> date = false
                else -> break
            }
        }

        // 2) Masih sempit: favorit dibuat bisa digulir di viewport sisa ruang.
        //    Kartu lagu baru dibuang bila tanpa itu tidak ada satu favorit pun yang muat.
        while (true) {
            val others = otherChildren(i, fs, chip, date)
            val viewport = i.availableMainDp - 2 * OUT - TaskbarMetrics.padMain(i.vertical) - others.sum() - GAP * others.size
            if (viewport >= TaskbarMetrics.MIN_VIEWPORT) {
                return build(i, fs, rec, chip, date, scrolls = true, showMiddle = true, viewport = viewport)
            }
            if (chip == ChipMode.NONE) {
                return build(i, fs, rec, chip, date, scrolls = false, showMiddle = false, viewport = 0f)
            }
            chip = ChipMode.NONE
        }
    }

    /** Ukuran minimum agar bagian esensial (tanpa kartu lagu & grup tengah) muat. */
    fun essentialMainDp(vertical: Boolean, tray: Boolean, fontScale: Float): Float {
        val fs = fontScale.coerceIn(1f, TaskbarMetrics.MAX_FONT_SCALE)
        val others = otherChildren(
            TaskbarInput(vertical, 0f, 0, 0, false, tray, fs, false), fs, ChipMode.NONE, false,
        )
        return stackLength(others) + TaskbarMetrics.padMain(vertical) + 2 * OUT
    }

    // ---------------------------------------------------------------- internal

    private fun trayMain(vertical: Boolean, date: Boolean, fs: Float): Float =
        if (vertical) TaskbarMetrics.TRAY_H * fs
        else (if (date) TaskbarMetrics.TRAY_DATE_W else TaskbarMetrics.TRAY_TIME_W) * fs + TaskbarMetrics.TRAY_PAD

    private fun chipMain(vertical: Boolean, chip: ChipMode): Float = when (chip) {
        ChipMode.NONE -> 0f
        ChipMode.COMPACT -> if (vertical) TaskbarMetrics.CHIP_VERTICAL else TaskbarMetrics.CHIP_COMPACT
        ChipMode.FULL -> TaskbarMetrics.CHIP_FULL
    }

    private fun stackLength(sizes: List<Float>): Float =
        if (sizes.isEmpty()) 0f else sizes.sum() + GAP * (sizes.size - 1)

    /** Isi grup tengah: [pembatas, favorit…] [pembatas, terakhir…] atau pintasan Pengaturan. */
    private fun middleChildren(fav: Int, rec: Int): List<Float> = buildList {
        if (fav > 0) { add(DIV); repeat(fav) { add(ICON) } }
        if (rec > 0) { add(DIV); repeat(rec) { add(ICON) } }
        if (fav == 0 && rec == 0) add(ICON)
    }

    private fun middleNatural(fav: Int, rec: Int): Float = stackLength(middleChildren(fav, rec))

    /** Anak langsung bar selain grup tengah: Home, Semua aplikasi, [kartu lagu], [tray], ciutkan. */
    private fun otherChildren(i: TaskbarInput, fs: Float, chip: ChipMode, date: Boolean): List<Float> = buildList {
        add(ICON); add(ICON)
        if (chip != ChipMode.NONE) { add(DIV); add(chipMain(i.vertical, chip)) }
        if (i.tray) { add(DIV); add(trayMain(i.vertical, date, fs)) }
        add(DIV); add(ICON)
    }

    private fun windowMain(i: TaskbarInput, fs: Float, chip: ChipMode, date: Boolean, middle: Float): Float {
        val others = otherChildren(i, fs, chip, date)
        // grup tengah ikut dihitung sebagai satu anak (jarak GAP di kedua sisinya)
        return others.sum() + middle + GAP * others.size + TaskbarMetrics.padMain(i.vertical) + 2 * OUT
    }

    private fun crossContent(i: TaskbarInput, fs: Float, chip: ChipMode): Float {
        var c = ICON
        if (chip != ChipMode.NONE) c = maxOf(c, TaskbarMetrics.CHIP_CROSS)
        if (i.tray) {
            c = maxOf(
                c,
                if (i.vertical) TaskbarMetrics.TRAY_TIME_W * fs + TaskbarMetrics.TRAY_PAD else TaskbarMetrics.TRAY_H * fs,
            )
        }
        return c
    }

    private fun build(
        i: TaskbarInput, fs: Float, rec: Int, chip: ChipMode, date: Boolean,
        scrolls: Boolean, showMiddle: Boolean, viewport: Float,
    ): TaskbarPlan {
        val middle = if (showMiddle) viewport else 0f
        val others = otherChildren(i, fs, chip, date)
        val childCount = others.size + if (showMiddle) 1 else 0
        val barMain = others.sum() + middle + GAP * (childCount - 1) + TaskbarMetrics.padMain(i.vertical)
        val barCross = crossContent(i, fs, chip) + TaskbarMetrics.padCross(i.vertical)
        val windowMain = barMain + 2 * OUT
        return TaskbarPlan(
            vertical = i.vertical, collapsed = false,
            recents = rec, favorites = i.favorites, chip = chip,
            showTray = i.tray, showDate = date,
            showSettingsShortcut = i.favorites == 0 && rec == 0,
            showMiddle = showMiddle, middleScrolls = scrolls, middleMainDp = middle,
            trayMainDp = trayMain(i.vertical, date, fs), fontScale = fs,
            barMainDp = barMain, barCrossDp = barCross,
            windowMainDp = windowMain, windowCrossDp = barCross + 2 * OUT,
            fits = windowMain <= i.availableMainDp + 0.01f,
        )
    }

    private fun collapsedPlan(i: TaskbarInput, fs: Float): TaskbarPlan {
        val main = TaskbarMetrics.HANDLE_LONG
        val cross = TaskbarMetrics.HANDLE_SHORT
        return TaskbarPlan(
            vertical = i.vertical, collapsed = true,
            recents = 0, favorites = 0, chip = ChipMode.NONE,
            showTray = false, showDate = false, showSettingsShortcut = false,
            showMiddle = false, middleScrolls = false, middleMainDp = 0f,
            trayMainDp = 0f, fontScale = fs,
            barMainDp = main, barCrossDp = cross,
            windowMainDp = main + 2 * OUT, windowCrossDp = cross + 2 * OUT,
            fits = true,
        )
    }
}
