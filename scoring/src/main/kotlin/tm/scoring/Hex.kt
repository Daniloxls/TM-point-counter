package tm.scoring

/**
 * One space on the map.
 *
 * [x] and [y] are the centre of the hex in the warped canonical board image,
 * each axis normalised to 0.0..1.0. Rescale [x] by [HexGrid.ASPECT_RATIO]
 * before doing any distance maths with these values.
 */
data class Hex(
    val id: String,
    val q: Int,
    val r: Int,
    val x: Double,
    val y: Double,
    val neighbors: List<String>,
)
