package tm.scoring

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class BoardTest {

    @Test
    fun `all three boards are available`() {
        assertEquals(listOf("tharsis", "hellas", "elysium"), Board.ALL.map { it.id })
    }

    @Test
    fun `every board has 61 hexes`() {
        for (board in Board.ALL) assertEquals(61, board.hexes.size, "${board.id} has the wrong hex count")
    }

    @Test
    fun `every board has exactly 12 ocean-reserved hexes`() {
        for (board in Board.ALL) {
            assertEquals(12, board.oceanReserved.size, "${board.id} has the wrong ocean count")
        }
    }

    @Test
    fun `every ocean-reserved id is a real hex on the board`() {
        for (board in Board.ALL) {
            for (id in board.oceanReserved) {
                assertTrue(id in board.hexes, "${board.id} reserves unknown hex $id")
            }
        }
    }

    @Test
    fun `boards can be looked up by id`() {
        assertEquals("hellas", Board.byId("hellas").id)
    }

    @Test
    fun `unknown board ids are rejected`() {
        assertFailsWith<IllegalArgumentException> { Board.byId("mars") }
    }

    @Test
    fun `neighbours come from the shared geometry`() {
        assertEquals(6, Board.THARSIS.neighbors("r5c5").size)
        assertEquals(3, Board.THARSIS.neighbors("r1c1").size)
    }

    @Test
    fun `neighbours of an unknown hex are rejected`() {
        assertFailsWith<IllegalArgumentException> { Board.THARSIS.neighbors("r99c99") }
    }
}
