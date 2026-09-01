package tm.scoring

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HexGridTest {

    @Test
    fun `board has exactly 61 hexes`() {
        assertEquals(61, HexGrid.coords().size)
    }

    @Test
    fun `all coordinates are inside the radius 4 hexagon`() {
        for ((q, r) in HexGrid.coords()) {
            assertTrue(abs(q) <= 4 && abs(r) <= 4 && abs(q + r) <= 4, "($q, $r) is outside the board")
        }
    }

    @Test
    fun `coordinates are unique`() {
        val coords = HexGrid.coords()
        assertEquals(coords.size, coords.toSet().size)
    }

    @Test
    fun `rows run 5 6 7 8 9 8 7 6 5`() {
        val perRow = HexGrid.coords().groupBy { (_, r) -> r }.toSortedMap().map { it.value.size }
        assertEquals(listOf(5, 6, 7, 8, 9, 8, 7, 6, 5), perRow)
    }
}
