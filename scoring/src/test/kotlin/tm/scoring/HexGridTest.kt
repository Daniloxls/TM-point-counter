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

    @Test
    fun `ids are row and column positions`() {
        assertEquals("r1c1", HexGrid.idOf(0, -4))
        assertEquals("r1c5", HexGrid.idOf(4, -4))
        assertEquals("r5c1", HexGrid.idOf(-4, 0))
        assertEquals("r5c9", HexGrid.idOf(4, 0))
        assertEquals("r9c1", HexGrid.idOf(-4, 4))
        assertEquals("r9c5", HexGrid.idOf(0, 4))
    }

    @Test
    fun `ids are unique across the board`() {
        val ids = HexGrid.coords().map { (q, r) -> HexGrid.idOf(q, r) }
        assertEquals(61, ids.toSet().size)
    }

    @Test
    fun `centre hex has six neighbours`() {
        assertEquals(6, HexGrid.neighborsOf(0, 0).size)
    }

    @Test
    fun `corner hex has three neighbours`() {
        assertEquals(3, HexGrid.neighborsOf(0, -4).size)
    }

    @Test
    fun `no hex is its own neighbour`() {
        for ((q, r) in HexGrid.coords()) {
            val id = HexGrid.idOf(q, r)
            assertTrue(id !in HexGrid.neighborsOf(q, r), "$id lists itself as a neighbour")
        }
    }

    @Test
    fun `neighbour relations are symmetric`() {
        val byId = HexGrid.coords().associateBy { (q, r) -> HexGrid.idOf(q, r) }
        for ((q, r) in HexGrid.coords()) {
            val id = HexGrid.idOf(q, r)
            for (neighborId in HexGrid.neighborsOf(q, r)) {
                val (nq, nr) = byId.getValue(neighborId)
                assertTrue(id in HexGrid.neighborsOf(nq, nr), "$neighborId does not list $id back")
            }
        }
    }

    @Test
    fun `board has 156 distinct adjacent pairs`() {
        val pairs = HexGrid.coords().flatMap { (q, r) ->
            HexGrid.neighborsOf(q, r).map { setOf(HexGrid.idOf(q, r), it) }
        }.toSet()
        assertEquals(156, pairs.size)
    }
}
