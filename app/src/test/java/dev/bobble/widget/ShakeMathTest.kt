package dev.bobble.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sign

class ShakeMathTest {

    private val eps = 1e-4f

    @Test
    fun liveSequenceHasRequestedLengthAndEndsUpright() {
        val a = ShakeMath.angles(ShakeMath.LIVE_FRAMES)
        assertEquals(12, a.size)
        assertEquals(0f, a.last(), 0f)
        assertTrue("first frame must move", abs(a.first()) > 1f)
    }

    @Test
    fun liveSequenceHitsEveryKeyframeAndIsDamped() {
        // With 12 frames and 6 segments, every odd frame lands exactly on a keyframe.
        val a = ShakeMath.angles(12)
        val peaks = listOf(a[1], a[3], a[5], a[7], a[9])
        assertEquals(listOf(14f, -10f, 7f, -4f, 2f).size, peaks.size)
        listOf(14f, -10f, 7f, -4f, 2f).zip(peaks).forEach { (want, got) -> assertEquals(want, got, eps) }
        for (i in 1 until peaks.size) {
            assertTrue("amplitude must shrink", abs(peaks[i]) < abs(peaks[i - 1]))
            assertTrue("direction must alternate", sign(peaks[i]) != sign(peaks[i - 1]))
        }
    }

    @Test
    fun anglesNeverExceedFirstSwing() {
        for (n in listOf(8, 10, 11, 12)) {
            ShakeMath.angles(n).forEach { assertTrue("|$it| <= 14 for n=$n", abs(it) <= 14f + eps) }
        }
    }

    @Test
    fun liteSequenceEndsUpright() {
        val a = ShakeMath.angles(ShakeMath.LITE_FRAMES)
        assertEquals(8, a.size)
        assertEquals(0f, a.last(), 0f)
    }

    @Test
    fun durationsFitTheSpec() {
        val live = ShakeMath.totalDurationMs(ShakeMath.LIVE_FRAMES, ShakeMath.LIVE_FRAME_MS)
        assertTrue("live $live ms in 800..1000", live in 800L..1000L)
        assertTrue(ShakeMath.LIVE_FRAME_MS in 70L..80L)
        val lite = ShakeMath.totalDurationMs(ShakeMath.LITE_FRAMES, ShakeMath.LITE_FRAME_MS)
        assertTrue("lite $lite ms <= 1200", lite <= 1200L)
    }

    @Test
    fun easingIsMonotonicWithFixedEnds() {
        assertEquals(0f, ShakeMath.easeInOut(0f), eps)
        assertEquals(0.5f, ShakeMath.easeInOut(0.5f), eps)
        assertEquals(1f, ShakeMath.easeInOut(1f), eps)
        var prev = -1f
        for (i in 0..100) {
            val v = ShakeMath.easeInOut(i / 100f)
            assertTrue(v >= prev)
            prev = v
        }
    }

    @Test
    fun rotationKeepsPivotFixed() {
        val (x, y) = ShakeMath.rotatePoint(50f, 80f, 50f, 80f, 14f)
        assertEquals(50f, x, eps)
        assertEquals(80f, y, eps)
    }

    @Test
    fun positiveAngleIsClockwiseOnScreen() {
        // A point straight above the pivot moves right when rotated +90 (y grows down).
        val (x, y) = ShakeMath.rotatePoint(0f, -10f, 0f, 0f, 90f)
        assertEquals(10f, x, eps)
        assertEquals(0f, y, eps)
    }

    @Test
    fun rotationPreservesDistanceToPivot() {
        val (x, y) = ShakeMath.rotatePoint(30f, -40f, 0f, 0f, -10f)
        assertEquals(50f, kotlin.math.hypot(x, y), 1e-3f)
    }

    @Test
    fun fallbackTriggersWhenHalfTheFramesAreLate() {
        assertFalse(ShakeMath.isLate(100, 75))
        assertTrue(ShakeMath.isLate(120, 75))
        assertFalse(ShakeMath.shouldFallBack(5, 12))
        assertTrue(ShakeMath.shouldFallBack(6, 12))
        assertFalse(ShakeMath.shouldFallBack(0, 0))
    }

    @Test
    fun smoothstepClampsAndInterpolates() {
        assertEquals(0f, smoothstep(0.3f, 0.7f, 0.1f), eps)
        assertEquals(0.5f, smoothstep(0.3f, 0.7f, 0.5f), eps)
        assertEquals(1f, smoothstep(0.3f, 0.7f, 0.9f), eps)
    }
}
