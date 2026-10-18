package com.vinyl.launcher.data

import com.vinyl.launcher.audio.dsp.CrossfeedLevel
import com.vinyl.launcher.audio.dsp.ParametricProfile
import com.vinyl.launcher.audio.dsp.ReplayGainMode
import com.vinyl.launcher.audio.dsp.ReplayGainSettings
import com.vinyl.launcher.player.LocalSort
import kotlinx.serialization.Serializable

/**
 * Pemutar bawaan (berkas musik di folder pilihan pengguna): folder, urutan daftar, dan rantai suaranya — ReplayGain, EQ
 * parametrik (bisa impor AutoEQ), crossfeed, keluaran float resolusi tinggi, dan pencocokan laju sampel USB. Semua lokal.
 */
@Serializable
data class PlayerSettings(
    /** URI pohon folder musik (izin baca persisten SAF); null = belum dipilih. */
    val folderUri: String? = null,
    val folderName: String = "",
    val sort: LocalSort = LocalSort.ALBUM,
    val replayGain: ReplayGainSettings = ReplayGainSettings(),
    val eqEnabled: Boolean = false,
    val eq: ParametricProfile = ParametricProfile(),
    /** Profil EQ tersimpan (mis. hasil impor AutoEQ untuk beberapa headphone). */
    val savedEqs: List<ParametricProfile> = emptyList(),
    /** Turunkan preamp sebesar penguatan EQ terbesar agar tidak pernah clipping. */
    val autoHeadroom: Boolean = true,
    /** Crossfeed headphone; null = mati. */
    val crossfeed: CrossfeedLevel? = null,
    /**
     * Keluaran float 32-bit: berkas 24-bit dikirim utuh ke mixer Android (bukan dipotong ke 16-bit). Mati = 16-bit
     * (hemat daya, untuk perangkat lama yang bermasalah dengan float).
     */
    val hiResOutput: Boolean = true,
    /**
     * Android 14+ dengan DAC USB: minta mixer berjalan di laju sampel lagu (tanpa resampling Android). Tidak sama dengan
     * bit-perfect: volume & suara lain tetap dicampur.
     */
    val usbRateMatch: Boolean = false,
)

object PlayerRules {
    const val MAX_SAVED_EQS = 30
    const val MAX_URI = 2000

    fun sanitize(p: PlayerSettings): PlayerSettings {
        val rg = p.replayGain
        return p.copy(
            folderUri = p.folderUri?.takeIf { it.startsWith("content://") && it.length <= MAX_URI },
            folderName = p.folderName.filterNot { it.isISOControl() }.take(120),
            replayGain = rg.copy(
                preampDb = if (rg.preampDb.isNaN()) 0f else rg.preampDb.coerceIn(-15f, 15f),
                untaggedDb = if (rg.untaggedDb.isNaN()) -6f else rg.untaggedDb.coerceIn(-30f, 12f),
            ),
            eq = ParametricProfile.clamp(p.eq),
            savedEqs = p.savedEqs.map(ParametricProfile::clamp).distinctBy { it.name }.take(MAX_SAVED_EQS),
        )
    }

    /** Simpan [profile] ke daftar (nama sama = diganti), paling banyak [MAX_SAVED_EQS]. */
    fun saveEq(p: PlayerSettings, profile: ParametricProfile): PlayerSettings {
        val c = ParametricProfile.clamp(profile)
        val others = p.savedEqs.filterNot { it.name == c.name }
        if (others.size >= MAX_SAVED_EQS) return p
        return p.copy(savedEqs = others + c)
    }

    fun deleteEq(p: PlayerSettings, name: String): PlayerSettings = p.copy(savedEqs = p.savedEqs.filterNot { it.name == name })

    /** Label mode ReplayGain untuk UI. */
    fun label(m: ReplayGainMode): String = when (m) {
        ReplayGainMode.OFF -> "Mati"
        ReplayGainMode.TRACK -> "Per lagu"
        ReplayGainMode.ALBUM -> "Per album"
    }
}
