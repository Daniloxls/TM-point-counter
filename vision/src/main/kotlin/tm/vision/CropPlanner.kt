package tm.vision

import tm.scoring.Board
import tm.scoring.HexGrid
import kotlin.math.roundToInt

/** Where one hex's crop sits in the canonical image, in pixels. */
data class CropBox(
    val hexId: String,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

/**
 * Works out where each hex's crop lands in the canonical image.
 *
 * Every crop is the same size and every crop is inside the image, so a caller
 * can slice them out of a warped bitmap without bounds checks of its own.
 */
object CropPlanner {

    fun plan(board: Board, image: CanonicalImage): List<CropBox> {
        val halfWidthPx = HexGrid.HEX_HALF_WIDTH * (image.xToPx(1.0) - image.xToPx(0.0))
        val halfHeightPx = HexGrid.HEX_HALF_HEIGHT * (image.yToPx(1.0) - image.yToPx(0.0))

        // Find the border hexes
        val leftmost = board.hexes.values.minBy { it.x }
        val rightmost = board.hexes.values.maxBy { it.x }
        val topmost = board.hexes.values.minBy { it.y }
        val bottommost = board.hexes.values.maxBy { it.y }

        // Calculate the left edge of the leftmost crop and the left edge of the rightmost crop
        val leftCropLeft = (image.xToPx(leftmost.x) - halfWidthPx).roundToInt()
        val rightCropLeft = (image.xToPx(rightmost.x) - halfWidthPx).roundToInt()
        val topCropTop = (image.yToPx(topmost.y) - halfHeightPx).roundToInt()
        val bottomCropTop = (image.yToPx(bottommost.y) - halfHeightPx).roundToInt()

        // Derive the crop size once and add it to each rounded origin, so all crops are identical in size
        val cropWidth = image.widthPx - rightCropLeft
        val cropHeight = image.heightPx - bottomCropTop

        return board.hexes.values.sortedBy { it.id }.map { hex ->
            val left = (image.xToPx(hex.x) - halfWidthPx).roundToInt()
            val top = (image.yToPx(hex.y) - halfHeightPx).roundToInt()
            CropBox(
                hexId = hex.id,
                left = left,
                top = top,
                right = left + cropWidth,
                bottom = top + cropHeight,
            )
        }
    }
}
