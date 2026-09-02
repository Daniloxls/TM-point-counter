package tm.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import tm.scoring.Board
import tm.scoring.PlayerColor
import tm.vision.CanonicalImage
import tm.vision.CropPlanner
import tm.vision.CubeColorReader
import tm.vision.WarpAnchors

/** What the app believes about one hex before the user confirms it. */
data class HexRead(val hexId: String, val suggestedOwner: PlayerColor?)

object BoardWarper {

    /**
     * Warps the photo so the four anchor hexes land where the canonical image
     * expects them. Returns null if the photo cannot be read or the anchors do
     * not describe a usable quadrilateral.
     */
    fun warp(
        photoPath: String,
        anchors: List<Pair<Float, Float>>,
        image: CanonicalImage,
    ): Bitmap? {
        require(anchors.size == 4) { "expected 4 anchors, got ${anchors.size}" }
        // The canonical image is the target resolution; decoding much beyond
        // that just gets thrown away by the warp, and a downsampled decode is
        // box-filtered rather than bilinear-scaled, which reads better.
        val source = decodeSampled(photoPath, image.widthPx * 2, image.heightPx * 2) ?: return null

        val sourcePoints = FloatArray(8)
        anchors.forEachIndexed { index, (x, y) ->
            sourcePoints[index * 2] = x
            sourcePoints[index * 2 + 1] = y
        }

        val matrix = Matrix()
        val mapped = matrix.setPolyToPoly(
            sourcePoints, 0,
            WarpAnchors.destinationPoints(image), 0,
            4,
        )
        if (!mapped) return null

        val out = Bitmap.createBitmap(image.widthPx, image.heightPx, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(source, matrix, Paint(Paint.FILTER_BITMAP_FLAG))
        source.recycle()
        return out
    }

    /** Reads a cube colour out of every hex's crop. Tile types come from the user. */
    fun readCubes(warped: Bitmap, board: Board, image: CanonicalImage): List<HexRead> =
        CropPlanner.plan(board, image).map { box ->
            val pixels = IntArray(box.width * box.height)
            warped.getPixels(pixels, 0, box.width, box.left, box.top, box.width, box.height)
            HexRead(box.hexId, CubeColorReader.read(pixels, box.width, box.height))
        }
}
