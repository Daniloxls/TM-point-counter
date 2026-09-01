package tm.scoring

import kotlin.test.Test
import kotlin.test.assertEquals

class ScorerTest {

    private val board = Board.THARSIS

    private fun grid(vararg entries: Pair<String, Tile>): Grid = mapOf(*entries)

    private fun greenery(owner: PlayerColor) = Tile(TileType.GREENERY, owner)

    @Test
    fun `an empty board scores nothing`() {
        assertEquals(emptyMap(), Scorer.score(board, emptyMap()))
    }

    @Test
    fun `a lone greenery scores one point`() {
        val result = Scorer.score(board, grid("r5c5" to greenery(PlayerColor.RED)))
        assertEquals(ScoreBreakdown(greeneries = 1, cityPoints = 0), result.getValue(PlayerColor.RED))
        assertEquals(1, result.getValue(PlayerColor.RED).total)
    }

    @Test
    fun `greeneries accumulate per owner`() {
        val result = Scorer.score(
            board,
            grid(
                "r5c5" to greenery(PlayerColor.RED),
                "r5c6" to greenery(PlayerColor.RED),
                "r1c1" to greenery(PlayerColor.BLUE),
            ),
        )
        assertEquals(2, result.getValue(PlayerColor.RED).greeneries)
        assertEquals(1, result.getValue(PlayerColor.BLUE).greeneries)
    }

    @Test
    fun `oceans specials and empties score nothing`() {
        val result = Scorer.score(
            board,
            grid(
                "r5c5" to Tile(TileType.OCEAN, null),
                "r5c6" to Tile(TileType.SPECIAL, PlayerColor.GREEN),
                "r5c7" to Tile(TileType.EMPTY, null),
            ),
        )
        assertEquals(emptyMap(), result)
    }

    @Test
    fun `an unowned greenery scores for nobody`() {
        val result = Scorer.score(board, grid("r5c5" to Tile(TileType.GREENERY, null)))
        assertEquals(emptyMap(), result)
    }

    @Test
    fun `players with no scoring tiles are absent from the result`() {
        val result = Scorer.score(board, grid("r5c5" to greenery(PlayerColor.RED)))
        assertEquals(setOf(PlayerColor.RED), result.keys)
    }
}
