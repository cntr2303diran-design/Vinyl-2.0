package com.vinyl.launcher.ui.room.stage

/** Hitungan jarum jam dinding dan ucapannya untuk pembaca layar (murni, teruji). */
object ClockMath {
    /** Sudut jarum jam dalam derajat searah jarum jam dari atas (0..360); bergeser mulus menurut menit. */
    fun hourAngle(hour: Int, minute: Int): Float = (((hour % 12 + 12) % 12) + minute.coerceIn(0, 59) / 60f) * 30f

    fun minuteAngle(minute: Int): Float = minute.coerceIn(0, 59) * 6f

    /** "Pukul 07.05" — dua angka untuk jam dan menit. */
    fun spoken(hour: Int, minute: Int): String = "Pukul %02d.%02d".format(hour.coerceIn(0, 23), minute.coerceIn(0, 59))

    /** Milidetik sampai menit berikutnya mulai (1..60000), untuk memperbarui jam tepat pada pergantian menit. */
    fun msToNextMinute(epochMs: Long): Long {
        val rest = 60_000L - Math.floorMod(epochMs, 60_000L)
        return rest.coerceIn(1L, 60_000L)
    }
}
