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

    /** Baterai perangkat Bluetooth aktif: hanya lewat Akses lanjutan; untuk keluaran non-Bluetooth dijelaskan. */
    fun battery(s: AudioSnapshot, adv: AdvancedReadings): Reading =
        if (!isBluetooth(s.active.device?.kind)) Reading.unavailable("Keluaran aktif bukan perangkat Bluetooth") else adv.battery

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
        SessionOrigin.GLOBAL -> "keluaran gabungan, eksperimental"
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
            val names = active.joinToString(", ") { it.packageName.ifBlank { "sesi ${it.id}" } }
            val experimental = active.any { it.origin == SessionOrigin.GLOBAL }
            return Reading.reported(
                "Memproses ${active.size} sesi",
                "Sesi: $names" + if (experimental) ". Efek keluaran gabungan bersifat eksperimental: belum tentu terdengar di perangkat ini" else "",
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
