package com.vinyl.launcher.fx

import com.vinyl.launcher.data.ParallaxMode
import com.vinyl.launcher.data.VisualSettings
import com.vinyl.launcher.data.VisualizerMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FxPlanTest {
    private val env = FxEnv(apiLevel = 34, reduceMotion = false, powerSave = false, playing = true, visible = true, micGranted = false, hasMotionSensor = true)
    private val all = VisualSettings()
    private fun plan(s: VisualSettings = all, e: FxEnv = env) = FxPlan.of(s, e)

    @Test fun defaultsOnAModernPhonePlayingMusic() {
        val p = plan()
        assertTrue(p.agsl && p.distortion && p.glow && p.particles && p.animateParticles && p.parallax)
        assertEquals(VisualizerPlan.OFF, p.visualizer)
        assertEquals("tanpa visualizer, energi hanya napas pelan", EnergySource.BREATH, p.energy)
        assertEquals(FxBlock.NONE, p.block)
        assertTrue(p.animating && p.anything)
    }

    @Test fun reduceMotionAndPowerSaveTurnEverythingOffAndSayWhy() {
        for ((e, why) in listOf(env.copy(reduceMotion = true) to FxBlock.REDUCE_MOTION, env.copy(powerSave = true) to FxBlock.POWER_SAVE)) {
            val p = plan(VisualSettings(visualizer = VisualizerMode.DECORATIVE), e)
            assertEquals(why, p.block)
            assertFalse(p.anything || p.animating)
            assertEquals(VisualizerPlan.OFF, p.visualizer)
            assertEquals(EnergySource.NONE, p.energy)
        }
        assertEquals("gerak dikurangi lebih dulu dijelaskan", FxBlock.REDUCE_MOTION, plan(all, env.copy(reduceMotion = true, powerSave = true)).block)
    }

    @Test fun nothingRunsWhileTheScreenIsNotVisible() {
        val p = plan(VisualSettings(visualizer = VisualizerMode.REAL), env.copy(visible = false, micGranted = true))
        assertFalse(p.anything || p.animating)
        assertEquals(VisualizerPlan.OFF, p.visualizer)
        assertEquals("bukan diblokir, hanya tidak terlihat", FxBlock.NONE, p.block)
    }

    @Test fun agslOnlyFromApi33AndOlderPhonesStillGetGlowAndParticles() {
        assertFalse(plan(e = env.copy(apiLevel = 32)).agsl)
        assertFalse(plan(e = env.copy(apiLevel = 32)).distortion)
        assertTrue(plan(e = env.copy(apiLevel = 33)).distortion)
        assertFalse(plan(e = env.copy(apiLevel = 29)).distortion)
        val old = plan(e = env.copy(apiLevel = 29))
        assertTrue(old.glow && old.particles && old.parallax)
        assertEquals(33, FxPlan.AGSL_MIN_API)
    }

    @Test fun eachSwitchControlsOnlyItsEffect() {
        assertFalse(plan(all.copy(glassDistortion = false)).distortion)
        assertTrue(plan(all.copy(glassDistortion = false)).glow)
        assertFalse(plan(all.copy(reactiveGlow = false)).glow)
        assertFalse(plan(all.copy(particles = false)).particles)
        assertFalse(plan(all.copy(particles = false)).animateParticles)
        assertFalse(plan(all.copy(parallax = ParallaxMode.OFF)).parallax)
        assertTrue(plan(all.copy(parallax = ParallaxMode.FLOAT)).parallax)
        assertFalse(plan(e = env.copy(hasMotionSensor = false)).parallax)
    }

    @Test fun pausedMusicFreezesParticlesAndStopsTheFrameLoop() {
        val p = plan(e = env.copy(playing = false))
        assertTrue(p.particles)
        assertFalse(p.animateParticles)
        assertEquals(EnergySource.NONE, p.energy)
        assertTrue("parallax tetap hidup saat jeda", p.parallax && p.animating)
        val still = plan(all.copy(parallax = ParallaxMode.OFF), env.copy(playing = false))
        assertFalse("tanpa musik dan tanpa parallax tidak ada pekerjaan berulang", still.animating)
    }

    @Test fun realVisualizerNeedsPermissionAndMusicAndNeverFakesIt() {
        val real = VisualSettings(visualizer = VisualizerMode.REAL)
        assertEquals(VisualizerPlan.NEEDS_PERMISSION, plan(real, env.copy(micGranted = false)).visualizer)
        assertEquals(EnergySource.BREATH, plan(real, env.copy(micGranted = false)).energy)
        assertEquals(VisualizerPlan.REAL, plan(real, env.copy(micGranted = true)).visualizer)
        assertEquals(EnergySource.REAL, plan(real, env.copy(micGranted = true)).energy)
        assertEquals("tanpa musik Visualizer tidak dibuat dan izin tidak ditagih", VisualizerPlan.OFF, plan(real, env.copy(playing = false, micGranted = true)).visualizer)
        assertEquals(VisualizerPlan.OFF, plan(real, env.copy(playing = false, micGranted = false)).visualizer)
    }

    @Test fun decorativeVisualizerNeverTouchesThePermissionOrTheMicrophone() {
        val deco = VisualSettings(visualizer = VisualizerMode.DECORATIVE)
        for (granted in listOf(false, true)) {
            val p = plan(deco, env.copy(micGranted = granted))
            assertEquals(VisualizerPlan.DECORATIVE, p.visualizer)
            assertEquals(EnergySource.DECORATIVE, p.energy)
        }
        assertEquals(VisualizerPlan.OFF, plan(deco, env.copy(playing = false)).visualizer)
    }

    @Test fun visualizerOffMeansNoCaptureEvenWithPermission() {
        assertEquals(VisualizerPlan.OFF, plan(all, env.copy(micGranted = true)).visualizer)
    }

    @Test fun realVisualizerAloneStillAnimatesEvenIfEveryOtherEffectIsOff() {
        val s = VisualSettings(glassDistortion = false, reactiveGlow = false, particles = false, parallax = ParallaxMode.OFF, visualizer = VisualizerMode.REAL)
        val p = plan(s, env.copy(micGranted = true))
        assertEquals(VisualizerPlan.REAL, p.visualizer)
        assertTrue(p.animating)
    }

    @Test fun everythingOffMeansNothingRuns() {
        val s = VisualSettings(glassDistortion = false, reactiveGlow = false, particles = false, parallax = ParallaxMode.OFF, visualizer = VisualizerMode.OFF)
        val p = plan(s)
        assertFalse(p.anything || p.animating)
        assertEquals(EnergySource.NONE, p.energy)
    }

    @Test fun breathStaysGentleAndPeriodic() {
        var lo = 1f; var hi = 0f
        for (i in 0..600) { val e = Breath.energy(i / 100f); lo = minOf(lo, e); hi = maxOf(hi, e) }
        assertTrue("lo=$lo", lo >= 0.17f); assertTrue("hi=$hi", hi <= 0.43f)
        assertTrue(hi - lo > 0.2f)
        assertEquals(Breath.energy(1.3f), Breath.energy(1.3f + Breath.PERIOD_SEC), 1e-4f)
        assertEquals(0f, Breath.energy(Float.NaN), 0f)
    }
}
