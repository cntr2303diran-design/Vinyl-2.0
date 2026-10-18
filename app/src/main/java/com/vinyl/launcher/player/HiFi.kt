package com.vinyl.launcher.player

import com.vinyl.launcher.audio.dsp.CrossfeedLevel
import com.vinyl.launcher.audio.dsp.ParametricProfile
import com.vinyl.launcher.audio.dsp.ReplayGainSettings
import com.vinyl.launcher.data.PlayerSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Jembatan antara pengaturan (thread utama) dan rantai suara pemutar (thread audio ExoPlayer), serta status jalur suara
 * yang ditampilkan di layar. Pengaturan diterbitkan sebagai snapshot tak berubah dengan nomor versi; sink audio membaca
 * versi di setiap buffer dan membangun ulang rantainya hanya bila berubah.
 */
object HiFi {
    /** Pengaturan suara yang dibaca thread audio. */
    data class Dsp(
        val version: Int = 0,
        val replayGain: ReplayGainSettings = ReplayGainSettings(),
        /** null = EQ mati. */
        val eq: ParametricProfile? = null,
        val autoHeadroom: Boolean = true,
        val crossfeed: CrossfeedLevel? = null,
        val floatOutput: Boolean = true,
    )

    @Volatile
    var dsp: Dsp = Dsp()
        private set

    fun apply(p: PlayerSettings) {
        val next = Dsp(
            version = dsp.version + 1,
            replayGain = p.replayGain,
            eq = p.eq.takeIf { p.eqEnabled },
            autoHeadroom = p.autoHeadroom,
            crossfeed = p.crossfeed,
            floatOutput = p.hiResOutput,
        )
        if (next.copy(version = dsp.version) != dsp) dsp = next
    }

    /** Apa yang sedang terjadi di jalur suara pemutar bawaan (untuk deck, OSD, dan Audio Console). */
    data class PathState(
        /** Pemutar bawaan sedang memegang lagu. */
        val active: Boolean = false,
        val trackId: String? = null,
        /** Sumber: "FLAC", 96000, 24 bit, 2 kanal; 0 = tidak diketahui. */
        val codec: String = "",
        val sourceRate: Int = 0,
        val sourceBits: Int = 0,
        val channels: Int = 0,
        val lossless: Boolean = false,
        /** Hasil dekoder yang masuk ke rantai: "float 32-bit", "PCM 24-bit", … */
        val decoded: String = "",
        /** Yang dikirim ke Android: "float 32-bit" atau "16-bit + dither"; "" = belum ada. */
        val output: String = "",
        /** Penguatan ReplayGain yang dipakai lagu ini (dB), null = mati/tanpa tag. */
        val replayGainDb: Float? = null,
        val eqName: String? = null,
        val crossfeed: CrossfeedLevel? = null,
        val limiter: Boolean = false,
        /** Sampel tidak diubah sama sekali (EQ datar, tanpa ReplayGain/crossfeed). */
        val transparent: Boolean = true,
        /** Status pencocokan laju sampel USB (Android 14+), teks jujur untuk ditampilkan. */
        val usbRate: String = "",
    )

    private val _path = MutableStateFlow(PathState())
    val path: StateFlow<PathState> = _path.asStateFlow()

    internal fun updatePath(block: (PathState) -> PathState) = _path.update(block)
}
