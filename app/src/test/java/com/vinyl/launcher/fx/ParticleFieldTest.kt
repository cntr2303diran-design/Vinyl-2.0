package com.vinyl.launcher.fx

import com.vinyl.launcher.ui.theme.Tokens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ParticleFieldTest {
    private fun snapshot(p: ParticleField) = p.x.toList() + p.y.toList() + p.depth.toList()
    private fun run(p: ParticleField, steps: Int, dt: Float = 0.016f, energy: Float = 0f) = repeat(steps) { p.step(dt, energy) }

    @Test fun sameSeedAndSameStepsGiveTheSameResult() {
        val a = ParticleField(seed = 5)
        val b = ParticleField(seed = 5)
        assertEquals(snapshot(a), snapshot(b))
        run(a, 500, energy = 0.4f); run(b, 500, energy = 0.4f)
        assertEquals(snapshot(a), snapshot(b))
    }

    @Test fun differentSeedsGiveDifferentFields() {
        assertNotEquals(snapshot(ParticleField(seed = 1)), snapshot(ParticleField(seed = 2)))
    }

    @Test fun particlesStayInsideTheFieldForever() {
        val p = ParticleField(seed = 9)
        val m = ParticleField.MARGIN
        repeat(20) {
            run(p, 1000, dt = 0.05f, energy = if (it % 2 == 0) 1f else 0f)
            for (i in 0 until p.capacity) {
                assertTrue("x=${p.x[i]}", p.x[i] in -m - 1e-3f..1f + m + 1e-3f)
                assertTrue("y=${p.y[i]}", p.y[i] in -m - 1e-3f..1f + m + 0.01f)
                assertTrue(p.depth[i] in 0f..1f)
            }
        }
    }

    @Test fun particlesDriftUpwardAndRespawnBelow() {
        val p = ParticleField(seed = 3)
        val before = p.y.copyOf()
        p.step(0.05f, 0f)
        for (i in 0 until p.capacity) if (before[i] > 0.2f) assertTrue(p.y[i] < before[i])
        // Setelah cukup lama, semua partikel pernah keluar di atas dan masuk lagi dari bawah
        var respawned = 0
        val last = p.y.copyOf()
        repeat(4000) {
            p.step(0.05f, 0f)
            for (i in 0 until p.capacity) { if (p.y[i] > last[i] + 0.5f) respawned++; last[i] = p.y[i] }
        }
        assertTrue("respawn=$respawned", respawned >= p.capacity)
    }

    @Test fun energySpeedsParticlesUp() {
        val calm = ParticleField(seed = 4)
        val loud = ParticleField(seed = 4)
        val y0 = calm.y.copyOf()
        calm.step(0.05f, 0f); loud.step(0.05f, 1f)
        var calmMove = 0f; var loudMove = 0f
        for (i in 0 until calm.capacity) { calmMove += y0[i] - calm.y[i]; loudMove += y0[i] - loud.y[i] }
        assertTrue("calm=$calmMove loud=$loudMove", loudMove > calmMove * 2f)
    }

    @Test fun alphaNeverExceedsTheContrastBudget() {
        assertTrue(ParticleField.MAX_ALPHA <= Tokens.A11y.FX_PEAK)
        val p = ParticleField(seed = 11)
        repeat(300) { n ->
            p.step(0.03f, (n % 11) / 10f)
            for (i in 0 until p.capacity) for (e in listOf(0f, 0.5f, 1f, 7f, -3f, Float.NaN)) {
                val a = p.alpha(i, e)
                assertTrue("alpha=$a", a in 0f..ParticleField.MAX_ALPHA)
            }
        }
    }

    @Test fun nearParticlesAreBiggerAndBrighterOnAverage() {
        val p = ParticleField(capacity = 96, seed = 8)
        val sorted = (0 until p.capacity).sortedBy { p.depth[it] }
        val far = sorted.take(30); val near = sorted.takeLast(30)
        assertTrue(far.map { p.radius(it) }.average() < near.map { p.radius(it) }.average())
        for (i in 0 until p.capacity) {
            assertTrue(p.radius(i) in ParticleField.MIN_RADIUS..ParticleField.MAX_RADIUS * 1.25f)
        }
        assertTrue(p.radius(0, 1f) > p.radius(0, 0f))
        assertEquals(p.radius(0, 0f), p.radius(0, Float.NaN), 0f)
    }

    @Test fun badTimeAndEnergyAreHarmless() {
        val p = ParticleField(seed = 6)
        val before = snapshot(p)
        p.step(Float.NaN, 0.5f); p.step(Float.POSITIVE_INFINITY, 0.5f)
        assertEquals(before, snapshot(p))
        p.step(-5f, 0.5f)                                   // dt negatif dipotong ke 0
        assertEquals(before, snapshot(p))
        p.step(0.016f, Float.NaN)
        assertTrue(snapshot(p).all { it.isFinite() })
    }

    @Test fun aHugeTimeStepIsCappedLikeALongFrame() {
        val a = ParticleField(seed = 12)
        val b = ParticleField(seed = 12)
        a.step(600f, 0f)
        b.step(ParticleField.MAX_DT, 0f)
        assertEquals(snapshot(a), snapshot(b))
    }

    @Test fun capacityIsBounded() {
        for (bad in listOf(0, -1, ParticleField.MAX_CAPACITY + 1)) {
            try { ParticleField(capacity = bad); fail("capacity $bad seharusnya ditolak") } catch (_: IllegalArgumentException) {}
        }
        assertEquals(1, ParticleField(1).capacity)
        assertEquals(ParticleField.MAX_CAPACITY, ParticleField(ParticleField.MAX_CAPACITY).capacity)
        assertTrue(ParticleField.DEFAULT_CAPACITY <= 48)       // anggaran gambar per frame
    }

    @Test fun positionsDoNotDependOnScreenSize() {
        // Koordinat satuan: tidak ada piksel di simulasi
        val p = ParticleField(seed = 2)
        assertFalse(p.x.any { it > 1.5f || it < -0.5f })
    }

    @Test fun particlesThatLeaveASideReenterFromTheOtherSide() {
        // Dengan konstanta sekarang ayunan hampir tak pernah melewati tepi; pembungkus adalah jaring pengaman dan
        // harus tetap bekerja bila keadaan di luar rentang (mis. setelah perubahan konstanta).
        val f = ParticleField(capacity = 4)
        val wrap = 1f + 2f * ParticleField.MARGIN
        f.x[0] = -0.3f; f.x[1] = 1.4f; f.x[2] = 0.5f
        f.step(0.016f, 0f)
        assertEquals(-0.3f + wrap, f.x[0], 0.01f)
        assertEquals(1.4f - wrap, f.x[1], 0.01f)
        assertEquals("yang di dalam tidak ikut dibungkus", 0.5f, f.x[2], 0.01f)
        for (i in 0 until f.capacity) assertTrue("x[$i]=${f.x[i]}", f.x[i] in -ParticleField.MARGIN..1f + ParticleField.MARGIN)
    }
}
