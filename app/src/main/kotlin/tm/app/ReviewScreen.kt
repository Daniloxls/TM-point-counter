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
