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

        for ((_, tile) in grid) {
            val owner = tile.owner ?: continue
            if (tile.type == TileType.GREENERY) greeneries.merge(owner, 1, Int::plus)
        }

        return greeneries.mapValues { (_, count) -> ScoreBreakdown(greeneries = count, cityPoints = 0) }
    }
}
