package com.vinyl.launcher.fx

import com.vinyl.launcher.data.ParallaxMode
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh

/*
 * Matematika parallax sensor — murni (tanpa Android) supaya bisa diuji unit.
 *
 * Gagasannya: ponsel adalah jendela. Latar berada "di belakang kaca", jadi saat ponsel dimiringkan, mata melihat
 * kaca dari sudut lain dan latar bergeser sedikit terhadap layar. Yang diukur hanyalah SEBERAPA JAUH ponsel
 * berputar dari pose pegangan santai (baseline) — bukan orientasi mutlaknya — sehingga pose pegang apa pun
 * (duduk, berbaring, di dudukan) terasa sama dan baseline pelan-pelan menyusul bila pose berubah.
 *
 * Sumber data (di sisi Android, lihat ParallaxSensor):
 *  - TYPE_GAME_ROTATION_VECTOR (giroskop + akselerometer): lengkap, termasuk putaran terhadap sumbu tegak.
 *  - TYPE_GRAVITY / akselerometer sebagai cadangan: gravitasi tidak bisa merasakan putaran terhadap sumbu
 *    vertikal ketika ponsel berdiri tegak, jadi gerak kiri-kanan melemah (lihat [Quat.fromGravity]).
 */

/** Kuaternion rotasi satuan (w, x, y, z). Konvensi: memutar vektor perangkat → vektor dunia. */
data class Quat(val w: Float, val x: Float, val y: Float, val z: Float) {
    val isFinite: Boolean get() = w.isFinite() && x.isFinite() && y.isFinite() && z.isFinite()

    fun conjugate() = Quat(w, -x, -y, -z)

    /** Perkalian Hamilton: `a * b` = putar dengan b dulu (pada kerangka badan), lalu a. */
    operator fun times(o: Quat) = Quat(
        w * o.w - x * o.x - y * o.y - z * o.z,
        w * o.x + x * o.w + y * o.z - z * o.y,
        w * o.y - x * o.z + y * o.w + z * o.x,
        w * o.z + x * o.y - y * o.x + z * o.w,
    )

    fun normalized(): Quat {
        val n = sqrt(w * w + x * x + y * y + z * z)
        return if (n < 1e-9f || !n.isFinite()) IDENTITY else Quat(w / n, x / n, y / n, z / n)
    }

    companion object {
        val IDENTITY = Quat(1f, 0f, 0f, 0f)

        /** Putar sebesar [angle] radian terhadap sumbu (ax, ay, az) (tidak harus satuan; sumbu nol = identitas). */
        fun fromAxisAngle(ax: Float, ay: Float, az: Float, angle: Float): Quat {
            val n = sqrt(ax * ax + ay * ay + az * az)
            if (n < 1e-9f || !n.isFinite() || !angle.isFinite()) return IDENTITY
            val s = sin(angle / 2f) / n
            return Quat(cos(angle / 2f), ax * s, ay * s, az * s)
        }

        /**
         * Dari nilai SensorEvent TYPE_(GAME_)ROTATION_VECTOR: [x, y, z] = sumbu·sin(θ/2) dan [w] = cos(θ/2) bila ada
         * (sebagian perangkat hanya memberi tiga nilai; w dipulihkan karena kuaternion bersatuan).
         */
        fun fromRotationVector(x: Float, y: Float, z: Float, w: Float = Float.NaN): Quat? {
            if (!x.isFinite() || !y.isFinite() || !z.isFinite()) return null
            val ww = if (w.isFinite()) w else sqrt(maxOf(0f, 1f - x * x - y * y - z * z))
            return Quat(ww, x, y, z).takeIf { it.isFinite }?.normalized()
        }

        /**
         * Dari vektor gravitasi/akselerometer (g di kerangka perangkat; saat diam menunjuk ke LANGIT). Rotasi terkecil
         * yang membawa "atas" perangkat ke sumbu Z dunia. Putaran terhadap arah gravitasi (yaw) tidak terdefinisi dan
         * dibuat nol. Null untuk data tidak sah atau jatuh bebas (besar vektor ≈ 0).
         */
        fun fromGravity(gx: Float, gy: Float, gz: Float): Quat? {
            if (!gx.isFinite() || !gy.isFinite() || !gz.isFinite()) return null
            val n = sqrt(gx * gx + gy * gy + gz * gz)
            if (n < 0.5f) return null
            val ux = gx / n
            val uy = gy / n
            val uz = gz / n
            // u × ẑ = (uy, -ux, 0); w = 1 + u·ẑ
            val w = 1f + uz
            if (w < 1e-4f) return Quat(0f, 1f, 0f, 0f)         // u ≈ -ẑ: balik 180° terhadap sumbu X
            return Quat(w, uy, -ux, 0f).normalized()
        }
    }
}

/**
 * Posisi mata terhadap sumbu tegak lurus layar, dinormalkan ke (-1, 1): [x] positif = mata di kanan layar,
 * [y] positif = mata di atas layar (sumbu y naik, seperti matematika — bukan piksel).
 */
data class Tilt(val x: Float, val y: Float) {
    companion object { val ZERO = Tilt(0f, 0f) }
}

/** Pergeseran satu lapisan dalam piksel layar (y ke bawah, seperti Compose). */
data class Shift(val dx: Float, val dy: Float) {
    companion object { val ZERO = Shift(0f, 0f) }
}

/**
 * Penyaring parallax: menerima kuaternion orientasi + selang waktu, menghasilkan [Tilt] yang halus.
 * Tidak thread-safe — panggil [update] dan [tilt] dari satu thread (di aplikasi: thread utama).
 */
class ParallaxFilter(private val cfg: Config = Config()) {
    data class Config(
        /** Putaran (radian) yang dianggap "miring penuh". 0,30 rad ≈ 17°. */
        val maxTiltRad: Float = 0.30f,
        /** Konstanta waktu baseline menyusul pose baru (detik). */
        val baselineTauSec: Float = 2.5f,
        /** Konstanta waktu penghalus (detik): meredam getar tangan tanpa terasa lambat. */
        val smoothTauSec: Float = 0.07f,
        /** Putaran di bawah ini diabaikan (radian) — ponsel diam di tangan tidak boleh bergoyang. */
        val deadZoneRad: Float = 0.012f,
        /** Putaran dari baseline di atas ini dianggap ponsel diangkat/diputar fisik, bukan miring: baseline diatur ulang. */
        val resetAngleRad: Float = 0.9f,
    )

    private var base: Quat? = null
    private var wx = 0f     // putaran terhaluskan terhadap sumbu X perangkat (rad)
    private var wy = 0f     // ... sumbu Y perangkat

    /** Lupakan baseline; sampel berikutnya menjadi pose awal. Panggil saat sensor baru dinyalakan. */
    fun reset() {
        base = null
        wx = 0f
        wy = 0f
    }

    /**
     * @param q orientasi perangkat saat ini (perangkat → dunia)
     * @param dtSec selang sejak sampel sebelumnya; dibatasi [0, MAX_DT] agar jeda panjang (aplikasi di latar) tidak
     * melompatkan hasil.
     */
    fun update(q: Quat, dtSec: Float) {
        if (!q.isFinite) return
        val cur = q.normalized()
        val b = base
        if (b == null) {
            base = cur
            wx = 0f
            wy = 0f
            return
        }
        val dt = if (dtSec.isFinite()) dtSec.coerceIn(0f, MAX_DT) else 0f
        var rel = b.conjugate() * cur
        if (rel.w < 0f) rel = Quat(-rel.w, -rel.x, -rel.y, -rel.z)       // rotasi terpendek
        val s = sqrt(rel.x * rel.x + rel.y * rel.y + rel.z * rel.z)
        val angle = 2f * atan2(s, rel.w)
        if (angle > cfg.resetAngleRad) {
            base = cur
            wx = 0f
            wy = 0f
            return
        }
        // Vektor putaran (sumbu × sudut) pada kerangka perangkat baseline
        val k = if (s > 1e-6f) angle / s else 2f
        val rx = rel.x * k
        val ry = rel.y * k
        val a = 1f - exp(-dt / cfg.smoothTauSec)
        wx += (rx - wx) * a
        wy += (ry - wy) * a
        // Baseline menyusul pose sekarang: majukan sebagian kecil sepanjang putaran relatif
        val f = 1f - exp(-dt / cfg.baselineTauSec)
        if (f > 0f && s > 1e-6f) {
            val step = Quat.fromAxisAngle(rel.x, rel.y, rel.z, angle * f)
            base = (b * step).normalized()
        }
    }

    /**
     * Posisi mata pada layar yang diputar [quarterTurns] × 90° (Display.getRotation(): 0..3). Putaran perangkat
     * dipetakan ke sumbu LAYAR: bagi layar yang dirotasi, sumbu "atas" UI berbeda dari sumbu Y badan ponsel.
     */
    fun tilt(quarterTurns: Int): Tilt {
        val (sx, sy) = when (((quarterTurns % 4) + 4) % 4) {
            1 -> -wy to wx        // ROTATION_90: atas UI = +X badan, kanan UI = -Y badan
            2 -> -wx to -wy
            3 -> wy to -wx
            else -> wx to wy
        }
        // Perangkat berputar +θ terhadap sumbu Y layar → mata berada di KIRI normal layar (x = -θ); putar +θ terhadap
        // sumbu X layar → mata di ATAS normal (y = +θ).
        return Tilt(shape(-sy), shape(sx))
    }

    private fun shape(v: Float): Float {
        val dz = cfg.deadZoneRad
        val shrunk = when {
            v > dz -> v - dz
            v < -dz -> v + dz
            else -> 0f
        }
        return tanh(shrunk / cfg.maxTiltRad)
    }

    companion object {
        const val MAX_DT = 0.1f
    }
}

object ParallaxMath {
    /** Pergeseran maksimum lapisan terjauh pada kekuatan 1 (dp). Kecil: parallax yang kuat membuat pusing. */
    const val MAX_SHIFT_DP = 16f

    /**
     * Pergeseran piksel sebuah lapisan.
     * @param depth kedalaman lapisan: 0 = pada bidang kaca (diam), 1 = jauh di belakang kaca; negatif = di depan kaca.
     * @param mode WINDOW = lapisan di belakang kaca; FLOAT = arah dibalik (seperti melayang di atas kaca); OFF = tidak bergeser.
     * @param strength 0..1 (di luar rentang dipotong).
     */
    fun layerShift(tilt: Tilt, depth: Float, maxShiftPx: Float, mode: ParallaxMode, strength: Float): Shift {
        if (mode == ParallaxMode.OFF) return Shift.ZERO
        if (!depth.isFinite() || !maxShiftPx.isFinite() || !strength.isFinite() || !tilt.x.isFinite() || !tilt.y.isFinite()) return Shift.ZERO
        val dir = if (mode == ParallaxMode.FLOAT) -1f else 1f
        val k = dir * depth.coerceIn(-1f, 1f) * maxShiftPx.coerceAtLeast(0f) * strength.coerceIn(0f, 1f)
        // Mata di kanan → lapisan jauh ikut ke kanan terhadap layar; mata di atas → lapisan jauh naik (piksel: y mengecil)
        return Shift(tilt.x * k, -tilt.y * k)
    }

    /**
     * Skala agar lapisan yang bergeser sampai [maxShiftPx] ke segala arah tidak memperlihatkan tepinya:
     * butuh tambahan [maxShiftPx] di tiap sisi, jadi 1 + 2·s / sisi-terpendek.
     */
    fun coverScale(maxShiftPx: Float, widthPx: Float, heightPx: Float): Float {
        val side = minOf(widthPx, heightPx)
        if (!maxShiftPx.isFinite() || !side.isFinite() || side <= 0f || maxShiftPx <= 0f) return 1f
        return 1f + 2f * maxShiftPx / side
    }
}
