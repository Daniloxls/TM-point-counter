package tm.scoring

import kotlin.test.Test
import kotlin.test.Ignore
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

    // The three ocean sets await transcription from the physical boards: lay each
    // printed map out with the 9-hex row across the middle, number rows 1-9 top to
    // bottom and columns 1-N left to right within each row, and record every space
    // printed with the blue ocean symbol as r<row>c<col>. Each board has exactly 12.
    @Ignore
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
