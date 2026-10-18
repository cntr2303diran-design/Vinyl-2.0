package com.vinyl.launcher.ui.home

/**
 * Navigasi tiga halaman beranda tema TV tabung, murni (tanpa Compose) agar aturannya bisa diuji.
 *
 *   [ Perpustakaan kaset ]  ←  [ Ruang TV (beranda) ]  →  [ Audio Console ]
 *
 * "Kiri" dan "kanan" adalah posisi di layar. Menggeser jari ke KANAN menarik halaman di kiri ke dalam layar (seperti
 * halaman widget di kiri beranda pada launcher umumnya), dan sebaliknya. Pengguna bisa menukar kedua sisi
 * (RetroSettings.swapPages), dan pada bahasa kanan-ke-kiri (RTL) tata letaknya dicerminkan.
 */

enum class RoomPage { LIBRARY, ROOM, CONSOLE }

object PageNav {
    /** Jarak geser horizontal minimum (px) agar dianggap pindah halaman; sama dengan ambang swipe lama. */
    const val SWIPE_PX = 160f

    /** Urutan halaman dari kiri ke kanan layar. */
    fun order(swap: Boolean, rtl: Boolean): List<RoomPage> {
        val base = if (swap) listOf(RoomPage.CONSOLE, RoomPage.ROOM, RoomPage.LIBRARY) else listOf(RoomPage.LIBRARY, RoomPage.ROOM, RoomPage.CONSOLE)
        return if (rtl) base.reversed() else base
    }

    /** Indeks halaman di layar (0 = paling kiri). */
    fun indexOf(page: RoomPage, swap: Boolean, rtl: Boolean): Int = order(swap, rtl).indexOf(page)

    /**
     * Halaman setelah jari digeser sejauh [dx] px (positif = ke kanan) dari [current]. Geser ke kanan menampilkan halaman
     * di KIRI. Di ujung, halaman tidak berubah. Geser yang terlalu pendek diabaikan.
     */
    fun afterSwipe(current: RoomPage, dx: Float, swap: Boolean, rtl: Boolean): RoomPage {
        if (dx.isNaN() || kotlin.math.abs(dx) < SWIPE_PX) return current
        val pages = order(swap, rtl)
        val i = pages.indexOf(current)
        val next = if (dx > 0) i - 1 else i + 1
        return pages.getOrNull(next) ?: current
    }

    /**
     * Pergeseran horizontal isi halaman, dalam satuan lebar layar, agar [page] tampil: halaman berindeks i berada di
     * (i - indeks halaman aktif) lebar layar dari posisi tampil.
     */
    fun offsetOf(page: RoomPage, active: RoomPage, swap: Boolean, rtl: Boolean): Int =
        indexOf(page, swap, rtl) - indexOf(active, swap, rtl)

    /** Nama untuk pembaca layar. */
    fun label(page: RoomPage): String = when (page) {
        RoomPage.LIBRARY -> "Perpustakaan kaset"
        RoomPage.ROOM -> "Ruang TV"
        RoomPage.CONSOLE -> "Konsol audio"
    }

    /** Nama tersimpan → halaman; nilai tak dikenal (mis. dari versi lain) kembali ke ruang TV. */
    fun parse(name: String?): RoomPage = RoomPage.entries.firstOrNull { it.name == name } ?: RoomPage.ROOM
}
