package tm.app

import tm.scoring.Board
import tm.scoring.PlayerColor
import tm.scoring.Tile
import tm.scoring.TileType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppStateTest {

    private val fresh = AppState()

    @Test
    fun `a fresh session starts at the board picker with nothing chosen`() {
        assertEquals(Step.PICK_BOARD, fresh.step)
        assertNull(fresh.board)
        assertTrue(fresh.grid.isEmpty())
    }

    @Test
    fun `choosing a board moves to capture`() {
        val state = fresh.withBoard(Board.THARSIS)
        assertEquals(Board.THARSIS, state.board)
        assertEquals(Step.CAPTURE, state.step)
    }

    @Test
    fun `taking a photo moves to the anchor step`() {
        val state = fresh.withBoard(Board.THARSIS).withPhoto("/tmp/board.jpg")
        assertEquals("/tmp/board.jpg", state.photoPath)
        assertEquals(Step.ANCHORS, state.step)
    }

    @Test
    fun `placing anchors moves to review`() {
        val anchors = List(4) { it.toFloat() to it.toFloat() }
        val state = fresh.withBoard(Board.THARSIS).withPhoto("/tmp/board.jpg").withAnchors(anchors)
        assertEquals(anchors, state.anchors)
        assertEquals(Step.REVIEW, state.step)
    }

    @Test
    fun `anchors must be exactly four points`() {
        val state = fresh.withBoard(Board.THARSIS).withPhoto("/tmp/board.jpg")
        kotlin.test.assertFailsWith<IllegalArgumentException> {
            state.withAnchors(List(3) { 0f to 0f })
        }
    }

    @Test
    fun `editing a tile keeps the user on the review step`() {
        val state = reviewing().withTile("r5c5", Tile(TileType.GREENERY, PlayerColor.RED))
        assertEquals(Step.REVIEW, state.step)
        assertEquals(Tile(TileType.GREENERY, PlayerColor.RED), state.grid["r5c5"])
    }

    @Test
    fun `setting a tile to empty removes it from the grid`() {
        val state = reviewing()
            .withTile("r5c5", Tile(TileType.GREENERY, PlayerColor.RED))
            .withTile("r5c5", Tile(TileType.EMPTY, null))
        assertTrue("r5c5" !in state.grid)
    }

    @Test
    fun `editing a hex that is not on the board is rejected`() {
        kotlin.test.assertFailsWith<IllegalArgumentException> {
            reviewing().withTile("r99c99", Tile(TileType.GREENERY, PlayerColor.RED))
        }
    }

    @Test
    fun `players are whoever owns a tile`() {
        val state = reviewing()
            .withTile("r5c5", Tile(TileType.GREENERY, PlayerColor.RED))
            .withTile("r5c6", Tile(TileType.CITY, PlayerColor.BLUE))
            .withTile("r5c7", Tile(TileType.OCEAN, null))
        assertEquals(setOf(PlayerColor.RED, PlayerColor.BLUE), state.players)
    }

    @Test
    fun `going back from review returns to the anchors`() {
        assertEquals(Step.ANCHORS, reviewing().back().step)
    }

    @Test
    fun `going back from the board picker stays put`() {
        assertEquals(Step.PICK_BOARD, fresh.back().step)
    }

    @Test
    fun `going back keeps the work already done`() {
        val state = reviewing().withTile("r5c5", Tile(TileType.GREENERY, PlayerColor.RED))
        assertEquals(state.grid, state.back().grid)
    }

    private fun reviewing(): AppState =
        fresh.withBoard(Board.THARSIS).withPhoto("/tmp/board.jpg").withAnchors(List(4) { 0f to 0f })
}
