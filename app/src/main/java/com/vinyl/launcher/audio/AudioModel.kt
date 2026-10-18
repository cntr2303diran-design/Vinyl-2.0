package com.vinyl.launcher.audio

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
    /** Kunci stabil untuk profil EQ per perangkat: jenis + alamat (atau nama bila alamat kosong). */
    val profileKey: String get() = "${kind.name}:" + address.ifBlank { name }.trim().lowercase()
}

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

/** EQ tiga pita + preamp + balance. Nilai dalam dB; balance −1 (kiri) .. 1 (kanan). */
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

    fun bands(v: EqValues): FloatArray = clamp(v).let { floatArrayOf(it.lowDb, it.midDb, it.highDb) }

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
