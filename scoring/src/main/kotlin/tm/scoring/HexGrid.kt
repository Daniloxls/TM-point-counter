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

    private val DIRECTIONS = listOf(1 to 0, 1 to -1, 0 to -1, -1 to 0, -1 to 1, 0 to 1)

    fun coords(): List<Pair<Int, Int>> =
        (-RADIUS..RADIUS).flatMap { r ->
            (-RADIUS..RADIUS).filter { q -> abs(q + r) <= RADIUS }.map { q -> q to r }
        }

    fun isOnBoard(q: Int, r: Int): Boolean =
        abs(q) <= RADIUS && abs(r) <= RADIUS && abs(q + r) <= RADIUS

    /** Leftmost q on row r. */
    private fun rowStart(r: Int): Int = maxOf(-RADIUS, -RADIUS - r)

    fun idOf(q: Int, r: Int): String {
        require(isOnBoard(q, r)) { "($q, $r) is not on the board" }
        val row = r + RADIUS + 1
        val col = q - rowStart(r) + 1
        return "r${row}c$col"
    }

    fun neighborsOf(q: Int, r: Int): List<String> =
        DIRECTIONS
            .map { (dq, dr) -> q + dq to r + dr }
            .filter { (nq, nr) -> isOnBoard(nq, nr) }
            .map { (nq, nr) -> idOf(nq, nr) }
}
