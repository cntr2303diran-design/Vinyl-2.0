package com.vinyl.launcher.drawer

/** Metadata satu notifikasi aktif. Hanya field ini yang dibaca; judul/isi notifikasi tidak pernah diakses. */
data class NotificationMeta(
    val packageName: String,
    /** Notifikasi berkelanjutan (pemutar musik, layanan latar) tidak dihitung sebagai "belum dibaca". */
    val ongoing: Boolean,
    /** Ringkasan grup menduplikasi anak-anaknya, jadi tidak dihitung. */
    val groupSummary: Boolean,
)

object BadgeCounter {
    const val MAX = 999

    fun count(list: List<NotificationMeta>): Map<String, Int> =
        list.asSequence()
            .filter { !it.ongoing && !it.groupSummary && it.packageName.isNotEmpty() }
            .groupingBy { it.packageName }
            .eachCount()
            .mapValues { (_, n) -> n.coerceAtMost(MAX) }

    /** Teks lencana: 1..99 apa adanya, selebihnya "99+". */
    fun format(n: Int): String = if (n > 99) "99+" else n.toString()
}
