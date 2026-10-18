package com.vinyl.launcher.fx

import com.vinyl.launcher.data.ParallaxMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh

class ParallaxTest {
    // ---------------------------------------------------------------- pembantu (ditulis terpisah dari kode produksi)

    /** Putar vektor v dengan kuaternion q: q v q*. */
    private fun rotate(q: Quat, v: FloatArray): FloatArray {
        val r = q * Quat(0f, v[0], v[1], v[2]) * q.conjugate()
        return floatArrayOf(r.x, r.y, r.z)
    }

    /** Perangkat berputar +[theta] terhadap sumbu BADAN (ax, ay, az) dari pose [from]. */
    private fun turned(from: Quat, ax: Float, ay: Float, az: Float, theta: Float) = (from * Quat.fromAxisAngle(ax, ay, az, theta)).normalized()

    /** Sumbu tegak (atas UI) dan sumbu mendatar (kanan UI) layar, dinyatakan di kerangka badan perangkat. */
    private val uiUp = mapOf(0 to floatArrayOf(0f, 1f, 0f), 1 to floatArrayOf(1f, 0f, 0f), 2 to floatArrayOf(0f, -1f, 0f), 3 to floatArrayOf(-1f, 0f, 0f))
    private val uiRight = mapOf(0 to floatArrayOf(1f, 0f, 0f), 1 to floatArrayOf(0f, -1f, 0f), 2 to floatArrayOf(-1f, 0f, 0f), 3 to floatArrayOf(0f, 1f, 0f))

    /** Konfigurasi tanpa baseline yang menyusul, untuk mengukur penyaring murni. */
    private val frozen = ParallaxFilter.Config(baselineTauSec = 1e9f)
    private fun settle(f: ParallaxFilter, q: Quat, seconds: Float = 2f) {
        repeat((seconds / 0.016f).toInt()) { f.update(q, 0.016f) }
    }
    private fun expected(theta: Float, cfg: ParallaxFilter.Config = frozen) = tanh((theta - cfg.deadZoneRad) / cfg.maxTiltRad)

    // ---------------------------------------------------------------- Quat

    @Test fun identityAndConjugateBehave() {
        val q = Quat.fromAxisAngle(1f, 2f, -1f, 0.7f)
        val back = (q * q.conjugate()).normalized()
        assertEquals(1f, back.w, 1e-6f); assertEquals(0f, back.x, 1e-6f); assertEquals(0f, back.y, 1e-6f); assertEquals(0f, back.z, 1e-6f)
        assertEquals(q, (Quat.IDENTITY * q))
    }

    @Test fun productRotatesInBodyFrameOrder() {
        // 90° terhadap Z lalu 90° terhadap X (kerangka badan) membawa sumbu X dunia ke Y lalu ke Z... diperiksa lewat vektor
        val a = Quat.fromAxisAngle(0f, 0f, 1f, (Math.PI / 2).toFloat())
        val v = rotate(a, floatArrayOf(1f, 0f, 0f))
        assertEquals(0f, v[0], 1e-6f); assertEquals(1f, v[1], 1e-6f); assertEquals(0f, v[2], 1e-6f)
        val b = Quat.fromAxisAngle(1f, 0f, 0f, (Math.PI / 2).toFloat())
        val ab = rotate(a * b, floatArrayOf(0f, 1f, 0f))            // b dulu (y → z), lalu a (z tetap)
        assertEquals(0f, ab[0], 1e-6f); assertEquals(0f, ab[1], 1e-6f); assertEquals(1f, ab[2], 1e-6f)
    }

    @Test fun normalizingDegenerateInputGivesIdentity() {
        assertEquals(Quat.IDENTITY, Quat(0f, 0f, 0f, 0f).normalized())
        assertEquals(Quat.IDENTITY, Quat(Float.NaN, 0f, 0f, 0f).normalized())
        assertEquals(Quat.IDENTITY, Quat.fromAxisAngle(0f, 0f, 0f, 1f))
        assertEquals(Quat.IDENTITY, Quat.fromAxisAngle(1f, 0f, 0f, Float.NaN))
    }

    @Test fun rotationVectorWithAndWithoutW() {
        val theta = 1.0f
        val s = sin(theta / 2)
        val full = Quat.fromRotationVector(0f, 0f, s, cos(theta / 2))!!
        val three = Quat.fromRotationVector(0f, 0f, s)!!
        assertEquals(full.w, three.w, 1e-5f)
        assertEquals(cos(theta / 2), three.w, 1e-5f)
        assertEquals(s, three.z, 1e-5f)
        assertNull(Quat.fromRotationVector(Float.NaN, 0f, 0f))
        assertNull(Quat.fromRotationVector(0f, Float.POSITIVE_INFINITY, 0f))
        // nilai x,y,z melebihi satuan (derau) tidak boleh menghasilkan NaN
        val noisy = Quat.fromRotationVector(0.9f, 0.9f, 0.9f)!!
        assertTrue(noisy.isFinite)
        val n = sqrt(noisy.w * noisy.w + noisy.x * noisy.x + noisy.y * noisy.y + noisy.z * noisy.z)
        assertEquals(1f, n, 1e-5f)
    }

    @Test fun gravityQuaternionCarriesUpToWorldZ() {
        for (g in listOf(floatArrayOf(0f, 9.8f, 0f), floatArrayOf(3f, 4f, 7f), floatArrayOf(0f, 0f, 9.8f), floatArrayOf(-5f, 1f, -6f), floatArrayOf(0f, 0f, -9.8f), floatArrayOf(9.8f, 0f, 0f))) {
            val q = Quat.fromGravity(g[0], g[1], g[2])!!
            val n = sqrt(g[0] * g[0] + g[1] * g[1] + g[2] * g[2])
            val up = rotate(q, floatArrayOf(g[0] / n, g[1] / n, g[2] / n))
            assertEquals("g=${g.toList()}", 0f, up[0], 1e-4f)
            assertEquals(0f, up[1], 1e-4f)
            assertEquals(1f, up[2], 1e-4f)
        }
    }

    @Test fun gravityRejectsFreeFallAndGarbage() {
        assertNull(Quat.fromGravity(0f, 0f, 0f))
        assertNull(Quat.fromGravity(0.1f, 0.1f, 0.1f))
        assertNull(Quat.fromGravity(Float.NaN, 1f, 9f))
        assertNull(Quat.fromGravity(1f, Float.NEGATIVE_INFINITY, 9f))
        assertNotNull(Quat.fromGravity(0f, 0f, 1f))
    }

    // ---------------------------------------------------------------- arah pada keempat rotasi layar

    @Test fun turningAroundTheVerticalOfTheScreenMovesTheEyeLeftOnEveryRotation() {
        for (rot in 0..3) {
            val f = ParallaxFilter(frozen)
            val start = Quat.fromAxisAngle(0.3f, -0.5f, 0.8f, 1.1f)            // pose awal sembarang
            f.update(start, 0.016f)
            val up = uiUp.getValue(rot)
            val theta = 0.15f
            settle(f, turned(start, up[0], up[1], up[2], theta))
            val t = f.tilt(rot)
            assertEquals("rotasi $rot: x", -expected(theta), t.x, 0.01f)
            assertEquals("rotasi $rot: y", 0f, t.y, 0.005f)
            assertTrue(t.x < 0f)
        }
    }

    @Test fun turningAroundTheHorizontalOfTheScreenMovesTheEyeUpOnEveryRotation() {
        for (rot in 0..3) {
            val f = ParallaxFilter(frozen)
            val start = Quat.fromAxisAngle(-0.2f, 0.4f, 0.1f, 0.6f)
            f.update(start, 0.016f)
            val right = uiRight.getValue(rot)
            val theta = 0.15f
            settle(f, turned(start, right[0], right[1], right[2], theta))
            val t = f.tilt(rot)
            assertEquals("rotasi $rot: y", expected(theta), t.y, 0.01f)
            assertEquals("rotasi $rot: x", 0f, t.x, 0.005f)
            assertTrue(t.y > 0f)
        }
    }

    @Test fun oppositeTurnGivesOppositeSign() {
        val f = ParallaxFilter(frozen)
        f.update(Quat.IDENTITY, 0.016f)
        settle(f, turned(Quat.IDENTITY, 0f, 1f, 0f, -0.15f))
        assertEquals(expected(0.15f), f.tilt(0).x, 0.01f)
        assertTrue(f.tilt(0).x > 0f)
    }

    @Test fun displayRotationNumbersWrapAround() {
        val f = ParallaxFilter(frozen)
        f.update(Quat.IDENTITY, 0.016f)
        settle(f, turned(Quat.IDENTITY, 0.3f, 0.9f, 0f, 0.2f))
        for (r in 0..3) {
            assertEquals(f.tilt(r), f.tilt(r + 4))
            assertEquals(f.tilt(r), f.tilt(r - 4))
            assertEquals(f.tilt(r), f.tilt(r + 400))
        }
        assertEquals(f.tilt(3), f.tilt(-1))
    }

    // ---------------------------------------------------------------- bentuk keluaran

    @Test fun restingPhoneProducesNoMotion() {
        val f = ParallaxFilter()
        assertEquals(Tilt.ZERO, f.tilt(0))
        f.update(Quat.fromAxisAngle(1f, 1f, 0f, 0.5f), 0.016f)
        repeat(300) { f.update(Quat.fromAxisAngle(1f, 1f, 0f, 0.5f), 0.016f) }
        assertEquals(Tilt.ZERO, f.tilt(0))
        assertEquals(Tilt.ZERO, f.tilt(1))
    }

    @Test fun deadZoneIgnoresHandJitter() {
        val f = ParallaxFilter(frozen)
        f.update(Quat.IDENTITY, 0.016f)
        settle(f, turned(Quat.IDENTITY, 1f, 1f, 0f, 0.012f))            // < zona mati pada tiap sumbu
        assertEquals(Tilt.ZERO, f.tilt(0))
    }

    @Test fun outputIsBoundedSmoothAndOdd() {
        val f = ParallaxFilter(frozen)
        f.update(Quat.IDENTITY, 0.016f)
        var prev = 0f
        for (deg in 1..50) {                                              // sampai 0,87 rad, di bawah batas reset
            val theta = Math.toRadians(deg.toDouble()).toFloat()
            val g = ParallaxFilter(frozen)
            g.update(Quat.IDENTITY, 0.016f)
            settle(g, turned(Quat.IDENTITY, 0f, 1f, 0f, theta))
            val x = -g.tilt(0).x
            assertTrue("θ=$theta -> $x", x < 1f && x >= prev - 1e-6f)
            prev = x
            if (theta < 0.05f) assertEquals(expected(theta), x, 0.01f)
        }
        assertTrue(prev > 0.97f)
    }

    @Test fun smoothingRisesGraduallyAndConverges() {
        val f = ParallaxFilter(frozen)
        f.update(Quat.IDENTITY, 0.016f)
        val target = turned(Quat.IDENTITY, 0f, 1f, 0f, 0.2f)
        f.update(target, 0.016f)
        val first = -f.tilt(0).x
        assertTrue("langkah pertama sudah melompat ke target: $first", first > 0f && first < 0.6f * expected(0.2f))
        settle(f, target, 2f)
        assertEquals(expected(0.2f), -f.tilt(0).x, 0.005f)
    }

    // ---------------------------------------------------------------- baseline

    @Test fun baselineFollowsTheNewHoldingPose() {
        val f = ParallaxFilter()
        f.update(Quat.IDENTITY, 0.016f)
        val tilted = turned(Quat.IDENTITY, 0f, 1f, 0f, 0.2f)
        settle(f, tilted, 0.6f)
        assertTrue("saat baru miring harus terasa", abs(f.tilt(0).x) > 0.2f)
        settle(f, tilted, 25f)
        assertEquals("setelah ditahan lama pose itu menjadi netral", 0f, f.tilt(0).x, 0.02f)
        // Kembali ke pose lama sekarang terasa sebagai gerak berlawanan
        settle(f, Quat.IDENTITY, 0.6f)
        assertTrue(f.tilt(0).x > 0.2f)
    }

    @Test fun liftingOrRotatingThePhoneRecentersInsteadOfFlinging() {
        val f = ParallaxFilter(frozen)
        f.update(Quat.IDENTITY, 0.016f)
        f.update(turned(Quat.IDENTITY, 0f, 0f, 1f, 1.5f), 0.016f)        // diputar 86° pada bidang layar
        assertEquals(Tilt.ZERO, f.tilt(0))
        // dan pose baru itu kini baseline
        settle(f, turned(Quat.IDENTITY, 0f, 0f, 1f, 1.5f))
        assertEquals(Tilt.ZERO, f.tilt(0))
    }

    @Test fun justBelowTheResetAngleStillTilts() {
        val f = ParallaxFilter(frozen)
        f.update(Quat.IDENTITY, 0.016f)
        settle(f, turned(Quat.IDENTITY, 0f, 1f, 0f, 0.85f))
        assertTrue(-f.tilt(0).x > 0.9f)
    }

    @Test fun resetForgetsTheBaseline() {
        val f = ParallaxFilter(frozen)
        f.update(Quat.IDENTITY, 0.016f)
        settle(f, turned(Quat.IDENTITY, 0f, 1f, 0f, 0.2f))
        assertTrue(f.tilt(0).x != 0f)
        f.reset()
        assertEquals(Tilt.ZERO, f.tilt(0))
        f.update(turned(Quat.IDENTITY, 0f, 1f, 0f, 0.2f), 0.016f)
        assertEquals("sampel pertama setelah reset menjadi pose netral", Tilt.ZERO, f.tilt(0))
    }

    @Test fun theSameOrientationWithFlippedQuaternionSignMovesNothing() {
        // q dan -q adalah rotasi yang sama; sensor boleh memberi salah satunya
        val f = ParallaxFilter(frozen)
        val q = turned(Quat.IDENTITY, 0.3f, 0.2f, 0.9f, 0.7f)
        f.update(q, 0.016f)
        settle(f, Quat(-q.w, -q.x, -q.y, -q.z))
        assertEquals(Tilt.ZERO, f.tilt(0))
        // dan rotasi kecil dengan tanda terbalik tetap diukur sebagai rotasi kecil (bukan hampir 360°)
        val g = ParallaxFilter(frozen)
        g.update(q, 0.016f)
        val nudged = turned(q, 0f, 1f, 0f, 0.15f)
        settle(g, Quat(-nudged.w, -nudged.x, -nudged.y, -nudged.z))
        assertEquals(-expected(0.15f), g.tilt(0).x, 0.02f)
    }

    // ---------------------------------------------------------------- masukan buruk

    @Test fun badInputsAreIgnored() {
        val f = ParallaxFilter(frozen)
        f.update(Quat.IDENTITY, 0.016f)
        settle(f, turned(Quat.IDENTITY, 0f, 1f, 0f, 0.2f))
        val before = f.tilt(0)
        f.update(Quat(Float.NaN, 0f, 0f, 0f), 0.016f)
        f.update(Quat(1f, Float.POSITIVE_INFINITY, 0f, 0f), 0.016f)
        assertEquals(before, f.tilt(0))
        val t = turned(Quat.IDENTITY, 0f, 1f, 0f, 0.2f)
        f.update(t, Float.NaN)
        f.update(t, -3f)
        f.update(t, Float.POSITIVE_INFINITY)
        val after = f.tilt(0)
        assertTrue(after.x.isFinite() && after.y.isFinite())
    }

    @Test fun aLongPauseDoesNotJumpTheResult() {
        val a = ParallaxFilter(frozen)
        val b = ParallaxFilter(frozen)
        for (f in listOf(a, b)) f.update(Quat.IDENTITY, 0.016f)
        val target = turned(Quat.IDENTITY, 0f, 1f, 0f, 0.2f)
        a.update(target, 600f)                   // aplikasi sempat di latar 10 menit
        b.update(target, ParallaxFilter.MAX_DT)
        assertEquals(b.tilt(0), a.tilt(0))
        assertFalse("dt dibatasi sehingga satu sampel tidak langsung sampai target", -a.tilt(0).x > 0.99f * expected(0.2f))
    }

    // ---------------------------------------------------------------- jalur gravitasi (tanpa giroskop)

    private fun gravityQuat(deviceQ: Quat, up0: FloatArray): Quat {
        // Gravitasi yang dirasakan perangkat yang telah berputar: vektor atas dunia di kerangka badan baru
        val u = rotate(deviceQ.conjugate(), up0)
        return Quat.fromGravity(u[0] * 9.8f, u[1] * 9.8f, u[2] * 9.8f)!!
    }

    @Test fun gravityPathSeesPitchLikeTheGyroPath() {
        val up0 = floatArrayOf(0f, 0.766f, 0.643f)               // ponsel disandarkan ±40°
        val f = ParallaxFilter(frozen)
        f.update(gravityQuat(Quat.IDENTITY, up0), 0.016f)
        val theta = 0.15f
        settle(f, gravityQuat(Quat.fromAxisAngle(1f, 0f, 0f, theta), up0))
        assertEquals(expected(theta), f.tilt(0).y, 0.02f)
        assertEquals(0f, f.tilt(0).x, 0.01f)
    }

    @Test fun gravityPathIsBlindToTurningAnUprightPhoneAroundTheVertical() {
        val up0 = floatArrayOf(0f, 1f, 0f)
        val f = ParallaxFilter(frozen)
        f.update(gravityQuat(Quat.IDENTITY, up0), 0.016f)
        settle(f, gravityQuat(Quat.fromAxisAngle(0f, 1f, 0f, 0.3f), up0))
        assertEquals("keterbatasan fisik gravitasi, bukan bug", 0f, f.tilt(0).x, 0.01f)
    }

    @Test fun gravityPathSeesLeftRightWeaklyWhenTheUserTiltsBack() {
        val up0 = floatArrayOf(0f, 0.766f, 0.643f)
        val f = ParallaxFilter(frozen)
        f.update(gravityQuat(Quat.IDENTITY, up0), 0.016f)
        settle(f, gravityQuat(Quat.fromAxisAngle(0f, 1f, 0f, 0.2f), up0))
        val x = f.tilt(0).x
        assertTrue("arah sama dengan jalur giroskop: $x", x < 0f)
        assertTrue("lebih lemah daripada jalur giroskop", -x < expected(0.2f))
    }

    // ---------------------------------------------------------------- ParallaxMath

    @Test fun windowShiftFollowsTheEyeForFarLayers() {
        val s = ParallaxMath.layerShift(Tilt(1f, 1f), depth = 1f, maxShiftPx = 10f, mode = ParallaxMode.WINDOW, strength = 1f)
        assertEquals(10f, s.dx, 1e-6f)
        assertEquals("mata di atas → lapisan naik; piksel y ke bawah", -10f, s.dy, 1e-6f)
    }

    @Test fun floatModeReversesAndNearLayersOppose() {
        val w = ParallaxMath.layerShift(Tilt(0.5f, -0.25f), 1f, 8f, ParallaxMode.WINDOW, 1f)
        val f = ParallaxMath.layerShift(Tilt(0.5f, -0.25f), 1f, 8f, ParallaxMode.FLOAT, 1f)
        assertEquals(-w.dx, f.dx, 1e-6f); assertEquals(-w.dy, f.dy, 1e-6f)
        val near = ParallaxMath.layerShift(Tilt(0.5f, -0.25f), -1f, 8f, ParallaxMode.WINDOW, 1f)
        assertEquals(-w.dx, near.dx, 1e-6f); assertEquals(-w.dy, near.dy, 1e-6f)
    }

    @Test fun shiftScalesWithDepthStrengthAndTilt() {
        val base = ParallaxMath.layerShift(Tilt(1f, 0f), 1f, 10f, ParallaxMode.WINDOW, 1f).dx
        assertEquals(base / 2, ParallaxMath.layerShift(Tilt(1f, 0f), 0.5f, 10f, ParallaxMode.WINDOW, 1f).dx, 1e-6f)
        assertEquals(base / 4, ParallaxMath.layerShift(Tilt(1f, 0f), 1f, 10f, ParallaxMode.WINDOW, 0.25f).dx, 1e-6f)
        assertEquals(base / 2, ParallaxMath.layerShift(Tilt(0.5f, 0f), 1f, 10f, ParallaxMode.WINDOW, 1f).dx, 1e-6f)
        assertEquals(0f, ParallaxMath.layerShift(Tilt(1f, 1f), 0f, 10f, ParallaxMode.WINDOW, 1f).dx, 0f)
    }

    @Test fun offAndBadValuesNeverMove() {
        assertEquals(Shift.ZERO, ParallaxMath.layerShift(Tilt(1f, 1f), 1f, 10f, ParallaxMode.OFF, 1f))
        assertEquals(Shift.ZERO, ParallaxMath.layerShift(Tilt(Float.NaN, 1f), 1f, 10f, ParallaxMode.WINDOW, 1f))
        assertEquals(Shift.ZERO, ParallaxMath.layerShift(Tilt(1f, 1f), Float.NaN, 10f, ParallaxMode.WINDOW, 1f))
        assertEquals(Shift.ZERO, ParallaxMath.layerShift(Tilt(1f, 1f), 1f, Float.POSITIVE_INFINITY, ParallaxMode.WINDOW, 1f))
        assertEquals(Shift.ZERO, ParallaxMath.layerShift(Tilt(1f, 1f), 1f, 10f, ParallaxMode.WINDOW, Float.NaN))
        assertEquals(0f, ParallaxMath.layerShift(Tilt(1f, 1f), 1f, 10f, ParallaxMode.WINDOW, 0f).dx, 0f)
        assertEquals(0f, ParallaxMath.layerShift(Tilt(1f, 1f), 1f, -10f, ParallaxMode.WINDOW, 1f).dx, 0f)
    }

    @Test fun shiftIsClampedToTheDocumentedRange() {
        val s = ParallaxMath.layerShift(Tilt(1f, 1f), 50f, 10f, ParallaxMode.WINDOW, 9f)
        assertEquals(10f, s.dx, 1e-6f)
        val n = ParallaxMath.layerShift(Tilt(1f, 1f), -50f, 10f, ParallaxMode.WINDOW, -9f)
        assertEquals("kekuatan negatif dipotong ke 0", 0f, n.dx, 0f)
    }

    @Test fun coverScaleHidesTheEdgesAtTheLargestShift() {
        assertEquals(1f, ParallaxMath.coverScale(0f, 1080f, 2400f), 0f)
        val s = ParallaxMath.coverScale(40f, 1080f, 2400f)
        assertEquals(1f + 80f / 1080f, s, 1e-6f)                // sisi terpendek yang menentukan
        // Setelah diskala di sekitar pusat, tepi tetap di luar layar walau lapisan bergeser maksimum
        assertTrue(1080f / 2 * s - 40f >= 1080f / 2 - 1e-3f)
        assertTrue(2400f / 2 * s - 40f >= 2400f / 2 - 1e-3f)
        assertEquals(s, ParallaxMath.coverScale(40f, 2400f, 1080f), 0f)
        assertEquals("pergeseran negatif tidak berarti: tanpa skala", 1f, ParallaxMath.coverScale(-40f, 1080f, 2400f), 0f)
    }

    @Test fun coverScaleIgnoresGarbage() {
        assertEquals(1f, ParallaxMath.coverScale(Float.NaN, 100f, 100f), 0f)
        assertEquals(1f, ParallaxMath.coverScale(10f, 0f, 100f), 0f)
        assertEquals(1f, ParallaxMath.coverScale(10f, -5f, 100f), 0f)
        assertEquals(1f, ParallaxMath.coverScale(10f, Float.NaN, 100f), 0f)
        assertEquals(1f, ParallaxMath.coverScale(Float.POSITIVE_INFINITY, 100f, 100f), 0f)
    }

    @Test fun baselineAdvancesInTheBodyFrameEvenWhenTheStartingPoseIsNotIdentity() {
        // Dengan baseline identitas, b·step dan step·b sama; urutan hanya terlihat bila pose awal jauh dari identitas.
        // Penyusulan geodesik menyusutkan sisa putaran persis eksponensial: sisa = 0,3·exp(-t/tau), jadi nilai antaranya
        // bisa dihitung (bukan hanya titik akhir, yang bisa sama untuk jalur yang salah karena baseline diatur ulang).
        val cfg = ParallaxFilter.Config()
        for (turn in listOf(1.1f, 2.5f)) {
            val start = Quat.fromAxisAngle(0f, 0f, 1f, turn)
            val f = ParallaxFilter(cfg)
            f.update(start, 0.016f)
            val tilted = turned(start, 1f, 0f, 0f, 0.3f)             // miring 0,3 rad terhadap sumbu X BADAN
            val steps = (0.6f / 0.016f).toInt()
            settle(f, tilted, 0.6f)
            val residual = 0.3f * exp(-steps * 0.016f / cfg.baselineTauSec)
            val want = tanh((residual - cfg.deadZoneRad) / cfg.maxTiltRad)
            assertEquals("pose awal ${turn} rad", want, f.tilt(0).y, 0.03f)
            assertEquals("tidak ada sumbu lain yang ikut miring", 0f, f.tilt(0).x, 0.03f)
            settle(f, tilted, 30f)
            assertEquals(0f, f.tilt(0).x, 0.02f)
            assertEquals(0f, f.tilt(0).y, 0.02f)
        }
    }
}
