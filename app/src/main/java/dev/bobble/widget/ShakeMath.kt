package dev.bobble.widget

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Pure (Android-free) math for the shake animation. Unit-tested in ShakeMathTest.
 *
 * Angles are in degrees. Positive = clockwise on screen (matches android.graphics.Canvas.rotate).
 */
object ShakeMath {

    /** Damped wobble: start upright, swing +14, -10, +7, -4, +2, settle at 0. */
    val KEYFRAMES = floatArrayOf(0f, 14f, -10f, 7f, -4f, 2f, 0f)

    /** Normal mode: 12 frames x 75 ms = 900 ms, rendered on the fly. */
    const val LIVE_FRAMES = 12
    const val LIVE_FRAME_MS = 75L

    /** Fallback ("lite") mode for launchers that throttle updates: 8 pre-rendered frames x 125 ms. */
    const val LITE_FRAMES = 8
    const val LITE_FRAME_MS = 125L

    /** A frame whose update took longer than this multiple of its budget counts as late. */
    const val LATE_FACTOR = 1.5f

    /**
     * Samples the keyframe path into [frameCount] angles. Frame 0 is the first moved frame
     * (the widget is already showing the upright pose), the last frame is exactly the last keyframe (0).
     * Each keyframe segment is eased with a cosine in-out curve.
     */
    fun angles(frameCount: Int, keyframes: FloatArray = KEYFRAMES): FloatArray {
        require(frameCount >= 2) { "need at least 2 frames" }
        require(keyframes.size >= 2) { "need at least 2 keyframes" }
        val segments = keyframes.size - 1
        val out = FloatArray(frameCount) { i ->
            val t = (i + 1).toFloat() / frameCount * segments
            val seg = min(t.toInt(), segments - 1)
            val local = t - seg
            lerp(keyframes[seg], keyframes[seg + 1], easeInOut(local))
        }
        out[out.lastIndex] = keyframes.last() // guarantee the final frame is upright
        return out
    }

    fun easeInOut(t: Float): Float = (0.5 - 0.5 * cos(PI * t.coerceIn(0f, 1f))).toFloat()

    fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

    /**
     * Rotates point (x, y) about pivot (px, py) by [degrees], in screen coordinates (y grows down),
     * matching Canvas.rotate(degrees, px, py).
     */
    fun rotatePoint(x: Float, y: Float, px: Float, py: Float, degrees: Float): Pair<Float, Float> {
        val r = degrees * PI / 180.0
        val c = cos(r)
        val s = sin(r)
        val dx = x - px
        val dy = y - py
        return Pair((px + dx * c - dy * s).toFloat(), (py + dx * s + dy * c).toFloat())
    }

    fun totalDurationMs(frames: Int, frameMs: Long): Long = frames * frameMs

    fun isLate(spentMs: Long, budgetMs: Long): Boolean = spentMs > budgetMs * LATE_FACTOR

    /** Switch to lite mode when at least half of the frames were late. */
    fun shouldFallBack(lateFrames: Int, totalFrames: Int): Boolean =
        totalFrames > 0 && lateFrames * 2 >= totalFrames
}

/** Hermite smoothstep: 0 below [edge0], 1 above [edge1], smooth in between. */
fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
    val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}
