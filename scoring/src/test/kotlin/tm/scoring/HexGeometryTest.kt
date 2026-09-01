package tm.scoring

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HexGeometryTest {

    private val hexes = HexGrid.hexes()

    @Test
    fun `every hex is present and keyed by its own id`() {
        assertEquals(61, hexes.size)
        for ((key, hex) in hexes) assertEquals(key, hex.id)
    }

    @Test
    fun `centres are inside the unit square`() {
        for (hex in hexes.values) {
            assertTrue(hex.x in 0.0..1.0, "${hex.id} has x=${hex.x}")
            assertTrue(hex.y in 0.0..1.0, "${hex.id} has y=${hex.y}")
        }
    }

    @Test
    fun `both axes are normalised to the full range`() {
        assertEquals(0.0, hexes.values.minOf { it.x }, 1e-9)
        assertEquals(1.0, hexes.values.maxOf { it.x }, 1e-9)
        assertEquals(0.0, hexes.values.minOf { it.y }, 1e-9)
        assertEquals(1.0, hexes.values.maxOf { it.y }, 1e-9)
    }

    @Test
    fun `rows are ordered top to bottom and columns left to right`() {
        val topLeft = hexes.getValue("r1c1")
        val bottomLeft = hexes.getValue("r9c1")
        val middleLeft = hexes.getValue("r5c1")
        val middleRight = hexes.getValue("r5c9")
        assertTrue(topLeft.y < middleLeft.y, "row 1 should sit above row 5")
        assertTrue(middleLeft.y < bottomLeft.y, "row 5 should sit above row 9")
        assertTrue(middleLeft.x < middleRight.x, "column 1 should sit left of column 9")
    }

    @Test
    fun `the board is wider than it is tall`() {
        assertTrue(HexGrid.ASPECT_RATIO > 1.0, "aspect ratio was ${HexGrid.ASPECT_RATIO}")
    }

    @Test
    fun `neighbouring centres are all the same distance apart`() {
        val spacings = hexes.values.flatMap { hex ->
            hex.neighbors.map { neighborId ->
                val other = hexes.getValue(neighborId)
                val dx = (hex.x - other.x) * HexGrid.ASPECT_RATIO
                val dy = hex.y - other.y
                kotlin.math.sqrt(dx * dx + dy * dy)
            }
        }
        val first = spacings.first()
        assertTrue(first > 1e-3, "spacing $first is suspiciously close to zero (collapsed grid?)")
        for (spacing in spacings) {
            assertTrue(abs(spacing - first) < 1e-6, "spacing $spacing differs from $first")
        }
    }

    @Test
    fun `r5c5 has the expected six neighbours`() {
        assertEquals(setOf("r4c4", "r4c5", "r5c4", "r5c6", "r6c4", "r6c5"), Board.THARSIS.neighbors("r5c5").toSet())
    }

    @Test
    fun `hex half-extents pad a border hex's crop box past the centre bounding box`() {
        // r5c1 is the leftmost hex in the middle row: its centre sits at x=0.0,
        // half a hex short of the true left edge of the board image.
        val leftBorder = hexes.getValue("r5c1")
        assertEquals(0.0, leftBorder.x, 1e-9)
        assertTrue(leftBorder.x - HexGrid.HEX_HALF_WIDTH < 0.0, "crop should extend past the left edge")

        // r1c1 is in the top row: its centre sits at y=0.0, half a hex short of
        // the true top edge of the board image.
        val topBorder = hexes.getValue("r1c1")
        assertEquals(0.0, topBorder.y, 1e-9)
        assertTrue(topBorder.y - HexGrid.HEX_HALF_HEIGHT < 0.0, "crop should extend past the top edge")

        // Adjacent hex centres in the same row are exactly one hex-width apart,
        // so their half-width crop boxes tile without gaps or overlap.
        val r5c1 = hexes.getValue("r5c1")
        val r5c2 = hexes.getValue("r5c2")
        assertEquals(
            r5c2.x - HexGrid.HEX_HALF_WIDTH,
            r5c1.x + HexGrid.HEX_HALF_WIDTH,
            1e-9,
        )
    }
}
