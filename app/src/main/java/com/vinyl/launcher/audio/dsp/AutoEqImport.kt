package com.vinyl.launcher.audio.dsp

import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Impor berkas "ParametricEQ.txt" (AutoEQ / Equalizer APO), mis.:
 *
 *     Preamp: -6.2 dB
 *     Filter 1: ON LSC Fc 105 Hz Gain 6.0 dB Q 0.70
 *     Filter 2: ON PK Fc 2245 Hz Gain -3.4 dB Q 1.29
 *
 * Jenis: PK/PEQ (peaking), LS/LSC (low shelf), HS/HSC (high shelf), LP/LPQ, HP/HPQ. Lebar pita boleh ditulis "Q x" atau
 * "BW Oct x". Baris yang tidak dikenal dilaporkan, bukan ditebak.
 */
object AutoEqImport {
    data class Result(val profile: ParametricProfile, val skipped: List<String>)

    /** Batas ukuran teks yang mau dibaca (berkas AutoEQ < 2 KB). */
    const val MAX_CHARS = 32_000

    fun parse(text: String, name: String): Result? {
        if (text.length > MAX_CHARS) return null
        var preamp = 0f
        val bands = mutableListOf<EqBand>()
        val skipped = mutableListOf<String>()
        for (raw in text.lineSequence()) {
            val line = raw.substringBefore('#').trim()
            if (line.isEmpty()) continue
            val lower = line.lowercase()
            when {
                lower.startsWith("preamp:") -> {
                    val v = number(line.substringAfter(':').trim().split(Regex("\\s+")).firstOrNull())
                    if (v != null && v in -30f..30f) preamp = v else skipped += line
                }
                lower.startsWith("filter") -> {
                    val band = parseFilter(line.substringAfter(':', "").trim())
                    if (band != null) bands += band else skipped += line
                }
                else -> skipped += line
            }
        }
        if (bands.isEmpty()) return null
        val profile = ParametricProfile.clamp(
            ParametricProfile(name = name, preampDb = preamp, bands = bands.take(ParametricProfile.MAX_BANDS)),
        )
        val over = (bands.size - ParametricProfile.MAX_BANDS).coerceAtLeast(0)
        return Result(profile, skipped + List(over) { "Filter ke-${ParametricProfile.MAX_BANDS + it + 1} (lebih dari ${ParametricProfile.MAX_BANDS} pita)" })
    }

    private fun parseFilter(spec: String): EqBand? {
        val t = spec.split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (t.size < 2) return null
        val enabled = when (t[0].uppercase()) {
            "ON" -> true
            "OFF" -> false
            else -> return null
        }
        val type = when (t[1].uppercase()) {
            "PK", "PEQ", "PEAK" -> FilterType.PEAK
            "LS", "LSC", "LSQ", "LOWSHELF" -> FilterType.LOW_SHELF
            "HS", "HSC", "HSQ", "HIGHSHELF" -> FilterType.HIGH_SHELF
            "LP", "LPQ" -> FilterType.LOW_PASS
            "HP", "HPQ" -> FilterType.HIGH_PASS
            else -> return null
        }
        var fc: Float? = null
        var gain = 0f
        var q: Float? = null
        var bwOct: Float? = null
        var i = 2
        while (i < t.size) {
            when (t[i].uppercase()) {
                "FC" -> { fc = number(t.getOrNull(i + 1)); i += 2 }
                "GAIN" -> { gain = number(t.getOrNull(i + 1)) ?: return null; i += 2 }
                "Q" -> { q = number(t.getOrNull(i + 1)); i += 2 }
                "BW" -> {
                    // "BW Oct 1.0"
                    if (t.getOrNull(i + 1)?.uppercase() == "OCT") { bwOct = number(t.getOrNull(i + 2)); i += 3 } else return null
                }
                else -> i++ // satuan "Hz"/"dB" dan kemiringan shelf ("12dB") dilewati
            }
        }
        val f = fc ?: return null
        if (f.isNaN() || f !in EqBand.MIN_FREQ..EqBand.MAX_FREQ) return null
        val quality = q ?: bwOct?.let(::bandwidthToQ) ?: if (type == FilterType.PEAK) 1f else 0.707f
        return EqBand.clamp(EqBand(type, f, gain, quality, enabled))
    }

    /** Lebar pita dalam oktaf → Q (rumus cookbook untuk peaking). */
    fun bandwidthToQ(octaves: Float): Float {
        if (octaves <= 0f || octaves.isNaN()) return 1f
        val p = 2.0.pow(octaves.toDouble())
        return (sqrt(p) / (p - 1.0)).toFloat()
    }

    private fun number(s: String?): Float? = s?.replace(',', '.')?.toFloatOrNull()?.takeIf { !it.isNaN() }

    /** Teks ParametricEQ.txt dari profil (untuk dibagikan / dicadangkan). */
    fun export(p: ParametricProfile): String = buildString {
        appendLine("Preamp: %.1f dB".format(java.util.Locale.ROOT, p.preampDb))
        p.bands.forEachIndexed { i, b ->
            val type = when (b.type) {
                FilterType.PEAK -> "PK"; FilterType.LOW_SHELF -> "LSC"; FilterType.HIGH_SHELF -> "HSC"
                FilterType.LOW_PASS -> "LPQ"; FilterType.HIGH_PASS -> "HPQ"
            }
            appendLine(
                "Filter %d: %s %s Fc %.0f Hz Gain %.1f dB Q %.2f".format(
                    java.util.Locale.ROOT, i + 1, if (b.enabled) "ON" else "OFF", type, b.freq, b.gainDb, b.q,
                ),
            )
        }
    }
}
