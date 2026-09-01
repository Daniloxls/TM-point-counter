package tm.vision

import tm.scoring.PlayerColor
import kotlin.math.max
import kotlin.math.min

/**
 * Reads a player cube's colour from one hex crop.
 *
 * Only the middle of the crop is sampled — the cube sits at the centre of the
 * tile and the surrounding art would drown it. Returns null when nothing in
 * that area looks like a cube, which is the right answer for an empty hex, an
 * ocean, or a crop too blurred to call.
 */
object CubeColorReader {

    // ponytail: fixed HSV thresholds, tuned by eye against the official component
    // colours. Real lighting will need adjustment — keep these constants together
    // and named so they can be retuned from real photos without touching the
    // logic. RED_MIN_VALUE is the most likely one to need retuning once real
    // photographs are available.

    /** Fraction of the crop's width and height sampled, centred. */
    private const val SAMPLE_FRACTION = 0.25

    /** Below this saturation a pixel has no usable hue; only black qualifies. */
    private const val MIN_SATURATION = 0.35

    /** Below this value a low-saturation pixel is a black cube rather than shadow. */
    private const val BLACK_MAX_VALUE = 0.22

    /** A red cube is vivid; Martian soil and shadow are dark brick at a similar hue. */
    private const val RED_MIN_VALUE = 0.65

    /** A cube must dominate the sampled area, not merely appear in it. */
    private const val MIN_SHARE = 0.5

    fun read(pixels: IntArray, width: Int, height: Int): PlayerColor? {
        require(pixels.size == width * height) {
            "pixels has ${pixels.size} entries, expected ${width * height}"
        }

        val marginX = ((width * (1 - SAMPLE_FRACTION)) / 2).toInt()
        val marginY = ((height * (1 - SAMPLE_FRACTION)) / 2).toInt()

        val votes = mutableMapOf<PlayerColor, Int>()
        var sampled = 0
        for (y in marginY until height - marginY) {
            for (x in marginX until width - marginX) {
                sampled++
                classify(pixels[y * width + x])?.let { votes.merge(it, 1, Int::plus) }
            }
        }
        if (sampled == 0) return null

        val (colour, count) = votes.maxByOrNull { it.value } ?: return null
        return if (count.toDouble() / sampled >= MIN_SHARE) colour else null
    }

    private fun classify(argb: Int): PlayerColor? {
        val r = ((argb shr 16) and 0xFF) / 255.0
        val g = ((argb shr 8) and 0xFF) / 255.0
        val b = (argb and 0xFF) / 255.0

        val value = max(r, max(g, b))
        val minChannel = min(r, min(g, b))
        val chroma = value - minChannel
        val saturation = if (value == 0.0) 0.0 else chroma / value

        if (saturation < MIN_SATURATION) {
            return if (value <= BLACK_MAX_VALUE) PlayerColor.BLACK else null
        }

        val hue = when (value) {
            r -> 60 * (((g - b) / chroma) % 6)
            g -> 60 * (((b - r) / chroma) + 2)
            else -> 60 * (((r - g) / chroma) + 4)
        }.let { if (it < 0) it + 360 else it }

        return when {
            (hue < 20 || hue >= 330) && value >= RED_MIN_VALUE -> PlayerColor.RED
            hue < 45 -> null // orange, or soil at any brightness: not a cube
            hue < 70 -> PlayerColor.YELLOW
            hue < 170 -> PlayerColor.GREEN
            hue < 260 -> PlayerColor.BLUE
            else -> null // violet: nothing in the box is this colour
        }
    }
}
