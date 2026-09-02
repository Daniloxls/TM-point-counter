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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import tm.scoring.Board
import tm.vision.CanonicalImage

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    var state by remember { mutableStateOf(AppState()) }
                    when (state.step) {
                        Step.PICK_BOARD -> BoardPicker(onBoardChosen = { state = state.withBoard(it) })
                        Step.CAPTURE -> CaptureScreen(
                            onCaptured = { state = state.withPhoto(it) },
                            onBack = { state = state.back() },
                        )
                        Step.ANCHORS -> AnchorScreen(
                            photoPath = state.photoPath.orEmpty(),
                            onPlaced = { state = state.withAnchors(it) },
                            onBack = { state = state.back() },
                        )
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
                        Step.SCORE -> ScoreScreen(
                            board = state.board!!,
                            grid = state.grid,
                            players = state.players,
                            onBack = { state = state.back() },
                            onRestart = { state = AppState() },
                        )
                    }
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
