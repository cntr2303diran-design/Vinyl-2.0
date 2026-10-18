package com.vinyl.launcher.ui.room

import com.vinyl.launcher.ui.theme.Tokens

/*
 * Isi VFD yang bergantian, murni agar bisa diuji. VFD adalah informasi SEKUNDER: semua yang ditampilkan juga ada di
 * tempat lain (jam, info lagu, konsol), dan teks yang sama disediakan untuk pembaca layar.
 */
object VfdTicker {
    /** Satu baris VFD. [text] sudah huruf besar dan dipotong ke [MAX_CHARS]. */
    data class Line(val key: String, val text: String)

    /** Lebar VFD dalam karakter (layar kecil di panel TV). */
    const val MAX_CHARS = 12

    fun line(key: String, text: String): Line = Line(key, text.uppercase().filter { !it.isISOControl() }.trim().take(MAX_CHARS))

    /**
     * Baris yang tampil pada [nowMs]. Bila [priority] ada dan belum lewat [Tokens.Motion.VFD_PRIORITY_MS] sejak
     * [priorityAtMs] (mis. output audio baru), baris itu yang tampil. Selain itu baris bergantian tiap
     * [Tokens.Motion.VFD_CYCLE_MS]. Daftar kosong → null (VFD menampilkan segmen mati saja).
     */
    fun current(lines: List<Line>, nowMs: Long, priority: Line? = null, priorityAtMs: Long = Long.MIN_VALUE): Line? {
        if (priority != null && priorityAtMs != Long.MIN_VALUE) {
            val age = nowMs - priorityAtMs
            if (age in 0 until Tokens.Motion.VFD_PRIORITY_MS) return priority
        }
        if (lines.isEmpty()) return null
        val slot = Math.floorMod(nowMs / Tokens.Motion.VFD_CYCLE_MS, lines.size.toLong()).toInt()
        return lines[slot]
    }

    /** Status pemutaran untuk VFD. */
    fun playback(hasTrack: Boolean, isPlaying: Boolean): String = when {
        !hasTrack -> "STANDBY"
        isPlaying -> "PLAY"
        else -> "PAUSE"
    }
}
