package tm.scoring

enum class TileType { EMPTY, OCEAN, GREENERY, CITY, SPECIAL }

enum class PlayerColor { BLUE, RED, GREEN, YELLOW, BLACK }

/** [owner] is null when the hex has no cube, or the cube could not be read. */
data class Tile(val type: TileType, val owner: PlayerColor?)

/** What the app believes is on each hex, keyed by hex id. Absent means empty. */
typealias Grid = Map<String, Tile>
