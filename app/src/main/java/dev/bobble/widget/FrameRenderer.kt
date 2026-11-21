package dev.bobble.widget

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import kotlin.math.roundToInt

/**
 * Draws one frame: the chosen catalog body + head rotated about the neck pivot, on a transparent square.
 * Frames are produced on demand (one per widget update), never held as a whole sequence,
 * except in lite mode (see ShakeController).
 */
object FrameRenderer {

    /** Widget frame side in px. Kept <= 300 to stay well inside RemoteViews bitmap limits. */
    const val FRAME_PX = 300

    /** Smaller frames for the pre-rendered fallback. */
    const val LITE_PX = 240

    private const val PLACEHOLDER_HEAD_ASPECT = 0.85f

    /** Widget frame: head comes from the saved PNG at [headPath] (null = placeholder head). */
    fun renderWidgetFrame(headPath: String?, bodyId: String?, angleDeg: Float, sizePx: Int): Bitmap {
        val spec = BodyCatalog.byId(bodyId)
        val original = headPath?.let { HeadCache.original(it) }
        val layout = layoutFor(spec, original, sizePx)
        val head = headPath?.let {
            HeadCache.scaled(it, layout.head.width.roundToInt(), layout.head.height.roundToInt())
        }
        val skin = headPath?.let { HeadCache.skin(it) } ?: SkinTone.DEFAULT
        return draw(sizePx, layout, spec, skin, head, angleDeg)
    }

    /** Config-screen preview / catalog thumbnail: head is an in-memory bitmap (not yet saved). */
    fun renderPreview(head: Bitmap?, bodyId: String?, skin: Int, angleDeg: Float, sizePx: Int): Bitmap {
        val spec = BodyCatalog.byId(bodyId)
        return draw(sizePx, layoutFor(spec, head, sizePx), spec, skin, head, angleDeg)
    }

    private fun layoutFor(spec: BodySpec, head: Bitmap?, sizePx: Int): FigureLayout {
        val aspect = head?.let { it.width.toFloat() / it.height } ?: PLACEHOLDER_HEAD_ASPECT
        return FigureLayout.compute(sizePx.toFloat(), spec.aspect, aspect, spec.neckPivotX, spec.neckPivotY)
    }

    private fun draw(
        sizePx: Int,
        layout: FigureLayout,
        spec: BodySpec,
        skin: Int,
        head: Bitmap?,
        angleDeg: Float,
    ): Bitmap {
        val out = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888) // transparent
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        val b = layout.body
        val body = BodyProvider.get(spec, skin, b.height.roundToInt())
        canvas.drawBitmap(body, null, RectF(b.left, b.top, b.right, b.bottom), paint)

        val h = layout.head
        val headRect = RectF(h.left, h.top, h.right, h.bottom)
        canvas.save()
        canvas.rotate(angleDeg, layout.pivotX, layout.pivotY)
        if (head != null) {
            canvas.drawBitmap(head, null, headRect, paint)
        } else {
            drawPlaceholderHead(canvas, headRect)
        }
        canvas.restore()
        return out
    }

    /** Shown until a photo is picked: blank head with a "+". */
    private fun drawPlaceholderHead(canvas: Canvas, r: RectF) {
        val stroke = r.height() * 0.04f
        val inner = RectF(r).apply { inset(stroke, stroke) }
        canvas.drawOval(inner, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFECEFF1.toInt() })
        canvas.drawOval(inner, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = stroke; color = 0xFFFFFFFF.toInt()
        })
        val plus = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = stroke * 1.2f
            color = 0xFF607D8B.toInt(); strokeCap = Paint.Cap.ROUND
        }
        val arm = r.width() * 0.16f
        canvas.drawLine(r.centerX() - arm, r.centerY(), r.centerX() + arm, r.centerY(), plus)
        canvas.drawLine(r.centerX(), r.centerY() - arm, r.centerX(), r.centerY() + arm, plus)
    }
}

/** Decoded head PNGs, pre-scaled copies and sampled skin tone, keyed by file path. Small LRU. */
object HeadCache {
    private val cache = object : LinkedHashMap<String, Bitmap>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>?) = size > 8
    }
    private val skins = HashMap<String, Int>()

    @Synchronized
    fun original(path: String): Bitmap? =
        cache[path] ?: BitmapFactory.decodeFile(path)?.also { cache[path] = it }

    @Synchronized
    fun scaled(path: String, w: Int, h: Int): Bitmap? {
        val key = "$path@${w}x$h"
        cache[key]?.let { return it }
        val src = original(path) ?: return null
        return BitmapUtil.scaleSmooth(src, w, h).also { cache[key] = it }
    }

    @Synchronized
    fun skin(path: String): Int =
        skins[path] ?: SkinTone.estimate(original(path)).also { skins[path] = it }
}

/** Catalog bodies, drawn directly at the needed height (sharp at any size) and cached. */
object BodyProvider {
    private val cache = object : LinkedHashMap<String, Bitmap>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>?) = size > 12
    }

    @Synchronized
    fun get(spec: BodySpec, skin: Int, heightPx: Int): Bitmap {
        val h = heightPx.coerceAtLeast(16)
        val key = "${spec.id}|$skin|$h"
        cache[key]?.let { return it }
        val bmp = when (spec.id) {
            "football" -> BodyPainter.drawSeatedFootball(h, skin)
            else -> StandingBodyPainter.draw(h, StandingBodyPainter.outfitFor(spec.id), skin)
        }
        cache[key] = bmp
        return bmp
    }
}
