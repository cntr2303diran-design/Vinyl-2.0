package com.vinyl.launcher.util

import java.io.ByteArrayOutputStream
import java.io.InputStream

/*
 * Pembacaan data dari sumber yang tidak dipercaya (internet, aplikasi musik lain) selalu berbatas,
 * supaya respons raksasa tidak menghabiskan memori. Murni Kotlin/Java — bisa diuji unit.
 */

/**
 * Membaca seluruh isi stream, paling banyak [max] byte.
 * Mengembalikan null bila isinya lebih besar (sengaja tidak dipotong diam-diam: data terpotong
 * biasanya berupa gambar/JSON rusak yang lebih membingungkan daripada ditolak).
 */
fun InputStream.readAtMost(max: Int): ByteArray? {
    require(max >= 0) { "max harus >= 0" }
    val out = ByteArrayOutputStream(minOf(max, CHUNK))
    val buf = ByteArray(CHUNK)
    var total = 0L
    while (true) {
        val n = read(buf)
        if (n < 0) break
        total += n
        if (total > max) return null
        out.write(buf, 0, n)
    }
    return out.toByteArray()
}

/** Seperti [readAtMost] tetapi hasilnya teks UTF-8. */
fun InputStream.readTextAtMost(max: Int): String? = readAtMost(max)?.toString(Charsets.UTF_8)

/**
 * Faktor sampling (pangkat 2) untuk BitmapFactory.inSampleSize agar sisi terpanjang gambar
 * tetap >= [target] piksel setelah diperkecil. Mengembalikan 1 bila ukuran tidak valid.
 */
fun sampleSizeFor(width: Int, height: Int, target: Int): Int {
    val longest = maxOf(width, height)
    if (longest <= 0 || target <= 0) return 1
    var s = 1
    while (s < MAX_SAMPLE && longest / (s * 2) >= target) s *= 2
    return s
}

private const val CHUNK = 8 * 1024
private const val MAX_SAMPLE = 1 shl 10
