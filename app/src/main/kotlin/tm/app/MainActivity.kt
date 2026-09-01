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
import androidx.compose.ui.unit.dp
import tm.scoring.Board

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    var state by remember { mutableStateOf(AppState()) }
                    when (state.step) {
                        Step.PICK_BOARD -> BoardPicker(onBoardChosen = { state = state.withBoard(it) })
                        Step.CAPTURE -> Text("Capture — Task 7")
                        Step.ANCHORS -> Text("Anchors — Task 8")
                        Step.REVIEW -> Text("Review — Task 9")
                        Step.SCORE -> Text("Score — Task 10")
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
