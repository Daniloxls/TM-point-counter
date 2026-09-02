package tm.app

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tm.scoring.PlayerColor
import tm.vision.CanonicalImage

/**
 * Holds the session's [AppState] so it survives a configuration change, and
 * runs the photo warp and cube reads off the main thread.
 */
class AppViewModel : ViewModel() {

    private val canonicalImage = CanonicalImage(hexCropHeightPx = 64)

    var state by mutableStateOf(AppState())
        private set

    /** The flattened board, once [ensureWarped] has finished for the current photo and anchors. */
    var warped by mutableStateOf<Bitmap?>(null)
        private set

    var suggestions by mutableStateOf<Map<String, PlayerColor?>>(emptyMap())
        private set

    /** False while a warp for the current key is still being computed. */
    var warpReady by mutableStateOf(false)
        private set

    private var warpKey: Pair<String?, List<Pair<Float, Float>>>? = null
    private var warpJob: Job? = null

    fun update(transform: (AppState) -> AppState) {
        state = transform(state)
    }

    /** Starts a background warp for the current photo/anchors, unless one is already current. */
    fun ensureWarped() {
        val key = state.photoPath to state.anchors
        if (warpKey == key) return
        warpKey = key
        warpJob?.cancel()
        warpReady = false

        val staleBitmap = warped
        warped = null
        suggestions = emptyMap()

        val photoPath = state.photoPath
        val anchors = state.anchors
        val board = state.board

        warpJob = viewModelScope.launch(Dispatchers.Default) {
            val next = photoPath?.let { BoardWarper.warp(it, anchors, canonicalImage) }
            val nextSuggestions = if (next != null && board != null) {
                BoardWarper.readCubes(next, board, canonicalImage)
                    .associate { it.hexId to it.suggestedOwner }
            } else {
                emptyMap()
            }
            withContext(Dispatchers.Main.immediate) {
                warped = next
                suggestions = nextSuggestions
                warpReady = true
                staleBitmap?.recycle()
            }
        }
    }

    override fun onCleared() {
        warpJob?.cancel()
        warped?.recycle()
    }
}
