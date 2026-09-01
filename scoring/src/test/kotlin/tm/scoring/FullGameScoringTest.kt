package tm.scoring

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A realistic end-of-game map. Worked by hand below so the expected numbers can
 * be checked against the rules rather than against the implementation.
 *
 * Layout, all on Tharsis:
 *
 *   RED   greeneries at r4c4, r4c5, r5c5      -> 3 points
 *         city at r4c6, adjacent to r4c5 (RED) and r3c5 (BLUE greenery)
 *                                              -> 2 points
 *   BLUE  greeneries at r3c5, r6c6            -> 2 points
 *         city at r6c5, adjacent to r6c6 (BLUE) and r5c5 (RED)
 *                                              -> 2 points
 *   GREEN city at r1c1, no adjacent greenery  -> 0 points
 *         special tile at r2c2                -> 0 points
 *   BLACK no tiles                            -> 0 points
 */
class FullGameScoringTest {

    private val grid: Grid = mapOf(
        "r4c4" to Tile(TileType.GREENERY, PlayerColor.RED),
        "r4c5" to Tile(TileType.GREENERY, PlayerColor.RED),
        "r5c5" to Tile(TileType.GREENERY, PlayerColor.RED),
        "r4c6" to Tile(TileType.CITY, PlayerColor.RED),
        "r3c5" to Tile(TileType.GREENERY, PlayerColor.BLUE),
        "r6c6" to Tile(TileType.GREENERY, PlayerColor.BLUE),
        "r6c5" to Tile(TileType.CITY, PlayerColor.BLUE),
        "r1c1" to Tile(TileType.CITY, PlayerColor.GREEN),
        "r2c2" to Tile(TileType.SPECIAL, PlayerColor.GREEN),
        "r5c1" to Tile(TileType.OCEAN, null),
        "r5c9" to Tile(TileType.OCEAN, null),
    )

    private val players = setOf(PlayerColor.RED, PlayerColor.BLUE, PlayerColor.GREEN, PlayerColor.BLACK)

    @Test
    fun `scores the whole board`() {
        val result = Scorer.scoreAll(Board.THARSIS, grid, players)

        assertEquals(ScoreBreakdown(greeneries = 3, cityPoints = 2), result.getValue(PlayerColor.RED))
        assertEquals(ScoreBreakdown(greeneries = 2, cityPoints = 2), result.getValue(PlayerColor.BLUE))
        assertEquals(ScoreBreakdown(greeneries = 0, cityPoints = 0), result.getValue(PlayerColor.GREEN))
        assertEquals(ScoreBreakdown(greeneries = 0, cityPoints = 0), result.getValue(PlayerColor.BLACK))
    }

    @Test
    fun `totals are the sum of the parts`() {
        val result = Scorer.scoreAll(Board.THARSIS, grid, players)
        assertEquals(5, result.getValue(PlayerColor.RED).total)
        assertEquals(4, result.getValue(PlayerColor.BLUE).total)
        assertEquals(0, result.getValue(PlayerColor.GREEN).total)
    }

    @Test
    fun `every requested player appears even on zero`() {
        val result = Scorer.scoreAll(Board.THARSIS, grid, players)
        assertEquals(players, result.keys)
    }
}
