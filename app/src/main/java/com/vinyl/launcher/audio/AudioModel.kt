package com.vinyl.launcher.audio

import kotlinx.serialization.Serializable
import kotlin.math.log10
import kotlin.math.roundToInt

/*
 * Model murni Audio Console (tanpa Android) agar aturan kejujurannya bisa diuji:
 *  - setiap nilai membawa SUMBER: terukur, dilaporkan sistem, perkiraan, atau tidak tersedia (dengan alasan);
 *  - perangkat yang TERDETEKSI dibedakan dari perangkat yang SEDANG menjadi keluaran;
 *  - format sumber dibedakan dari format keluaran.
 */

/** Jenis perangkat keluaran (dipetakan dari AudioDeviceInfo di lapisan Android). */
enum class DeviceKind { SPEAKER, EARPIECE, WIRED, BLUETOOTH, BLUETOOTH_CALL, BLE_AUDIO, USB, HDMI, HEARING_AID, OTHER }

/** Asal sebuah nilai di konsol. */
enum class Source { MEASURED, REPORTED, ESTIMATED, UNAVAILABLE }

/** Satu nilai beserta asalnya. [text] null hanya bila [source] = UNAVAILABLE; [note] menjelaskan asal/alasannya. */
data class Reading(val text: String?, val source: Source, val note: String) {
    companion object {
        fun reported(text: String, note: String = "Dilaporkan sistem") = Reading(text, Source.REPORTED, note)
        fun measured(text: String, note: String = "Terukur") = Reading(text, Source.MEASURED, note)
        fun estimated(text: String, note: String) = Reading(text, Source.ESTIMATED, note)
        fun unavailable(reason: String) = Reading(null, Source.UNAVAILABLE, reason)
    }

    /** Teks tampilan: nilai, atau "Tidak tersedia". */
    val display: String get() = text ?: "Tidak tersedia"

    /** Label sumber singkat untuk ditampilkan di samping nilai. */
    val sourceLabel: String get() = when (source) {
        Source.MEASURED -> "Terukur"
        Source.REPORTED -> "Dilaporkan sistem"
        Source.ESTIMATED -> "Perkiraan"
        Source.UNAVAILABLE -> "Tidak tersedia"
    }
}

/** Perangkat keluaran yang terdeteksi. [id] = AudioDeviceInfo.id (berubah tiap sambung ulang). */
data class OutputDevice(
    val id: Int,
    val kind: DeviceKind,
    val name: String,
    /** Alamat stabil (mis. MAC Bluetooth) bila sistem membukanya; dipakai untuk profil per perangkat. */
    val address: String = "",
    val sampleRates: List<Int> = emptyList(),
    val channelCounts: List<Int> = emptyList(),
) {
    /**
     * Kunci stabil untuk profil EQ per perangkat: jenis + sidik SHA-256 dari alamat (atau nama bila alamat kosong).
     * Alamat perangkat (mis. MAC Bluetooth) sendiri tidak pernah disimpan, jadi juga tidak ikut berkas cadangan.
     */
    val profileKey: String get() = "${kind.name}:" + fingerprint(address.ifBlank { name }.trim().lowercase())

    private companion object {
        fun fingerprint(s: String): String =
            java.security.MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).take(8).joinToString("") { "%02x".format(it) }
    }
}

/** Keadaan audio perangkat yang dibaca dari API publik (diisi AudioMonitor; murni agar console bisa diuji). */
data class AudioSnapshot(
    val devices: List<OutputDevice> = emptyList(),
    val inputs: List<String> = emptyList(),
    val active: ActiveOutput = ActiveOutput(null, Reading.unavailable("Belum dibaca")),
    /** Sample rate keluaran bawaan sistem (bukan format lagu, bukan jaminan format aktif). */
    val nativeRate: Reading = Reading.unavailable("Belum dibaca"),
    val latency: Reading = Reading.unavailable("Belum dibaca"),
    /** Kemampuan perangkat keluaran aktif: sample rate & format yang DIDUKUNG. */
    val capability: Reading = Reading.unavailable("Belum dibaca"),
    /** Pemutaran bit-perfect untuk USB (Android 14+): didukung atau tidak. */
    val bitPerfect: Reading = Reading.unavailable("Belum dibaca"),
    val spatial: Reading = Reading.unavailable("Belum dibaca"),
    val headTracking: Reading = Reading.unavailable("Belum dibaca"),
    val musicActive: Boolean = false,
    /** Waktu (ms) keluaran aktif terakhir berganti; dipakai VFD untuk memprioritaskan nama keluaran baru. */
    val activeChangedAt: Long = Long.MIN_VALUE,
)

/** Keluaran aktif dan seberapa yakin kita. */
data class ActiveOutput(val device: OutputDevice?, val reading: Reading)

object AudioRoutes {
    fun label(kind: DeviceKind): String = when (kind) {
        DeviceKind.SPEAKER -> "Speaker perangkat"
        DeviceKind.EARPIECE -> "Speaker telepon"
        DeviceKind.WIRED -> "Headphone kabel"
        DeviceKind.BLUETOOTH -> "Bluetooth (A2DP)"
        DeviceKind.BLUETOOTH_CALL -> "Bluetooth (panggilan)"
        DeviceKind.BLE_AUDIO -> "Bluetooth LE Audio"
        DeviceKind.USB -> "USB (DAC/headset)"
        DeviceKind.HDMI -> "HDMI"
        DeviceKind.HEARING_AID -> "Alat bantu dengar"
        DeviceKind.OTHER -> "Lainnya"
    }

    /** Urutan dugaan keluaran media bila sistem tidak melaporkan rutenya (Android 10–12). */
    private fun guessRank(k: DeviceKind): Int = when (k) {
        DeviceKind.BLE_AUDIO, DeviceKind.BLUETOOTH, DeviceKind.HEARING_AID -> 0
        DeviceKind.USB -> 1
        DeviceKind.WIRED -> 2
        DeviceKind.HDMI -> 3
        DeviceKind.SPEAKER -> 4
        DeviceKind.BLUETOOTH_CALL, DeviceKind.EARPIECE, DeviceKind.OTHER -> 9
    }

    /**
     * Keluaran media aktif. [routedIds] = id perangkat yang dilaporkan sistem untuk audio media (Android 13+), atau null
     * bila sistem tidak menyediakannya — maka dipakai dugaan berdasarkan jenis, berlabel "Perkiraan".
     */
    fun active(devices: List<OutputDevice>, routedIds: List<Int>?): ActiveOutput {
        if (routedIds != null) {
            val d = routedIds.firstNotNullOfOrNull { id -> devices.firstOrNull { it.id == id } }
            return if (d != null) ActiveOutput(d, Reading.reported(d.name.ifBlank { label(d.kind) }, "Rute media dilaporkan sistem"))
            else ActiveOutput(null, Reading.unavailable("Sistem tidak melaporkan perangkat keluaran untuk media"))
        }
        val guess = devices.filter { guessRank(it.kind) < 9 }.minByOrNull { guessRank(it.kind) }
            ?: return ActiveOutput(null, Reading.unavailable("Tidak ada perangkat keluaran media yang terdeteksi"))
        return ActiveOutput(
            guess,
            Reading.estimated(guess.name.ifBlank { label(guess.kind) }, "Perkiraan: Android versi ini tidak melaporkan rute media"),
        )
    }

    /** Teks sample rate, mis. "48 kHz" / "44,1 kHz". */
    fun khz(hz: Int): String {
        val k = hz / 1000.0
        return if (hz % 1000 == 0) "${hz / 1000} kHz" else "%.1f kHz".format(java.util.Locale.forLanguageTag("id-ID"), k)
    }

    /**
     * Perkiraan latensi buffer keluaran dari ukuran buffer & sample rate yang dilaporkan sistem. Ini BATAS BAWAH (satu
     * buffer), bukan latensi ujung-ke-ujung; label menyatakannya.
     */
    fun bufferLatency(framesPerBuffer: Int?, sampleRate: Int?): Reading {
        if (framesPerBuffer == null || sampleRate == null || framesPerBuffer <= 0 || sampleRate <= 0) {
            return Reading.unavailable("Ukuran buffer atau sample rate tidak dilaporkan")
        }
        val ms = framesPerBuffer * 1000.0 / sampleRate
        return Reading.estimated("≥ %.1f ms".format(java.util.Locale.forLanguageTag("id-ID"), ms), "Satu buffer keluaran (bukan latensi ujung-ke-ujung)")
    }
}

/**
 * Bentuk EQ untuk aplikasi lain: 3 pita sederhana (dengan preset & profil perangkat), 10 pita grafis, atau profil
 * parametrik (mis. impor AutoEQ) yang didekati dengan 31 pita 1/3 oktaf DynamicsProcessing.
 */
@Serializable
enum class EqMode { THREE, GRAPHIC10, PROFILE }

/** EQ tiga pita + preamp + balance. Nilai dalam dB; balance −1 (kiri) .. 1 (kanan). */
@Serializable
data class EqValues(
    val lowDb: Float = 0f,
    val midDb: Float = 0f,
    val highDb: Float = 0f,
    val preampDb: Float = 0f,
    val balance: Float = 0f,
)

object Eq {
    const val MIN_DB = -12f
    const val MAX_DB = 12f
    const val PREAMP_MIN_DB = -12f
    const val PREAMP_MAX_DB = 6f
    /** Frekuensi potong atas tiap pita (Hz): rendah ≤ 250, tengah ≤ 4000, tinggi ≤ 20000. */
    val CUTOFFS = floatArrayOf(250f, 4000f, 20000f)
    val BAND_NAMES = listOf("Rendah", "Tengah", "Tinggi")

    fun clamp(v: EqValues): EqValues {
        fun db(x: Float, lo: Float, hi: Float) = if (x.isNaN()) 0f else x.coerceIn(lo, hi)
        return EqValues(
            db(v.lowDb, MIN_DB, MAX_DB), db(v.midDb, MIN_DB, MAX_DB), db(v.highDb, MIN_DB, MAX_DB),
            db(v.preampDb, PREAMP_MIN_DB, PREAMP_MAX_DB), if (v.balance.isNaN()) 0f else v.balance.coerceIn(-1f, 1f),
        )
    }

    /**
     * Penguatan masukan per kanal (dB) dari preamp & balance. Balance hanya MELEMAHKAN kanal di sisi seberang (tidak
     * pernah menguatkan), jadi tidak menambah risiko clipping. Balance penuh ke satu sisi = kanal lain senyap (−60 dB).
     */
    fun channelGains(v: EqValues): Pair<Float, Float> {
        val c = clamp(v)
        fun atten(level: Float): Float = if (level <= 0.001f) -60f else (20 * log10(level.toDouble())).toFloat().coerceAtLeast(-60f)
        val left = c.preampDb + if (c.balance > 0f) atten(1f - c.balance) else 0f
        val right = c.preampDb + if (c.balance < 0f) atten(1f + c.balance) else 0f
        return left to right
    }

    /**
     * Penguatan masukan yang benar-benar dipasang ke efek. Dengan [safeHeadroom], masukan diturunkan sebesar penguatan
     * pita terbesar agar penguatan EQ tidak membuat sinyal melewati 0 dBFS (clipping).
     */
    fun inputGains(v: EqValues, safeHeadroom: Boolean): Pair<Float, Float> {
        val (l, r) = channelGains(v)
        val drop = if (safeHeadroom) bands(v).maxOrNull()?.coerceAtLeast(0f) ?: 0f else 0f
        return (l - drop) to (r - drop)
    }

    fun bands(v: EqValues): FloatArray = clamp(v).let { floatArrayOf(it.lowDb, it.midDb, it.highDb) }

    /** Pusat 10 pita EQ grafis (oktaf). */
    val GRAPHIC_FREQS = floatArrayOf(31f, 62f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 16000f)
    val GRAPHIC_LABELS = listOf("31", "62", "125", "250", "500", "1k", "2k", "4k", "8k", "16k")

    /** 31 pita 1/3 oktaf untuk DynamicsProcessing: frekuensi pusat (standar ISO) dan batas atas tiap pita. */
    val DP_CENTERS = floatArrayOf(
        20f, 25f, 31.5f, 40f, 50f, 63f, 80f, 100f, 125f, 160f, 200f, 250f, 315f, 400f, 500f, 630f,
        800f, 1000f, 1250f, 1600f, 2000f, 2500f, 3150f, 4000f, 5000f, 6300f, 8000f, 10000f, 12500f, 16000f, 20000f,
    )
    val DP_CUTOFFS = FloatArray(DP_CENTERS.size) { i ->
        if (i == DP_CENTERS.lastIndex) 20_000f else kotlin.math.sqrt(DP_CENTERS[i] * DP_CENTERS[i + 1])
    }

    /** Nilai 10 pita grafis dibersihkan: tepat 10 nilai, masing-masing di ±12 dB. */
    fun cleanGraphic(g: List<Float>): List<Float> = List(GRAPHIC_FREQS.size) { i ->
        g.getOrNull(i)?.takeIf { !it.isNaN() }?.coerceIn(MIN_DB, MAX_DB) ?: 0f
    }

    /** Kurva 10 pita di [f]: interpolasi linear terhadap log frekuensi, datar di luar ujung. */
    fun graphicAt(graphic: List<Float>, f: Float): Float {
        val g = cleanGraphic(graphic)
        val x = f.coerceIn(GRAPHIC_FREQS.first(), GRAPHIC_FREQS.last())
        val i = GRAPHIC_FREQS.indexOfLast { it <= x }.coerceIn(0, GRAPHIC_FREQS.size - 2)
        val f0 = GRAPHIC_FREQS[i]
        val f1 = GRAPHIC_FREQS[i + 1]
        val t = (kotlin.math.ln(x / f0) / kotlin.math.ln(f1 / f0)).coerceIn(0f, 1f)
        return g[i] + (g[i + 1] - g[i]) * t
    }

    /** Penguatan EQ (dB) pada frekuensi [f] untuk mode yang dipilih. */
    fun gainAt(s: com.vinyl.launcher.data.AudioConsoleSettings, f: Float): Float = when (s.eqMode) {
        EqMode.THREE -> {
            val b = bands(s.values)
            if (f <= CUTOFFS[0]) b[0] else if (f <= CUTOFFS[1]) b[1] else b[2]
        }
        EqMode.GRAPHIC10 -> graphicAt(s.graphic, f)
        EqMode.PROFILE -> s.profile?.let {
            com.vinyl.launcher.audio.dsp.EqMath.responseDb(
                com.vinyl.launcher.audio.dsp.ParametricProfile.clamp(it).bands.filter { b -> b.enabled }, f.toDouble(),
            ).toFloat().coerceIn(-24f, 24f)
        } ?: 0f
    }

    /**
     * Penguatan tiap pita DynamicsProcessing dengan batas atas [cutoffs]: nilai kurva di pusat geometris pita
     * (pita pertama mulai 10 Hz).
     */
    fun bandGains(s: com.vinyl.launcher.data.AudioConsoleSettings, cutoffs: FloatArray): FloatArray = FloatArray(cutoffs.size) { i ->
        val lo = if (i == 0) 10f else cutoffs[i - 1]
        gainAt(s, kotlin.math.sqrt(lo * cutoffs[i]))
    }

    /**
     * Penguatan masukan per kanal untuk [gains] yang dipasang: preamp (3/10 pita: preamp konsol; profil: preamp profil
     * ditambah preamp konsol), balance, dan pengaman clipping sebesar penguatan pita terbesar.
     */
    fun inputGainsFor(s: com.vinyl.launcher.data.AudioConsoleSettings, gains: FloatArray): Pair<Float, Float> {
        val (l, r) = channelGains(s.values)
        val profilePre = if (s.eqMode == EqMode.PROFILE) s.profile?.preampDb?.takeIf { !it.isNaN() }?.coerceIn(-30f, 12f) ?: 0f else 0f
        // Profil AutoEQ sudah membawa preamp-nya sendiri sebagai headroom; pengaman hanya menambah yang masih kurang
        val maxBoost = gains.maxOrNull()?.coerceAtLeast(0f) ?: 0f
        val drop = if (!s.safeHeadroom) 0f else (maxBoost + minOf(profilePre, 0f)).coerceAtLeast(0f)
        return (l + profilePre - drop) to (r + profilePre - drop)
    }

    fun isFlat(v: EqValues): Boolean = clamp(v) == EqValues()

    /** Teks dB untuk UI, mis. "+3 dB" / "−4,5 dB". */
    fun dbText(db: Float): String {
        val r = (db * 2).roundToInt() / 2f
        val sign = if (r > 0) "+" else if (r < 0) "−" else ""
        val abs = kotlin.math.abs(r)
        val num = if (abs % 1f == 0f) abs.toInt().toString() else abs.toString().replace('.', ',')
        return "$sign$num dB"
    }

    /** Preset bawaan (tidak bisa dihapus). */
    val BUILT_IN: List<Pair<String, EqValues>> = listOf(
        "Datar" to EqValues(),
        "Bass hangat" to EqValues(lowDb = 5f, midDb = 0f, highDb = -1f, preampDb = -4f),
        "Vokal jelas" to EqValues(lowDb = -2f, midDb = 3f, highDb = 1f, preampDb = -2f),
        "Treble cerah" to EqValues(lowDb = 0f, midDb = 0f, highDb = 4f, preampDb = -3f),
        "Headphone kabel" to EqValues(lowDb = 2f, midDb = -1f, highDb = 2f, preampDb = -2f),
        "Earphone Bluetooth" to EqValues(lowDb = 3f, midDb = 0f, highDb = -2f, preampDb = -3f),
    )
}

/** Aturan otomasi dan hasil terakhirnya. */
object Automation {
    enum class Outcome { DONE, FAILED, NOT_ALLOWED, SKIPPED }

    data class Result(val rule: String, val outcome: Outcome, val detail: String, val atMs: Long)

    /** Preset yang harus dipakai saat [device] menjadi keluaran: profil perangkat itu, atau null (biarkan). */
    fun presetFor(device: OutputDevice?, profiles: Map<String, String>): String? = device?.let { profiles[it.profileKey] }

    /**
     * Keputusan saat keluaran aktif berubah dari [before] ke [after]:
     *  - perangkat baru punya profil → pakai presetnya (simpan preset sebelumnya untuk dipulihkan);
     *  - perangkat berprofil terputus dan [restoreOnDisconnect] → kembalikan preset sebelumnya.
     * @return preset baru yang harus diterapkan, atau null bila tidak ada perubahan.
     */
    fun onOutputChanged(
        before: OutputDevice?,
        after: OutputDevice?,
        profiles: Map<String, String>,
        current: String?,
        previous: String?,
        restoreOnDisconnect: Boolean,
    ): Decision {
        if (before?.profileKey == after?.profileKey) return Decision(null, previous, "Keluaran tidak berubah")
        val target = presetFor(after, profiles)
        if (target != null) {
            val keep = if (presetFor(before, profiles) != null) previous else current
            return Decision(target, keep, "Profil ${after?.name.orEmpty()} diterapkan")
        }
        if (restoreOnDisconnect && presetFor(before, profiles) != null && previous != null) {
            return Decision(previous, null, "Preset sebelumnya dipulihkan")
        }
        return Decision(null, previous, "Tidak ada aturan untuk perangkat ini")
    }

    /** [apply] preset yang harus diterapkan (null = tidak ada), [previous] preset yang diingat untuk dipulihkan. */
    data class Decision(val apply: String?, val previous: String?, val reason: String)

    /** True bila perangkat [d] adalah USB DAC/headset (aturan "USB tersambung → buka panel Devices"). */
    fun isUsbDac(d: OutputDevice?): Boolean = d?.kind == DeviceKind.USB
}
