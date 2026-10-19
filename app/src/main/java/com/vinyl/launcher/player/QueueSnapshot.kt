package com.vinyl.launcher.player

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Antrean pemutar yang diingat di perangkat supaya pemutaran bisa dilanjutkan bila sistem mematikan proses aplikasi
 * (tombol putar di headset/Bluetooth/layar kunci). Hanya id lagu, id lagu yang sedang diputar, dan posisinya; tidak ada
 * judul, nama berkas, atau data lain.
 */
data class QueueSnapshot(
    val ids: List<String>,
    /** Id lagu yang terakhir diputar (null = belum ada posisi tersimpan: mulai dari lagu pertama). */
    val currentId: String? = null,
    val positionMs: Long = 0L,
)

/** Antrean yang siap dipasang ke pemutar. */
data class QueueResume(val ids: List<String>, val startIndex: Int, val positionMs: Long)

object QueueRules {
    /** Sama dengan batas pemindaian pustaka: antrean tidak mungkin lebih panjang dari itu. */
    const val MAX_IDS = 10_000
    const val MAX_ID_LENGTH = 1_000

    /**
     * Pulihkan antrean dari [saved] dengan hanya menyisakan lagu yang masih ada dan bisa diputar ([alive]).
     * - Lagu terakhir masih ada → lanjut di lagu itu pada posisi tersimpan.
     * - Lagu terakhir sudah hilang → lanjut di lagu pertama yang masih ada sesudah posisinya, dari awal.
     * - Tidak ada lagu tersisa → null (jangan memutar apa-apa).
     */
    fun resolve(saved: QueueSnapshot, alive: (String) -> Boolean): QueueResume? {
        val ids = saved.ids.asSequence().filter { it.isNotEmpty() && it.length <= MAX_ID_LENGTH }.take(MAX_IDS).toList()
        if (ids.isEmpty()) return null
        val currentAt = saved.currentId?.let { ids.indexOf(it) } ?: -1
        val kept = ArrayList<String>(ids.size)
        var start = -1
        var firstAfter = -1
        for ((i, id) in ids.withIndex()) {
            if (!alive(id)) continue
            if (i == currentAt) start = kept.size
            if (firstAfter < 0 && currentAt >= 0 && i > currentAt) firstAfter = kept.size
            kept.add(id)
        }
        if (kept.isEmpty()) return null
        return when {
            start >= 0 -> QueueResume(kept, start, saved.positionMs.coerceAtLeast(0L))
            // Lagu terakhir hilang: sesudah posisinya bila ada, jika tidak awal antrean
            firstAfter >= 0 -> QueueResume(kept, firstAfter, 0L)
            else -> QueueResume(kept, 0, 0L)
        }
    }

    private val json = Json { ignoreUnknownKeys = true }

    fun encodeIds(ids: List<String>): String = json.encodeToString(ListSerializer(String.serializer()), ids.take(MAX_IDS))

    /** Null bila teksnya rusak (mis. berkas diubah atau terpotong): antrean tersimpan diabaikan. */
    fun decodeIds(text: String?): List<String>? {
        if (text.isNullOrEmpty()) return null
        return runCatching { json.decodeFromString(ListSerializer(String.serializer()), text) }.getOrNull()
    }
}
