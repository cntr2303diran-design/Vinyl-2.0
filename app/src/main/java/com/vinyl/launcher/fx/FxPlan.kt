package com.vinyl.launcher.fx

import com.vinyl.launcher.data.ParallaxMode
import com.vinyl.launcher.data.VisualSettings
import com.vinyl.launcher.data.VisualizerMode
import kotlin.math.PI
import kotlin.math.sin

/** Keadaan lingkungan yang menentukan efek mana yang boleh jalan sekarang. */
data class FxEnv(
    val apiLevel: Int,
    /** Skala durasi animasi sistem = 0 ("Hapus animasi"). */
    val reduceMotion: Boolean,
    /** Penghemat baterai aktif. */
    val powerSave: Boolean,
    /** Ada musik yang sedang diputar. */
    val playing: Boolean,
    /** Layar utama terlihat dan aplikasi berstatus RESUMED. */
    val visible: Boolean,
    /** Izin RECORD_AUDIO sudah diberikan. */
    val micGranted: Boolean,
    /** Perangkat punya sensor rotasi atau gravitasi. */
    val hasMotionSensor: Boolean,
)

/** Mengapa semua efek dimatikan sementara; dipakai Pengaturan untuk menjelaskannya. */
enum class FxBlock { NONE, REDUCE_MOTION, POWER_SAVE }

/** Apa yang harus dilakukan visualizer SEKARANG. */
enum class VisualizerPlan { OFF, REAL, DECORATIVE, NEEDS_PERMISSION }

/** Dari mana "energi" (0..1) yang menggerakkan cahaya, partikel, dan distorsi berasal. */
enum class EnergySource {
    NONE,
    /** Napas pelan tetap selama musik diputar; TIDAK mengikuti suara. */
    BREATH,
    /** Spektrum audio asli dari Visualizer. */
    REAL,
    /** Animasi dekoratif (tempo tetap); tidak mengikuti suara. */
    DECORATIVE,
}

/**
 * Keputusan efek: kombinasi pengaturan pengguna dan lingkungan. Murni. Prinsipnya: efek hanya jalan bila
 * pengguna menyalakannya, layar terlihat, dan sistem tidak meminta pengurangan gerak/penghematan daya; kerja
 * berulang (frame clock, sensor, Visualizer) hanya berjalan saat benar-benar ada yang digerakkan.
 */
data class FxPlan(
    val block: FxBlock,
    /** Perangkat mampu menjalankan shader AGSL (Android 13+). */
    val agsl: Boolean,
    /** Gambar distorsi kaca (butuh AGSL; tidak ada pengganti di Android lebih lama). */
    val distortion: Boolean,
    /** Gambar cahaya (AGSL bila bisa, gradien Canvas bila tidak). */
    val glow: Boolean,
    val particles: Boolean,
    /** Partikel bergerak; bila false (musik berhenti) partikel membeku dan tidak memicu frame. */
    val animateParticles: Boolean,
    val parallax: Boolean,
    val visualizer: VisualizerPlan,
    val energy: EnergySource,
) {
    /**
     * Perlu loop frame (withFrameNanos) sekarang? Bila false tidak ada pekerjaan berulang sama sekali.
     * Parallax TIDAK memerlukannya: kemiringan datang dari callback sensor dan dibaca langsung di fase gambar.
     */
    val animating: Boolean
        get() = visualizer == VisualizerPlan.REAL || visualizer == VisualizerPlan.DECORATIVE ||
            (energy != EnergySource.NONE && (glow || animateParticles || distortion))

    val anything: Boolean get() = distortion || glow || particles || parallax || visualizer != VisualizerPlan.OFF

    companion object {
        /** AGSL / RuntimeShader tersedia mulai Android 13 (API 33). */
        const val AGSL_MIN_API = 33

        val OFF = FxPlan(FxBlock.NONE, false, false, false, false, false, false, VisualizerPlan.OFF, EnergySource.NONE)

        fun of(s: VisualSettings, env: FxEnv): FxPlan {
            val agsl = env.apiLevel >= AGSL_MIN_API
            val block = when {
                env.reduceMotion -> FxBlock.REDUCE_MOTION
                env.powerSave -> FxBlock.POWER_SAVE
                else -> FxBlock.NONE
            }
            if (block != FxBlock.NONE || !env.visible) return OFF.copy(block = block, agsl = agsl)

            // Tanpa musik tidak ada yang divisualisasikan, jadi Visualizer tidak dibuat dan izin tidak ditagih
            val visualizer = if (!env.playing) VisualizerPlan.OFF else when (s.visualizer) {
                VisualizerMode.OFF -> VisualizerPlan.OFF
                VisualizerMode.DECORATIVE -> VisualizerPlan.DECORATIVE
                VisualizerMode.REAL -> if (env.micGranted) VisualizerPlan.REAL else VisualizerPlan.NEEDS_PERMISSION
            }
            val distortion = s.glassDistortion && agsl
            val glow = s.reactiveGlow
            val particles = s.particles
            val wantsEnergy = distortion || glow || particles
            val energy = when {
                visualizer == VisualizerPlan.REAL -> EnergySource.REAL
                visualizer == VisualizerPlan.DECORATIVE -> EnergySource.DECORATIVE
                env.playing && wantsEnergy -> EnergySource.BREATH
                else -> EnergySource.NONE
            }
            return FxPlan(
                block = FxBlock.NONE,
                agsl = agsl,
                distortion = distortion,
                glow = glow,
                particles = particles,
                animateParticles = particles && env.playing,
                parallax = s.parallax != ParallaxMode.OFF && env.hasMotionSensor,
                visualizer = visualizer,
                energy = energy,
            )
        }
    }
}

/** Napas pelan untuk [EnergySource.BREATH]: 0,18..0,42 dalam siklus 6 detik. Tidak mengikuti suara. */
object Breath {
    const val PERIOD_SEC = 6f
    fun energy(timeSec: Float): Float {
        if (!timeSec.isFinite()) return 0f
        return 0.3f + 0.12f * sin(2.0 * PI * (timeSec / PERIOD_SEC)).toFloat()
    }
}
