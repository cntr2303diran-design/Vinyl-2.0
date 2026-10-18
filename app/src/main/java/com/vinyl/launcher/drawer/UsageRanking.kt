package com.vinyl.launcher.drawer

import kotlinx.serialization.Serializable
import kotlin.math.pow

/**
 * Riwayat peluncuran aplikasi lewat launcher ini: kunci aplikasi → cap waktu (ms) peluncuran, terlama dulu.
 * Disimpan hanya di perangkat (lihat UsageStore); tidak pernah dikirim ke mana pun.
 */
@Serializable
data class UsageData(val launches: Map<String, List<Long>> = emptyMap())

/** Peringkat "Sering dipakai": skor berbobot waktu (peluncuran baru dihitung lebih berat, separuh nilai tiap [HALF_LIFE_DAYS] hari). */
object UsageRanking {
    const val MAX_KEYS = 600
    const val MAX_STAMPS = 40
    const val HALF_LIFE_DAYS = 10.0
    /** Skor minimum agar masuk daftar: satu peluncuran kemarin saja belum cukup, dua sudah. */
    const val MIN_SCORE = 1.0
    private const val DAY_MS = 86_400_000.0

    fun score(stamps: List<Long>, now: Long): Double =
        stamps.sumOf { t ->
            val ageDays = ((now - t).coerceAtLeast(0L)) / DAY_MS   // cap waktu di masa depan (jam mundur) dianggap "baru saja"
            0.5.pow(ageDays / HALF_LIFE_DAYS)
        }

    fun record(data: UsageData, key: String, now: Long): UsageData {
        val updated = (data.launches[key].orEmpty() + now).takeLast(MAX_STAMPS)
        val map = data.launches + (key to updated)
        return prune(UsageData(map), now)
    }

    /** Teratas berdasarkan skor; seri → yang terakhir dipakai lebih baru; hanya kunci yang lolos [include]. */
    fun top(data: UsageData, now: Long, limit: Int, include: (String) -> Boolean = { true }): List<String> =
        data.launches.entries
            .asSequence()
            .filter { include(it.key) }
            .map { Triple(it.key, score(it.value, now), it.value.maxOrNull() ?: 0L) }
            .filter { it.second >= MIN_SCORE }
            .sortedWith(compareByDescending<Triple<String, Double, Long>> { it.second }.thenByDescending { it.third }.thenBy { it.first })
            .take(limit.coerceAtLeast(0))
            .map { it.first }
            .toList()

    /**
     * Lupakan semua komponen milik [packageName] (kunci berbentuk "paket/kelas" atau "paket/kelas#id-profil").
     * Dipanggil hanya saat sistem melaporkan paket dihapus — BUKAN dari daftar aplikasi terpasang, yang bisa kosong
     * sementara (profil kerja dijeda, kartu SD belum terpasang) dan akan menghapus riwayat yang sah.
     */
    fun forgetPackage(data: UsageData, packageName: String): UsageData {
        val prefix = "$packageName/"
        return if (data.launches.keys.none { it.startsWith(prefix) }) data
        else UsageData(data.launches.filterKeys { !it.startsWith(prefix) })
    }

    /** Batasi ukuran: bila terlalu banyak aplikasi tercatat, buang yang skornya paling rendah. */
    fun prune(data: UsageData, now: Long): UsageData {
        if (data.launches.size <= MAX_KEYS) return data
        val keep = data.launches.entries.sortedByDescending { score(it.value, now) }.take(MAX_KEYS).map { it.key }.toSet()
        return UsageData(data.launches.filterKeys { it in keep })
    }

    /** Membersihkan data hasil baca dari penyimpanan (bisa rusak/diubah): cap tidak masuk akal dan kelebihan dibuang. */
    fun sanitize(data: UsageData, now: Long): UsageData {
        val horizon = now + DAY_MS.toLong()   // toleransi jam bergeser satu hari
        val clean = data.launches
            .filterKeys { it.length in 1..512 }
            .mapValues { (_, v) -> v.filter { it in 1..horizon }.sorted().takeLast(MAX_STAMPS) }
            .filterValues { it.isNotEmpty() }
        return prune(UsageData(clean), now)
    }
}
