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
        for (spacing in spacings) {
            assertTrue(abs(spacing - first) < 1e-6, "spacing $spacing differs from $first")
        }
    }
}
