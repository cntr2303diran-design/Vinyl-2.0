package com.vinyl.launcher.audio

/*
 * Model murni Audio Console: isi panel (baris nilai + sumbernya) dan daftar sesi audio yang diproses EQ.
 * Lapisan Android (AudioMonitor, EqEngine) hanya mengisi data; semua keputusan teks & kejujuran ada di sini dan diuji.
 */

/** Satu baris panel: label dan nilai beserta sumbernya. */
data class ConsoleRow(val label: String, val reading: Reading)

/** Satu perangkat di panel Perangkat. [active] = sedang menjadi keluaran media (bukan sekadar terdeteksi). */
data class DeviceRow(val name: String, val kind: String, val active: Boolean, val status: String)

/**
 * Nilai yang hanya bisa dibaca lewat Akses lanjutan (laporan diagnostik sistem). Tanpa akses itu semuanya
 * "Tidak tersedia" dengan alasan yang sama, bukan tebakan.
 */
data class AdvancedReadings(
    val codec: Reading = NEEDS_ADVANCED,
    val outputFormat: Reading = NEEDS_ADVANCED,
    val resampling: Reading = NEEDS_ADVANCED,
    val battery: Reading = NEEDS_ADVANCED,
) {
    companion object {
        val NEEDS_ADVANCED = Reading.unavailable("Butuh Akses lanjutan (laporan diagnostik sistem)")
    }
}

object ConsoleRows {
    private fun isBluetooth(k: DeviceKind?) = k == DeviceKind.BLUETOOTH || k == DeviceKind.BLE_AUDIO || k == DeviceKind.HEARING_AID

    /** Perangkat panggilan (earpiece, SCO) tidak relevan untuk musik; disembunyikan agar daftar tidak membingungkan. */
    private fun forMusic(k: DeviceKind) = k != DeviceKind.EARPIECE && k != DeviceKind.BLUETOOTH_CALL

    fun devices(s: AudioSnapshot): List<DeviceRow> {
        val activeKey = s.active.device?.let { it.kind to it.name }
        return s.devices
            .filter { forMusic(it.kind) }
            .distinctBy { it.kind to it.name }
            .map { d ->
                val active = activeKey == (d.kind to d.name)
                DeviceRow(
                    name = d.name,
                    kind = AudioRoutes.label(d.kind),
                    active = active,
                    status = when {
                        !active -> "Terdeteksi"
                        s.active.reading.source == Source.ESTIMATED -> "Keluaran media (perkiraan)"
                        else -> "Keluaran media"
                    },
                )
            }
            .sortedByDescending { it.active }
    }

    /**
     * Baterai perangkat Bluetooth aktif. Sumber, dari yang lebih diutamakan: laporan diagnostik (Akses lanjutan) bila
     * memuatnya, lalu siaran baterai Bluetooth sistem (jalur tidak resmi, butuh izin Perangkat sekitar) yang dicocokkan
     * dengan ALAMAT perangkat aktif — tidak pernah ditebak dari perangkat lain.
     */
    fun battery(
        s: AudioSnapshot,
        adv: AdvancedReadings,
        permissionGranted: Boolean = false,
        reports: Map<String, Int> = emptyMap(),
        remembered: Map<String, RememberedBattery> = emptyMap(),
        nowMs: Long = 0L,
    ): Reading {
        val d = s.active.device
        if (!isBluetooth(d?.kind)) return Reading.unavailable("Keluaran aktif bukan perangkat Bluetooth")
        if (adv.battery.source != Source.UNAVAILABLE) return adv.battery
        val address = d?.address?.takeIf { it.isNotBlank() }
        val level = address?.let { reports[BatteryReports.key(it)] }
        val last = address?.let { remembered[BatteryReports.storeKey(it)] }?.takeIf { nowMs - it.atMs in 0..BatteryReports.MAX_AGE_MS }
        return when {
            level != null -> Reading.reported("$level%", "Dilaporkan sistem Bluetooth lewat siaran tidak resmi; diperbarui saat levelnya berubah")
            last != null -> Reading.estimated(
                "≈ ${last.level}%",
                "Laporan terakhir ${BatteryReports.ageText(last.atMs, nowMs)}; sistem baru melapor lagi saat levelnya berubah",
            )
            !permissionGranted -> Reading.unavailable("Butuh izin Perangkat sekitar (Bluetooth)")
            d?.address.isNullOrBlank() -> Reading.unavailable("Sistem tidak membuka alamat perangkat ini, jadi laporan baterai tidak bisa dicocokkan")
            else -> Reading.unavailable("Belum ada laporan. Sistem mengirimnya saat perangkat tersambung atau levelnya berubah")
        }
    }

    fun quality(s: AudioSnapshot, adv: AdvancedReadings): List<ConsoleRow> {
        val bt = isBluetooth(s.active.device?.kind)
        return listOf(
            // Sesi media Android tidak membawa format file; tidak ditebak dari judul atau aplikasi
            ConsoleRow("Format sumber", Reading.unavailable("Aplikasi musik tidak membagikan format file lewat sesi media")),
            ConsoleRow("Sample rate sistem", s.nativeRate),
            ConsoleRow("Format keluaran", adv.outputFormat),
            ConsoleRow("Kemampuan keluaran", s.capability),
            ConsoleRow("Codec Bluetooth", if (bt) adv.codec else Reading.unavailable("Keluaran aktif bukan Bluetooth")),
            ConsoleRow("Resampling", adv.resampling),
            ConsoleRow("Bit-perfect (USB)", s.bitPerfect),
        )
    }

    fun spatial(s: AudioSnapshot): List<ConsoleRow> = listOf(
        ConsoleRow("Audio spasial", s.spatial),
        ConsoleRow("Pelacakan kepala", s.headTracking),
    )

    fun diagnostics(s: AudioSnapshot): List<ConsoleRow> {
        val usb = s.devices.firstOrNull { it.kind == DeviceKind.USB }
        return listOf(
            ConsoleRow(
                "Jalur media",
                s.active.device?.let { d ->
                    Reading(AudioRoutes.label(d.kind) + " · " + d.name, s.active.reading.source, s.active.reading.note)
                } ?: s.active.reading,
            ),
            ConsoleRow(
                "Perangkat masukan",
                if (s.inputs.isEmpty()) Reading.unavailable("Tidak ada yang dilaporkan sistem") else Reading.reported(s.inputs.joinToString(", ")),
            ),
            ConsoleRow("Musik berbunyi", Reading.reported(if (s.musicActive) "Ya" else "Tidak", "Dilaporkan sistem (semua aplikasi)")),
            ConsoleRow("Latensi", s.latency),
            ConsoleRow(
                "USB DAC",
                if (usb == null) Reading.unavailable("Tidak ada perangkat audio USB tersambung")
                else Reading.reported(
                    usb.name + if (usb.sampleRates.isNotEmpty()) " · " + usb.sampleRates.filter { it > 0 }.sorted().joinToString("/") { AudioRoutes.khz(it) } else "",
                    "Terdeteksi sistem; kemampuan, bukan format yang sedang dipakai",
                ),
            ),
        )
    }
}

/** Level baterai terakhir yang pernah dilaporkan untuk satu perangkat, dan kapan. */
data class RememberedBattery(val level: Int, val atMs: Long)

/** Laporan baterai Bluetooth per alamat perangkat (dari siaran sistem). Murni agar validasinya diuji. */
object BatteryReports {
    const val MAX_DEVICES = 16
    /** Laporan lebih tua dari ini tidak ditampilkan lagi. */
    const val MAX_AGE_MS = 3L * 24 * 60 * 60 * 1000

    fun key(address: String): String = address.trim().uppercase()

    /** Kunci simpan: 16 karakter awal SHA-256 alamat — alamat Bluetooth asli tidak pernah ditulis ke penyimpanan. */
    fun storeKey(address: String): String =
        java.security.MessageDigest.getInstance("SHA-256").digest(key(address).toByteArray()).take(8).joinToString("") { "%02x".format(it) }

    /** "baru saja", "12 menit lalu", "3 jam lalu", "2 hari lalu". */
    fun ageText(atMs: Long, nowMs: Long): String {
        val m = ((nowMs - atMs) / 60_000L).coerceAtLeast(0L)
        return when {
            m < 1 -> "baru saja"
            m < 60 -> "$m menit lalu"
            m < 24 * 60 -> "${m / 60} jam lalu"
            else -> "${m / (24 * 60)} hari lalu"
        }
    }

    /** Simpan ke teks "kunci=level@waktu;…" (paling banyak [MAX_DEVICES], yang terbaru). */
    fun encode(map: Map<String, RememberedBattery>): String =
        map.entries.sortedByDescending { it.value.atMs }.take(MAX_DEVICES).joinToString(";") { (k, v) -> "$k=${v.level}@${v.atMs}" }

    /** Kebalikan [encode]; entri rusak atau berkunci aneh dibuang. */
    fun decode(text: String?): Map<String, RememberedBattery> {
        if (text.isNullOrBlank() || text.length > 4096) return emptyMap()
        return text.split(';').mapNotNull { e ->
            val k = e.substringBefore('=', "")
            val level = e.substringAfter('=', "").substringBefore('@').toIntOrNull()
            val at = e.substringAfter('@', "").toLongOrNull()
            if (!Regex("^[0-9a-f]{16}$").matches(k) || level == null || level !in 0..100 || at == null || at <= 0) null
            else k to RememberedBattery(level, at)
        }.take(MAX_DEVICES).toMap()
    }

    /** Terima laporan hanya bila level 0..100 dan alamatnya berbentuk alamat Bluetooth; selain itu abaikan. */
    fun accept(current: Map<String, Int>, address: String?, level: Int): Map<String, Int> {
        val a = address?.let(::key) ?: return current
        if (level !in 0..100 || !Regex("^[0-9A-F]{2}(:[0-9A-F]{2}){5}$").matches(a)) return current
        val next = current - a + (a to level)
        return if (next.size > MAX_DEVICES) next.entries.drop(next.size - MAX_DEVICES).associate { it.key to it.value } else next
    }
}

/** Asal sesi audio yang diproses EQ, dari yang paling andal. */
enum class SessionOrigin { APP, DETECTED, GLOBAL }

/** Keadaan efek di satu sesi. NO_CONTROL = efek terpasang tetapi dikendalikan aplikasi lain berprioritas lebih tinggi. */
enum class SessionState { PENDING, ACTIVE, NO_CONTROL, FAILED }

data class EqSession(
    val id: Int,
    val packageName: String,
    val origin: SessionOrigin,
    val state: SessionState = SessionState.PENDING,
    val detail: String = "",
)

object EqSessions {
    /** Batas sesi yang dilacak (siaran dari aplikasi lain tidak boleh membuat daftar tumbuh tanpa batas). */
    const val MAX = 16
    const val MAX_PACKAGE = 200

    fun originLabel(o: SessionOrigin): String = when (o) {
        SessionOrigin.APP -> "dibuka aplikasi musik"
        SessionOrigin.DETECTED -> "terdeteksi lewat Akses lanjutan"
        SessionOrigin.GLOBAL -> "keluaran gabungan (sesi 0)"
    }

    /**
     * Tambah sesi. Sesi 0 hanya sah untuk [SessionOrigin.GLOBAL]; id negatif ditolak. Sesi yang sama dari sumber yang lebih
     * andal (APP) menggantikan yang kurang andal. Bila penuh, sesi terlama dibuang.
     */
    fun open(list: List<EqSession>, id: Int, packageName: String, origin: SessionOrigin): List<EqSession> {
        if (id < 0 || (id == 0) != (origin == SessionOrigin.GLOBAL)) return list
        val pkg = packageName.take(MAX_PACKAGE)
        val existing = list.firstOrNull { it.id == id }
        if (existing != null) {
            return if (origin.ordinal < existing.origin.ordinal) list.map { if (it.id == id) it.copy(packageName = pkg, origin = origin) else it } else list
        }
        return (list + EqSession(id, pkg, origin)).takeLast(MAX)
    }

    fun close(list: List<EqSession>, id: Int): List<EqSession> = list.filterNot { it.id == id }

    fun withState(list: List<EqSession>, id: Int, state: SessionState, detail: String = ""): List<EqSession> =
        list.map { if (it.id == id) it.copy(state = state, detail = detail) else it }

    /** Ringkasan untuk panel Suara: sesi mana yang benar-benar sedang diproses, atau mengapa tidak ada. */
    fun summary(enabled: Boolean, list: List<EqSession>): Reading {
        if (!enabled) return Reading.unavailable("EQ mati")
        val active = list.filter { it.state == SessionState.ACTIVE }
        if (active.isNotEmpty()) {
            val global = active.any { it.origin == SessionOrigin.GLOBAL }
            val names = active.joinToString(", ") { if (it.origin == SessionOrigin.GLOBAL) "semua audio (sesi 0)" else it.packageName.ifBlank { "sesi ${it.id}" } }
            return Reading.reported(
                "Memproses ${active.size} sesi",
                "Sesi: $names" + if (global) ". Keluaran gabungan adalah cara lama Android: di sebagian perangkat tidak terdengar; " +
                    "putar lagu di aplikasi yang membuka sesi efek, atau pakai pemutar bawaan, untuk EQ yang pasti bekerja" else "",
            )
        }
        if (list.any { it.state == SessionState.NO_CONTROL }) {
            return Reading.unavailable("Sesi dikendalikan aplikasi efek lain (mis. equalizer lain atau efek bawaan); matikan itu dulu")
        }
        if (list.any { it.state == SessionState.FAILED }) {
            return Reading.unavailable("Sesi ditemukan tetapi sistem menolak efek: " + list.first { it.state == SessionState.FAILED }.detail)
        }
        if (list.isNotEmpty()) return Reading.unavailable("Memasang efek…")
        return Reading.unavailable(
            "Belum ada sesi. Putar lagu di aplikasi musik yang membuka sesi efek untuk equalizer sistem; tidak semua aplikasi melakukannya",
        )
    }
}
