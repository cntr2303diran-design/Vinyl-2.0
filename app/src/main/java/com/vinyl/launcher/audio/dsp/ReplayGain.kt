package com.vinyl.launcher.audio.dsp

import kotlinx.serialization.Serializable
import kotlin.math.log10
import kotlin.math.min
import kotlin.math.pow

/**
 * ReplayGain: menyamakan kenyaringan antarlagu memakai tag yang sudah ada di berkas (REPLAYGAIN_* di FLAC/Ogg/MP3/M4A,
 * R128_* di Opus). Tidak menganalisis audio; berkas tanpa tag memakai [ReplayGainSettings.untaggedDb].
 */
@Serializable
enum class ReplayGainMode { OFF, TRACK, ALBUM }

@Serializable
data class ReplayGainSettings(
    val mode: ReplayGainMode = ReplayGainMode.TRACK,
    /** Tambahan untuk berkas bertag (standar ReplayGain = 89 dB SPL; banyak orang memakai +0..+6). */
    val preampDb: Float = 0f,
    /** Penguatan untuk berkas tanpa tag (biasanya negatif agar tidak lebih keras dari lagu bertag). */
    val untaggedDb: Float = -6f,
    /** Batasi penguatan dengan puncak tag agar tidak clipping. */
    val preventClipping: Boolean = true,
)

data class ReplayGainTags(
    val trackGainDb: Float? = null,
    val trackPeak: Float? = null,
    val albumGainDb: Float? = null,
    val albumPeak: Float? = null,
) {
    val isEmpty: Boolean get() = trackGainDb == null && albumGainDb == null
}

object ReplayGain {
    /** Dari pasangan kunci→nilai tag (VorbisComment, TXXX ID3, atom iTunes); kunci tidak peka huruf besar. */
    fun fromTags(pairs: List<Pair<String, String>>): ReplayGainTags {
        var tg: Float? = null; var tp: Float? = null; var ag: Float? = null; var ap: Float? = null
        var r128Track: Float? = null; var r128Album: Float? = null
        for ((rawKey, value) in pairs) {
            when (rawKey.trim().uppercase()) {
                "REPLAYGAIN_TRACK_GAIN" -> tg = tg ?: parseGain(value)
                "REPLAYGAIN_TRACK_PEAK" -> tp = tp ?: parsePeak(value)
                "REPLAYGAIN_ALBUM_GAIN" -> ag = ag ?: parseGain(value)
                "REPLAYGAIN_ALBUM_PEAK" -> ap = ap ?: parsePeak(value)
                "R128_TRACK_GAIN" -> r128Track = r128Track ?: parseR128(value)
                "R128_ALBUM_GAIN" -> r128Album = r128Album ?: parseR128(value)
            }
        }
        return ReplayGainTags(tg ?: r128Track, tp, ag ?: r128Album, ap)
    }

    /** "−7.21 dB", "-7,21 dB", "+1.5" → dB; nilai di luar ±60 dB dianggap rusak. */
    fun parseGain(raw: String): Float? {
        val t = raw.trim().replace('−', '-').replace(',', '.').removeSuffix("dB").removeSuffix("db").removeSuffix("DB").trim()
        val v = t.toFloatOrNull() ?: return null
        return v.takeIf { !it.isNaN() && it in -60f..60f }
    }

    /** Puncak linear (1,0 = 0 dBFS); di luar 0..10 dianggap rusak. */
    fun parsePeak(raw: String): Float? {
        val v = raw.trim().replace(',', '.').toFloatOrNull() ?: return null
        return v.takeIf { !it.isNaN() && it > 0f && it <= 10f }
    }

    /**
     * R128 (Opus): bilangan bulat Q7.8 relatif −23 LUFS. ReplayGain memakai acuan ±−18 LUFS, jadi +5 dB agar setara
     * dengan lagu bertag ReplayGain.
     */
    fun parseR128(raw: String): Float? {
        val q = raw.trim().toIntOrNull() ?: return null
        val db = q / 256f + 5f
        return db.takeIf { it in -60f..60f }
    }

    /**
     * Penguatan (dB) untuk satu lagu. Mode ALBUM memakai nilai album bila ada (lalu lagu), TRACK sebaliknya. Dengan
     * [ReplayGainSettings.preventClipping], penguatan dibatasi agar puncak × penguatan ≤ 1.
     */
    fun gainDb(tags: ReplayGainTags, s: ReplayGainSettings): Float {
        if (s.mode == ReplayGainMode.OFF) return 0f
        val (gain, peak) = when (s.mode) {
            ReplayGainMode.ALBUM -> (tags.albumGainDb ?: tags.trackGainDb) to (if (tags.albumGainDb != null) tags.albumPeak else tags.trackPeak)
            else -> (tags.trackGainDb ?: tags.albumGainDb) to (if (tags.trackGainDb != null) tags.trackPeak else tags.albumPeak)
        }
        if (gain == null) return s.untaggedDb.coerceIn(-30f, 12f)
        var db = gain + s.preampDb.coerceIn(-15f, 15f)
        if (s.preventClipping && peak != null && peak > 0f) {
            val maxDb = (-20.0 * log10(peak.toDouble())).toFloat()
            db = min(db, maxDb)
        }
        return db
    }

    fun linear(db: Float): Float = 10.0.pow(db / 20.0).toFloat()
}
