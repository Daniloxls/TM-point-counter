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

    /** Width divided by height of the bounding box of all hex centres. */
    val ASPECT_RATIO: Double = run {
        val points = coords().map { (q, r) -> pixel(q, r) }
        val width = points.maxOf { it.first } - points.minOf { it.first }
        val height = points.maxOf { it.second } - points.minOf { it.second }
        width / height
    }

    private val hexCache: Map<String, Hex> = buildHexes()

    fun hexes(): Map<String, Hex> = hexCache

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

    /** Pointy-top hex layout in arbitrary units; only ratios matter. */
    private fun pixel(q: Int, r: Int): Pair<Double, Double> =
        Pair(kotlin.math.sqrt(3.0) * (q + r / 2.0), 1.5 * r)

    private fun buildHexes(): Map<String, Hex> {
        val all = coords()
        val points = all.associateWith { (q, r) -> pixel(q, r) }
        val minX = points.values.minOf { it.first }
        val maxX = points.values.maxOf { it.first }
        val minY = points.values.minOf { it.second }
        val maxY = points.values.maxOf { it.second }
        return all.associate { coord ->
            val (q, r) = coord
            val (px, py) = points.getValue(coord)
            val id = idOf(q, r)
            id to Hex(
                id = id,
                q = q,
                r = r,
                x = (px - minX) / (maxX - minX),
                y = (py - minY) / (maxY - minY),
                neighbors = neighborsOf(q, r),
            )
        }
    }
}
