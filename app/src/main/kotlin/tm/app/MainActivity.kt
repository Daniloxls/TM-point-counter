package tm.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import tm.scoring.Board

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val viewModel: AppViewModel = viewModel()
                    val state = viewModel.state
                    when (state.step) {
                        Step.PICK_BOARD -> BoardPicker(
                            onBoardChosen = { viewModel.update { s -> s.withBoard(it) } },
                        )
                        Step.CAPTURE -> CaptureScreen(
                            onCaptured = { viewModel.update { s -> s.withPhoto(it) } },
                            onBack = { viewModel.update { s -> s.back() } },
                        )
                        Step.ANCHORS -> AnchorScreen(
                            photoPath = state.photoPath.orEmpty(),
                            onPlaced = { viewModel.update { s -> s.withAnchors(it) } },
                            onBack = { viewModel.update { s -> s.back() } },
                        )
                        Step.REVIEW -> {
                            val board = state.board!!
                            LaunchedEffect(state.photoPath, state.anchors) { viewModel.ensureWarped() }
                            if (!viewModel.warpReady) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator()
                                }
                            } else {
                                ReviewScreen(
                                    board = board,
                                    grid = state.grid,
                                    suggestions = viewModel.suggestions,
                                    warped = viewModel.warped?.asImageBitmap(),
                                    onTileChanged = { hexId, tile ->
                                        viewModel.update { s -> s.withTile(hexId, tile) }
                                    },
                                    onDone = { viewModel.update { s -> s.toScore() } },
                                    onBack = { viewModel.update { s -> s.back() } },
                                )
                            }
                        }
                        Step.SCORE -> ScoreScreen(
                            board = state.board!!,
                            grid = state.grid,
                            players = state.players,
                            onBack = { viewModel.update { s -> s.back() } },
                            onRestart = { viewModel.update { AppState() } },
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
