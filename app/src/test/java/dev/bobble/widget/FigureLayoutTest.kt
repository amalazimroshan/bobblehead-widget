package dev.bobble.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FigureLayoutTest {

    private val size = 300f
    private val bodyAspect = BodyCatalog.SEATED_ASPECT

    private fun layout(headAspect: Float = 0.85f) = FigureLayout.compute(size, bodyAspect, headAspect)

    @Test
    fun catalogIdsAreUniqueAndLookupFallsBack() {
        val ids = BodyCatalog.ALL.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertEquals(BodyCatalog.DEFAULT, BodyCatalog.byId(null))
        assertEquals(BodyCatalog.DEFAULT, BodyCatalog.byId("missing"))
        assertEquals("football", BodyCatalog.byId("football").id)
    }

    @Test
    fun everyCatalogBodyKeepsHeadInFrameAndBigEnough() {
        for (spec in BodyCatalog.ALL) {
            for (aspect in listOf(0.6f, 0.85f, 1.2f)) {
                val l = FigureLayout.compute(size, spec.aspect, aspect, spec.neckPivotX, spec.neckPivotY)
                val ratio = l.head.height / l.figureHeight
                assertTrue("${spec.id} ratio $ratio", ratio in 0.55f..0.60f)
                assertTrue("${spec.id} body fits", l.body.left >= 0f && l.body.right <= size)
                val corners = listOf(
                    l.head.left to l.head.top, l.head.right to l.head.top,
                    l.head.left to l.head.bottom, l.head.right to l.head.bottom,
                )
                for (deg in listOf(-14f, 14f)) for ((x, y) in corners) {
                    val (rx, ry) = ShakeMath.rotatePoint(x, y, l.pivotX, l.pivotY, deg)
                    assertTrue("${spec.id} x=$rx", rx in 0f..size)
                    assertTrue("${spec.id} y=$ry", ry in 0f..size)
                }
            }
        }
    }

    @Test
    fun headIsAbout55To60PercentOfFigure() {
        val l = layout()
        val ratio = l.head.height / l.figureHeight
        assertTrue("head ratio $ratio", ratio in 0.55f..0.60f)
    }

    @Test
    fun headBottomOverlapsBodyAtNeckPivot() {
        val l = layout()
        assertTrue("head bottom below pivot", l.head.bottom > l.pivotY)
        assertTrue("head bottom inside body", l.head.bottom < l.body.bottom)
        assertTrue("pivot inside body", l.pivotY in l.body.top..l.body.bottom)
        assertTrue("pivot inside body", l.pivotX in l.body.left..l.body.right)
        assertEquals("head centered on pivot", l.pivotX, l.head.centerX, 1e-3f)
    }

    @Test
    fun uprightFigureFitsTheFrame() {
        val l = layout()
        assertTrue(l.head.top >= 0f)
        assertTrue(l.body.bottom <= size)
        assertTrue(l.body.left >= 0f && l.body.right <= size)
    }

    @Test
    fun headStaysInFrameAtMaxSwing() {
        for (aspect in listOf(0.6f, 0.85f, 1.0f, 1.2f, 2.0f)) {
            val l = layout(aspect)
            val corners = listOf(
                l.head.left to l.head.top, l.head.right to l.head.top,
                l.head.left to l.head.bottom, l.head.right to l.head.bottom,
            )
            for (deg in listOf(-14f, 14f)) {
                for ((x, y) in corners) {
                    val (rx, ry) = ShakeMath.rotatePoint(x, y, l.pivotX, l.pivotY, deg)
                    assertTrue("x=$rx aspect=$aspect deg=$deg", rx in 0f..size)
                    assertTrue("y=$ry aspect=$aspect deg=$deg", ry in 0f..size)
                }
            }
        }
    }

    @Test
    fun layoutScalesLinearly() {
        val a = FigureLayout.compute(100f, bodyAspect, 0.85f)
        val b = FigureLayout.compute(300f, bodyAspect, 0.85f)
        assertEquals(a.pivotX * 3f, b.pivotX, 1e-3f)
        assertEquals(a.pivotY * 3f, b.pivotY, 1e-3f)
        assertEquals(a.head.height * 3f, b.head.height, 1e-3f)
    }
}
