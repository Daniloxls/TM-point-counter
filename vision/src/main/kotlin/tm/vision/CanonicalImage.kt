package tm.vision

import tm.scoring.HexGrid
import kotlin.math.roundToInt

/**
 * The flat, head-on view a board photo is warped into.
 *
 * Hex centres from [HexGrid] are normalised to the bounding box of centres, so
 * a border hex sits at 0.0 or 1.0 with half a hex hanging outside. This image
 * spans half a hex further in every direction, so every crop box falls inside
 * it with nothing clipped.
 *
 * @param hexCropHeightPx how tall one hex's crop should be, in pixels. The
 *   image's own size follows from it.
 */
class CanonicalImage(hexCropHeightPx: Int) {

    init {
        require(hexCropHeightPx > 0) { "hex crop height must be positive, was $hexCropHeightPx" }
    }

    /** Pixels spanned by the full 0.0..1.0 range of hex centres, vertically. */
    private val contentHeight: Double = hexCropHeightPx / (2.0 * HexGrid.HEX_HALF_HEIGHT)

    /** The same, horizontally — the board's proportions, restored. */
    private val contentWidth: Double = contentHeight * HexGrid.ASPECT_RATIO

    private val marginX: Double = contentWidth * HexGrid.HEX_HALF_WIDTH
    private val marginY: Double = contentHeight * HexGrid.HEX_HALF_HEIGHT

    val widthPx: Int = (contentWidth + 2 * marginX).roundToInt()
    val heightPx: Int = (contentHeight + 2 * marginY).roundToInt()

    fun xToPx(x: Double): Double = marginX + x * contentWidth

    fun yToPx(y: Double): Double = marginY + y * contentHeight
}
