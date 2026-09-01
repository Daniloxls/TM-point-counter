# Android App Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the Android app that photographs a Terraforming Mars board, lets the user confirm what is on each hex, and shows the per-player map score — fully usable with no model, by manual entry.

**Architecture:** Two new modules on top of the finished `:scoring`. `:vision` holds the image geometry as pure Kotlin — where each hex crop lands in a canonical warped image, and what colour cube sits in a crop — so the part that can be wrong silently is unit-tested without an emulator. `:app` is a single-Activity Compose app that walks capture → corner anchors → warp → review grid → score, holding one immutable state object throughout. No machine learning in this plan: every hex starts `EMPTY` and the user edits it, which makes the app complete on its own and leaves Plan 3 a single function to replace.

**Tech Stack:** Kotlin 2.1.0, JDK 17, Gradle 8.14.3, AGP 8.11.0, compileSdk 36, minSdk 26, Jetpack Compose (Material 3), CameraX, JUnit 5 / kotlin-test. No OpenCV, no navigation library, no dependency injection framework, no image-loading library.

**Spec:** `docs/superpowers/specs/2026-09-01-tm-point-counter-design.md`

**Depends on:** `docs/superpowers/plans/2026-09-01-scoring-core.md` (complete — `:scoring` exists and is green).

## Deviations from the spec

Three, all deliberate. Task 9 folds them back into the spec.

1. **No OpenCV.** The spec names `getPerspectiveTransform` and `warpPerspective`. `android.graphics.Matrix.setPolyToPoly` performs exactly the same 4-point perspective mapping, and `Canvas.drawBitmap(bitmap, matrix, paint)` applies it. OpenCV's Android distribution is roughly 100MB of native libraries for one function the platform already ships.
2. **Corner anchors are hex centres, not map corners.** The spec has the user dragging four handles onto "the corners of the printed map area". A Terraforming Mars board is a printed sheet with the hex field inside it and no crisp corner to aim at, so that instruction is ambiguous to within a centimetre. Instead the user drags four handles onto the centres of the four corner hexes — `r1c1`, `r1c5`, `r9c1`, `r9c5` — which are visually unambiguous. In board space those four centres form an exact rectangle (verified below), so they anchor a perspective transform cleanly.
3. **Ocean-reserved hints are inert.** `Board.oceanReserved` is still empty pending transcription from the physical boards, so the spec's "greenery on an ocean-reserved hex is flagged" behaviour is implemented but never fires. The code path is built and tested against a board with a stubbed ocean set, so it starts working the moment the real data lands.

## Global Constraints

- Kotlin 2.1.0, JVM target 17, Gradle 8.14.3, AGP 8.11.0.
- `compileSdk = 36`, `targetSdk = 36`, `minSdk = 26`.
- `:scoring` keeps zero Android dependencies. Nothing in this plan may add one to it.
- `:vision` is pure Kotlin with no Android dependencies either: it depends only on `:scoring`. Anything needing `android.graphics` lives in `:app`.
- `:app` may depend on `:vision` and `:scoring`. Neither may depend on `:app`.
- Board geometry facts, from `:scoring`: 61 hexes, ids `r<row>c<col>`, rows 1-9 top to bottom and columns 1-N left to right. `Hex.x` and `Hex.y` are normalised to the bounding box of hex **centres** — 0.0 and 1.0 are the centres of border hexes, not image edges. `HexGrid.HEX_HALF_WIDTH` = 0.0625 and `HexGrid.HEX_HALF_HEIGHT` ≈ 0.08333 are the hex half-extents in those same normalised units. `HexGrid.ASPECT_RATIO` ≈ 1.1547 is the width-to-height ratio of the centre bounding box.
- The four anchor hexes are `r1c1`, `r1c5`, `r9c1`, `r9c5`, in that order wherever an ordered list of anchors is passed.
- Player colours: `BLUE`, `RED`, `GREEN`, `YELLOW`, `BLACK`. Tile types: `EMPTY`, `OCEAN`, `GREENERY`, `CITY`, `SPECIAL`.
- The score screen labels its output "map points", never "score" or "victory points", so it is never mistaken for a final game score.

## Why the anchor hexes form a rectangle

Worth stating once, because Task 2 depends on it. `HexGrid.pixel(q, r)` is `(√3·(q + r/2), 1.5·r)`, and `idOf` maps these four ids to axial coordinates as follows:

| id | (q, r) | pixel x | pixel y |
|---|---|---|---|
| `r1c1` | (0, -4) | −2√3 | −6 |
| `r1c5` | (4, -4) | +2√3 | −6 |
| `r9c1` | (-4, 4) | −2√3 | +6 |
| `r9c5` | (0, 4) | +2√3 | +6 |

Two distinct x values and two distinct y values, paired the way a rectangle's corners are. Task 2's first test asserts this against the real geometry rather than taking this table's word for it.

---

### Task 1: `:vision` module and the canonical image mapping

**Files:**
- Modify: `settings.gradle.kts`
- Create: `vision/build.gradle.kts`
- Create: `vision/src/main/kotlin/tm/vision/CanonicalImage.kt`
- Test: `vision/src/test/kotlin/tm/vision/CanonicalImageTest.kt`

**Interfaces:**
- Consumes: `tm.scoring.HexGrid.ASPECT_RATIO`, `HEX_HALF_WIDTH`, `HEX_HALF_HEIGHT`.
- Produces: `tm.vision.CanonicalImage(hexCropHeightPx: Int)` with `widthPx: Int`, `heightPx: Int`, `xToPx(x: Double): Double`, `yToPx(y: Double): Double`.

The canonical image is the flat, head-on view the photo is warped into. It spans normalised x from `-HEX_HALF_WIDTH` to `1 + HEX_HALF_WIDTH` and normalised y from `-HEX_HALF_HEIGHT` to `1 + HEX_HALF_HEIGHT`, so every hex's crop box falls inside the image with nothing clipped at the border. Its size is derived from one parameter — how many pixels tall each hex crop should be — so callers pick image resolution by picking crop quality.

- [ ] **Step 1: Add the module to the build**

In `settings.gradle.kts`, add `include(":vision")` below the existing `include(":scoring")`.

`vision/build.gradle.kts`:

```kotlin
plugins {
    kotlin("jvm") version "2.1.0"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":scoring"))
    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(17)
}

tasks.test {
    useJUnitPlatform()
}
```

- [ ] **Step 2: Write the failing test**

`vision/src/test/kotlin/tm/vision/CanonicalImageTest.kt`:

```kotlin
package tm.vision

import tm.scoring.HexGrid
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CanonicalImageTest {

    private val image = CanonicalImage(hexCropHeightPx = 64)

    @Test
    fun `a hex crop is the requested height`() {
        val top = image.yToPx(0.5 - HexGrid.HEX_HALF_HEIGHT)
        val bottom = image.yToPx(0.5 + HexGrid.HEX_HALF_HEIGHT)
        assertEquals(64.0, bottom - top, 1e-6)
    }

    @Test
    fun `the border hexes' crops sit exactly at the image edges`() {
        assertEquals(0.0, image.xToPx(0.0 - HexGrid.HEX_HALF_WIDTH), 1e-6)
        assertEquals(0.0, image.yToPx(0.0 - HexGrid.HEX_HALF_HEIGHT), 1e-6)
        assertEquals(image.widthPx.toDouble(), image.xToPx(1.0 + HexGrid.HEX_HALF_WIDTH), 0.5)
        assertEquals(image.heightPx.toDouble(), image.yToPx(1.0 + HexGrid.HEX_HALF_HEIGHT), 0.5)
    }

    @Test
    fun `the mapping is linear and increasing`() {
        assertTrue(image.xToPx(0.0) < image.xToPx(0.5))
        assertTrue(image.xToPx(0.5) < image.xToPx(1.0))
        val firstHalf = image.xToPx(0.5) - image.xToPx(0.0)
        val secondHalf = image.xToPx(1.0) - image.xToPx(0.5)
        assertEquals(firstHalf, secondHalf, 1e-6)
    }

    @Test
    fun `the image keeps the board's proportions`() {
        val contentWidth = image.xToPx(1.0) - image.xToPx(0.0)
        val contentHeight = image.yToPx(1.0) - image.yToPx(0.0)
        assertEquals(HexGrid.ASPECT_RATIO, contentWidth / contentHeight, 1e-6)
    }

    @Test
    fun `the image is large enough to be useful and small enough to hold in memory`() {
        assertTrue(image.widthPx in 200..4000, "width was ${image.widthPx}")
        assertTrue(image.heightPx in 200..4000, "height was ${image.heightPx}")
    }

    @Test
    fun `a non-positive crop height is rejected`() {
        assertFailsWith<IllegalArgumentException> { CanonicalImage(0) }
        assertFailsWith<IllegalArgumentException> { CanonicalImage(-64) }
    }
}
```

- [ ] **Step 3: Run the test to verify it fails**

Run: `./gradlew :vision:test`
Expected: FAIL — `Unresolved reference: CanonicalImage`.

- [ ] **Step 4: Write the implementation**

`vision/src/main/kotlin/tm/vision/CanonicalImage.kt`:

```kotlin
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
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `./gradlew :vision:test`
Expected: PASS, 6 tests.

- [ ] **Step 6: Commit**

```bash
git add settings.gradle.kts vision
git commit -m "feat(vision): add canonical warped-image mapping"
```

---

### Task 2: Warp anchors

**Files:**
- Create: `vision/src/main/kotlin/tm/vision/WarpAnchors.kt`
- Test: `vision/src/test/kotlin/tm/vision/WarpAnchorsTest.kt`

**Interfaces:**
- Consumes: `CanonicalImage`, `tm.scoring.Board`, `tm.scoring.Hex`.
- Produces: `tm.vision.WarpAnchors.HEX_IDS: List<String>`, `tm.vision.WarpAnchors.destinationPoints(image: CanonicalImage): FloatArray`.

The user drags four handles onto the centres of the four corner hexes in the photo. Those four screen points are the source polygon; this task supplies the destination polygon — where those same four hex centres belong in the canonical image. `android.graphics.Matrix.setPolyToPoly` takes both as flat `FloatArray`s of `[x0, y0, x1, y1, ...]`, which is why the output is shaped that way.

- [ ] **Step 1: Write the failing test**

`vision/src/test/kotlin/tm/vision/WarpAnchorsTest.kt`:

```kotlin
package tm.vision

import tm.scoring.Board
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WarpAnchorsTest {

    private val image = CanonicalImage(hexCropHeightPx = 64)

    @Test
    fun `the anchors are the four corner hexes in a fixed order`() {
        assertEquals(listOf("r1c1", "r1c5", "r9c1", "r9c5"), WarpAnchors.HEX_IDS)
    }

    @Test
    fun `every anchor is a real hex on the board`() {
        for (id in WarpAnchors.HEX_IDS) {
            assertTrue(id in Board.THARSIS.hexes, "$id is not a hex")
        }
    }

    @Test
    fun `the anchor centres form a rectangle in board space`() {
        val hexes = WarpAnchors.HEX_IDS.map { Board.THARSIS.hexes.getValue(it) }
        val (topLeft, topRight, bottomLeft, bottomRight) = hexes
        assertEquals(topLeft.y, topRight.y, 1e-9, "the top two anchors should share a row")
        assertEquals(bottomLeft.y, bottomRight.y, 1e-9, "the bottom two anchors should share a row")
        assertEquals(topLeft.x, bottomLeft.x, 1e-9, "the left two anchors should share a column")
        assertEquals(topRight.x, bottomRight.x, 1e-9, "the right two anchors should share a column")
        assertTrue(topLeft.x < topRight.x, "left should be left of right")
        assertTrue(topLeft.y < bottomLeft.y, "top should be above bottom")
    }

    @Test
    fun `destination points are eight floats in x y order`() {
        val points = WarpAnchors.destinationPoints(image)
        assertEquals(8, points.size)
    }

    @Test
    fun `destination points match the canonical mapping of those hexes`() {
        val points = WarpAnchors.destinationPoints(image)
        WarpAnchors.HEX_IDS.forEachIndexed { index, id ->
            val hex = Board.THARSIS.hexes.getValue(id)
            assertEquals(image.xToPx(hex.x).toFloat(), points[index * 2], 1e-3f, "$id x")
            assertEquals(image.yToPx(hex.y).toFloat(), points[index * 2 + 1], 1e-3f, "$id y")
        }
    }

    @Test
    fun `destination points sit inside the canonical image`() {
        val points = WarpAnchors.destinationPoints(image)
        for (i in 0 until 4) {
            assertTrue(points[i * 2] in 0f..image.widthPx.toFloat(), "point $i x is outside")
            assertTrue(points[i * 2 + 1] in 0f..image.heightPx.toFloat(), "point $i y is outside")
        }
    }

    @Test
    fun `the anchor rectangle is not degenerate`() {
        val points = WarpAnchors.destinationPoints(image)
        val width = abs(points[2] - points[0])
        val height = abs(points[5] - points[1])
        assertTrue(width > 1f, "anchors are horizontally collapsed")
        assertTrue(height > 1f, "anchors are vertically collapsed")
    }
}
```

The rectangle test is the one that matters: the whole corner-anchor scheme rests on those four centres being a rectangle in board space, and this asserts it against the real geometry rather than trusting the plan's arithmetic.

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :vision:test --tests '*WarpAnchorsTest*'`
Expected: FAIL — `Unresolved reference: WarpAnchors`.

- [ ] **Step 3: Write the implementation**

`vision/src/main/kotlin/tm/vision/WarpAnchors.kt`:

```kotlin
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
```

All boards share one geometry, so `Board.THARSIS` here is a source of coordinates, not a claim about which board the user is scoring. The kdoc says so; do not add a `board` parameter that would imply otherwise.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :vision:test`
Expected: PASS, 13 tests.

- [ ] **Step 5: Commit**

```bash
git add vision
git commit -m "feat(vision): add warp anchor hexes and destination points"
```

---

### Task 3: Crop planning

**Files:**
- Create: `vision/src/main/kotlin/tm/vision/CropPlanner.kt`
- Test: `vision/src/test/kotlin/tm/vision/CropPlannerTest.kt`

**Interfaces:**
- Consumes: `CanonicalImage`, `tm.scoring.Board`, `tm.scoring.HexGrid`.
- Produces: `tm.vision.CropBox(hexId: String, left: Int, top: Int, right: Int, bottom: Int)` with `width: Int` and `height: Int`; `tm.vision.CropPlanner.plan(board: Board, image: CanonicalImage): List<CropBox>`.

- [ ] **Step 1: Write the failing test**

`vision/src/test/kotlin/tm/vision/CropPlannerTest.kt`:

```kotlin
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
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :vision:test --tests '*CropPlannerTest*'`
Expected: FAIL — `Unresolved reference: CropPlanner`.

- [ ] **Step 3: Write the implementation**

`vision/src/main/kotlin/tm/vision/CropPlanner.kt`:

```kotlin
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
        val cropWidth = (2 * halfWidthPx).roundToInt()
        val cropHeight = (2 * halfHeightPx).roundToInt()

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
```

Deriving the crop size once and adding it to each rounded origin — rather than rounding both edges independently — is what keeps every box identical in size, which the model in Plan 3 will require.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :vision:test`
Expected: PASS, 20 tests.

If `the border hexes' crops touch the image edges` fails by one pixel, the cause is rounding, not geometry: report the actual values rather than loosening the assertion, and fix it by rounding the image dimensions and the crop origins consistently.

- [ ] **Step 5: Commit**

```bash
git add vision
git commit -m "feat(vision): add per-hex crop planning"
```

---

### Task 4: Cube colour reading

**Files:**
- Create: `vision/src/main/kotlin/tm/vision/CubeColorReader.kt`
- Test: `vision/src/test/kotlin/tm/vision/CubeColorReaderTest.kt`

**Interfaces:**
- Consumes: `tm.scoring.PlayerColor`.
- Produces: `tm.vision.CubeColorReader.read(pixels: IntArray, width: Int, height: Int): PlayerColor?`.

Reads the player cube's colour out of one hex crop. Pixels arrive as packed ARGB ints — the format `android.graphics.Bitmap.getPixels` produces — so this stays pure Kotlin and testable without an emulator. The cube sits at the centre of the tile, so only the middle of the crop is sampled; the tile art around it would drown the signal.

Returns null when nothing looks like a cube, which is the honest answer for an empty hex, an ocean, or a crop too blurred to call. The review screen shows null as "unknown" and lets the user set it.

- [ ] **Step 1: Write the failing test**

`vision/src/test/kotlin/tm/vision/CubeColorReaderTest.kt`:

```kotlin
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
```

The two negative tests are the point of this task. A reddish-brown Mars surface is the single most likely thing to be misread as a red cube, and a shadowed board as a black one. If those two pass, the reader is doing something better than "which colour is nearest".

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :vision:test --tests '*CubeColorReaderTest*'`
Expected: FAIL — `Unresolved reference: CubeColorReader`.

- [ ] **Step 3: Write the implementation**

`vision/src/main/kotlin/tm/vision/CubeColorReader.kt`:

```kotlin
package tm.vision

import tm.scoring.PlayerColor
import kotlin.math.abs
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

    /** Fraction of the crop's width and height sampled, centred. */
    private const val SAMPLE_FRACTION = 0.35

    /** Below this saturation a pixel has no usable hue; only black qualifies. */
    private const val MIN_SATURATION = 0.35

    /** Below this value a low-saturation pixel is a black cube rather than shadow. */
    private const val BLACK_MAX_VALUE = 0.22

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
            hue < 20 || hue >= 330 -> PlayerColor.RED
            hue < 45 -> null // orange: the Mars surface, not a cube
            hue < 70 -> PlayerColor.YELLOW
            hue < 170 -> PlayerColor.GREEN
            hue < 260 -> PlayerColor.BLUE
            else -> null // violet: nothing in the box is this colour
        }
    }
}
```

`// ponytail: fixed HSV thresholds, tuned by eye against the official component
colours. Real lighting will need adjustment — keep these constants together
and named so they can be retuned from real photos without touching the logic.`
Put that note as a comment above `SAMPLE_FRACTION`, reformatted as normal
Kotlin comment lines.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :vision:test`
Expected: PASS, 30 tests.

If a threshold needs moving to make a test pass, move the named constant and say which one in your report — do not special-case a colour in the `when`.

- [ ] **Step 5: Commit**

```bash
git add vision
git commit -m "feat(vision): read player cube colour from a hex crop"
```

---

### Task 5: `:app` module skeleton

**Files:**
- Modify: `settings.gradle.kts`
- Modify: `build.gradle.kts`
- Create: `gradle/libs.versions.toml`
- Create: `app/build.gradle.kts`
- Create: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/kotlin/tm/app/MainActivity.kt`
- Create: `app/src/main/res/values/strings.xml`
- Modify: `.gitignore`

**Interfaces:**
- Consumes: nothing yet.
- Produces: an installable debug APK that launches to a board picker.

This task's deliverable is a running app, not a feature. It ends when the APK builds and launches.

- [ ] **Step 1: Add the Android plugins to the root build**

`settings.gradle.kts` — add `include(":app")`, and add the plugin repositories Android requires. The full file afterwards:

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "tm-point-counter"
include(":scoring")
include(":vision")
include(":app")
```

`build.gradle.kts` at the root:

```kotlin
plugins {
    kotlin("jvm") version "2.1.0" apply false
    id("com.android.application") version "8.11.0" apply false
    id("org.jetbrains.kotlin.android") version "2.1.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.0" apply false
}
```

The `:scoring` and `:vision` modules declare their own `repositories { mavenCentral() }` blocks. Leave those alone — `dependencyResolutionManagement` above defaults to `PREFER_PROJECT` mode, so per-project repositories still win and those modules keep building exactly as they do now.

- [ ] **Step 2: Add the version catalog**

`gradle/libs.versions.toml`:

```toml
[versions]
composeBom = "2024.09.03"
activityCompose = "1.9.2"
coreKtx = "1.13.1"
lifecycle = "2.8.6"
camerax = "1.3.4"

[libraries]
androidx-core-ktx = { module = "androidx.core:core-ktx", version.ref = "coreKtx" }
androidx-activity-compose = { module = "androidx.activity:activity-compose", version.ref = "activityCompose" }
androidx-lifecycle-runtime-ktx = { module = "androidx.lifecycle:lifecycle-runtime-ktx", version.ref = "lifecycle" }
compose-bom = { module = "androidx.compose:compose-bom", version.ref = "composeBom" }
compose-ui = { module = "androidx.compose.ui:ui" }
compose-ui-graphics = { module = "androidx.compose.ui:ui-graphics" }
compose-ui-tooling-preview = { module = "androidx.compose.ui:ui-tooling-preview" }
compose-material3 = { module = "androidx.compose.material3:material3" }
camera-core = { module = "androidx.camera:camera-core", version.ref = "camerax" }
camera-camera2 = { module = "androidx.camera:camera-camera2", version.ref = "camerax" }
camera-lifecycle = { module = "androidx.camera:camera-lifecycle", version.ref = "camerax" }
camera-view = { module = "androidx.camera:camera-view", version.ref = "camerax" }
```

These versions are known-good starting points, not gospel. If any fails to resolve, bump it to the newest version that does, and **record every version you changed in your report** — the next tasks and Plan 3 need to know what the project actually resolved to.

- [ ] **Step 3: Configure the app module**

`app/build.gradle.kts`:

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "tm.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "tm.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    sourceSets["main"].java.srcDirs("src/main/kotlin")
}

dependencies {
    implementation(project(":scoring"))
    implementation(project(":vision"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)

    implementation(libs.camera.core)
    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.view)
}
```

- [ ] **Step 4: Add the manifest and resources**

`app/src/main/AndroidManifest.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.CAMERA" />
    <uses-feature android:name="android.hardware.camera.any" android:required="false" />

    <application
        android:allowBackup="true"
        android:label="@string/app_name"
        android:supportsRtl="true"
        android:theme="@style/Theme.Material3.DayNight.NoActionBar">
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:screenOrientation="portrait">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

`app/src/main/res/values/strings.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">TM Points</string>
</resources>
```

- [ ] **Step 5: Add the activity and board picker**

`app/src/main/kotlin/tm/app/MainActivity.kt`:

```kotlin
package tm.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import tm.scoring.Board

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    BoardPicker(onBoardChosen = { })
                }
            }
        }
    }
}

@Composable
fun BoardPicker(onBoardChosen: (Board) -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Which board?", style = MaterialTheme.typography.headlineSmall)
        for (board in Board.ALL) {
            Button(
                onClick = { onBoardChosen(board) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(board.id.replaceFirstChar { it.uppercase() })
            }
        }
    }
}
```

- [ ] **Step 6: Ignore Android build output**

Add to `.gitignore`, keeping every existing line:

```
app/build/
vision/build/
scoring/build/
.cxx/
```

- [ ] **Step 7: Build and install**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL, and `app/build/outputs/apk/debug/app-debug.apk` exists.

Then confirm the whole project still builds together:

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL, `:scoring` and `:vision` tests still green.

If an emulator or device is available (`adb devices` lists one), install and launch it:

```bash
./gradlew :app:installDebug
adb shell am start -n tm.app/.MainActivity
```

Confirm the picker shows three buttons — Tharsis, Hellas, Elysium. If no device is available, say so in your report; the build gate is what this task turns on.

- [ ] **Step 8: Commit**

```bash
git add settings.gradle.kts build.gradle.kts gradle/libs.versions.toml app .gitignore
git commit -m "feat(app): add Android module and board picker"
```

---

### Task 6: App state and the screen flow

**Files:**
- Create: `app/src/main/kotlin/tm/app/AppState.kt`
- Modify: `app/src/main/kotlin/tm/app/MainActivity.kt`
- Test: `app/src/test/kotlin/tm/app/AppStateTest.kt`
- Modify: `app/build.gradle.kts`

**Interfaces:**
- Consumes: `tm.scoring.Board`, `tm.scoring.Grid`, `tm.scoring.Tile`, `tm.scoring.PlayerColor`.
- Produces: `tm.app.Step` (`PICK_BOARD`, `CAPTURE`, `ANCHORS`, `REVIEW`, `SCORE`), `tm.app.AppState` (immutable data class) with `board`, `photoPath`, `anchors`, `grid`, `players`, `step`, plus the transitions `withBoard`, `withPhoto`, `withAnchors`, `withTile`, `back`.

One immutable state object drives every screen. No navigation library: `MainActivity` renders a `when (state.step)`. That is the whole router, it fits on a screen, and it makes the flow's legality testable in plain JVM — which is why the transitions live here rather than inside composables.

- [ ] **Step 1: Enable unit tests in the app module**

Add to `app/build.gradle.kts`, inside the `android` block:

```kotlin
    testOptions {
        unitTests.all { it.useJUnitPlatform() }
    }
```

and to its `dependencies` block:

```kotlin
    testImplementation(kotlin("test"))
```

- [ ] **Step 2: Write the failing test**

`app/src/test/kotlin/tm/app/AppStateTest.kt`:

```kotlin
package tm.app

import tm.scoring.Board
import tm.scoring.PlayerColor
import tm.scoring.Tile
import tm.scoring.TileType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppStateTest {

    private val fresh = AppState()

    @Test
    fun `a fresh session starts at the board picker with nothing chosen`() {
        assertEquals(Step.PICK_BOARD, fresh.step)
        assertNull(fresh.board)
        assertTrue(fresh.grid.isEmpty())
    }

    @Test
    fun `choosing a board moves to capture`() {
        val state = fresh.withBoard(Board.THARSIS)
        assertEquals(Board.THARSIS, state.board)
        assertEquals(Step.CAPTURE, state.step)
    }

    @Test
    fun `taking a photo moves to the anchor step`() {
        val state = fresh.withBoard(Board.THARSIS).withPhoto("/tmp/board.jpg")
        assertEquals("/tmp/board.jpg", state.photoPath)
        assertEquals(Step.ANCHORS, state.step)
    }

    @Test
    fun `placing anchors moves to review`() {
        val anchors = List(4) { it.toFloat() to it.toFloat() }
        val state = fresh.withBoard(Board.THARSIS).withPhoto("/tmp/board.jpg").withAnchors(anchors)
        assertEquals(anchors, state.anchors)
        assertEquals(Step.REVIEW, state.step)
    }

    @Test
    fun `anchors must be exactly four points`() {
        val state = fresh.withBoard(Board.THARSIS).withPhoto("/tmp/board.jpg")
        kotlin.test.assertFailsWith<IllegalArgumentException> {
            state.withAnchors(List(3) { 0f to 0f })
        }
    }

    @Test
    fun `editing a tile keeps the user on the review step`() {
        val state = reviewing().withTile("r5c5", Tile(TileType.GREENERY, PlayerColor.RED))
        assertEquals(Step.REVIEW, state.step)
        assertEquals(Tile(TileType.GREENERY, PlayerColor.RED), state.grid["r5c5"])
    }

    @Test
    fun `setting a tile to empty removes it from the grid`() {
        val state = reviewing()
            .withTile("r5c5", Tile(TileType.GREENERY, PlayerColor.RED))
            .withTile("r5c5", Tile(TileType.EMPTY, null))
        assertTrue("r5c5" !in state.grid)
    }

    @Test
    fun `editing a hex that is not on the board is rejected`() {
        kotlin.test.assertFailsWith<IllegalArgumentException> {
            reviewing().withTile("r99c99", Tile(TileType.GREENERY, PlayerColor.RED))
        }
    }

    @Test
    fun `players are whoever owns a tile`() {
        val state = reviewing()
            .withTile("r5c5", Tile(TileType.GREENERY, PlayerColor.RED))
            .withTile("r5c6", Tile(TileType.CITY, PlayerColor.BLUE))
            .withTile("r5c7", Tile(TileType.OCEAN, null))
        assertEquals(setOf(PlayerColor.RED, PlayerColor.BLUE), state.players)
    }

    @Test
    fun `going back from review returns to the anchors`() {
        assertEquals(Step.ANCHORS, reviewing().back().step)
    }

    @Test
    fun `going back from the board picker stays put`() {
        assertEquals(Step.PICK_BOARD, fresh.back().step)
    }

    @Test
    fun `going back keeps the work already done`() {
        val state = reviewing().withTile("r5c5", Tile(TileType.GREENERY, PlayerColor.RED))
        assertEquals(state.grid, state.back().grid)
    }

    private fun reviewing(): AppState =
        fresh.withBoard(Board.THARSIS).withPhoto("/tmp/board.jpg").withAnchors(List(4) { 0f to 0f })
}
```

- [ ] **Step 3: Run the test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest`
Expected: FAIL — `Unresolved reference: AppState`.

- [ ] **Step 4: Write the implementation**

`app/src/main/kotlin/tm/app/AppState.kt`:

```kotlin
package tm.app

import tm.scoring.Board
import tm.scoring.Grid
import tm.scoring.PlayerColor
import tm.scoring.Tile
import tm.scoring.TileType

/** Where the user is in the flow. */
enum class Step { PICK_BOARD, CAPTURE, ANCHORS, REVIEW, SCORE }

/**
 * Everything one scoring session knows, as one immutable value.
 *
 * The transitions are here rather than in the composables so the flow's rules
 * can be tested without an emulator.
 */
data class AppState(
    val board: Board? = null,
    val photoPath: String? = null,
    val anchors: List<Pair<Float, Float>> = emptyList(),
    val grid: Grid = emptyMap(),
    val step: Step = Step.PICK_BOARD,
) {
    /** Whoever owns at least one tile. Drives the score table's rows. */
    val players: Set<PlayerColor> get() = grid.values.mapNotNull { it.owner }.toSet()

    fun withBoard(board: Board): AppState = copy(board = board, step = Step.CAPTURE)

    fun withPhoto(path: String): AppState = copy(photoPath = path, step = Step.ANCHORS)

    fun withAnchors(points: List<Pair<Float, Float>>): AppState {
        require(points.size == 4) { "expected 4 anchors, got ${points.size}" }
        return copy(anchors = points, step = Step.REVIEW)
    }

    fun withTile(hexId: String, tile: Tile): AppState {
        val board = requireNotNull(board) { "no board chosen" }
        require(hexId in board.hexes) { "$hexId is not a hex on ${board.id}" }
        // An empty hex is an absent hex: one encoding, so :scoring never sees both.
        val updated = if (tile.type == TileType.EMPTY) grid - hexId else grid + (hexId to tile)
        return copy(grid = updated)
    }

    fun toScore(): AppState = copy(step = Step.SCORE)

    fun back(): AppState = copy(
        step = when (step) {
            Step.PICK_BOARD -> Step.PICK_BOARD
            Step.CAPTURE -> Step.PICK_BOARD
            Step.ANCHORS -> Step.CAPTURE
            Step.REVIEW -> Step.ANCHORS
            Step.SCORE -> Step.REVIEW
        },
    )
}
```

- [ ] **Step 5: Wire the router**

Replace `MainActivity`'s `setContent` body so it holds the state and dispatches on the step. Keep `BoardPicker` as it is:

```kotlin
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    var state by remember { mutableStateOf(AppState()) }
                    when (state.step) {
                        Step.PICK_BOARD -> BoardPicker(onBoardChosen = { state = state.withBoard(it) })
                        Step.CAPTURE -> Text("Capture — Task 7")
                        Step.ANCHORS -> Text("Anchors — Task 8")
                        Step.REVIEW -> Text("Review — Task 9")
                        Step.SCORE -> Text("Score — Task 10")
                    }
                }
            }
        }
```

Add the imports `androidx.compose.runtime.getValue`, `androidx.compose.runtime.setValue`, `androidx.compose.runtime.mutableStateOf`, and `androidx.compose.runtime.remember`.

The placeholder `Text` calls are scaffolding this plan replaces in the next four tasks — each one names the task that fills it in. Leave them exactly as written.

- [ ] **Step 6: Run the tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS, 12 tests.

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add app
git commit -m "feat(app): add session state and screen flow"
```

---

### Task 7: Capture screen

**Files:**
- Create: `app/src/main/kotlin/tm/app/CaptureScreen.kt`
- Modify: `app/src/main/kotlin/tm/app/MainActivity.kt`

**Interfaces:**
- Consumes: `AppState.withPhoto`.
- Produces: `@Composable fun CaptureScreen(onCaptured: (String) -> Unit, onBack: () -> Unit)`.

Asks for the camera permission, shows a live preview, and writes a full-resolution still to the app's cache directory, handing back the path. Nothing is analysed here.

- [ ] **Step 1: Write the screen**

`app/src/main/kotlin/tm/app/CaptureScreen.kt`:

```kotlin
package tm.app

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import java.io.File

@Composable
fun CaptureScreen(onCaptured: (String) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var error by remember { mutableStateOf<String?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> hasPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    if (!hasPermission) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("The camera is needed to photograph the board.")
            Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                Text("Allow camera")
            }
            TextButton(onClick = onBack) { Text("Back") }
        }
        return
    }

    val imageCapture = remember { ImageCapture.Builder().build() }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    val previewView = PreviewView(ctx)
                    val providerFuture = ProcessCameraProvider.getInstance(ctx)
                    providerFuture.addListener({
                        val provider = providerFuture.get()
                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }
                        provider.unbindAll()
                        provider.bindToLifecycle(
                            lifecycleOwner,
                            androidx.camera.core.CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            imageCapture,
                        )
                    }, ContextCompat.getMainExecutor(ctx))
                    previewView
                },
            )
        }

        error?.let { Text(it, modifier = Modifier.padding(horizontal = 16.dp)) }

        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Frame the whole board, as square-on as you can.")
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    val file = File(context.cacheDir, "board-${System.currentTimeMillis()}.jpg")
                    imageCapture.takePicture(
                        ImageCapture.OutputFileOptions.Builder(file).build(),
                        ContextCompat.getMainExecutor(context),
                        object : ImageCapture.OnImageSavedCallback {
                            override fun onImageSaved(results: ImageCapture.OutputFileResults) {
                                onCaptured(file.absolutePath)
                            }

                            override fun onError(exception: ImageCaptureException) {
                                error = "Could not take the photo: ${exception.message}"
                            }
                        },
                    )
                },
            ) {
                Text("Take photo")
            }
            TextButton(onClick = onBack) { Text("Back") }
        }
    }
}
```

The capture error is shown, not swallowed and not crashed on — a failed shutter must leave the user on this screen with a way to retry.

- [ ] **Step 2: Wire it into the router**

In `MainActivity`, replace `Step.CAPTURE -> Text("Capture — Task 7")` with:

```kotlin
                        Step.CAPTURE -> CaptureScreen(
                            onCaptured = { state = state.withPhoto(it) },
                            onBack = { state = state.back() },
                        )
```

- [ ] **Step 3: Build and verify on a device**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

If a device or emulator is available, install and check by hand: the permission prompt appears on first launch; the preview shows; "Take photo" returns to a screen reading "Anchors — Task 8"; and a JPEG lands in the app's cache directory (`adb shell run-as tm.app ls cache`). Note in your report which of these you could verify and which you could not — an emulator's simulated camera is enough to prove the flow, even though the picture is meaningless.

- [ ] **Step 4: Commit**

```bash
git add app
git commit -m "feat(app): add camera capture screen"
```

---

### Task 8: Anchor placement screen

**Files:**
- Create: `app/src/main/kotlin/tm/app/AnchorScreen.kt`
- Modify: `app/src/main/kotlin/tm/app/MainActivity.kt`

**Interfaces:**
- Consumes: `tm.vision.WarpAnchors.HEX_IDS`, `AppState.withAnchors`.
- Produces: `@Composable fun AnchorScreen(photoPath: String, onPlaced: (List<Pair<Float, Float>>) -> Unit, onBack: () -> Unit)`.

Shows the photo with four draggable handles, one per anchor hex, and hands back their positions in **image pixel coordinates** — not screen coordinates, which would be meaningless once the view is resized.

- [ ] **Step 1: Write the screen**

`app/src/main/kotlin/tm/app/AnchorScreen.kt`:

```kotlin
package tm.app

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import tm.vision.WarpAnchors
import kotlin.math.min

private val ANCHOR_LABELS = listOf("top-left", "top-right", "bottom-left", "bottom-right")

@Composable
fun AnchorScreen(
    photoPath: String,
    onPlaced: (List<Pair<Float, Float>>) -> Unit,
    onBack: () -> Unit,
) {
    val bitmap = remember(photoPath) { BitmapFactory.decodeFile(photoPath) }

    if (bitmap == null) {
        Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
            Text("That photo could not be opened. Take another.")
            TextButton(onClick = onBack) { Text("Back") }
        }
        return
    }

    val imageWidth = bitmap.width.toFloat()
    val imageHeight = bitmap.height.toFloat()

    // Anchors live in image pixels, so they survive any resize of the view.
    var anchors by remember(photoPath) {
        mutableStateOf(
            listOf(
                0.25f * imageWidth to 0.25f * imageHeight,
                0.75f * imageWidth to 0.25f * imageHeight,
                0.25f * imageWidth to 0.75f * imageHeight,
                0.75f * imageWidth to 0.75f * imageHeight,
            ),
        )
    }
    var dragging by remember { mutableStateOf(-1) }
    var canvasSize by remember { mutableStateOf(Size.Zero) }

    fun scale(): Float =
        if (canvasSize == Size.Zero) 1f
        else min(canvasSize.width / imageWidth, canvasSize.height / imageHeight)

    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { canvasSize = Size(it.width.toFloat(), it.height.toFloat()) }
                    .pointerInput(photoPath) {
                        detectDragGestures(
                            onDragStart = { touch ->
                                val s = scale()
                                dragging = anchors.indices.minByOrNull { index ->
                                    val (ax, ay) = anchors[index]
                                    val dx = ax * s - touch.x
                                    val dy = ay * s - touch.y
                                    dx * dx + dy * dy
                                } ?: -1
                            },
                            onDragEnd = { dragging = -1 },
                            onDrag = { change, amount ->
                                change.consume()
                                if (dragging >= 0) {
                                    val s = scale()
                                    anchors = anchors.toMutableList().also { list ->
                                        val (ax, ay) = list[dragging]
                                        list[dragging] =
                                            (ax + amount.x / s).coerceIn(0f, imageWidth) to
                                                (ay + amount.y / s).coerceIn(0f, imageHeight)
                                    }
                                }
                            },
                        )
                    },
            ) {
                val s = min(size.width / imageWidth, size.height / imageHeight)
                drawImage(
                    image = bitmap.asImageBitmap(),
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(bitmap.width, bitmap.height),
                    dstOffset = IntOffset.Zero,
                    dstSize = IntSize((imageWidth * s).toInt(), (imageHeight * s).toInt()),
                )
                anchors.forEach { (ax, ay) ->
                    val centre = Offset(ax * s, ay * s)
                    drawCircle(Color.White, radius = 26f, center = centre, style = Stroke(width = 6f))
                    drawCircle(Color.Black, radius = 20f, center = centre, style = Stroke(width = 3f))
                }
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Drag each handle onto the centre of a corner hex:")
            WarpAnchors.HEX_IDS.forEachIndexed { index, id ->
                Text("• ${ANCHOR_LABELS[index]} handle → hex $id")
            }
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = { onPlaced(anchors) },
            ) {
                Text("Looks right")
            }
            TextButton(onClick = onBack) { Text("Retake photo") }
        }
    }
}
```

- [ ] **Step 2: Wire it into the router**

Replace `Step.ANCHORS -> Text("Anchors — Task 8")` with:

```kotlin
                        Step.ANCHORS -> AnchorScreen(
                            photoPath = state.photoPath.orEmpty(),
                            onPlaced = { state = state.withAnchors(it) },
                            onBack = { state = state.back() },
                        )
```

- [ ] **Step 3: Build and verify**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

On a device: the captured photo appears, four handles are draggable independently, a handle dragged to a corner stays where it was put, and "Looks right" advances to "Review — Task 9". Report what you could and could not verify.

- [ ] **Step 4: Commit**

```bash
git add app
git commit -m "feat(app): add anchor placement screen"
```

---

### Task 9: Warp, crop, and the review grid

**Files:**
- Create: `app/src/main/kotlin/tm/app/BoardWarper.kt`
- Create: `app/src/main/kotlin/tm/app/ReviewScreen.kt`
- Modify: `app/src/main/kotlin/tm/app/MainActivity.kt`

**Interfaces:**
- Consumes: `tm.vision.CanonicalImage`, `WarpAnchors.destinationPoints`, `CropPlanner.plan`, `CubeColorReader.read`, `AppState.withTile`, `AppState.toScore`.
- Produces: `tm.app.BoardWarper.warp(photoPath: String, anchors: List<Pair<Float, Float>>, image: CanonicalImage): Bitmap?`; `tm.app.HexRead(hexId: String, suggestedOwner: PlayerColor?)`; `tm.app.BoardWarper.readCubes(warped: Bitmap, board: Board, image: CanonicalImage): List<HexRead>`; `@Composable fun ReviewScreen(...)`.

This is where the pure geometry meets the platform. `Matrix.setPolyToPoly` maps the four anchors the user placed onto the four destination points `:vision` computed, and `Canvas.drawBitmap` applies it. Tile types all start `EMPTY` — Plan 3 replaces that with the model — but cube colours are read now, because `CubeColorReader` already exists and costs nothing.

- [ ] **Step 1: Write the warper**

`app/src/main/kotlin/tm/app/BoardWarper.kt`:

```kotlin
package tm.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
        val source = BitmapFactory.decodeFile(photoPath) ?: return null

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
```

`setPolyToPoly` returning false is a real case — three anchors dragged onto the same spot produce a degenerate mapping — and it must surface as "try again", never as a crash or a blank grid presented as fact.

- [ ] **Step 2: Write the shared player-colour swatches**

Both this screen and the score screen paint a player's colour, so the mapping
lives in one place rather than being written twice.

`app/src/main/kotlin/tm/app/PlayerColors.kt`:

```kotlin
package tm.app

import androidx.compose.ui.graphics.Color
import tm.scoring.PlayerColor

/** The on-screen colour for a player, or grey when the owner is unknown. */
fun swatch(colour: PlayerColor?): Color = when (colour) {
    PlayerColor.BLUE -> Color(0xFF2E5BDA)
    PlayerColor.RED -> Color(0xFFCC2E2E)
    PlayerColor.GREEN -> Color(0xFF2E9E4F)
    PlayerColor.YELLOW -> Color(0xFFE2C31F)
    PlayerColor.BLACK -> Color(0xFF1E1E1E)
    null -> Color(0xFF9E9E9E)
}
```

- [ ] **Step 3: Write the review screen**

`app/src/main/kotlin/tm/app/ReviewScreen.kt`:

```kotlin
package tm.app

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.dp
import tm.scoring.Board
import tm.scoring.Grid
import tm.scoring.PlayerColor
import tm.scoring.Tile
import tm.scoring.TileType

@Composable
fun ReviewScreen(
    board: Board,
    grid: Grid,
    suggestions: Map<String, PlayerColor?>,
    warped: ImageBitmap?,
    onTileChanged: (String, Tile) -> Unit,
    onDone: () -> Unit,
    onBack: () -> Unit,
) {
    var editing by remember { mutableStateOf<String?>(null) }

    val placed = board.hexes.keys.sorted().filter { it in grid }

    if (warped == null) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("The photo could not be flattened.", style = MaterialTheme.typography.headlineSmall)
            Text(
                "That usually means two anchors landed on the same spot, or the photo is too " +
                    "steeply angled. Go back and place them on the four corner hexes again.",
            )
            Button(modifier = Modifier.fillMaxWidth(), onClick = onBack) { Text("Place anchors again") }
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // The spec requires the flattened board be shown before anything is read
        // off it, so a bad warp is caught by eye rather than by a wrong score.
        Image(
            bitmap = warped,
            contentDescription = "The board, flattened",
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        )
        Text(
            "Does that look like the board, square-on? If not, go back and move the anchors.",
            style = MaterialTheme.typography.bodySmall,
        )
        Text("What is on the board?", style = MaterialTheme.typography.headlineSmall)
        Text(
            "${placed.size} of 61 hexes have a tile. Tap a hex to set what is on it.",
            style = MaterialTheme.typography.bodyMedium,
        )

        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
            items(board.hexes.keys.sorted()) { hexId ->
                val tile = grid[hexId]
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { editing = hexId }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .background(swatch(tile?.owner ?: suggestions[hexId])),
                    )
                    Text(hexId, modifier = Modifier.weight(1f))
                    Text((tile?.type ?: TileType.EMPTY).name.lowercase())
                }
            }
        }

        Button(modifier = Modifier.fillMaxWidth(), onClick = onDone) { Text("Show map points") }
        TextButton(onClick = onBack) { Text("Back") }
    }

    editing?.let { hexId ->
        TileEditor(
            hexId = hexId,
            current = grid[hexId],
            suggestedOwner = suggestions[hexId],
            oceanReserved = hexId in board.oceanReserved,
            onPick = { tile ->
                onTileChanged(hexId, tile)
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun TileEditor(
    hexId: String,
    current: Tile?,
    suggestedOwner: PlayerColor?,
    oceanReserved: Boolean,
    onPick: (Tile) -> Unit,
    onDismiss: () -> Unit,
) {
    var type by remember(hexId) { mutableStateOf(current?.type ?: TileType.EMPTY) }
    var owner by remember(hexId) { mutableStateOf(current?.owner ?: suggestedOwner) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(hexId) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (oceanReserved && type != TileType.OCEAN && type != TileType.EMPTY) {
                    Text(
                        "This hex is printed as an ocean space.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Text("Tile")
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (option in TileType.entries) {
                        TextButton(onClick = { type = option }) {
                            Text(
                                option.name.lowercase(),
                                color = if (option == type) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Text("Owner")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (option in PlayerColor.entries) {
                        Box(
                            modifier = Modifier
                                .size(if (option == owner) 34.dp else 26.dp)
                                .background(swatch(option))
                                .clickable { owner = option },
                        )
                    }
                    TextButton(onClick = { owner = null }) { Text("none") }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val ownerForType = if (type == TileType.EMPTY || type == TileType.OCEAN) null else owner
                onPick(Tile(type, ownerForType))
            }) { Text("Set") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
```

The ocean-reserved warning is written now and fires never, because those sets are still empty — it starts working the day the transcription lands, with no code change.

- [ ] **Step 4: Wire it into the router**

Replace `Step.REVIEW -> Text("Review — Task 9")`. The warp runs once per anchor placement, not on every recomposition, so it belongs in a `remember` keyed on the photo and anchors. The warped bitmap is kept rather than recycled, because the screen now shows it:

```kotlin
                        Step.REVIEW -> {
                            val board = state.board!!
                            val image = remember { CanonicalImage(hexCropHeightPx = 64) }
                            val warped = remember(state.photoPath, state.anchors) {
                                state.photoPath?.let { BoardWarper.warp(it, state.anchors, image) }
                            }
                            val suggestions = remember(warped) {
                                warped?.let {
                                    BoardWarper.readCubes(it, board, image)
                                        .associate { read -> read.hexId to read.suggestedOwner }
                                } ?: emptyMap()
                            }
                            ReviewScreen(
                                board = board,
                                grid = state.grid,
                                suggestions = suggestions,
                                warped = warped?.asImageBitmap(),
                                onTileChanged = { hexId, tile -> state = state.withTile(hexId, tile) },
                                onDone = { state = state.toScore() },
                                onBack = { state = state.back() },
                            )
                        }
```

Add the imports `tm.vision.CanonicalImage`, `androidx.compose.runtime.remember`, and `androidx.compose.ui.graphics.asImageBitmap` if not already present.

- [ ] **Step 5: Build and verify**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

On a device: the flattened board appears above the list; all 61 hex ids are listed; tapping one opens the editor; setting a tile type and owner updates the row; the count of placed hexes rises; "Show map points" advances to "Score — Task 10". Drag three anchors onto the same spot deliberately and confirm you get the "could not be flattened" screen rather than a crash or an empty grid. Report what you verified.

- [ ] **Step 6: Commit**

```bash
git add app
git commit -m "feat(app): warp the photo and add the review grid"
```

---

### Task 10: Score screen

**Files:**
- Create: `app/src/main/kotlin/tm/app/ScoreScreen.kt`
- Modify: `app/src/main/kotlin/tm/app/MainActivity.kt`

**Interfaces:**
- Consumes: `tm.scoring.Scorer.scoreAll`, `AppState.players`.
- Produces: `@Composable fun ScoreScreen(board: Board, grid: Grid, players: Set<PlayerColor>, onBack: () -> Unit, onRestart: () -> Unit)`.

- [ ] **Step 1: Write the screen**

`app/src/main/kotlin/tm/app/ScoreScreen.kt`:

```kotlin
package tm.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import tm.scoring.Board
import tm.scoring.Grid
import tm.scoring.PlayerColor
import tm.scoring.Scorer

@Composable
fun ScoreScreen(
    board: Board,
    grid: Grid,
    players: Set<PlayerColor>,
    onBack: () -> Unit,
    onRestart: () -> Unit,
) {
    val scores = Scorer.scoreAll(board, grid, players)
    val ranked = scores.entries.sortedByDescending { it.value.total }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Map points", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Tiles on the board only — not terraform rating, milestones, awards, or card points.",
            style = MaterialTheme.typography.bodySmall,
        )

        if (ranked.isEmpty()) {
            Text("No tiles have an owner yet. Go back and set some.")
        }

        for ((player, breakdown) in ranked) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(modifier = Modifier.size(20.dp).background(swatch(player)))
                Column(modifier = Modifier.weight(1f)) {
                    Text(player.name.lowercase().replaceFirstChar { it.uppercase() })
                    Text(
                        "${breakdown.greeneries} from greeneries, ${breakdown.cityPoints} from cities",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Text(
                    breakdown.total.toString(),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        Button(modifier = Modifier.fillMaxWidth(), onClick = onBack) { Text("Back to the board") }
        TextButton(onClick = onRestart) { Text("Score another board") }
    }
}
```

`swatch` comes from `PlayerColors.kt`, written in Task 9 — import it, do not
write a second copy of the colour mapping here.

The subtitle is not decoration. A number labelled "points" next to a Terraforming Mars board will be read as the game score unless the screen says plainly that it is not.

- [ ] **Step 2: Wire it into the router**

Replace `Step.SCORE -> Text("Score — Task 10")` with:

```kotlin
                        Step.SCORE -> ScoreScreen(
                            board = state.board!!,
                            grid = state.grid,
                            players = state.players,
                            onBack = { state = state.back() },
                            onRestart = { state = AppState() },
                        )
```

- [ ] **Step 3: Build and verify end to end**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL — `:scoring` 46 tests, `:vision` 30 tests, `:app` 12 tests, one ignored test in `:scoring`.

On a device, walk the whole flow once: pick Tharsis, take a photo, place the anchors, set a greenery and an adjacent city for two different players, and check the arithmetic by hand against the rules. Report the numbers you saw and the numbers you expected.

- [ ] **Step 4: Commit**

```bash
git add app
git commit -m "feat(app): add score screen"
```

---

### Task 11: Update the spec

**Files:**
- Modify: `docs/superpowers/specs/2026-09-01-tm-point-counter-design.md`

- [ ] **Step 1: Record the three deviations**

Read this plan's "Deviations from the spec" section and fold each into the spec so it describes what exists:

1. The Pipeline's warp step names OpenCV. Replace with `android.graphics.Matrix.setPolyToPoly` and `Canvas.drawBitmap`, and say why: the platform provides the same 4-point perspective mapping, so the dependency buys nothing.
2. The Pipeline's corner step says the user drags handles onto the corners of the printed map area. Replace with the four corner-hex centres — `r1c1`, `r1c5`, `r9c1`, `r9c5` — and note that those four centres form a rectangle in board space, which is what makes them a sound basis for the transform.
3. Add the canonical image to the Board definition format section: it spans half a hex beyond the centre bounding box in every direction so no crop is clipped, and its size derives from the chosen hex crop height.

- [ ] **Step 2: Update the module list**

`:vision` as built is pure Kotlin and holds only geometry and colour reading; the bitmap work (`BoardWarper`) lives in `:app` because it needs `android.graphics`. The spec currently describes `:vision` as doing the warping. Correct it.

- [ ] **Step 3: Note what Plan 3 replaces**

Add a short note saying that tile types are currently entered by hand and that the model's arrival replaces exactly one thing: the `EMPTY` default in the review grid becomes a classification with a confidence, and low-confidence hexes get highlighted. Everything else in the flow stays as it is.

- [ ] **Step 4: Commit**

```bash
git add docs
git commit -m "docs: align spec with the Android app as built"
```

---

## What this plan does not build

- Any model, training script, or classification. Tile types are entered by hand. That is Plan 3.
- Automatic corner detection. The user places four anchors; the spec keeps this as an open question.
- Automatic board identification — the user picks the board.
- Saving or resuming a session, exporting a score, or scoring history. Nothing in the spec asks for them.
- The ocean-reserved data itself, which still awaits transcription from three physical boards.
