package dev.bobble.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/** Adds the white die-cut "sticker" border around everything opaque in a bitmap. */
object StickerOutline {

    /**
     * @param strokePx outline thickness in px
     * @param pad extra transparent border added on every side (use >= strokePx so the outline
     *            is not clipped; use 0 when the source already has enough empty margin)
     * @return a new bitmap (the source is not modified or recycled)
     */
    fun apply(src: Bitmap, strokePx: Float, pad: Int): Bitmap {
        val w = src.width
        val h = src.height
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)
        // Solid white silhouette with a slightly soft edge.
        for (i in px.indices) {
            val a = px[i] ushr 24
            val m = ((a - 40) * 255 / 120).coerceIn(0, 255)
            px[i] = (m shl 24) or 0x00FFFFFF
        }
        val silhouette = Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)

        val out = Bitmap.createBitmap(w + 2 * pad, h + 2 * pad, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        val steps = max(16, (strokePx * 2.5f).roundToInt()).coerceAtMost(48)
        // Stamp the silhouette around two rings: cheap morphological dilation.
        for (ring in floatArrayOf(strokePx, strokePx * 0.5f)) {
            for (k in 0 until steps) {
                val t = 2.0 * PI * k / steps
                canvas.drawBitmap(
                    silhouette,
                    pad + (cos(t) * ring).toFloat(),
                    pad + (sin(t) * ring).toFloat(),
                    paint,
                )
            }
        }
        silhouette.recycle()
        canvas.drawBitmap(src, pad.toFloat(), pad.toFloat(), paint)
        return out
    }
}

object BitmapUtil {

    /** Downscale in halving steps so large reductions stay smooth. Returns [src] if already that size. */
    fun scaleSmooth(src: Bitmap, targetW: Int, targetH: Int): Bitmap {
        val tw = targetW.coerceAtLeast(1)
        val th = targetH.coerceAtLeast(1)
        if (src.width == tw && src.height == th) return src
        var cur = src
        while (cur.width / 2 >= tw && cur.height / 2 >= th) {
            val next = Bitmap.createScaledBitmap(cur, cur.width / 2, cur.height / 2, true)
            if (cur !== src) cur.recycle()
            cur = next
        }
        val out = Bitmap.createScaledBitmap(cur, tw, th, true)
        if (cur !== src && cur !== out) cur.recycle()
        return out
    }

    /** Crops away fully transparent borders. Returns null if nothing is visible, [src] if no crop needed. */
    fun trimTransparent(src: Bitmap, threshold: Int = 8): Bitmap? {
        val w = src.width
        val h = src.height
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)
        var minX = w
        var minY = h
        var maxX = -1
        var maxY = -1
        for (y in 0 until h) {
            val row = y * w
            for (x in 0 until w) {
                if ((px[row + x] ushr 24) > threshold) {
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
            }
        }
        if (maxX < 0) return null
        if (minX == 0 && minY == 0 && maxX == w - 1 && maxY == h - 1) return src
        return Bitmap.createBitmap(src, minX, minY, maxX - minX + 1, maxY - minY + 1)
    }
}
