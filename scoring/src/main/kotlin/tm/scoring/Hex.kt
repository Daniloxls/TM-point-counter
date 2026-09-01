package tm.scoring

/**
 * One space on the map.
 *
 * [x] and [y] are the centre of the hex, each axis normalised to 0.0..1.0
 * against the bounding box of hex *centres* — not the board outline. 0.0 and
 * 1.0 are therefore the centres of the border hexes on that axis, not the
 * edges of the warped canonical board image: a border hex's crop extends
 * past 0.0 or 1.0 by [HexGrid.HEX_HALF_WIDTH]/[HexGrid.HEX_HALF_HEIGHT].
 * Rescale [x] by [HexGrid.ASPECT_RATIO] before doing any distance maths with
 * these values.
 */
data class Hex(
    val id: String,
    val q: Int,
    val r: Int,
    val x: Double,
    val y: Double,
    val neighbors: List<String>,
)
