package dev.bobble.widget

/** Android-free rectangle so the layout math can be unit-tested on the JVM. */
data class LRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val centerX: Float get() = (left + right) / 2f
}

/**
 * Where the body and head go inside a square frame of side `size`.
 *
 * The body sits at the bottom. The head is ~58% of the total figure height. Its bottom edge
 * overlaps the body slightly below the neck pivot. The head rotates about (pivotX, pivotY).
 */
data class FigureLayout(
    val head: LRect,
    val body: LRect,
    val pivotX: Float,
    val pivotY: Float,
) {
    /** Height from the top of the head to the bottom of the body. */
    val figureHeight: Float get() = body.bottom - head.top

    companion object {
        /** Empty space above and below the figure, as a fraction of the frame. */
        const val MARGIN = 0.03f

        /** Head height as a fraction of the available figure height (target 55-60% of the figure). */
        const val HEAD_FRACTION = 0.58f

        /** Body height as a fraction of the available figure height (head + body overlap a bit). */
        const val BODY_FRACTION = 0.47f

        /** How much of the head (fraction of its height) hangs below the neck pivot. */
        const val HEAD_OVERLAP = 0.05f

        /**
         * Default neck pivot inside a body image, as fractions of the body image width/height.
         * All drawn catalog bodies put the neck here; a BodySpec can override it.
         */
        const val NECK_PIVOT_X = 0.5f
        const val NECK_PIVOT_Y = 0.06f

        const val MIN_HEAD_ASPECT = 0.6f
        const val MAX_HEAD_ASPECT = 1.2f

        /**
         * @param size side of the square frame in px
         * @param bodyAspect body image width / height
         * @param headAspect head image width / height
         */
        fun compute(
            size: Float,
            bodyAspect: Float,
            headAspect: Float,
            neckPivotX: Float = NECK_PIVOT_X,
            neckPivotY: Float = NECK_PIVOT_Y,
        ): FigureLayout {
            val top = size * MARGIN
            val bottom = size * (1f - MARGIN)
            val figH = bottom - top

            val bodyH = figH * BODY_FRACTION
            val bodyW = (bodyH * bodyAspect).coerceAtMost(size * 0.95f)
            val bodyLeft = (size - bodyW) / 2f
            val bodyTop = bottom - bodyH
            val body = LRect(bodyLeft, bodyTop, bodyLeft + bodyW, bottom)

            val pivotX = bodyLeft + bodyW * neckPivotX
            val pivotY = bodyTop + bodyH * neckPivotY

            val headH = figH * HEAD_FRACTION
            val headW = headH * headAspect.coerceIn(MIN_HEAD_ASPECT, MAX_HEAD_ASPECT)
            val headBottom = pivotY + headH * HEAD_OVERLAP
            val head = LRect(pivotX - headW / 2f, headBottom - headH, pivotX + headW / 2f, headBottom)

            return FigureLayout(head, body, pivotX, pivotY)
        }
    }
}
