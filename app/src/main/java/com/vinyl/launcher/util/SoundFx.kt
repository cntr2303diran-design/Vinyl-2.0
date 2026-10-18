package com.vinyl.launcher.util

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.vinyl.launcher.data.EffectSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.tanh
import kotlin.random.Random

/**
 * Efek suara & getar turntable, disintesis langsung (tanpa file audio):
 * - [needleDown]: "tuk" jarum menyentuh piringan + desis & kresek vinyl selama ±2,5 detik
 * - [needleUp]: klik saat lengan terangkat
 * - [tick]: klik kecil saat vinyl diputar dengan jari / tonearm melewati tepi piringan
 * - [flip]: kibasan saat ganti lagu
 * - [startCrackle]/[stopCrackle]: kresek vinyl pelan berulang selama lagu diputar
 *
 * Suara diputar TANPA meminta audio focus, sehingga musik tidak terhenti.
 */
object SoundFx {
    private const val RATE = 44100
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Buffer dibuat sekali lalu dipakai ulang
    private val dropPcm: ShortArray by lazy { synthDrop() }
    private val liftPcm: ShortArray by lazy { synthLift() }
    private val tickPcm: ShortArray by lazy { synthTick() }
    private val flipPcm: ShortArray by lazy { synthFlip() }
    private val cracklePcm: ShortArray by lazy { synthCrackle() }

    private var lastTickMs = 0L
    private var crackleTrack: AudioTrack? = null

    /** Penguat: slider 0..1 dipetakan agar nilai tengah pun sudah cukup keras. */
    private fun gain(volume: Float) = (volume * 2.1f + 0.2f).coerceIn(0f, 1f)

    fun needleDown(context: Context, fx: EffectSettings) {
        if (fx.haptics) buzz(context, fx.hapticStrength, longArrayOf(60, 40, 35, 50, 25), intArrayOf(255, 0, 200, 0, 120))
        if (fx.needleSound) play(dropPcm, gain(fx.volume))
    }

    fun needleUp(context: Context, fx: EffectSettings) {
        if (fx.haptics) buzz(context, fx.hapticStrength, longArrayOf(35, 30, 20), intArrayOf(210, 0, 130))
        if (fx.needleSound) play(liftPcm, gain(fx.volume))
    }

    /** Klik kecil, dibatasi agar tidak menumpuk saat vinyl diputar cepat. */
    fun tick(context: Context, fx: EffectSettings) {
        if (!fx.interactionFx) return
        val now = System.currentTimeMillis()
        if (now - lastTickMs < 70) return
        lastTickMs = now
        if (fx.haptics) buzz(context, fx.hapticStrength, longArrayOf(14), intArrayOf(200))
        if (fx.needleSound) play(tickPcm, gain(fx.volume) * 0.8f)
    }

    fun flip(context: Context, fx: EffectSettings) {
        if (!fx.interactionFx) return
        if (fx.haptics) buzz(context, fx.hapticStrength, longArrayOf(22, 30, 45), intArrayOf(190, 0, 255))
        if (fx.needleSound) play(flipPcm, gain(fx.volume))
    }

    /** Kresek vinyl pelan yang diulang terus sampai [stopCrackle] dipanggil. */
    fun startCrackle(volume: Float) {
        stopCrackle()
        runCatching {
            val track = track(cracklePcm, usage = AudioAttributes.USAGE_MEDIA, content = AudioAttributes.CONTENT_TYPE_MUSIC)
            track.write(cracklePcm, 0, cracklePcm.size)
            track.setLoopPoints(0, cracklePcm.size, -1)
            track.setVolume((gain(volume) * 0.5f).coerceIn(0f, 1f))
            track.play()
            crackleTrack = track
        }
    }

    fun stopCrackle() {
        crackleTrack?.let { t -> runCatching { t.stop(); t.release() } }
        crackleTrack = null
    }

    private fun track(pcm: ShortArray, usage: Int, content: Int): AudioTrack = AudioTrack.Builder()
        .setAudioAttributes(AudioAttributes.Builder().setUsage(usage).setContentType(content).build())
        .setAudioFormat(
            AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(RATE)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build()
        )
        .setTransferMode(AudioTrack.MODE_STATIC)
        .setBufferSizeInBytes(pcm.size * 2)
        .build()

    private fun play(pcm: ShortArray, volume: Float) = scope.launch {
        runCatching {
            val track = track(
                pcm,
                usage = AudioAttributes.USAGE_ASSISTANCE_SONIFICATION,
                content = AudioAttributes.CONTENT_TYPE_SONIFICATION,
            )
            track.write(pcm, 0, pcm.size)
            track.setVolume(volume.coerceIn(0f, 1f))
            track.play()
            // Lepas setelah selesai
            kotlinx.coroutines.delay(pcm.size * 1000L / RATE + 200)
            track.release()
        }
    }

    private fun toShort(v: Double): Short = (tanh(v * 1.5).coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort()

    /** "Tuk" bernada rendah + klik tajam, lalu desis & kresek yang memudar. */
    private fun synthDrop(): ShortArray {
        val n = (RATE * 2.6).toInt()
        val out = ShortArray(n)
        val rnd = Random(33)
        var lp = 0.0
        for (i in 0 until n) {
            val t = i.toDouble() / RATE
            // thump 80 Hz + harmonik, cepat meredam
            val thump = (sin(2 * PI * 80 * t) + 0.5 * sin(2 * PI * 160 * t)) * exp(-t * 24) * 0.95
            // klik awal (noise sangat singkat)
            val click = if (t < 0.006) (rnd.nextDouble() * 2 - 1) * (1 - t / 0.006) * 0.9 else 0.0
            // desis vinyl: noise yang di-low-pass, memudar
            lp += 0.1 * ((rnd.nextDouble() * 2 - 1) - lp)
            val hiss = lp * 0.2 * exp(-t * 0.8) * (if (t > 0.05) 1.0 else t / 0.05)
            // kresek acak (impuls jarang)
            val crackle = if (rnd.nextDouble() < 0.0012 * exp(-t * 0.6)) (rnd.nextDouble() * 2 - 1) * 0.8 else 0.0
            val fadeOut = if (t > 2.2) (2.6 - t) / 0.4 else 1.0
            out[i] = toShort((thump + click + hiss + crackle) * fadeOut)
        }
        return out
    }

    private fun synthLift(): ShortArray {
        val n = (RATE * 0.14).toInt()
        val rnd = Random(45)
        return ShortArray(n) { i ->
            val t = i.toDouble() / RATE
            toShort((rnd.nextDouble() * 2 - 1) * exp(-t * 80) * 0.7 + sin(2 * PI * 200 * t) * exp(-t * 50) * 0.5)
        }
    }

    private fun synthTick(): ShortArray {
        val n = (RATE * 0.035).toInt()
        val rnd = Random(7)
        return ShortArray(n) { i ->
            val t = i.toDouble() / RATE
            toShort((rnd.nextDouble() * 2 - 1) * exp(-t * 160) * 0.8 + sin(2 * PI * 1400 * t) * exp(-t * 200) * 0.4)
        }
    }

    /** Kibasan: noise yang menyapu naik, ditutup klik. */
    private fun synthFlip(): ShortArray {
        val n = (RATE * 0.22).toInt()
        val rnd = Random(19)
        var lp = 0.0
        return ShortArray(n) { i ->
            val t = i.toDouble() / RATE
            val env = sin(PI * (t / 0.22)).coerceAtLeast(0.0)
            lp += (0.05 + 0.5 * t / 0.22) * ((rnd.nextDouble() * 2 - 1) - lp)
            val click = if (t > 0.19) (rnd.nextDouble() * 2 - 1) * exp(-(t - 0.19) * 300) * 0.8 else 0.0
            toShort(lp * env * 0.9 + click)
        }
    }

    /** 4 detik desis halus + letupan kecil acak, untuk diulang. */
    private fun synthCrackle(): ShortArray {
        val n = RATE * 4
        val rnd = Random(91)
        var lp = 0.0
        var pop = 0.0
        return ShortArray(n) { i ->
            lp += 0.06 * ((rnd.nextDouble() * 2 - 1) - lp)
            if (rnd.nextDouble() < 0.0006) pop = (rnd.nextDouble() * 2 - 1) * 0.9
            pop *= 0.82
            toShort(lp * 0.22 + pop)
        }
    }

    /** Getar bergelombang; [strength] 0.3..1 menskalakan amplitudo. */
    private fun buzz(context: Context, strength: Float, timings: LongArray, amps: IntArray) {
        runCatching {
            val vib = if (Build.VERSION.SDK_INT >= 31)
                context.getSystemService(VibratorManager::class.java)?.defaultVibrator
            else @Suppress("DEPRECATION") context.getSystemService(Vibrator::class.java)
            if (vib == null || !vib.hasVibrator()) return
            val k = strength.coerceIn(0.3f, 1f)
            val effect = if (vib.hasAmplitudeControl()) {
                VibrationEffect.createWaveform(
                    timings,
                    IntArray(amps.size) { if (amps[it] == 0) 0 else (amps[it] * k).toInt().coerceIn(1, 255) },
                    -1,
                )
            } else {
                // Tanpa kendali amplitudo: satu getar lebih panjang sebanding kekuatan
                val on = timings.filterIndexed { i, _ -> amps[i] > 0 }.sum()
                VibrationEffect.createOneShot((on * (0.6f + 0.6f * k)).toLong().coerceAtLeast(10), VibrationEffect.DEFAULT_AMPLITUDE)
            }
            vib.vibrate(effect)
        }
    }
}
