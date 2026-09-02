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
