package tm.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory

/**
 * Decodes the image at [path] downsampled to within about 2x of
 * [targetWidth] by [targetHeight].
 *
 * A CameraX still is 12MP or more; decoding it at full resolution just to
 * throw most of it away downstream wastes memory and time. A power-of-two
 * [BitmapFactory.Options.inSampleSize] is box-filtered by the decoder, which
 * is the right filter at this ratio — better than decoding at full size and
 * bilinear-scaling down later.
 */
internal fun decodeSampled(path: String, targetWidth: Int, targetHeight: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    var inSampleSize = 1
    if (bounds.outHeight > targetHeight || bounds.outWidth > targetWidth) {
        val halfHeight = bounds.outHeight / 2
        val halfWidth = bounds.outWidth / 2
        while (halfHeight / inSampleSize >= targetHeight && halfWidth / inSampleSize >= targetWidth) {
            inSampleSize *= 2
        }
    }

    return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { this.inSampleSize = inSampleSize })
}
