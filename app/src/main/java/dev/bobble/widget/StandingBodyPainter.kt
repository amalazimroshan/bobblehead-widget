package dev.bobble.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import kotlin.math.roundToInt

enum class Bottom { PANTS, SHORTS, SKIRT, DRESS }

/** What a standing body wears. Colours are ARGB ints. */
data class Outfit(
    val top: Int,
    val longSleeves: Boolean,
    val bottom: Bottom,
    val bottomColor: Int,
    val shoes: Int,
    /** Open jacket / cardigan over the top (also colours the sleeves). */
    val jacket: Int? = null,
    val jacketButtons: Boolean = false,
    val tie: Int? = null,
    val hood: Boolean = false,
    val socks: Int? = null,
    val number: String? = null,
    val whiteSoles: Boolean = false,
)

/**
 * Standing chibi figure (like a clay bobblehead figurine), drawn from an [Outfit].
 * Aspect = BodyCatalog.STANDING_ASPECT. Neck pivot = FigureLayout.NECK_PIVOT_X / NECK_PIVOT_Y.
 */
object StandingBodyPainter {

    private const val WHITE = 0xFFFFFFFF.toInt()

    val OUTFITS: Map<String, Outfit> = mapOf(
        "cardigan" to Outfit(
            top = 0xFFF5F3EE.toInt(), longSleeves = true, bottom = Bottom.PANTS,
            bottomColor = 0xFF2E3A59.toInt(), shoes = 0xFF212121.toInt(),
            jacket = 0xFFD6C07A.toInt(), jacketButtons = true, whiteSoles = true,
        ),
        "tee" to Outfit(
            top = 0xFF8E2B34.toInt(), longSleeves = false, bottom = Bottom.PANTS,
            bottomColor = 0xFF2A4DA8.toInt(), shoes = 0xFF1E1E1E.toInt(),
        ),
        "suit" to Outfit(
            top = 0xFFFAFAFA.toInt(), longSleeves = true, bottom = Bottom.PANTS,
            bottomColor = 0xFF2B2F36.toInt(), shoes = 0xFF111111.toInt(),
            jacket = 0xFF2B2F36.toInt(), tie = 0xFFB71C1C.toInt(),
        ),
        "dress" to Outfit(
            top = 0xFFE57373.toInt(), longSleeves = false, bottom = Bottom.DRESS,
            bottomColor = 0xFFE57373.toInt(), shoes = 0xFFB0855A.toInt(),
        ),
        "hoodie" to Outfit(
            top = 0xFF5C6BC0.toInt(), longSleeves = true, bottom = Bottom.PANTS,
            bottomColor = 0xFF9E9E9E.toInt(), shoes = 0xFFEEEEEE.toInt(), hood = true,
        ),
        "hoops" to Outfit(
            top = 0xFFFFC107.toInt(), longSleeves = false, bottom = Bottom.SHORTS,
            bottomColor = 0xFF5E35B1.toInt(), shoes = 0xFF212121.toInt(),
            socks = WHITE, number = "23", whiteSoles = true,
        ),
        "skirt" to Outfit(
            top = 0xFFFFF3E0.toInt(), longSleeves = true, bottom = Bottom.SKIRT,
            bottomColor = 0xFF26A69A.toInt(), shoes = 0xFF212121.toInt(),
        ),
    )

    fun outfitFor(id: String): Outfit = OUTFITS[id] ?: OUTFITS.getValue(BodyCatalog.DEFAULT.id)

    fun draw(heightPx: Int, o: Outfit, skin: Int): Bitmap {
        val h = heightPx.coerceAtLeast(16).toFloat()
        val w = h * BodyCatalog.STANDING_ASPECT
        val bmp = Bitmap.createBitmap(w.roundToInt(), h.roundToInt(), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)

        val pad = h * BodyPainter.PAD
        val cw = w - 2 * pad
        val ch = h - 2 * pad
        fun x(f: Float) = pad + f * cw
        fun y(f: Float) = pad + f * ch
        fun rect(l: Float, t: Float, r: Float, b: Float) = RectF(x(l), y(t), x(r), y(b))
        val lineW = h * 0.010f

        fun box(r: RectF, radius: Float, color: Int) {
            c.drawRoundRect(r, radius, radius, Clay.fill(color, r.left, r.right))
            c.drawRoundRect(r, radius, radius, Clay.outline(color, lineW))
        }

        fun poly(color: Int, vararg pts: Float) {
            val p = Path()
            p.moveTo(x(pts[0]), y(pts[1]))
            var i = 2
            while (i < pts.size) { p.lineTo(x(pts[i]), y(pts[i + 1])); i += 2 }
            p.close()
            val left = x(pts.filterIndexed { k, _ -> k % 2 == 0 }.min())
            val right = x(pts.filterIndexed { k, _ -> k % 2 == 0 }.max())
            c.drawPath(p, Clay.fill(color, left, right))
            c.drawPath(p, Clay.outline(color, lineW))
        }

        fun limb(x0: Float, y0: Float, x1: Float, y1: Float, thick: Float, color: Int) {
            c.drawLine(x(x0), y(y0), x(x1), y(y1), Clay.outline(color, thick + 2 * lineW))
            c.drawLine(x(x0), y(y0), x(x1), y(y1), Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE; strokeWidth = thick; strokeCap = Paint.Cap.ROUND
                shader = Clay.fill(color, x(minOf(x0, x1)) - thick / 2, x(maxOf(x0, x1)) + thick / 2).shader
            })
        }

        val r = ch * 0.05f

        // Hood sits behind the neck; the head covers most of it.
        if (o.hood) box(rect(0.24f, 0.0f, 0.76f, 0.16f), ch * 0.07f, o.top)
        // Neck.
        box(rect(0.41f, 0.0f, 0.59f, 0.13f), ch * 0.03f, skin)

        // Legs and lower garment.
        when (o.bottom) {
            Bottom.PANTS -> {
                box(rect(0.25f, 0.50f, 0.49f, 0.92f), r, o.bottomColor)
                box(rect(0.51f, 0.50f, 0.75f, 0.92f), r, o.bottomColor)
                box(rect(0.22f, 0.44f, 0.78f, 0.60f), r, o.bottomColor)
            }
            Bottom.SHORTS -> {
                box(rect(0.29f, 0.58f, 0.46f, 0.92f), r, skin)
                box(rect(0.54f, 0.58f, 0.71f, 0.92f), r, skin)
                o.socks?.let {
                    box(rect(0.285f, 0.78f, 0.465f, 0.92f), r * 0.5f, it)
                    box(rect(0.535f, 0.78f, 0.715f, 0.92f), r * 0.5f, it)
                }
                box(rect(0.21f, 0.44f, 0.79f, 0.66f), r, o.bottomColor)
                c.drawLine(x(0.5f), y(0.56f), x(0.5f), y(0.66f), Clay.outline(o.bottomColor, lineW))
            }
            Bottom.SKIRT -> {
                box(rect(0.31f, 0.60f, 0.46f, 0.92f), r, skin)
                box(rect(0.54f, 0.60f, 0.69f, 0.92f), r, skin)
                poly(o.bottomColor, 0.24f, 0.44f, 0.76f, 0.44f, 0.86f, 0.70f, 0.14f, 0.70f)
            }
            Bottom.DRESS -> {
                box(rect(0.31f, 0.62f, 0.46f, 0.92f), r, skin)
                box(rect(0.54f, 0.62f, 0.69f, 0.92f), r, skin)
                poly(o.top, 0.22f, 0.30f, 0.78f, 0.30f, 0.90f, 0.72f, 0.10f, 0.72f)
            }
        }

        // Shoes.
        for (l in floatArrayOf(0.20f, 0.50f)) {
            val s = rect(l, 0.88f, l + 0.30f, 1.0f)
            c.drawOval(s, Clay.fill(o.shoes, s.left, s.right))
            if (o.whiteSoles) {
                c.save()
                c.clipRect(s.left, s.bottom - s.height() * 0.32f, s.right, s.bottom)
                c.drawOval(s, Clay.flat(0xFFF2F2F2.toInt()))
                c.restore()
            }
            c.drawOval(s, Clay.outline(o.shoes, lineW))
        }

        // Torso.
        val torsoBottom = if (o.bottom == Bottom.DRESS) 0.46f else 0.52f
        box(rect(0.19f, 0.07f, 0.81f, torsoBottom), ch * 0.09f, o.top)

        // Neckline.
        if (o.jacket == null && !o.hood) {
            val neck = RectF(x(0.40f), y(0.04f), x(0.60f), y(0.14f))
            c.drawArc(neck, 0f, 180f, false, Clay.outline(o.top, lineW * 1.4f))
        }

        o.tie?.let { tie ->
            poly(WHITE, 0.42f, 0.07f, 0.58f, 0.07f, 0.5f, 0.15f) // shirt collar
            poly(tie, 0.47f, 0.10f, 0.53f, 0.10f, 0.55f, 0.36f, 0.5f, 0.41f, 0.45f, 0.36f)
        }

        // Open jacket / cardigan panels.
        o.jacket?.let { j ->
            poly(j, 0.19f, 0.09f, 0.42f, 0.07f, 0.45f, 0.22f, 0.41f, 0.53f, 0.19f, 0.53f)
            poly(j, 0.81f, 0.09f, 0.58f, 0.07f, 0.55f, 0.22f, 0.59f, 0.53f, 0.81f, 0.53f)
            if (o.jacketButtons) {
                for (by in floatArrayOf(0.26f, 0.34f, 0.42f, 0.50f)) {
                    c.drawCircle(x(0.40f), y(by), cw * 0.014f, Clay.flat(Clay.darken(j, 0.35f)))
                }
            }
        }

        // Hood strings.
        if (o.hood) {
            val s = Clay.outline(WHITE, lineW * 1.2f).apply { color = 0xFFF5F5F5.toInt() }
            c.drawLine(x(0.45f), y(0.12f), x(0.44f), y(0.26f), s)
            c.drawLine(x(0.55f), y(0.12f), x(0.56f), y(0.26f), s)
            box(rect(0.32f, 0.33f, 0.68f, 0.45f), ch * 0.03f, Clay.darken(o.top, 0.08f)) // pocket
        }

        // Jersey number.
        o.number?.let { n ->
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = WHITE; textAlign = Paint.Align.CENTER; textSize = ch * 0.15f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val stroke = Paint(p).apply {
                style = Paint.Style.STROKE; strokeWidth = lineW * 1.4f; color = Clay.darken(o.top, 0.45f)
            }
            c.drawText(n, x(0.5f), y(0.36f), stroke)
            c.drawText(n, x(0.5f), y(0.36f), p)
        }

        // Arms hang at the sides, slightly away from the body.
        val sleeve = o.jacket ?: o.top
        val armW = cw * 0.15f
        for (side in floatArrayOf(-1f, 1f)) {
            val sx = 0.5f + side * 0.31f
            val ex = 0.5f + side * 0.36f
            if (o.longSleeves) {
                limb(sx, 0.13f, ex, 0.45f, armW, sleeve)
            } else {
                limb(sx, 0.13f, 0.5f + side * 0.335f, 0.25f, armW * 1.12f, sleeve)
                limb(0.5f + side * 0.335f, 0.27f, ex, 0.45f, armW * 0.82f, skin)
            }
            val hr = cw * 0.07f
            c.drawCircle(x(ex), y(0.50f), hr, Clay.fill(skin, x(ex) - hr, x(ex) + hr))
            c.drawCircle(x(ex), y(0.50f), hr, Clay.outline(skin, lineW))
        }

        val out = StickerOutline.apply(bmp, strokePx = h * 0.027f, pad = 0)
        bmp.recycle()
        return out
    }
}
