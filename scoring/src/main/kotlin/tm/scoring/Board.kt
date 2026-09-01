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
