package tm.scoring

import kotlin.math.abs

/**
 * Geometry shared by every Terraforming Mars map board: a hexagon of hexes with
 * radius 4, laid out in rows of 5-6-7-8-9-8-7-6-5 for 61 hexes total.
 *
 * Coordinates are axial: q increases to the right along a row, r increases
 * downward from row to row.
 */
object HexGrid {

    const val RADIUS = 4

    fun coords(): List<Pair<Int, Int>> =
        (-RADIUS..RADIUS).flatMap { r ->
            (-RADIUS..RADIUS).filter { q -> abs(q + r) <= RADIUS }.map { q -> q to r }
        }
}
