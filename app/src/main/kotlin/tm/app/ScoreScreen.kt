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
