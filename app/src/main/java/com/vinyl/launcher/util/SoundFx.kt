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
 * Efek suara & getar deck kaset, disintesis langsung (tanpa file audio):
 * - [playKey]: tombol PLAY ditekan — "klak" mekanik + motor gulungan mulai berputar
 * - [stopKey]: tombol dilepas saat jeda — klik pegas pendek
 * - [tick]: klik kecil saat knob seek diputar (tiap "gigi")
 * - [flip]: desing pita digulung cepat saat ganti lagu, ditutup klik
 * - [startCrackle]/[stopCrackle]: desis pita pelan berulang selama lagu diputar (tanpa letupan)
 *
 * Suara diputar TANPA meminta audio focus, sehingga musik tidak terhenti. Nama field pengaturan (needleSound, crackle)
 * dipertahankan dari tema vinyl agar pengaturan & cadangan lama tetap terbaca.
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

    fun playKey(context: Context, fx: EffectSettings) {
        if (fx.haptics) buzz(context, fx.hapticStrength, longArrayOf(60, 40, 35, 50, 25), intArrayOf(255, 0, 200, 0, 120))
        if (fx.needleSound) play(dropPcm, gain(fx.volume))
    }

    fun stopKey(context: Context, fx: EffectSettings) {
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

    private val crtPcm: ShortArray by lazy { synthCrtOn() }
    private val staticPcm: ShortArray by lazy { synthStatic() }

    /**
     * Membuat semua buffer suara sekali di thread latar. Tanpa ini buffer dibuat (malas) di thread yang pertama kali
     * membunyikannya — sering thread utama, tepat saat halaman mulai bergeser.
     */
    fun warmUp() {
        scope.launch {
            val samples = dropPcm.size + liftPcm.size + tickPcm.size + flipPcm.size + crtPcm.size + staticPcm.size
            check(samples > 0)
        }
    }

    /** TV menyala (keluar dari siaga): "thunk" tabung + dengung singkat. Mengikuti sakelar bunyi tombol kaset & TV. */
    fun crtOn(fx: EffectSettings) {
        if (fx.needleSound) play(crtPcm, gain(fx.volume) * 0.8f)
    }

    /** Pindah halaman (ganti "channel"): desis statis sangat singkat. Mengikuti sakelar efek sentuh. */
    fun channel(fx: EffectSettings) {
        if (fx.interactionFx && fx.needleSound) play(staticPcm, gain(fx.volume) * 0.45f)
    }

    /** Desis pita pelan yang diulang terus sampai [stopCrackle] dipanggil. */
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

    /** Tombol PLAY: klik tajam + "klak" bernada rendah, lalu dengung motor yang naik dan memudar (±0,6 detik). */
    private fun synthDrop(): ShortArray {
        val n = (RATE * 0.62).toInt()
        val out = ShortArray(n)
        val rnd = Random(33)
        var lp = 0.0
        for (i in 0 until n) {
            val t = i.toDouble() / RATE
            val click = if (t < 0.004) (rnd.nextDouble() * 2 - 1) * (1 - t / 0.004) * 0.9 else 0.0
            // "klak" tuas: dua dentum pendek berdekatan
            val clunk = (sin(2 * PI * 140 * t) * exp(-t * 60) + sin(2 * PI * 95 * (t - 0.03).coerceAtLeast(0.0)) *
                exp(-(t - 0.03).coerceAtLeast(0.0) * 45) * (if (t > 0.03) 1.0 else 0.0)) * 0.8
            // motor gulungan: dengung 55 Hz + noise lembut, naik lalu memudar
            lp += 0.04 * ((rnd.nextDouble() * 2 - 1) - lp)
            val rise = (t / 0.15).coerceAtMost(1.0)
            val motor = (sin(2 * PI * 55 * t) * 0.18 + lp * 0.25) * rise * exp(-(t - 0.15).coerceAtLeast(0.0) * 4.5)
            out[i] = toShort(click + clunk + motor)
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

    /** Pita digulung cepat: desing bernada naik (noise terfilter + nada), ditutup klik berhenti. */
    private fun synthFlip(): ShortArray {
        val n = (RATE * 0.30).toInt()
        val rnd = Random(19)
        var lp = 0.0
        var phase = 0.0
        return ShortArray(n) { i ->
            val t = i.toDouble() / RATE
            val env = sin(PI * (t / 0.30)).coerceAtLeast(0.0)
            lp += (0.08 + 0.4 * t / 0.30) * ((rnd.nextDouble() * 2 - 1) - lp)
            phase += 2 * PI * (300 + 900 * t / 0.30) / RATE
            val whir = (lp * 0.6 + sin(phase) * 0.12) * env
            val click = if (t > 0.27) (rnd.nextDouble() * 2 - 1) * exp(-(t - 0.27) * 300) * 0.8 else 0.0
            toShort(whir + click)
        }
    }

    /** 4 detik desis pita halus (noise terfilter, tanpa letupan), untuk diulang. */
    private fun synthCrackle(): ShortArray {
        val n = RATE * 4
        val rnd = Random(91)
        var lp = 0.0
        var hp = 0.0
        return ShortArray(n) { i ->
            val x = rnd.nextDouble() * 2 - 1
            lp += 0.35 * (x - lp)          // potong frekuensi sangat tinggi
            hp += 0.02 * (lp - hp)         // buang dengung rendah → desis
            // ujung lingkaran dibuat pelan agar sambungan putaran ulang tidak berbunyi klik
            val edge = minOf(1.0, i / 2000.0, (n - 1 - i) / 2000.0)
            toShort((lp - hp) * 0.16 * edge)
        }
    }

    /** "Thunk" degauss tabung: dentum 60 Hz yang cepat meredam + dengung 50 Hz singkat. */
    private fun synthCrtOn(): ShortArray {
        val n = (RATE * 0.55).toInt()
        val rnd = Random(61)
        return ShortArray(n) { i ->
            val t = i.toDouble() / RATE
            val thunk = (sin(2 * PI * 60 * t) + 0.4 * sin(2 * PI * 120 * t)) * exp(-t * 14) * 0.9
            val click = if (t < 0.003) (rnd.nextDouble() * 2 - 1) * 0.6 else 0.0
            val hum = sin(2 * PI * 50 * t) * 0.12 * (t / 0.05).coerceAtMost(1.0) * exp(-t * 5)
            toShort(thunk + click + hum)
        }
    }

    /** Statis channel: noise putih singkat dengan selubung naik-turun cepat. */
    private fun synthStatic(): ShortArray {
        val n = (RATE * 0.16).toInt()
        val rnd = Random(73)
        return ShortArray(n) { i ->
            val t = i.toDouble() / RATE
            val env = sin(PI * (t / 0.16)).coerceAtLeast(0.0)
            toShort((rnd.nextDouble() * 2 - 1) * env * 0.5)
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
