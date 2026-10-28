package com.vinyl.launcher.data

/**
 * Aturan "Ingat tombol daya TV" ([RetroSettings.rememberTvPower]); murni agar bisa diuji.
 *
 * Keadaan yang disimpan ([TvPowerStore]) selalu sudah diperhitungkan dengan pilihannya: bila pilihan mati, yang
 * tersimpan adalah "menyala", sehingga tidak ada keadaan lama yang bisa hidup lagi ketika pilihan dinyalakan kembali.
 */
object TvPowerRules {
    /**
     * Apakah TV berstatus mati yang BERLAKU, dari pilihan [remember] dan keadaan [off] (TV sedang/terakhir dimatikan
     * dengan tombol dayanya). Dipakai dua arah: nilai yang disimpan ([off] = TV sekarang mati) dan keadaan awal beranda
     * ([off] = nilai tersimpan). Tanpa pilihan itu, TV tidak pernah dianggap mati.
     */
    fun effectiveOff(remember: Boolean, off: Boolean): Boolean = remember && off
}
