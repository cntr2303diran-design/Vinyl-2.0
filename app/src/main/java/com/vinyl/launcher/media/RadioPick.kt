package com.vinyl.launcher.media

import kotlin.random.Random

/**
 * Pilihan lagu acak untuk radio di meja (murni, teruji; pengacaknya disuntikkan). Urutan putar diacak seluruhnya supaya lagu
 * sesudahnya juga acak, dan lagu yang diputar radio sebelumnya tidak jatuh di urutan pertama lagi bila ada pilihan lain.
 */
object RadioPick {
    /** Urutan putar acak dari [ids]; kosong bila [ids] kosong. [lastId] tidak menjadi yang pertama kecuali satu-satunya lagu. */
    fun order(ids: List<String>, lastId: String?, rnd: Random): List<String> {
        if (ids.isEmpty()) return emptyList()
        val shuffled = ids.distinct().shuffled(rnd)
        if (shuffled.size > 1 && shuffled.first() == lastId) {
            // Tukar dengan lagu lain yang dipilih acak, supaya tidak selalu lagu kedua yang naik
            val swapWith = 1 + rnd.nextInt(shuffled.size - 1)
            return shuffled.toMutableList().also { val t = it[0]; it[0] = it[swapWith]; it[swapWith] = t }
        }
        return shuffled
    }
}
