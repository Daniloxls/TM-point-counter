package tm.vision

import tm.scoring.HexGrid
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CanonicalImageTest {

    private val image = CanonicalImage(hexCropHeightPx = 64)

    @Test
    fun `a hex crop is the requested height`() {
        val top = image.yToPx(0.5 - HexGrid.HEX_HALF_HEIGHT)
        val bottom = image.yToPx(0.5 + HexGrid.HEX_HALF_HEIGHT)
        assertEquals(64.0, bottom - top, 1e-6)
    }

    @Test
    fun `the border hexes' crops sit exactly at the image edges`() {
        assertEquals(0.0, image.xToPx(0.0 - HexGrid.HEX_HALF_WIDTH), 1e-6)
        assertEquals(0.0, image.yToPx(0.0 - HexGrid.HEX_HALF_HEIGHT), 1e-6)
        assertEquals(image.widthPx.toDouble(), image.xToPx(1.0 + HexGrid.HEX_HALF_WIDTH), 0.5)
        assertEquals(image.heightPx.toDouble(), image.yToPx(1.0 + HexGrid.HEX_HALF_HEIGHT), 0.5)
    }

    @Test
    fun `the mapping is linear and increasing`() {
        assertTrue(image.xToPx(0.0) < image.xToPx(0.5))
        assertTrue(image.xToPx(0.5) < image.xToPx(1.0))
        val firstHalf = image.xToPx(0.5) - image.xToPx(0.0)
        val secondHalf = image.xToPx(1.0) - image.xToPx(0.5)
        assertEquals(firstHalf, secondHalf, 1e-6)
    }

    @Test
    fun `the image keeps the board's proportions`() {
        val contentWidth = image.xToPx(1.0) - image.xToPx(0.0)
        val contentHeight = image.yToPx(1.0) - image.yToPx(0.0)
        assertEquals(HexGrid.ASPECT_RATIO, contentWidth / contentHeight, 1e-6)
    }

    @Test
    fun `the image is large enough to be useful and small enough to hold in memory`() {
        assertTrue(image.widthPx in 200..4000, "width was ${image.widthPx}")
        assertTrue(image.heightPx in 200..4000, "height was ${image.heightPx}")
    }

    @Test
    fun `a non-positive crop height is rejected`() {
        assertFailsWith<IllegalArgumentException> { CanonicalImage(0) }
        assertFailsWith<IllegalArgumentException> { CanonicalImage(-64) }
    }
}
