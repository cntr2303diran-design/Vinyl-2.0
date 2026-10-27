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
 * - [drawer]: laci kaset di meja ditarik/didorong; [crtOff]: TV dimatikan (siulan jatuh + dentum)
 * - [gameEat]/[gameOver]/[gameWin]: bunyi dan getar game Ular di kaset game
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
    private val crtOffPcm: ShortArray by lazy { synthCrtOff() }
    private val drawerOpenPcm: ShortArray by lazy { synthDrawer(open = true) }
    private val drawerClosePcm: ShortArray by lazy { synthDrawer(open = false) }
    private val eatPcm: ShortArray by lazy { synthTones(doubleArrayOf(988.0, 1319.0), noteSec = 0.05, decay = 22.0, level = 0.5) }
    private val overPcm: ShortArray by lazy { synthTones(doubleArrayOf(392.0, 330.0, 262.0, 196.0), noteSec = 0.13, decay = 8.0, level = 0.55) }
    private val winPcm: ShortArray by lazy { synthTones(doubleArrayOf(523.0, 659.0, 784.0, 1047.0), noteSec = 0.11, decay = 6.0, level = 0.5) }

    /**
     * Membuat semua buffer suara sekali di thread latar. Tanpa ini buffer dibuat (malas) di thread yang pertama kali
     * membunyikannya — sering thread utama, tepat saat halaman mulai bergeser.
     */
    fun warmUp() {
        scope.launch {
            val samples = dropPcm.size + liftPcm.size + tickPcm.size + flipPcm.size + crtPcm.size + staticPcm.size +
                crtOffPcm.size + drawerOpenPcm.size + drawerClosePcm.size + eatPcm.size + overPcm.size + winPcm.size
            check(samples > 0)
        }
    }

    /** TV menyala (keluar dari siaga): "thunk" tabung + dengung singkat. Mengikuti sakelar bunyi tombol kaset & TV. */
    fun crtOn(fx: EffectSettings) {
        if (fx.needleSound) play(crtPcm, gain(fx.volume) * 0.8f)
    }

    /** TV dimatikan dengan tombol dayanya: siulan tabung yang jatuh, lalu dentum kecil. Mengikuti sakelar bunyi tombol & TV. */
    fun crtOff(fx: EffectSettings) {
        if (fx.needleSound) play(crtOffPcm, gain(fx.volume) * 0.7f)
    }

    /** Pindah halaman (ganti "channel"): desis statis sangat singkat. Mengikuti sakelar efek sentuh. */
    fun channel(fx: EffectSettings) {
        if (fx.interactionFx && fx.needleSound) play(staticPcm, gain(fx.volume) * 0.45f)
    }

    /** Laci kaset di meja ditarik ([open]) atau didorong: gesekan kayu lalu ketukan di ujung gerak. */
    fun drawer(context: Context, fx: EffectSettings, open: Boolean) {
        if (!fx.interactionFx) return
        if (fx.haptics) buzz(context, fx.hapticStrength, longArrayOf(26, 22, 18), intArrayOf(120, 0, if (open) 150 else 230))
        if (fx.needleSound) play(if (open) drawerOpenPcm else drawerClosePcm, gain(fx.volume) * 0.9f)
    }

    /** Ular makan: dua nada pendek naik, getar tipis. */
    fun gameEat(context: Context, fx: EffectSettings) {
        if (!fx.interactionFx) return
        if (fx.haptics) buzz(context, fx.hapticStrength, longArrayOf(12), intArrayOf(170))
        if (fx.needleSound) play(eatPcm, gain(fx.volume) * 0.7f)
    }

    /** Ular menabrak tubuhnya: nada turun, getar dua kali. */
    fun gameOver(context: Context, fx: EffectSettings) {
        if (!fx.interactionFx) return
        if (fx.haptics) buzz(context, fx.hapticStrength, longArrayOf(60, 50, 90), intArrayOf(255, 0, 180))
        if (fx.needleSound) play(overPcm, gain(fx.volume) * 0.8f)
    }

    /** Papan penuh: nada naik, getar bergelombang. */
    fun gameWin(context: Context, fx: EffectSettings) {
        if (!fx.interactionFx) return
        if (fx.haptics) buzz(context, fx.hapticStrength, longArrayOf(30, 30, 30, 30, 70), intArrayOf(150, 0, 190, 0, 255))
        if (fx.needleSound) play(winPcm, gain(fx.volume) * 0.8f)
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

    /**
     * TV mati: siulan tabung yang nadanya jatuh cepat dari ±1,8 kHz ke ±60 Hz dan memudar, lalu "pop" dan dentum 70 Hz kecil
     * di akhir (±0,42 detik). Selubungnya menuju nol sebelum dentum berakhir, jadi tidak ada klik di ujung buffer.
     */
    private fun synthCrtOff(): ShortArray {
        val n = (RATE * 0.42).toInt()
        val rnd = Random(83)
        var phase = 0.0
        return ShortArray(n) { i ->
            val t = i.toDouble() / RATE
            val k = t / 0.42
            val freq = 1800.0 * exp(-k * 4.2) + 60.0
            phase += 2 * PI * freq / RATE
            val env = (1.0 - k).coerceAtLeast(0.0)
            val attack = (t / 0.003).coerceAtMost(1.0)
            val whine = sin(phase) * 0.35 * env * env * attack
            val dt = t - 0.30
            val pop = if (dt in 0.0..0.004) (rnd.nextDouble() * 2 - 1) * 0.7 else 0.0
            val thump = if (dt >= 0.0) sin(2 * PI * 70 * dt) * exp(-dt * 30) * 0.8 else 0.0
            val edge = ((n - 1 - i) / (RATE * 0.006)).coerceAtMost(1.0)
            toShort((whine + pop + thump) * edge)
        }
    }

    /**
     * Laci kayu: gesekan rendah (noise terfilter, selubung naik-turun) lalu ketukan kayu di ujung gerak. Laci yang
     * didorong menutup berketuk lebih berat dan rendah daripada yang ditarik terbuka.
     */
    private fun synthDrawer(open: Boolean): ShortArray {
        val n = (RATE * 0.36).toInt()
        val rnd = Random(if (open) 101 else 103)
        val knockAt = if (open) 0.285 else 0.295
        val knockHz = if (open) 190.0 else 140.0
        var lp = 0.0
        return ShortArray(n) { i ->
            val t = i.toDouble() / RATE
            lp += 0.06 * ((rnd.nextDouble() * 2 - 1) - lp)
            val slideEnv = if (t < 0.30) sin(PI * t / 0.30) else 0.0
            val slide = lp * 2.2 * slideEnv * 0.55
            val dt = t - knockAt
            val knock = if (dt >= 0.0) {
                sin(2 * PI * knockHz * dt) * exp(-dt * 55) * 0.75 + (if (dt < 0.003) (rnd.nextDouble() * 2 - 1) * 0.4 else 0.0)
            } else 0.0
            val edge = ((n - 1 - i) / (RATE * 0.006)).coerceAtMost(1.0)
            toShort((slide + knock) * edge)
        }
    }

    /**
     * Rangkaian nada pendek [freqs] (Hz), masing-masing [noteSec] detik, dengan serangan 4 ms, peredaman [decay] per detik,
     * dan pelepasan 10 ms di ujung tiap nada supaya sambungan antarnada tidak berbunyi klik. Dipakai bunyi game.
     */
    private fun synthTones(freqs: DoubleArray, noteSec: Double, decay: Double, level: Double): ShortArray {
        val per = (RATE * noteSec).toInt()
        val out = ShortArray(per * freqs.size)
        var phase = 0.0
        for (k in freqs.indices) for (j in 0 until per) {
            val t = j.toDouble() / RATE
            phase += 2 * PI * freqs[k] / RATE
            val attack = (t / 0.004).coerceAtMost(1.0)
            val release = ((noteSec - t) / 0.010).coerceIn(0.0, 1.0)
            val tone = (sin(phase) + 0.25 * sin(2 * phase)) * level * attack * release * exp(-t * decay)
            out[k * per + j] = toShort(tone)
        }
        return out
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
