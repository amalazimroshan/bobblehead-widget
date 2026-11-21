package dev.bobble.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.roundToInt

/** Shared drawing helpers for the clay-figurine look: soft left-to-right shading, darker outline. */
object Clay {
    fun mix(c: Int, with: Int, f: Float): Int {
        val g = f.coerceIn(0f, 1f)
        fun ch(a: Int, b: Int) = (a + (b - a) * g).roundToInt().coerceIn(0, 255)
        return Color.argb(
            Color.alpha(c),
            ch(Color.red(c), Color.red(with)),
            ch(Color.green(c), Color.green(with)),
            ch(Color.blue(c), Color.blue(with)),
        )
    }

    fun lighten(c: Int, f: Float) = mix(c, Color.WHITE, f)
    fun darken(c: Int, f: Float) = mix(c, Color.BLACK, f)

    /** Fill with a horizontal light-to-shadow gradient across [left, right]. */
    fun fill(color: Int, left: Float, right: Float): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(
            left, 0f, right, 0f,
            intArrayOf(lighten(color, 0.22f), color, darken(color, 0.22f)),
            floatArrayOf(0f, 0.45f, 1f),
            Shader.TileMode.CLAMP,
        )
    }

    fun flat(color: Int): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }

    fun outline(color: Int, width: Float): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = width
        this.color = darken(color, 0.45f)
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
}

/** Skin colour for hands and neck, sampled from the cutout face. */
object SkinTone {
    const val DEFAULT = 0xFFF1C27D.toInt()

    /** Averages opaque pixels in the cheek/nose area of a head cutout. */
    fun estimate(head: Bitmap?): Int {
        if (head == null) return DEFAULT
        val l = (head.width * 0.35f).toInt()
        val t = (head.height * 0.45f).toInt()
        val w = (head.width * 0.30f).toInt().coerceAtLeast(1)
        val h = (head.height * 0.25f).toInt().coerceAtLeast(1)
        if (l + w > head.width || t + h > head.height) return DEFAULT
        val px = IntArray(w * h)
        head.getPixels(px, 0, w, l, t, w, h)
        var r = 0L
        var g = 0L
        var b = 0L
        var n = 0
        for (p in px) {
            if ((p ushr 24) > 200) {
                r += (p shr 16) and 0xFF; g += (p shr 8) and 0xFF; b += p and 0xFF; n++
            }
        }
        if (n < 20) return DEFAULT
        return Color.rgb((r / n).toInt(), (g / n).toInt(), (b / n).toInt())
    }
}

/**
 * Catalog entry "football": small seated player in a red jersey holding a football.
 * Neck pivot = FigureLayout.NECK_PIVOT_X / NECK_PIVOT_Y. Aspect = BodyCatalog.SEATED_ASPECT.
 */
object BodyPainter {

    /** Transparent margin (fraction of height) so the sticker outline fits inside the bitmap. */
    const val PAD = 0.035f

    private const val JERSEY = 0xFFD32F2F.toInt()
    private const val SHORTS = 0xFFF5F5F5.toInt()
    private const val SHOE = 0xFF263238.toInt()
    private const val BALL = 0xFF8B4513.toInt()
    private const val WHITE = 0xFFFFFFFF.toInt()

    fun drawSeatedFootball(heightPx: Int, skin: Int): Bitmap {
        val h = heightPx.coerceAtLeast(16).toFloat()
        val w = h * BodyCatalog.SEATED_ASPECT
        val bmp = Bitmap.createBitmap(w.roundToInt(), h.roundToInt(), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)

        val pad = h * PAD
        val cw = w - 2 * pad
        val ch = h - 2 * pad
        fun x(f: Float) = pad + f * cw
        fun y(f: Float) = pad + f * ch
        fun rect(l: Float, t: Float, r: Float, b: Float) = RectF(x(l), y(t), x(r), y(b))
        val lineW = h * 0.011f

        fun roundRect(r: RectF, radius: Float, color: Int) {
            c.drawRoundRect(r, radius, radius, Clay.fill(color, r.left, r.right))
            c.drawRoundRect(r, radius, radius, Clay.outline(color, lineW))
        }

        fun oval(r: RectF, color: Int) {
            c.drawOval(r, Clay.fill(color, r.left, r.right))
            c.drawOval(r, Clay.outline(color, lineW))
        }

        fun limb(x0: Float, y0: Float, x1: Float, y1: Float, thick: Float, color: Int) {
            val outer = Clay.outline(color, thick + 2 * lineW)
            val inner = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE; strokeWidth = thick; this.color = color; strokeCap = Paint.Cap.ROUND
            }
            c.drawLine(x(x0), y(y0), x(x1), y(y1), outer)
            c.drawLine(x(x0), y(y0), x(x1), y(y1), inner)
        }

        roundRect(rect(0.42f, 0.0f, 0.58f, 0.14f), ch * 0.03f, skin) // neck

        for (l in floatArrayOf(0.25f, 0.55f)) { // shins with striped socks
            roundRect(rect(l, 0.62f, l + 0.20f, 0.88f), ch * 0.04f, JERSEY)
            c.drawRect(rect(l + 0.01f, 0.70f, l + 0.19f, 0.74f), Clay.flat(WHITE))
        }
        oval(rect(0.19f, 0.85f, 0.47f, 1.0f), SHOE)
        oval(rect(0.53f, 0.85f, 0.81f, 1.0f), SHOE)
        roundRect(rect(0.18f, 0.50f, 0.82f, 0.70f), ch * 0.08f, SHORTS)
        roundRect(rect(0.14f, 0.08f, 0.86f, 0.60f), ch * 0.14f, JERSEY)

        val collar = Path().apply {
            moveTo(x(0.40f), y(0.085f)); lineTo(x(0.5f), y(0.17f)); lineTo(x(0.6f), y(0.085f)); close()
        }
        c.drawPath(collar, Clay.flat(skin))
        c.drawPath(collar, Paint(Clay.outline(WHITE, lineW * 1.6f)).apply { color = WHITE })

        val armW = cw * 0.11f
        limb(0.20f, 0.17f, 0.26f, 0.38f, armW, JERSEY)
        limb(0.80f, 0.17f, 0.74f, 0.38f, armW, JERSEY)
        limb(0.26f, 0.38f, 0.36f, 0.44f, armW * 0.85f, skin)
        limb(0.74f, 0.38f, 0.64f, 0.44f, armW * 0.85f, skin)

        // Football, tilted.
        c.save()
        val bx = x(0.5f)
        val by = y(0.40f)
        c.rotate(-18f, bx, by)
        val rx = cw * 0.20f
        val ry = ch * 0.10f
        val ball = RectF(bx - rx, by - ry, bx + rx, by + ry)
        c.drawOval(ball, Clay.fill(BALL, ball.left, ball.right))
        val stripe = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; color = WHITE; strokeWidth = lineW * 1.3f; strokeCap = Paint.Cap.ROUND
        }
        c.save()
        c.clipPath(Path().apply { addOval(ball, Path.Direction.CW) })
        c.drawLine(bx - rx * 0.62f, by - ry, bx - rx * 0.62f, by + ry, stripe)
        c.drawLine(bx + rx * 0.62f, by - ry, bx + rx * 0.62f, by + ry, stripe)
        c.restore()
        c.drawLine(bx - rx * 0.28f, by - ry * 0.15f, bx + rx * 0.28f, by - ry * 0.15f, stripe)
        for (i in -2..2) {
            val lx = bx + i * rx * 0.12f
            c.drawLine(lx, by - ry * 0.38f, lx, by + ry * 0.08f, stripe)
        }
        c.drawOval(ball, Clay.outline(BALL, lineW))
        c.restore()

        val handR = cw * 0.055f
        for (hx in floatArrayOf(0.37f, 0.63f)) {
            c.drawCircle(x(hx), y(0.44f), handR, Clay.fill(skin, x(hx) - handR, x(hx) + handR))
            c.drawCircle(x(hx), y(0.44f), handR, Clay.outline(skin, lineW))
        }

        val out = StickerOutline.apply(bmp, strokePx = h * 0.027f, pad = 0)
        bmp.recycle()
        return out
    }
}
