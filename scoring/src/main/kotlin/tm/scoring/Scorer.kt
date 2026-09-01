package tm.scoring

data class ScoreBreakdown(val greeneries: Int, val cityPoints: Int) {
    val total: Int get() = greeneries + cityPoints
}

/**
 * Victory points visible on the map, and nothing else. Terraform rating,
 * milestones, awards, and card points are not derivable from a photo of the
 * board and are deliberately not counted here.
 */
object Scorer {

    fun score(board: Board, grid: Grid): Map<PlayerColor, ScoreBreakdown> {
        val greeneries = mutableMapOf<PlayerColor, Int>()
        val cityPoints = mutableMapOf<PlayerColor, Int>()

        for ((hexId, tile) in grid) {
            val owner = tile.owner ?: continue
            when (tile.type) {
                TileType.GREENERY -> greeneries.merge(owner, 1, Int::plus)
                TileType.CITY -> {
                    val adjacent = board.neighbors(hexId).count { grid[it]?.type == TileType.GREENERY }
                    if (adjacent > 0) cityPoints.merge(owner, adjacent, Int::plus)
                }
                TileType.EMPTY, TileType.OCEAN, TileType.SPECIAL -> Unit
            }
        }

        return (greeneries.keys + cityPoints.keys).associateWith { player ->
            ScoreBreakdown(
                greeneries = greeneries[player] ?: 0,
                cityPoints = cityPoints[player] ?: 0,
            )
        }
    }

    /**
     * As [score], but reports a zero breakdown for every player in [players] so
     * the score screen can show a complete table.
     */
    fun scoreAll(board: Board, grid: Grid, players: Set<PlayerColor>): Map<PlayerColor, ScoreBreakdown> {
        val scored = score(board, grid)
        return players.associateWith { scored[it] ?: ScoreBreakdown(0, 0) }
    }
}
