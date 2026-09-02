package tm.vision

import tm.scoring.Board

/**
 * The four hexes the user aligns by hand when framing a photo.
 *
 * They are the corner hexes of the map's hex field: visually unambiguous to
 * point at, and — because their centres form a rectangle in board space — a
 * clean basis for a perspective transform.
 *
 * The order is fixed and shared with the UI: top-left, top-right, bottom-left,
 * bottom-right.
 */
object WarpAnchors {

    val HEX_IDS: List<String> = listOf("r1c1", "r1c5", "r9c1", "r9c5")

    /**
     * Where the anchor hex centres belong in [image], as
     * `[x0, y0, x1, y1, x2, y2, x3, y3]` — the shape
     * `android.graphics.Matrix.setPolyToPoly` expects.
     */
    fun destinationPoints(image: CanonicalImage): FloatArray {
        val out = FloatArray(8)
        HEX_IDS.forEachIndexed { index, id ->
            val hex = Board.THARSIS.hexes.getValue(id)
            out[index * 2] = image.xToPx(hex.x).toFloat()
            out[index * 2 + 1] = image.yToPx(hex.y).toFloat()
        }
        return out
    }
}
