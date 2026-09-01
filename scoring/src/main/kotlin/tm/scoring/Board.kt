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
        val THARSIS = Board(
            "tharsis",
            setOf(
                "r1c2", "r1c4", "r1c5",
                "r2c6",
                "r4c8",
                "r5c4", "r5c5", "r5c6",
                "r6c6", "r6c7", "r6c8",
                "r9c5",
            ),
        )

        val HELLAS = Board(
            "hellas",
            setOf(
                "r1c1",
                "r2c1",
                "r3c1",
                "r4c1", "r4c6", "r4c7",
                "r5c6", "r5c7", "r5c8",
                "r6c6", "r6c7",
                "r7c1",
            ),
        )

        val ELYSIUM = Board(
            "elysium",
            setOf(
                "r1c1", "r1c2", "r1c3", "r1c4",
                "r2c4", "r2c5",
                "r3c5", "r3c6",
                "r4c4", "r4c6", "r4c7",
                "r5c4",
            ),
        )

        val ALL = listOf(THARSIS, HELLAS, ELYSIUM)

        fun byId(id: String): Board =
            ALL.firstOrNull { it.id == id } ?: throw IllegalArgumentException("unknown board: $id")
    }
}
