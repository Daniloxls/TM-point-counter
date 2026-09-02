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
