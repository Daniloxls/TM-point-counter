package tm.vision

import tm.scoring.PlayerColor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CubeColorReaderTest {

    private val size = 32

    /** A crop whose centre holds a solid block of [centre] on a [surround] field. */
    private fun crop(centre: Int, surround: Int): IntArray {
        val pixels = IntArray(size * size) { surround }
        for (y in 12 until 20) {
            for (x in 12 until 20) {
                pixels[y * size + x] = centre
            }
        }
        return pixels
    }

    private fun argb(r: Int, g: Int, b: Int): Int =
        (0xFF shl 24) or (r shl 16) or (g shl 8) or b

    private val marsSurface = argb(150, 80, 60)

    private fun read(centre: Int) = CubeColorReader.read(crop(centre, marsSurface), size, size)

    @Test
    fun `reads a blue cube`() {
        assertEquals(PlayerColor.BLUE, read(argb(40, 80, 200)))
    }

    @Test
    fun `reads a red cube`() {
        assertEquals(PlayerColor.RED, read(argb(200, 40, 40)))
    }

    @Test
    fun `reads a green cube`() {
        assertEquals(PlayerColor.GREEN, read(argb(50, 170, 70)))
    }

    @Test
    fun `reads a yellow cube`() {
        assertEquals(PlayerColor.YELLOW, read(argb(230, 210, 50)))
    }

    @Test
    fun `reads a black cube`() {
        assertEquals(PlayerColor.BLACK, read(argb(30, 30, 32)))
    }

    @Test
    fun `does not mistake the mars surface for a red cube`() {
        assertNull(CubeColorReader.read(IntArray(size * size) { marsSurface }, size, size))
    }

    @Test
    fun `does not mistake a shadowed board for a black cube`() {
        assertNull(CubeColorReader.read(IntArray(size * size) { argb(70, 45, 38) }, size, size))
    }

    @Test
    fun `reads a cube under a warm light`() {
        assertEquals(PlayerColor.BLUE, read(argb(60, 100, 190)))
    }

    @Test
    fun `ignores pixels outside the sampled centre`() {
        val pixels = IntArray(size * size) { argb(40, 80, 200) }
        for (y in 12 until 20) for (x in 12 until 20) pixels[y * size + x] = argb(200, 40, 40)
        assertEquals(PlayerColor.RED, CubeColorReader.read(pixels, size, size))
    }

    @Test
    fun `rejects a pixel array that does not match its stated size`() {
        kotlin.test.assertFailsWith<IllegalArgumentException> {
            CubeColorReader.read(IntArray(10), size, size)
        }
    }
}
