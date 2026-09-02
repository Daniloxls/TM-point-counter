package tm.vision

import tm.scoring.Board
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CropPlannerTest {

    private val image = CanonicalImage(hexCropHeightPx = 64)
    private val boxes = CropPlanner.plan(Board.THARSIS, image)

    @Test
    fun `there is one crop per hex`() {
        assertEquals(61, boxes.size)
        assertEquals(Board.THARSIS.hexes.keys, boxes.map { it.hexId }.toSet())
    }

    @Test
    fun `every crop lies inside the image`() {
        for (box in boxes) {
            assertTrue(box.left >= 0, "${box.hexId} left is ${box.left}")
            assertTrue(box.top >= 0, "${box.hexId} top is ${box.top}")
            assertTrue(box.right <= image.widthPx, "${box.hexId} right is ${box.right} of ${image.widthPx}")
            assertTrue(box.bottom <= image.heightPx, "${box.hexId} bottom is ${box.bottom} of ${image.heightPx}")
        }
    }

    @Test
    fun `every crop is the same size`() {
        val first = boxes.first()
        for (box in boxes) {
            assertEquals(first.width, box.width, "${box.hexId} width")
            assertEquals(first.height, box.height, "${box.hexId} height")
        }
    }

    @Test
    fun `crops are the requested height and a sensible width`() {
        assertEquals(64, boxes.first().height)
        assertTrue(boxes.first().width in 40..64, "width was ${boxes.first().width}")
    }

    @Test
    fun `the border hexes' crops touch the image edges`() {
        assertEquals(0, boxes.first { it.hexId == "r5c1" }.left)
        assertEquals(image.widthPx, boxes.first { it.hexId == "r5c9" }.right)
        assertEquals(0, boxes.first { it.hexId == "r1c1" }.top)
        assertEquals(image.heightPx, boxes.first { it.hexId == "r9c1" }.bottom)
    }

    @Test
    fun `crops of adjacent hexes overlap or touch, never leaving a gap`() {
        val byId = boxes.associateBy { it.hexId }
        for (hex in Board.THARSIS.hexes.values) {
            val box = byId.getValue(hex.id)
            for (neighborId in hex.neighbors) {
                val other = byId.getValue(neighborId)
                val horizontalGap = maxOf(box.left - other.right, other.left - box.right)
                val verticalGap = maxOf(box.top - other.bottom, other.top - box.bottom)
                assertTrue(
                    horizontalGap <= 0 && verticalGap <= 0,
                    "${hex.id} and $neighborId leave a gap between their crops",
                )
            }
        }
    }

    @Test
    fun `the plan is ordered by hex id so output is stable`() {
        assertEquals(boxes.map { it.hexId }.sorted(), boxes.map { it.hexId })
    }
}
