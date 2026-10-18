package com.vinyl.launcher.fx

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.vinyl.launcher.data.ParallaxMode
import com.vinyl.launcher.data.VisualSettings
import com.vinyl.launcher.ui.theme.Tokens
import com.vinyl.launcher.ui.theme.rememberReduceMotion
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/*
 * Penyambung Compose untuk efek visual (Tahap 4). Aturan arsitektur:
 *  - SATU loop frame untuk semua efek, hanya hidup selama [FxPlan.animating] atau ada riak sentuhan.
 *  - State yang berubah tiap frame (waktu, energi, kemiringan) dibaca HANYA di fase gambar (Canvas / graphicsLayer),
 *    sehingga tidak ada komposisi ulang per frame.
 *  - Sensor dan Visualizer didaftarkan mengikuti lifecycle (resume/pause) dan dilepas saat layar utama tidak terlihat.
 */

private const val RIPPLE_SEC = 0.9f
private const val STALE_CAPTURE_MS = 600L
private const val ENERGY_EPS = 0.01f
private const val BACKDROP_DEPTH = 1f
private const val GLOW_DEPTH = 0.55f
private const val FOREGROUND_DEPTH = -0.2f
private const val GLOW_CENTER_Y = 0.40f

/** Keadaan efek yang dibagi seluruh lapisan: waktu animasi, energi, spektrum, partikel, kemiringan, dan riak sentuhan. */
@Stable
class FxState(context: Context) {
    /** Dipasang dari komposisi; dibaca sensor pada tiap sampel. */
    var displayRotation: () -> Int = { 0 }

    val sensor = ParallaxSensor(context) { displayRotation() }
    val visualizer = AudioVisualizer()
    val particles = ParticleField()

    private val analyzer = SpectrumAnalyzer()
    private val target = FloatArray(analyzer.bandCount)

    /** Tingkat pita 0..1 (dipakai ulang; dibaca di fase gambar). */
    val bands = FloatArray(analyzer.bandCount)
    val bandCount: Int get() = analyzer.bandCount

    var time by mutableFloatStateOf(0f)
        private set
    var energy by mutableFloatStateOf(0f)
        private set
    var touch by mutableStateOf(Offset.Zero)
        private set
    var rippleLeft by mutableFloatStateOf(0f)
        private set

    /** Kemajuan riak 0..1; 1 = tidak ada riak. */
    val rippleProgress: Float get() = if (rippleLeft <= 0f) 1f else (1f - rippleLeft / RIPPLE_SEC).coerceIn(0f, 1f)

    /** Mulai riak dari titik sentuh [at] (koordinat lapisan latar). */
    fun pokeRipple(at: Offset) {
        touch = at
        rippleLeft = RIPPLE_SEC
    }

    /** Majukan satu frame. [dtRaw] detik sejak frame sebelumnya. */
    fun step(dtRaw: Float, plan: FxPlan) {
        val dt = if (dtRaw.isFinite()) dtRaw.coerceIn(0f, 0.1f) else 0f
        time += dt
        if (rippleLeft > 0f) rippleLeft = (rippleLeft - dt).coerceAtLeast(0f)
        when (plan.energy) {
            EnergySource.REAL -> {
                val cap = visualizer.latest()
                if (cap != null && cap.ageMs < STALE_CAPTURE_MS) analyzer.process(cap.fft, cap.samplingRateMilliHz, dt) else analyzer.decay(dt)
                analyzer.bands.copyInto(bands)
                energy = analyzer.energy
            }
            EnergySource.DECORATIVE -> {
                val e = DecorativeSpectrum.fill(time, true, target)
                val up = 1f - exp(-dt / SpectrumAnalyzer.ATTACK_SEC)
                val down = 1f - exp(-dt / SpectrumAnalyzer.RELEASE_SEC)
                for (i in bands.indices) bands[i] += (target[i] - bands[i]) * (if (target[i] > bands[i]) up else down)
                energy = e
            }
            EnergySource.BREATH -> {
                bands.fill(0f)
                energy = Breath.energy(time)
            }
            EnergySource.NONE -> Unit
        }
        if (plan.animateParticles) particles.step(dt, energy)
    }

    /** Tidak ada sumber energi: tenangkan semuanya (dipanggil saat musik berhenti atau efek dimatikan). */
    fun settle() {
        energy = 0f
        bands.fill(0f)
        analyzer.clear()
    }

    /** Lepas sensor dan Visualizer. */
    fun release() {
        sensor.stop()
        visualizer.stop()
    }
}

@Composable
fun rememberFxState(): FxState {
    val context = LocalContext.current
    val view = LocalView.current
    val state = remember(context) { FxState(context.applicationContext) }
    SideEffect { state.displayRotation = { view.display?.rotation ?: 0 } }
    DisposableEffect(state) { onDispose { state.release() } }
    return state
}

/**
 * Menjalankan efek sesuai [plan]: mendaftarkan sensor dan Visualizer mengikuti lifecycle, dan menjalankan loop frame
 * tunggal hanya selama ada yang bergerak.
 */
@Composable
fun FxRunner(fx: FxState, plan: FxPlan) {
    val planState = rememberUpdatedState(plan)

    LifecycleResumeEffect(plan.parallax) {
        if (plan.parallax) fx.sensor.start()
        onPauseOrDispose { fx.sensor.stop() }
    }
    LifecycleResumeEffect(plan.visualizer == VisualizerPlan.REAL) {
        if (plan.visualizer == VisualizerPlan.REAL) fx.visualizer.start()
        onPauseOrDispose { fx.visualizer.stop() }
    }
    LaunchedEffect(plan.energy) {
        if (plan.energy == EnergySource.NONE) fx.settle()
    }
    LaunchedEffect(fx) {
        snapshotFlow { planState.value.animating || fx.rippleLeft > 0f }
            .distinctUntilChanged()
            .collectLatest { running ->
                if (!running) return@collectLatest
                var last = 0L
                while (isActive) {
                    withFrameNanos { now ->
                        val dt = if (last == 0L) 0f else (now - last) / 1e9f
                        last = now
                        fx.step(dt, planState.value)
                    }
                }
            }
    }
}

// ---------------------------------------------------------------- lingkungan → FxPlan

/** True bila izin RECORD_AUDIO (dipakai Visualizer) sudah diberikan. */
fun micPermissionGranted(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

/** True bila izin RECORD_AUDIO sudah diberikan; dibaca ulang tiap kembali ke layar. */
@Composable
fun rememberMicGranted(): Boolean {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(micPermissionGranted(context)) }
    LifecycleResumeEffect(Unit) {
        granted = micPermissionGranted(context)
        onPauseOrDispose { }
    }
    return granted
}

/** True saat Penghemat baterai sistem aktif; mengikuti siaran perubahan modenya. */
@Composable
fun rememberPowerSave(): Boolean {
    val context = LocalContext.current
    val pm = remember(context) { context.getSystemService(Context.POWER_SERVICE) as? PowerManager }
    var saving by remember { mutableStateOf(pm?.isPowerSaveMode == true) }
    DisposableEffect(context, pm) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                saving = pm?.isPowerSaveMode == true
            }
        }
        ContextCompat.registerReceiver(
            context, receiver, IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        saving = pm?.isPowerSaveMode == true
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }
    return saving
}

/**
 * Menyusun [FxPlan] dari pengaturan dan lingkungan saat ini.
 * @param screenVisible layar utama sedang tampil penuh (tidak tertutup drawer, rak, atau lirik).
 */
@Composable
fun rememberFxPlan(settings: VisualSettings, playing: Boolean, screenVisible: Boolean, fx: FxState): FxPlan {
    val reduce = rememberReduceMotion()
    val powerSave = rememberPowerSave()
    val mic = rememberMicGranted()
    var resumed by remember { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) {
        resumed = true
        onPauseOrDispose { resumed = false }
    }
    val env = FxEnv(
        apiLevel = Build.VERSION.SDK_INT,
        reduceMotion = reduce,
        powerSave = powerSave,
        playing = playing,
        visible = resumed && screenVisible,
        micGranted = mic,
        hasMotionSensor = fx.sensor.available,
    )
    return remember(settings, env) { FxPlan.of(settings, env) }
}

// ---------------------------------------------------------------- lapisan

/**
 * Lapisan "jauh" (latar): parallax (geser + skala agar tepi tak terlihat) dan distorsi kaca AGSL. Semua dibaca di blok
 * graphicsLayer sehingga hanya lapisan yang digambar ulang.
 */
@Composable
fun Modifier.fxBackdrop(fx: FxState?, plan: FxPlan, mode: ParallaxMode, strength: Float): Modifier {
    if (fx == null || (!plan.parallax && !plan.distortion)) return this
    val density = LocalDensity.current.density
    val distortion = remember(plan.distortion) {
        if (plan.distortion && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) GlassDistortion.createOrNull() else null
    }
    val maxShiftPx = ParallaxMath.MAX_SHIFT_DP * density
    val s = strength.coerceIn(0f, 1f)
    return this.graphicsLayer {
        if (plan.parallax) {
            val shift = ParallaxMath.layerShift(fx.sensor.tilt, BACKDROP_DEPTH, maxShiftPx, mode, s)
            translationX = shift.dx
            translationY = shift.dy
            val scale = ParallaxMath.coverScale(maxShiftPx * s, size.width, size.height)
            scaleX = scale
            scaleY = scale
        }
        if (distortion != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            (fx.energy > ENERGY_EPS || fx.rippleLeft > 0f)
        ) {
            renderEffect = distortion.effect(size.width, size.height, fx.time, fx.energy, fx.touch.x, fx.touch.y, fx.rippleProgress)
        }
    }
}

/** Lapisan "depan" kecil (turntable) yang bergeser berlawanan arah latar untuk memberi kedalaman. */
@Composable
fun Modifier.fxForeground(fx: FxState?, plan: FxPlan, mode: ParallaxMode, strength: Float): Modifier {
    if (fx == null || !plan.parallax) return this
    val maxShiftPx = ParallaxMath.MAX_SHIFT_DP * LocalDensity.current.density
    val s = strength.coerceIn(0f, 1f)
    return this.graphicsLayer {
        val shift = ParallaxMath.layerShift(fx.sensor.tilt, FOREGROUND_DEPTH, maxShiftPx, mode, s)
        translationX = shift.dx
        translationY = shift.dy
    }
}

/**
 * Cahaya reaktif dan partikel, digambar di atas peredam latar dan di bawah konten. Setiap lapisan dibatasi
 * [Tokens.A11y.FX_PEAK] (lihat BackdropDim.withFx) sehingga kontras teks tetap terjamin.
 */
@Composable
fun FxOverlay(
    fx: FxState,
    plan: FxPlan,
    tint: Color,
    mode: ParallaxMode,
    strength: Float,
    modifier: Modifier = Modifier,
) {
    if (!plan.glow && !plan.particles) return
    val density = LocalDensity.current.density
    val glow = remember(plan.glow) {
        if (plan.glow && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) ReactiveGlow.createOrNull() else null
    }
    val tintArgb = tint.toArgb()
    val maxShiftPx = ParallaxMath.MAX_SHIFT_DP * density
    val s = strength.coerceIn(0f, 1f)
    val peak = Tokens.A11y.FX_PEAK
    Canvas(modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val t = fx.time               // dibaca agar Canvas digambar ulang tiap frame animasi
        val level = fx.energy
        val tilt = if (plan.parallax) fx.sensor.tilt else Tilt.ZERO
        if (plan.glow) {
            val shift = ParallaxMath.layerShift(tilt, GLOW_DEPTH, maxShiftPx, mode, s)
            val cx = w * 0.5f + shift.dx
            val cy = h * GLOW_CENTER_Y + shift.dy
            if (glow != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                glow.update(w, h, t, level, tintArgb, cx, cy)
                drawRect(glow.brush)
            } else {
                // Cadangan tanpa AGSL: gradien radial dengan alfa puncak yang sama
                val a = (peak * (0.35f + 0.65f * level)).coerceIn(0f, peak)
                val radius = min(w, h) * 0.6f
                drawCircle(
                    Brush.radialGradient(listOf(tint.copy(alpha = a), Color.Transparent), center = Offset(cx, cy), radius = radius),
                    radius, Offset(cx, cy),
                )
            }
        }
        if (plan.particles) {
            val field = fx.particles
            val minSide = min(w, h)
            for (i in 0 until field.capacity) {
                val shift = ParallaxMath.layerShift(tilt, 0.25f + 0.75f * field.depth[i], maxShiftPx, mode, s)
                val c = Offset(field.x[i] * w + shift.dx, field.y[i] * h + shift.dy)
                val r = field.radius(i, level) * minSide
                val a = field.alpha(i, level)
                drawCircle(Color.White.copy(alpha = a * 0.35f), r * 2.4f, c)
                drawCircle(Color.White.copy(alpha = a), r, c)
            }
        }
    }
}

/** Batang spektrum. Dekoratif semata (tidak interaktif); keterangan dan label aksesibilitas dipasang pemanggil. */
@Composable
fun SpectrumBars(fx: FxState, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        @Suppress("UNUSED_VARIABLE")
        val tick = fx.time               // dibaca agar digambar ulang tiap frame animasi
        val n = fx.bandCount
        val gap = 3.dp.toPx()
        val barW = ((size.width - gap * (n - 1)) / n).coerceAtLeast(1f)
        val floor = 2.dp.toPx()
        for (i in 0 until n) {
            val v = fx.bands[i].coerceIn(0f, 1f)
            val bh = max(floor, v * size.height)
            drawRoundRect(
                color.copy(alpha = 0.55f + 0.4f * v),
                topLeft = Offset(i * (barW + gap), size.height - bh),
                size = Size(barW, bh),
                cornerRadius = CornerRadius(barW / 2f),
            )
        }
    }
}
