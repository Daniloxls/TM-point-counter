# Scoring Core Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a pure-JVM Kotlin module that models the Terraforming Mars map as a hex graph and computes each player's map victory points from a confirmed grid of tiles.

**Architecture:** All three supported boards share one geometry: a hexagon of hexagons with radius 4, giving rows of 5-6-7-8-9-8-7-6-5 for 61 hexes. That geometry is computed from axial coordinates rather than stored as data, so hex ids, neighbour lists, and normalised centre coordinates all come from one small generator with tests. Per-board data reduces to the set of ocean-reserved hex ids. Scoring reads a `Map<hexId, Tile>` and returns a per-player breakdown.

**Tech Stack:** Kotlin 2.1.0 (JVM), JDK 17, Gradle 8.14, JUnit 5, kotlin-test. No Android dependencies, no serialization library, no third-party dependencies at all.

**Spec:** `docs/superpowers/specs/2026-09-01-tm-point-counter-design.md`

## Deviations from the spec

Two, both simplifications, both intentional:

1. The spec describes a `:boards` module holding JSON with per-hex coordinates and precomputed neighbour lists. The three boards share identical geometry, so this plan computes coordinates and neighbours from axial hex math instead. Hand-maintained JSON for 183 hexes would be 183 chances to typo data that a 30-line generator produces provably. The tests in Tasks 1-3 assert the properties the spec wanted the data to guarantee.
2. Per-board ocean-reserved hex ids live in a Kotlin source file rather than JSON, so the module needs no JSON parser and stays dependency-free.

Update the spec's "Board definition format" section to match once this plan is executed.

## Global Constraints

- Kotlin 2.1.0, JVM target 17.
- `:scoring` has zero Android dependencies and zero third-party runtime dependencies. Test dependencies are JUnit 5 and kotlin-test only.
- Every board has exactly 61 hexes.
- Every board has exactly 12 ocean-reserved hexes.
- Supported boards: `tharsis`, `hellas`, `elysium`.
- Player colours: `BLUE`, `RED`, `GREEN`, `YELLOW`, `BLACK`. No neutral colour — neutral tiles are out of scope per the spec.
- Hex ids have the form `r<row>c<col>`, rows 1-9 top to bottom, columns 1-N left to right within a row.
- Scoring rules, and only these: a greenery scores 1 VP for its owner; a city scores 1 VP for its owner per adjacent greenery, regardless of who owns that greenery; oceans, special tiles, and empty hexes score nothing.

---

### Task 1: Gradle skeleton and the hex coordinate set

**Files:**
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts`
- Create: `scoring/build.gradle.kts`
- Create: `scoring/src/main/kotlin/tm/scoring/HexGrid.kt`
- Create: `.gitignore`
- Test: `scoring/src/test/kotlin/tm/scoring/HexGridTest.kt`

**Interfaces:**
- Consumes: nothing.
- Produces: `tm.scoring.HexGrid.RADIUS: Int`, `tm.scoring.HexGrid.coords(): List<Pair<Int, Int>>` returning axial `(q, r)` pairs.

- [ ] **Step 1: Create the Gradle skeleton**

`settings.gradle.kts`:

```kotlin
rootProject.name = "tm-point-counter"
include(":scoring")
```

`build.gradle.kts`:

```kotlin
plugins {
    kotlin("jvm") version "2.1.0" apply false
}
```

`scoring/build.gradle.kts`:

```kotlin
plugins {
    kotlin("jvm") version "2.1.0"
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(17)
}

tasks.test {
    useJUnitPlatform()
}
```

`.gitignore`:

```
.gradle/
build/
local.properties
*.iml
.idea/
```

- [ ] **Step 2: Generate the Gradle wrapper**

Run: `gradle wrapper --gradle-version 8.14`

Expected: creates `gradlew`, `gradlew.bat`, and `gradle/wrapper/`.

If `gradle` is not on PATH, open the project folder in Android Studio or IntelliJ once and let it generate the wrapper, or install Gradle 8.14 first. Do not hand-write the wrapper jar.

- [ ] **Step 3: Write the failing test**

`scoring/src/test/kotlin/tm/scoring/HexGridTest.kt`:

```kotlin
package tm.scoring

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HexGridTest {

    @Test
    fun `board has exactly 61 hexes`() {
        assertEquals(61, HexGrid.coords().size)
    }

    @Test
    fun `all coordinates are inside the radius 4 hexagon`() {
        for ((q, r) in HexGrid.coords()) {
            assertTrue(abs(q) <= 4 && abs(r) <= 4 && abs(q + r) <= 4, "($q, $r) is outside the board")
        }
    }

    @Test
    fun `coordinates are unique`() {
        val coords = HexGrid.coords()
        assertEquals(coords.size, coords.toSet().size)
    }

    @Test
    fun `rows run 5 6 7 8 9 8 7 6 5`() {
        val perRow = HexGrid.coords().groupBy { (_, r) -> r }.toSortedMap().map { it.value.size }
        assertEquals(listOf(5, 6, 7, 8, 9, 8, 7, 6, 5), perRow)
    }
}
```

- [ ] **Step 4: Run the test to verify it fails**

Run: `./gradlew :scoring:test`
Expected: FAIL — `Unresolved reference: HexGrid`.

- [ ] **Step 5: Write the minimal implementation**

`scoring/src/main/kotlin/tm/scoring/HexGrid.kt`:

```kotlin
package tm.scoring

import kotlin.math.abs

/**
 * Geometry shared by every Terraforming Mars map board: a hexagon of hexes with
 * radius 4, laid out in rows of 5-6-7-8-9-8-7-6-5 for 61 hexes total.
 *
 * Coordinates are axial: q increases to the right along a row, r increases
 * downward from row to row.
 */
object HexGrid {

    const val RADIUS = 4

    fun coords(): List<Pair<Int, Int>> =
        (-RADIUS..RADIUS).flatMap { r ->
            (-RADIUS..RADIUS).filter { q -> abs(q + r) <= RADIUS }.map { q -> q to r }
        }
}
```

- [ ] **Step 6: Run the test to verify it passes**

Run: `./gradlew :scoring:test`
Expected: PASS, 4 tests.

- [ ] **Step 7: Commit**

```bash
git add settings.gradle.kts build.gradle.kts .gitignore gradlew gradlew.bat gradle scoring
git commit -m "feat(scoring): add Gradle skeleton and hex coordinate generator"
```

---

### Task 2: Hex ids and neighbour lists

**Files:**
- Modify: `scoring/src/main/kotlin/tm/scoring/HexGrid.kt`
- Test: `scoring/src/test/kotlin/tm/scoring/HexGridTest.kt`

**Interfaces:**
- Consumes: `HexGrid.RADIUS`, `HexGrid.coords()`.
- Produces: `HexGrid.idOf(q: Int, r: Int): String`, `HexGrid.neighborsOf(q: Int, r: Int): List<String>`.

- [ ] **Step 1: Write the failing tests**

Append to `scoring/src/test/kotlin/tm/scoring/HexGridTest.kt`, inside the class:

```kotlin
    @Test
    fun `ids are row and column positions`() {
        assertEquals("r1c1", HexGrid.idOf(0, -4))
        assertEquals("r1c5", HexGrid.idOf(4, -4))
        assertEquals("r5c1", HexGrid.idOf(-4, 0))
        assertEquals("r5c9", HexGrid.idOf(4, 0))
        assertEquals("r9c1", HexGrid.idOf(-4, 4))
        assertEquals("r9c5", HexGrid.idOf(0, 4))
    }

    @Test
    fun `ids are unique across the board`() {
        val ids = HexGrid.coords().map { (q, r) -> HexGrid.idOf(q, r) }
        assertEquals(61, ids.toSet().size)
    }

    @Test
    fun `centre hex has six neighbours`() {
        assertEquals(6, HexGrid.neighborsOf(0, 0).size)
    }

    @Test
    fun `corner hex has three neighbours`() {
        assertEquals(3, HexGrid.neighborsOf(0, -4).size)
    }

    @Test
    fun `no hex is its own neighbour`() {
        for ((q, r) in HexGrid.coords()) {
            val id = HexGrid.idOf(q, r)
            assertTrue(id !in HexGrid.neighborsOf(q, r), "$id lists itself as a neighbour")
        }
    }

    @Test
    fun `neighbour relations are symmetric`() {
        val byId = HexGrid.coords().associateBy { (q, r) -> HexGrid.idOf(q, r) }
        for ((q, r) in HexGrid.coords()) {
            val id = HexGrid.idOf(q, r)
            for (neighborId in HexGrid.neighborsOf(q, r)) {
                val (nq, nr) = byId.getValue(neighborId)
                assertTrue(id in HexGrid.neighborsOf(nq, nr), "$neighborId does not list $id back")
            }
        }
    }

    @Test
    fun `board has 156 distinct adjacent pairs`() {
        val pairs = HexGrid.coords().flatMap { (q, r) ->
            HexGrid.neighborsOf(q, r).map { setOf(HexGrid.idOf(q, r), it) }
        }.toSet()
        assertEquals(156, pairs.size)
    }
```

156 is the edge count of a radius-4 hexagonal grid — 37 interior hexes with six
neighbours, 18 border hexes with four, and 6 corner hexes with three, halved. It catches a
generator that quietly drops or duplicates adjacencies at the border, which the
symmetry test alone would not.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :scoring:test`
Expected: FAIL — `Unresolved reference: idOf`.

- [ ] **Step 3: Write the minimal implementation**

Add to `HexGrid.kt`, inside the object:

```kotlin
    private val DIRECTIONS = listOf(1 to 0, 1 to -1, 0 to -1, -1 to 0, -1 to 1, 0 to 1)

    fun isOnBoard(q: Int, r: Int): Boolean =
        abs(q) <= RADIUS && abs(r) <= RADIUS && abs(q + r) <= RADIUS

    /** Leftmost q on row r. */
    private fun rowStart(r: Int): Int = maxOf(-RADIUS, -RADIUS - r)

    fun idOf(q: Int, r: Int): String {
        require(isOnBoard(q, r)) { "($q, $r) is not on the board" }
        val row = r + RADIUS + 1
        val col = q - rowStart(r) + 1
        return "r${row}c$col"
    }

    fun neighborsOf(q: Int, r: Int): List<String> =
        DIRECTIONS
            .map { (dq, dr) -> q + dq to r + dr }
            .filter { (nq, nr) -> isOnBoard(nq, nr) }
            .map { (nq, nr) -> idOf(nq, nr) }
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :scoring:test`
Expected: PASS, 11 tests.

- [ ] **Step 5: Commit**

```bash
git add scoring
git commit -m "feat(scoring): add hex ids and neighbour lists"
```

---

### Task 3: Normalised centre coordinates

**Files:**
- Modify: `scoring/src/main/kotlin/tm/scoring/HexGrid.kt`
- Create: `scoring/src/main/kotlin/tm/scoring/Hex.kt`
- Test: `scoring/src/test/kotlin/tm/scoring/HexGeometryTest.kt`

**Interfaces:**
- Consumes: `HexGrid.coords()`, `HexGrid.idOf()`, `HexGrid.neighborsOf()`.
- Produces: `tm.scoring.Hex(id: String, q: Int, r: Int, x: Double, y: Double, neighbors: List<String>)`, `HexGrid.hexes(): Map<String, Hex>`, `HexGrid.ASPECT_RATIO: Double`.

`x` and `y` are the hex centre in the warped canonical board image, normalised so
the leftmost centre is at `x = 0.0`, the rightmost at `x = 1.0`, the topmost at
`y = 0.0`, and the bottommost at `y = 1.0`. The vision module scales these onto
its warped image, which is why `ASPECT_RATIO` is published alongside: normalising
each axis independently discards the shape, so the consumer needs the width-to-height
ratio of the centre bounding box to place crops without distortion.

- [ ] **Step 1: Write the failing test**

`scoring/src/test/kotlin/tm/scoring/HexGeometryTest.kt`:

```kotlin
package tm.scoring

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HexGeometryTest {

    private val hexes = HexGrid.hexes()

    @Test
    fun `every hex is present and keyed by its own id`() {
        assertEquals(61, hexes.size)
        for ((key, hex) in hexes) assertEquals(key, hex.id)
    }

    @Test
    fun `centres are inside the unit square`() {
        for (hex in hexes.values) {
            assertTrue(hex.x in 0.0..1.0, "${hex.id} has x=${hex.x}")
            assertTrue(hex.y in 0.0..1.0, "${hex.id} has y=${hex.y}")
        }
    }

    @Test
    fun `both axes are normalised to the full range`() {
        assertEquals(0.0, hexes.values.minOf { it.x }, 1e-9)
        assertEquals(1.0, hexes.values.maxOf { it.x }, 1e-9)
        assertEquals(0.0, hexes.values.minOf { it.y }, 1e-9)
        assertEquals(1.0, hexes.values.maxOf { it.y }, 1e-9)
    }

    @Test
    fun `rows are ordered top to bottom and columns left to right`() {
        val topLeft = hexes.getValue("r1c1")
        val bottomLeft = hexes.getValue("r9c1")
        val middleLeft = hexes.getValue("r5c1")
        val middleRight = hexes.getValue("r5c9")
        assertTrue(topLeft.y < middleLeft.y, "row 1 should sit above row 5")
        assertTrue(middleLeft.y < bottomLeft.y, "row 5 should sit above row 9")
        assertTrue(middleLeft.x < middleRight.x, "column 1 should sit left of column 9")
    }

    @Test
    fun `the board is wider than it is tall`() {
        assertTrue(HexGrid.ASPECT_RATIO > 1.0, "aspect ratio was ${HexGrid.ASPECT_RATIO}")
    }

    @Test
    fun `neighbouring centres are all the same distance apart`() {
        val spacings = hexes.values.flatMap { hex ->
            hex.neighbors.map { neighborId ->
                val other = hexes.getValue(neighborId)
                val dx = (hex.x - other.x) * HexGrid.ASPECT_RATIO
                val dy = hex.y - other.y
                kotlin.math.sqrt(dx * dx + dy * dy)
            }
        }
        val first = spacings.first()
        for (spacing in spacings) {
            assertTrue(abs(spacing - first) < 1e-6, "spacing $spacing differs from $first")
        }
    }
}
```

The last test is the one that catches an aspect-ratio mistake: on a real hex
grid every adjacent pair sits exactly the same distance apart, and that is only
true once `x` has been rescaled by the aspect ratio.

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :scoring:test --tests '*HexGeometryTest*'`
Expected: FAIL — `Unresolved reference: hexes`.

- [ ] **Step 3: Write the minimal implementation**

`scoring/src/main/kotlin/tm/scoring/Hex.kt`:

```kotlin
package tm.scoring

/**
 * One space on the map.
 *
 * [x] and [y] are the centre of the hex in the warped canonical board image,
 * each axis normalised to 0.0..1.0. Rescale [x] by [HexGrid.ASPECT_RATIO]
 * before doing any distance maths with these values.
 */
data class Hex(
    val id: String,
    val q: Int,
    val r: Int,
    val x: Double,
    val y: Double,
    val neighbors: List<String>,
)
```

Add to `HexGrid.kt`, inside the object:

```kotlin
    /** Width divided by height of the bounding box of all hex centres. */
    val ASPECT_RATIO: Double = run {
        val points = coords().map { (q, r) -> pixel(q, r) }
        val width = points.maxOf { it.first } - points.minOf { it.first }
        val height = points.maxOf { it.second } - points.minOf { it.second }
        width / height
    }

    private val hexCache: Map<String, Hex> = buildHexes()

    fun hexes(): Map<String, Hex> = hexCache

    /** Pointy-top hex layout in arbitrary units; only ratios matter. */
    private fun pixel(q: Int, r: Int): Pair<Double, Double> =
        Pair(kotlin.math.sqrt(3.0) * (q + r / 2.0), 1.5 * r)

    private fun buildHexes(): Map<String, Hex> {
        val all = coords()
        val points = all.associateWith { (q, r) -> pixel(q, r) }
        val minX = points.values.minOf { it.first }
        val maxX = points.values.maxOf { it.first }
        val minY = points.values.minOf { it.second }
        val maxY = points.values.maxOf { it.second }
        return all.associate { coord ->
            val (q, r) = coord
            val (px, py) = points.getValue(coord)
            val id = idOf(q, r)
            id to Hex(
                id = id,
                q = q,
                r = r,
                x = (px - minX) / (maxX - minX),
                y = (py - minY) / (maxY - minY),
                neighbors = neighborsOf(q, r),
            )
        }
    }
```

Note on initialisation order: `ASPECT_RATIO` and `hexCache` are both object
properties initialised top to bottom, and both call `pixel`, which is a function
rather than a property, so ordering is safe. Keep `DIRECTIONS` declared above
them.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :scoring:test`
Expected: PASS, 17 tests.

- [ ] **Step 5: Commit**

```bash
git add scoring
git commit -m "feat(scoring): add normalised hex centre coordinates"
```

---

### Task 4: Boards and ocean-reserved hexes

**Files:**
- Create: `scoring/src/main/kotlin/tm/scoring/Board.kt`
- Test: `scoring/src/test/kotlin/tm/scoring/BoardTest.kt`

**Interfaces:**
- Consumes: `HexGrid.hexes()`.
- Produces: `tm.scoring.Board(id: String, oceanReserved: Set<String>)` with `hexes: Map<String, Hex>`, `neighbors(hexId: String): List<String>`, and `Board.Companion.THARSIS`, `HELLAS`, `ELYSIUM`, `ALL: List<Board>`, `byId(id: String): Board`.

This task contains the only hand-entered data in the module. Read it before starting.

- [ ] **Step 1: Write the failing test**

`scoring/src/test/kotlin/tm/scoring/BoardTest.kt`:

```kotlin
package tm.scoring

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class BoardTest {

    @Test
    fun `all three boards are available`() {
        assertEquals(listOf("tharsis", "hellas", "elysium"), Board.ALL.map { it.id })
    }

    @Test
    fun `every board has 61 hexes`() {
        for (board in Board.ALL) assertEquals(61, board.hexes.size, "${board.id} has the wrong hex count")
    }

    @Test
    fun `every board has exactly 12 ocean-reserved hexes`() {
        for (board in Board.ALL) {
            assertEquals(12, board.oceanReserved.size, "${board.id} has the wrong ocean count")
        }
    }

    @Test
    fun `every ocean-reserved id is a real hex on the board`() {
        for (board in Board.ALL) {
            for (id in board.oceanReserved) {
                assertTrue(id in board.hexes, "${board.id} reserves unknown hex $id")
            }
        }
    }

    @Test
    fun `boards can be looked up by id`() {
        assertEquals("hellas", Board.byId("hellas").id)
    }

    @Test
    fun `unknown board ids are rejected`() {
        assertFailsWith<IllegalArgumentException> { Board.byId("mars") }
    }

    @Test
    fun `neighbours come from the shared geometry`() {
        assertEquals(6, Board.THARSIS.neighbors("r5c5").size)
        assertEquals(3, Board.THARSIS.neighbors("r1c1").size)
    }

    @Test
    fun `neighbours of an unknown hex are rejected`() {
        assertFailsWith<IllegalArgumentException> { Board.THARSIS.neighbors("r99c99") }
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :scoring:test --tests '*BoardTest*'`
Expected: FAIL — `Unresolved reference: Board`.

- [ ] **Step 3: Write the implementation with empty ocean sets**

`scoring/src/main/kotlin/tm/scoring/Board.kt`:

```kotlin
package tm.scoring

/**
 * A map board. All supported boards share the same 61-hex geometry; they differ
 * only in which hexes are printed as ocean spaces.
 */
class Board(
    val id: String,
    val oceanReserved: Set<String>,
) {
    val hexes: Map<String, Hex> = HexGrid.hexes()

    init {
        val unknown = oceanReserved - hexes.keys
        require(unknown.isEmpty()) { "$id reserves unknown hexes: $unknown" }
    }

    fun neighbors(hexId: String): List<String> =
        (hexes[hexId] ?: throw IllegalArgumentException("$id has no hex $hexId")).neighbors

    companion object {
        val THARSIS = Board("tharsis", emptySet())
        val HELLAS = Board("hellas", emptySet())
        val ELYSIUM = Board("elysium", emptySet())

        val ALL = listOf(THARSIS, HELLAS, ELYSIUM)

        fun byId(id: String): Board =
            ALL.firstOrNull { it.id == id } ?: throw IllegalArgumentException("unknown board: $id")
    }
}
```

- [ ] **Step 4: Transcribe the ocean-reserved hexes**

This is manual data entry from the physical component. Do it once, carefully.

For each board in turn, lay the printed map in front of you the way the rules
picture it, with the rows running left to right and the 9-hex row across the
middle. Number the rows 1 at the top through 9 at the bottom, and within each
row number the columns 1 from the left. Every space printed with the blue ocean
symbol gets an id of `r<row>c<col>`.

Each board has exactly 12 such spaces; the test in Step 1 fails if you find a
different number, which is the point — a miscount is caught immediately rather
than becoming a wrong score months later.

Replace the three `emptySet()` calls with the transcribed sets, formatted one
row per line so a later reader can check them against the board:

```kotlin
        val THARSIS = Board(
            "tharsis",
            setOf(
                // row 1
                "r1c3",
                // ... one line per row that contains ocean spaces
            ),
        )
```

Do not guess. If a board is not to hand, leave that board's set empty, let its
test fail, and finish the transcription before moving to Task 5 — this data is
what every score depends on.

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew :scoring:test`
Expected: PASS, 25 tests.

- [ ] **Step 6: Commit**

```bash
git add scoring
git commit -m "feat(scoring): add boards and ocean-reserved hexes"
```

---

### Task 5: Tile model and greenery scoring

**Files:**
- Create: `scoring/src/main/kotlin/tm/scoring/Tile.kt`
- Create: `scoring/src/main/kotlin/tm/scoring/Scorer.kt`
- Test: `scoring/src/test/kotlin/tm/scoring/ScorerTest.kt`

**Interfaces:**
- Consumes: `Board`.
- Produces: `tm.scoring.TileType` (`EMPTY`, `OCEAN`, `GREENERY`, `CITY`, `SPECIAL`), `tm.scoring.PlayerColor` (`BLUE`, `RED`, `GREEN`, `YELLOW`, `BLACK`), `tm.scoring.Tile(type: TileType, owner: PlayerColor?)`, `tm.scoring.Grid = Map<String, Tile>`, `tm.scoring.ScoreBreakdown(greeneries: Int, cityPoints: Int)` with `total: Int`, `tm.scoring.Scorer.score(board: Board, grid: Grid): Map<PlayerColor, ScoreBreakdown>`.

`owner` is null when the tile's owner is unknown or absent — an ocean, an empty
hex, or a tile whose cube the vision module could not read. Unowned tiles score
for nobody.

- [ ] **Step 1: Write the failing tests**

`scoring/src/test/kotlin/tm/scoring/ScorerTest.kt`:

```kotlin
package tm.scoring

import kotlin.test.Test
import kotlin.test.assertEquals

class ScorerTest {

    private val board = Board.THARSIS

    private fun grid(vararg entries: Pair<String, Tile>): Grid = mapOf(*entries)

    private fun greenery(owner: PlayerColor) = Tile(TileType.GREENERY, owner)

    @Test
    fun `an empty board scores nothing`() {
        assertEquals(emptyMap(), Scorer.score(board, emptyMap()))
    }

    @Test
    fun `a lone greenery scores one point`() {
        val result = Scorer.score(board, grid("r5c5" to greenery(PlayerColor.RED)))
        assertEquals(ScoreBreakdown(greeneries = 1, cityPoints = 0), result.getValue(PlayerColor.RED))
        assertEquals(1, result.getValue(PlayerColor.RED).total)
    }

    @Test
    fun `greeneries accumulate per owner`() {
        val result = Scorer.score(
            board,
            grid(
                "r5c5" to greenery(PlayerColor.RED),
                "r5c6" to greenery(PlayerColor.RED),
                "r1c1" to greenery(PlayerColor.BLUE),
            ),
        )
        assertEquals(2, result.getValue(PlayerColor.RED).greeneries)
        assertEquals(1, result.getValue(PlayerColor.BLUE).greeneries)
    }

    @Test
    fun `oceans specials and empties score nothing`() {
        val result = Scorer.score(
            board,
            grid(
                "r5c5" to Tile(TileType.OCEAN, null),
                "r5c6" to Tile(TileType.SPECIAL, PlayerColor.GREEN),
                "r5c7" to Tile(TileType.EMPTY, null),
            ),
        )
        assertEquals(emptyMap(), result)
    }

    @Test
    fun `an unowned greenery scores for nobody`() {
        val result = Scorer.score(board, grid("r5c5" to Tile(TileType.GREENERY, null)))
        assertEquals(emptyMap(), result)
    }

    @Test
    fun `players with no scoring tiles are absent from the result`() {
        val result = Scorer.score(board, grid("r5c5" to greenery(PlayerColor.RED)))
        assertEquals(setOf(PlayerColor.RED), result.keys)
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :scoring:test --tests '*ScorerTest*'`
Expected: FAIL — `Unresolved reference: TileType`.

- [ ] **Step 3: Write the minimal implementation**

`scoring/src/main/kotlin/tm/scoring/Tile.kt`:

```kotlin
package tm.scoring

enum class TileType { EMPTY, OCEAN, GREENERY, CITY, SPECIAL }

enum class PlayerColor { BLUE, RED, GREEN, YELLOW, BLACK }

/** [owner] is null when the hex has no cube, or the cube could not be read. */
data class Tile(val type: TileType, val owner: PlayerColor?)

/** What the app believes is on each hex, keyed by hex id. Absent means empty. */
typealias Grid = Map<String, Tile>
```

`scoring/src/main/kotlin/tm/scoring/Scorer.kt`:

```kotlin
package tm.scoring

data class ScoreBreakdown(val greeneries: Int, val cityPoints: Int) {
    val total: Int get() = greeneries + cityPoints
}

/**
 * Victory points visible on the map, and nothing else. Terraform rating,
 * milestones, awards, and card points are not derivable from a photo of the
 * board and are deliberately not counted here.
 */
object Scorer {

    fun score(board: Board, grid: Grid): Map<PlayerColor, ScoreBreakdown> {
        val greeneries = mutableMapOf<PlayerColor, Int>()

        for ((_, tile) in grid) {
            val owner = tile.owner ?: continue
            if (tile.type == TileType.GREENERY) greeneries.merge(owner, 1, Int::plus)
        }

        return greeneries.mapValues { (_, count) -> ScoreBreakdown(greeneries = count, cityPoints = 0) }
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :scoring:test`
Expected: PASS, 31 tests.

- [ ] **Step 5: Commit**

```bash
git add scoring
git commit -m "feat(scoring): add tile model and greenery scoring"
```

---

### Task 6: City adjacency scoring

**Files:**
- Modify: `scoring/src/main/kotlin/tm/scoring/Scorer.kt`
- Test: `scoring/src/test/kotlin/tm/scoring/ScorerTest.kt`

**Interfaces:**
- Consumes: `Scorer.score`, `Board.neighbors`.
- Produces: no new signatures; `ScoreBreakdown.cityPoints` starts being populated.

- [ ] **Step 1: Write the failing tests**

Append to `ScorerTest.kt`, inside the class:

```kotlin
    private fun city(owner: PlayerColor) = Tile(TileType.CITY, owner)

    @Test
    fun `a city with no adjacent greenery scores nothing`() {
        val result = Scorer.score(board, grid("r5c5" to city(PlayerColor.RED)))
        assertEquals(emptyMap(), result)
    }

    @Test
    fun `a city scores one point per adjacent greenery`() {
        val result = Scorer.score(
            board,
            grid(
                "r5c5" to city(PlayerColor.RED),
                "r5c4" to greenery(PlayerColor.RED),
                "r5c6" to greenery(PlayerColor.RED),
            ),
        )
        assertEquals(ScoreBreakdown(greeneries = 2, cityPoints = 2), result.getValue(PlayerColor.RED))
        assertEquals(4, result.getValue(PlayerColor.RED).total)
    }

    @Test
    fun `a city scores for greeneries owned by other players`() {
        val result = Scorer.score(
            board,
            grid(
                "r5c5" to city(PlayerColor.RED),
                "r5c4" to greenery(PlayerColor.BLUE),
                "r5c6" to greenery(PlayerColor.GREEN),
                "r4c4" to greenery(PlayerColor.YELLOW),
            ),
        )
        assertEquals(3, result.getValue(PlayerColor.RED).cityPoints)
        assertEquals(0, result.getValue(PlayerColor.RED).greeneries)
        assertEquals(1, result.getValue(PlayerColor.BLUE).greeneries)
        assertEquals(1, result.getValue(PlayerColor.GREEN).greeneries)
        assertEquals(1, result.getValue(PlayerColor.YELLOW).greeneries)
    }

    @Test
    fun `one greenery scores for every adjacent city`() {
        val result = Scorer.score(
            board,
            grid(
                "r5c5" to greenery(PlayerColor.BLUE),
                "r5c4" to city(PlayerColor.RED),
                "r5c6" to city(PlayerColor.GREEN),
            ),
        )
        assertEquals(1, result.getValue(PlayerColor.RED).cityPoints)
        assertEquals(1, result.getValue(PlayerColor.GREEN).cityPoints)
        assertEquals(1, result.getValue(PlayerColor.BLUE).greeneries)
    }

    @Test
    fun `adjacent cities do not score off each other`() {
        val result = Scorer.score(
            board,
            grid(
                "r5c5" to city(PlayerColor.RED),
                "r5c6" to city(PlayerColor.BLUE),
            ),
        )
        assertEquals(emptyMap(), result)
    }

    @Test
    fun `a city at the board edge only counts hexes that exist`() {
        val result = Scorer.score(
            board,
            grid(
                "r1c1" to city(PlayerColor.RED),
                "r1c2" to greenery(PlayerColor.RED),
                "r2c1" to greenery(PlayerColor.RED),
                "r2c2" to greenery(PlayerColor.RED),
            ),
        )
        assertEquals(3, result.getValue(PlayerColor.RED).cityPoints)
    }

    @Test
    fun `an unowned city scores for nobody`() {
        val result = Scorer.score(
            board,
            grid(
                "r5c5" to Tile(TileType.CITY, null),
                "r5c4" to greenery(PlayerColor.BLUE),
            ),
        )
        assertEquals(setOf(PlayerColor.BLUE), result.keys)
    }

    @Test
    fun `an unowned greenery still scores points for an adjacent city`() {
        val result = Scorer.score(
            board,
            grid(
                "r5c5" to city(PlayerColor.RED),
                "r5c4" to Tile(TileType.GREENERY, null),
            ),
        )
        assertEquals(1, result.getValue(PlayerColor.RED).cityPoints)
    }
```

The last test pins a decision worth stating out loud: an unread cube costs its
owner the greenery point, but the greenery is still physically on the board, so
an adjacent city still scores from it.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :scoring:test --tests '*ScorerTest*'`
Expected: FAIL — `a city scores one point per adjacent greenery` expects `cityPoints = 2` but gets `0`.

- [ ] **Step 3: Write the implementation**

Replace the body of `Scorer.score` in `Scorer.kt`:

```kotlin
    fun score(board: Board, grid: Grid): Map<PlayerColor, ScoreBreakdown> {
        val greeneries = mutableMapOf<PlayerColor, Int>()
        val cityPoints = mutableMapOf<PlayerColor, Int>()

        for ((hexId, tile) in grid) {
            val owner = tile.owner ?: continue
            when (tile.type) {
                TileType.GREENERY -> greeneries.merge(owner, 1, Int::plus)
                TileType.CITY -> {
                    val adjacent = board.neighbors(hexId).count { grid[it]?.type == TileType.GREENERY }
                    if (adjacent > 0) cityPoints.merge(owner, adjacent, Int::plus)
                }
                TileType.EMPTY, TileType.OCEAN, TileType.SPECIAL -> Unit
            }
        }

        return (greeneries.keys + cityPoints.keys).associateWith { player ->
            ScoreBreakdown(
                greeneries = greeneries[player] ?: 0,
                cityPoints = cityPoints[player] ?: 0,
            )
        }
    }
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :scoring:test`
Expected: PASS, 39 tests.

- [ ] **Step 5: Commit**

```bash
git add scoring
git commit -m "feat(scoring): score cities by adjacent greeneries"
```

---

### Task 7: End-to-end scoring test on a full board

**Files:**
- Create: `scoring/src/test/kotlin/tm/scoring/FullGameScoringTest.kt`
- Modify: `scoring/src/main/kotlin/tm/scoring/Scorer.kt`

**Interfaces:**
- Consumes: everything above.
- Produces: `Scorer.scoreAll(board: Board, grid: Grid, players: Set<PlayerColor>): Map<PlayerColor, ScoreBreakdown>`, which reports a zero breakdown for every player in `players` rather than omitting them. The score screen uses this so a player on zero still appears in the table.

- [ ] **Step 1: Write the failing test**

`scoring/src/test/kotlin/tm/scoring/FullGameScoringTest.kt`:

```kotlin
package tm.scoring

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A realistic end-of-game map. Worked by hand below so the expected numbers can
 * be checked against the rules rather than against the implementation.
 *
 * Layout, all on Tharsis:
 *
 *   RED   greeneries at r4c4, r4c5, r5c5      -> 3 points
 *         city at r4c6, adjacent to r4c5 (RED) and r3c5 (BLUE greenery)
 *                                              -> 2 points
 *   BLUE  greeneries at r3c5, r6c6            -> 2 points
 *         city at r6c5, adjacent to r6c6 (BLUE) and r5c5 (RED)
 *                                              -> 2 points
 *   GREEN city at r1c1, no adjacent greenery  -> 0 points
 *         special tile at r2c2                -> 0 points
 *   BLACK no tiles                            -> 0 points
 */
class FullGameScoringTest {

    private val grid: Grid = mapOf(
        "r4c4" to Tile(TileType.GREENERY, PlayerColor.RED),
        "r4c5" to Tile(TileType.GREENERY, PlayerColor.RED),
        "r5c5" to Tile(TileType.GREENERY, PlayerColor.RED),
        "r4c6" to Tile(TileType.CITY, PlayerColor.RED),
        "r3c5" to Tile(TileType.GREENERY, PlayerColor.BLUE),
        "r6c6" to Tile(TileType.GREENERY, PlayerColor.BLUE),
        "r6c5" to Tile(TileType.CITY, PlayerColor.BLUE),
        "r1c1" to Tile(TileType.CITY, PlayerColor.GREEN),
        "r2c2" to Tile(TileType.SPECIAL, PlayerColor.GREEN),
        "r5c1" to Tile(TileType.OCEAN, null),
        "r5c9" to Tile(TileType.OCEAN, null),
    )

    private val players = setOf(PlayerColor.RED, PlayerColor.BLUE, PlayerColor.GREEN, PlayerColor.BLACK)

    @Test
    fun `scores the whole board`() {
        val result = Scorer.scoreAll(Board.THARSIS, grid, players)

        assertEquals(ScoreBreakdown(greeneries = 3, cityPoints = 2), result.getValue(PlayerColor.RED))
        assertEquals(ScoreBreakdown(greeneries = 2, cityPoints = 2), result.getValue(PlayerColor.BLUE))
        assertEquals(ScoreBreakdown(greeneries = 0, cityPoints = 0), result.getValue(PlayerColor.GREEN))
        assertEquals(ScoreBreakdown(greeneries = 0, cityPoints = 0), result.getValue(PlayerColor.BLACK))
    }

    @Test
    fun `totals are the sum of the parts`() {
        val result = Scorer.scoreAll(Board.THARSIS, grid, players)
        assertEquals(5, result.getValue(PlayerColor.RED).total)
        assertEquals(4, result.getValue(PlayerColor.BLUE).total)
        assertEquals(0, result.getValue(PlayerColor.GREEN).total)
    }

    @Test
    fun `every requested player appears even on zero`() {
        val result = Scorer.scoreAll(Board.THARSIS, grid, players)
        assertEquals(players, result.keys)
    }
}
```

The two adjacencies this fixture depends on: `r4c6` neighbours `r4c5` and
`r3c5`; `r6c5` neighbours `r6c6` and `r5c5`. Both hold in the geometry built in
Tasks 1-3. If either assertion about `cityPoints` fails, check those two
neighbour lists first — a fixture built on the wrong adjacency looks exactly
like a scorer bug.

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :scoring:test --tests '*FullGameScoringTest*'`
Expected: FAIL — `Unresolved reference: scoreAll`.

- [ ] **Step 3: Write the implementation**

Add to `Scorer.kt`, inside the object:

```kotlin
    /**
     * As [score], but reports a zero breakdown for every player in [players] so
     * the score screen can show a complete table.
     */
    fun scoreAll(board: Board, grid: Grid, players: Set<PlayerColor>): Map<PlayerColor, ScoreBreakdown> {
        val scored = score(board, grid)
        return players.associateWith { scored[it] ?: ScoreBreakdown(0, 0) }
    }
```

- [ ] **Step 4: Run the full suite**

Run: `./gradlew :scoring:test`
Expected: PASS, 42 tests.

- [ ] **Step 5: Commit**

```bash
git add scoring
git commit -m "test(scoring): add end-to-end board scoring test"
```

---

### Task 8: Update the spec to match the built module

**Files:**
- Modify: `docs/superpowers/specs/2026-09-01-tm-point-counter-design.md`

- [ ] **Step 1: Replace the "Board definition format" section**

The spec describes per-board JSON with hand-listed coordinates and neighbours.
The module computes them instead. Replace that section with a description of
what was actually built: shared radius-4 axial geometry in `HexGrid`, per-board
data reduced to a set of ocean-reserved hex ids, `HexGrid.ASPECT_RATIO` published
for the vision module, and hex ids in `r<row>c<col>` form.

- [ ] **Step 2: Update the module list**

`:boards` no longer exists as a separate module. Fold its one remaining
responsibility into the `:scoring` line.

- [ ] **Step 3: Commit**

```bash
git add docs
git commit -m "docs: align spec with the built scoring module"
```

---

## What this plan does not build

- Any Android code, camera handling, or UI. That is Plan 2.
- Any model, training script, or image processing. That is Plan 3.
- Distinguishing one special tile from another, and the Capital and Commercial
  District card points that would need it. Open question in the spec.
