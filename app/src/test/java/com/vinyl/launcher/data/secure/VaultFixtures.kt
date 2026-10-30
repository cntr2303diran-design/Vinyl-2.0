package com.vinyl.launcher.data.secure

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/** Kunci AES-256 perangkat lunak untuk uji JVM (di perangkat kuncinya ada di Android Keystore). */
fun softwareKey(): SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

/**
 * Penyedia kunci uji. [key] bisa diganti selama tes: null = kunci tidak tersedia, kunci lain = kunci hilang dan diganti.
 * [failOpen] = kunci ada untuk menyegel tetapi tidak untuk membuka (mensimulasikan segel yang tidak bisa dibaca kembali).
 */
class FakeKeys(var key: SecretKey? = softwareKey()) : KeyProvider {
    var createCalls = 0
    var openCalls = 0
    var failOpen = false

    override fun key(create: Boolean): SecretKey? {
        if (create) createCalls++ else openCalls++
        if (!create && failOpen) return null
        return key
    }
}

/** Pengganti SharedPreferences: peta biasa yang mencatat berapa kali perubahan diterapkan. */
class MapSlots(initial: Map<String, String> = emptyMap()) : TextSlots {
    private val map = LinkedHashMap(initial)
    var changeCalls = 0
        private set

    @Synchronized
    override fun get(key: String): String? = map[key]

    @Synchronized
    override fun change(changes: Map<String, String?>) {
        changeCalls++
        for ((k, v) in changes) if (v == null) map.remove(k) else map[k] = v
    }

    @Synchronized
    fun keys(): Set<String> = map.keys.toSet()

    @Synchronized
    fun values(): List<String> = map.values.toList()
}

/** Tunggu sampai semua pekerjaan yang sudah diantrekan selesai (antrean berurutan, jadi pekerjaan penanda ini yang terakhir). */
fun SerialWorker.drain() {
    val done = CountDownLatch(1)
    post { done.countDown() }
    check(done.await(10, TimeUnit.SECONDS)) { "antrean tidak selesai dalam 10 detik" }
}
