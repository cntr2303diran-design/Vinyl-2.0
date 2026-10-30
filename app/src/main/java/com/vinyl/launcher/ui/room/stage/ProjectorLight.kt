package com.vinyl.launcher.ui.room.stage

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.vinyl.launcher.data.LightLevel
import com.vinyl.launcher.ui.room.TvGeometry
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * Geometri dan angka cahaya proyektor (murni, tanpa Compose, teruji): kerucut cahaya dari proyektor di bawah layar ke
 * gambar di dinding, dan butir debu yang melayang di dalam kerucut itu. Semua koordinat dalam dp layar.
 */
object ProjectorLight {
    /** Banyak butir debu di berkas cahaya untuk [LightLevel.NORMAL]. */
    const val MOTES_NORMAL = 28
    const val MOTES_SOFT = 12

    /**
     * Kerucut cahaya: puncaknya ([apex]) di bawah tepi layar (proyektor ada di belakang pemirsa), dasarnya tepi atas gambar
     * proyeksi ([topLeft], [topRight]). TV dan meja digambar di depannya, jadi yang tampak hanya sisi-sisinya.
     */
    class Beam(val apex: P2, val topLeft: P2, val topRight: P2)

    fun beam(f: StageLayout.Frame): Beam {
        val p = f.projection
        return Beam(P2(p.centerX, f.height + APEX_BELOW), P2(p.left, p.top), P2(p.right, p.top))
    }

    private const val APEX_BELOW = 40f

    /** Satu butir debu: [t] jarak 0 (puncak)..1 (dinding), [u] -1..1 melintang kerucut, kecepatan, ukuran (dp), kedipan. */
    class Mote(val t: Float, val u: Float, val speed: Float, val size: Float, val twinkle: Float)

    /** Butir debu deterministik (benih tetap): tampilan sama di tiap peluncuran dan teruji. */
    fun motes(count: Int, seed: Long = 7L): List<Mote> {
        val rnd = Random(seed)
        return List(count.coerceIn(0, MOTES_NORMAL)) {
            Mote(
                t = rnd.nextFloat(), u = rnd.nextFloat() * 2f - 1f, speed = 0.4f + rnd.nextFloat() * 0.9f,
                size = 0.7f + rnd.nextFloat() * 0.9f, twinkle = rnd.nextFloat(),
            )
        }
    }

    /**
     * Posisi butir debu [m] pada fase [phase] (0..1, berulang). Butir naik pelan di sepanjang kerucut dan bergoyang ke
     * samping; selalu di dalam kerucut. [phase] tak hingga/NaN dianggap 0.
     */
    fun position(b: Beam, m: Mote, phase: Float): P2 {
        val ph = if (phase.isNaN() || phase.isInfinite()) 0f else phase
        val t = ((m.t + ph * m.speed) % 1f).let { if (it < 0f) it + 1f else it }
        val sway = 0.12f * sin((ph * 2.0 * PI * (1.0 + m.twinkle) + m.t * 6.0)).toFloat()
        val u = (m.u * 0.92f + sway).coerceIn(-0.97f, 0.97f)
        val y = b.apex.y + (b.topLeft.y - b.apex.y) * t
        val left = b.apex.x + (b.topLeft.x - b.apex.x) * t
        val right = b.apex.x + (b.topRight.x - b.apex.x) * t
        return P2(left + (right - left) * (u + 1f) / 2f, y)
    }

    /** Kedipan butir 0.35..1 pada [phase]. */
    fun twinkle(m: Mote, phase: Float): Float {
        val ph = if (phase.isNaN() || phase.isInfinite()) 0f else phase
        return 0.675f + 0.325f * sin(ph * 2.0 * PI * (2.0 + 3.0 * m.twinkle) + m.t * 9.0).toFloat()
    }

    /** Pengali terang semua efek cahaya menurut tingkat. */
    fun strength(level: LightLevel): Float = when (level) {
        LightLevel.OFF -> 0f
        LightLevel.SOFT -> 0.5f
        LightLevel.NORMAL -> 1f
    }

    fun moteCount(level: LightLevel): Int = when (level) {
        LightLevel.OFF -> 0
        LightLevel.SOFT -> MOTES_SOFT
        LightLevel.NORMAL -> MOTES_NORMAL
    }
}

/**
 * Berkas cahaya tipis dari proyektor ke gambar di dinding, dengan butir debu yang melayang di dalamnya. Digambar di fase
 * gambar (Canvas); fase animasi dibaca di dalam blok gambar sehingga tidak ada rekomposisi tiap frame. [level] OFF =
 * tidak menggambar apa pun. [animate] false = berkas diam dan debu membeku (Hapus animasi, atau tidak ada yang diputar).
 */
@Composable
fun ProjectorBeam(f: StageLayout.Frame, color: Color, level: LightLevel, animate: Boolean, modifier: Modifier = Modifier) {
    if (level == LightLevel.OFF) return
    val beam = remember(f) { ProjectorLight.beam(f) }
    val motes = remember(level) { ProjectorLight.motes(ProjectorLight.moteCount(level)) }
    val k = ProjectorLight.strength(level)
    val phase: State<Float>? = if (animate) {
        rememberInfiniteTransition(label = "beam")
            .animateFloat(0f, 1f, infiniteRepeatable(tween(40_000, easing = LinearEasing), RepeatMode.Restart), label = "beamPhase")
    } else null
    Canvas(modifier.fillMaxSize().clearAndSetSemantics { }) {
        val d = density
        val ph = phase?.value ?: 0.37f
        val shimmer = 0.92f + 0.08f * sin(ph * 2.0 * PI * 40.0).toFloat()
        val ax = beam.apex.x * d; val ay = beam.apex.y * d
        val lx = beam.topLeft.x * d; val rx = beam.topRight.x * d; val ty = beam.topLeft.y * d
        val cone = Path().apply { moveTo(ax, ay); lineTo(lx, ty); lineTo(rx, ty); close() }
        // Isi kerucut: hampir tak terlihat di dekat proyektor, paling terang di dinding
        drawPath(
            cone,
            Brush.verticalGradient(listOf(Color.Transparent, color.copy(alpha = 0.085f * k * shimmer)), startY = ay, endY = ty),
            blendMode = BlendMode.Screen,
        )
        // Dua sisi kerucut yang lebih terang (berkas tipis)
        for (corner in listOf(Offset(lx, ty), Offset(rx, ty))) {
            drawLine(
                Brush.linearGradient(listOf(Color.Transparent, color.copy(alpha = 0.22f * k * shimmer)), start = Offset(ax, ay), end = corner),
                Offset(ax, ay), corner, strokeWidth = 1.4f * d, blendMode = BlendMode.Screen,
            )
        }
        // Debu yang tertangkap cahaya: hanya di dalam kerucut, makin jelas ke arah dinding
        clipPath(cone) {
            for (m in motes) {
                val p = ProjectorLight.position(beam, m, ph)
                val along = ((p.y - beam.apex.y) / (beam.topLeft.y - beam.apex.y)).coerceIn(0f, 1f)
                val a = 0.55f * k * ProjectorLight.twinkle(m, ph) * along
                if (a > 0.01f) drawCircle(Color.White.copy(alpha = a), m.size * d, Offset(p.x * d, p.y * d), blendMode = BlendMode.Screen)
            }
        }
    }
}

/**
 * Kilau di kaca TV: pantulan jendela yang lembut di sudut kiri atas kaca, dan (saat [animate]) pita cahaya miring yang
 * lewat pelan tiap beberapa detik. Dibatasi di dalam kaca ([TvGeometry.SCREEN]), tidak menangkap sentuhan.
 */
@Composable
fun TvGlint(tv: SRect, level: LightLevel, animate: Boolean, modifier: Modifier = Modifier) {
    if (level == LightLevel.OFF) return
    val k = ProjectorLight.strength(level)
    val phase: State<Float>? = if (animate) {
        rememberInfiniteTransition(label = "glint")
            .animateFloat(0f, 1f, infiniteRepeatable(tween(14_000, easing = LinearEasing), RepeatMode.Restart), label = "glintPhase")
    } else null
    Canvas(
        modifier
            .offset(tv.left.dp, tv.top.dp)
            .size(tv.width.dp, tv.height.dp)
            .clearAndSetSemantics { },
    ) {
        val s = TvGeometry.SCREEN
        val sx = size.width * s.left; val sy = size.height * s.top
        val sw = size.width * s.width; val sh = size.height * s.height
        val glass = Path().apply {
            addRoundRect(androidx.compose.ui.geometry.RoundRect(sx, sy, sx + sw, sy + sh, androidx.compose.ui.geometry.CornerRadius(sw * TvGeometry.SCREEN_CORNER)))
        }
        clipPath(glass) {
            // Pantulan jendela di sudut kiri atas
            val c = Offset(sx + sw * 0.2f, sy + sh * 0.16f)
            drawCircle(
                Brush.radialGradient(listOf(Color.White.copy(alpha = 0.13f * k), Color.Transparent), center = c, radius = sw * 0.42f),
                sw * 0.42f, c, blendMode = BlendMode.Screen,
            )
            // Pita miring yang lewat pelan; terlihat hanya sebagian siklus
            val p = phase?.value
            if (p != null) {
                val pos = (p * 1.9f - 0.45f)
                if (pos > -0.3f && pos < 1.3f) {
                    val cx = sx + sw * pos
                    val band = sw * 0.16f
                    val path = Path().apply {
                        moveTo(cx - band, sy); lineTo(cx + band, sy); lineTo(cx + band - sh * 0.35f, sy + sh); lineTo(cx - band - sh * 0.35f, sy + sh); close()
                    }
                    drawPath(
                        path,
                        Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.075f * k), Color.Transparent), startX = cx - band, endX = cx + band),
                        blendMode = BlendMode.Screen,
                    )
                }
            }
        }
    }
}
