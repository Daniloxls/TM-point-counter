package tm.scoring

import kotlin.test.Test
import kotlin.test.assertEquals

class ScorerTest {

    private val board = Board.THARSIS

    private fun grid(vararg entries: Pair<String, Tile>): Grid = mapOf(*entries)

    private fun greenery(owner: PlayerColor) = Tile(TileType.GREENERY, owner)

    private fun city(owner: PlayerColor) = Tile(TileType.CITY, owner)

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

    @Test
    fun `a city with no adjacent greenery scores nothing`() {
        val result = Scorer.score(board, grid("r5c5" to city(PlayerColor.RED)))
        assertEquals(emptyMap(), result)
    }

    @Test
    fun `a city scores one point per adjacent greenery`() {
        val result = Scorer.score(
            board,
            grid(
                "r5c5" to city(PlayerColor.RED),
                "r5c4" to greenery(PlayerColor.RED),
                "r5c6" to greenery(PlayerColor.RED),
            ),
        )
        assertEquals(ScoreBreakdown(greeneries = 2, cityPoints = 2), result.getValue(PlayerColor.RED))
        assertEquals(4, result.getValue(PlayerColor.RED).total)
    }

    @Test
    fun `a city scores for greeneries owned by other players`() {
        val result = Scorer.score(
            board,
            grid(
                "r5c5" to city(PlayerColor.RED),
                "r5c4" to greenery(PlayerColor.BLUE),
                "r5c6" to greenery(PlayerColor.GREEN),
                "r4c4" to greenery(PlayerColor.YELLOW),
            ),
        )
        assertEquals(3, result.getValue(PlayerColor.RED).cityPoints)
        assertEquals(0, result.getValue(PlayerColor.RED).greeneries)
        assertEquals(1, result.getValue(PlayerColor.BLUE).greeneries)
        assertEquals(1, result.getValue(PlayerColor.GREEN).greeneries)
        assertEquals(1, result.getValue(PlayerColor.YELLOW).greeneries)
    }

    @Test
    fun `one greenery scores for every adjacent city`() {
        val result = Scorer.score(
            board,
            grid(
                "r5c5" to greenery(PlayerColor.BLUE),
                "r5c4" to city(PlayerColor.RED),
                "r5c6" to city(PlayerColor.GREEN),
            ),
        )
        assertEquals(1, result.getValue(PlayerColor.RED).cityPoints)
        assertEquals(1, result.getValue(PlayerColor.GREEN).cityPoints)
        assertEquals(1, result.getValue(PlayerColor.BLUE).greeneries)
    }

    @Test
    fun `adjacent cities do not score off each other`() {
        val result = Scorer.score(
            board,
            grid(
                "r5c5" to city(PlayerColor.RED),
                "r5c6" to city(PlayerColor.BLUE),
            ),
        )
        assertEquals(emptyMap(), result)
    }

    @Test
    fun `a city at the board edge only counts hexes that exist`() {
        val result = Scorer.score(
            board,
            grid(
                "r1c1" to city(PlayerColor.RED),
                "r1c2" to greenery(PlayerColor.RED),
                "r2c1" to greenery(PlayerColor.RED),
                "r2c2" to greenery(PlayerColor.RED),
            ),
        )
        assertEquals(3, result.getValue(PlayerColor.RED).cityPoints)
    }

    @Test
    fun `an unowned city scores for nobody`() {
        val result = Scorer.score(
            board,
            grid(
                "r5c5" to Tile(TileType.CITY, null),
                "r5c4" to greenery(PlayerColor.BLUE),
            ),
        )
        assertEquals(setOf(PlayerColor.BLUE), result.keys)
    }

    @Test
    fun `an unowned greenery still scores points for an adjacent city`() {
        val result = Scorer.score(
            board,
            grid(
                "r5c5" to city(PlayerColor.RED),
                "r5c4" to Tile(TileType.GREENERY, null),
            ),
        )
        assertEquals(1, result.getValue(PlayerColor.RED).cityPoints)
    }
}
