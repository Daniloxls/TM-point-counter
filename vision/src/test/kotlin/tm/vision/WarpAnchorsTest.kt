package tm.vision

import tm.scoring.Board
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WarpAnchorsTest {

    private val image = CanonicalImage(hexCropHeightPx = 64)

    @Test
    fun `the anchors are the four corner hexes in a fixed order`() {
        assertEquals(listOf("r1c1", "r1c5", "r9c1", "r9c5"), WarpAnchors.HEX_IDS)
    }

    @Test
    fun `every anchor is a real hex on the board`() {
        for (id in WarpAnchors.HEX_IDS) {
            assertTrue(id in Board.THARSIS.hexes, "$id is not a hex")
        }
    }

    @Test
    fun `the anchor centres form a rectangle in board space`() {
        val hexes = WarpAnchors.HEX_IDS.map { Board.THARSIS.hexes.getValue(it) }
        val (topLeft, topRight, bottomLeft, bottomRight) = hexes
        assertEquals(topLeft.y, topRight.y, 1e-9, "the top two anchors should share a row")
        assertEquals(bottomLeft.y, bottomRight.y, 1e-9, "the bottom two anchors should share a row")
        assertEquals(topLeft.x, bottomLeft.x, 1e-9, "the left two anchors should share a column")
        assertEquals(topRight.x, bottomRight.x, 1e-9, "the right two anchors should share a column")
        assertTrue(topLeft.x < topRight.x, "left should be left of right")
        assertTrue(topLeft.y < bottomLeft.y, "top should be above bottom")
    }

    @Test
    fun `destination points are eight floats in x y order`() {
        val points = WarpAnchors.destinationPoints(image)
        assertEquals(8, points.size)
    }

    @Test
    fun `destination points match the canonical mapping of those hexes`() {
        val points = WarpAnchors.destinationPoints(image)
        WarpAnchors.HEX_IDS.forEachIndexed { index, id ->
            val hex = Board.THARSIS.hexes.getValue(id)
            assertEquals(image.xToPx(hex.x).toFloat(), points[index * 2], 1e-3f, "$id x")
            assertEquals(image.yToPx(hex.y).toFloat(), points[index * 2 + 1], 1e-3f, "$id y")
        }
    }

    @Test
    fun `destination points sit inside the canonical image`() {
        val points = WarpAnchors.destinationPoints(image)
        for (i in 0 until 4) {
            assertTrue(points[i * 2] in 0f..image.widthPx.toFloat(), "point $i x is outside")
            assertTrue(points[i * 2 + 1] in 0f..image.heightPx.toFloat(), "point $i y is outside")
        }
    }

    @Test
    fun `the anchor rectangle is not degenerate`() {
        val points = WarpAnchors.destinationPoints(image)
        val width = abs(points[2] - points[0])
        val height = abs(points[5] - points[1])
        assertTrue(width > 1f, "anchors are horizontally collapsed")
        assertTrue(height > 1f, "anchors are vertically collapsed")
    }
}
