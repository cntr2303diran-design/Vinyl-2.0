package com.vinyl.launcher.music

/**
 * Hasil mencari lirik. Tiga keadaan yang HARUS dibedakan:
 * - [Found]    : ada lirik;
 * - [NotFound] : server menjawab dan memang tidak punya lirik untuk lagu ini (boleh diingat sementara);
 * - [Failed]   : jaringan/server bermasalah, jadi jawabannya tidak diketahui (tidak boleh dianggap "tidak ada");
 * - [Disabled] : pengguna mematikan pencarian lirik online; tidak ada permintaan jaringan yang dibuat.
 * Dulu semuanya jatuh ke null dan disimpan permanen, sehingga sinyal buruk sesaat membuat sebuah lagu
 * "tidak punya lirik" sampai aplikasi dimulai ulang.
 */
sealed interface LyricsOutcome {
    class Found(val lyrics: Lyrics) : LyricsOutcome
    object NotFound : LyricsOutcome
    object Failed : LyricsOutcome
    object Disabled : LyricsOutcome
}

/**
 * Cache lirik di memori, aman lintas thread. Aturan per keadaan:
 * - [LyricsOutcome.Found]    : disimpan sampai terusir (LRU, [maxEntries]);
 * - [LyricsOutcome.NotFound] : disimpan [notFoundTtlMs] (lirik baru bisa muncul di server);
 * - [LyricsOutcome.Failed]   : hanya [failureCooldownMs] agar tampilan yang menggambar ulang tidak memalu server;
 *   [invalidate] (tombol "Coba lagi") menghapusnya seketika.
 */
class LyricsCache(
    private val now: () -> Long,
    private val maxEntries: Int = 60,
    private val notFoundTtlMs: Long = 6 * 60 * 60_000L,
    private val failureCooldownMs: Long = 20_000L,
) {
    private class Entry(val outcome: LyricsOutcome, val expiresAt: Long)

    private val map = object : LinkedHashMap<String, Entry>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>?) = size > maxEntries
    }

    /** Hasil tersimpan yang masih berlaku, atau null bila harus bertanya ke server. */
    fun get(key: String): LyricsOutcome? = synchronized(map) {
        val e = map[key] ?: return null
        if (now() >= e.expiresAt) { map.remove(key); return null }
        e.outcome
    }

    fun put(key: String, outcome: LyricsOutcome) {
        val ttl = when (outcome) {
            is LyricsOutcome.Found -> Long.MAX_VALUE
            LyricsOutcome.NotFound -> notFoundTtlMs
            LyricsOutcome.Failed -> failureCooldownMs
            LyricsOutcome.Disabled -> return      // keadaan pengaturan, bukan jawaban server: tidak untuk disimpan
        }
        val t = now()
        val expires = if (ttl == Long.MAX_VALUE || t > Long.MAX_VALUE - ttl) Long.MAX_VALUE else t + ttl
        synchronized(map) { map[key] = Entry(outcome, expires) }
    }

    fun invalidate(key: String) { synchronized(map) { map.remove(key) } }

    fun clear() { synchronized(map) { map.clear() } }

    val size: Int get() = synchronized(map) { map.size }

    companion object {
        /** Kunci tidak peka huruf besar/kecil dan spasi, supaya "Song " dan "song" memakai hasil yang sama. */
        fun key(title: String, artist: String): String =
            "${title.trim().lowercase()}|${artist.trim().lowercase()}"
    }
}
