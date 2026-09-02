package tm.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import tm.vision.WarpAnchors
import kotlin.math.min

private val ANCHOR_LABELS = listOf("top-left", "top-right", "bottom-left", "bottom-right")

@Composable
fun AnchorScreen(
    photoPath: String,
    onPlaced: (List<Pair<Float, Float>>) -> Unit,
    onBack: () -> Unit,
) {
    // This screen only ever draws at canvas resolution, so decoding the full
    // 12MP+ still is wasted memory and time.
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val bitmap = remember(photoPath) {
        val targetWidth = with(density) { configuration.screenWidthDp.dp.roundToPx() }
        val targetHeight = with(density) { configuration.screenHeightDp.dp.roundToPx() }
        decodeSampled(photoPath, targetWidth, targetHeight)
    }
    DisposableEffect(bitmap) {
        onDispose { bitmap?.recycle() }
    }

    if (bitmap == null) {
        Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
            Text("That photo could not be opened. Take another.")
            TextButton(onClick = onBack) { Text("Back") }
        }
        return
    }

    val imageWidth = bitmap.width.toFloat()
    val imageHeight = bitmap.height.toFloat()

    // Anchors live in image pixels, so they survive any resize of the view.
    var anchors by remember(photoPath) {
        mutableStateOf(
            listOf(
                0.25f * imageWidth to 0.25f * imageHeight,
                0.75f * imageWidth to 0.25f * imageHeight,
                0.25f * imageWidth to 0.75f * imageHeight,
                0.75f * imageWidth to 0.75f * imageHeight,
            ),
        )
    }
    var dragging by remember { mutableStateOf(-1) }
    var canvasSize by remember { mutableStateOf(Size.Zero) }

    fun scale(): Float =
        if (canvasSize == Size.Zero) 1f
        else min(canvasSize.width / imageWidth, canvasSize.height / imageHeight)

    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { canvasSize = Size(it.width.toFloat(), it.height.toFloat()) }
                    .pointerInput(photoPath) {
                        detectDragGestures(
                            onDragStart = { touch ->
                                val s = scale()
                                dragging = anchors.indices.minByOrNull { index ->
                                    val (ax, ay) = anchors[index]
                                    val dx = ax * s - touch.x
                                    val dy = ay * s - touch.y
                                    dx * dx + dy * dy
                                } ?: -1
                            },
                            onDragEnd = { dragging = -1 },
                            onDrag = { change, amount ->
                                change.consume()
                                if (dragging >= 0) {
                                    val s = scale()
                                    anchors = anchors.toMutableList().also { list ->
                                        val (ax, ay) = list[dragging]
                                        list[dragging] =
                                            (ax + amount.x / s).coerceIn(0f, imageWidth) to
                                                (ay + amount.y / s).coerceIn(0f, imageHeight)
                                    }
                                }
                            },
                        )
                    },
            ) {
                val s = min(size.width / imageWidth, size.height / imageHeight)
                drawImage(
                    image = bitmap.asImageBitmap(),
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(bitmap.width, bitmap.height),
                    dstOffset = IntOffset.Zero,
                    dstSize = IntSize((imageWidth * s).toInt(), (imageHeight * s).toInt()),
                )
                anchors.forEach { (ax, ay) ->
                    val centre = Offset(ax * s, ay * s)
                    drawCircle(Color.White, radius = 26f, center = centre, style = Stroke(width = 6f))
                    drawCircle(Color.Black, radius = 20f, center = centre, style = Stroke(width = 3f))
                }
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Drag each handle onto the centre of a corner hex:")
            WarpAnchors.HEX_IDS.forEachIndexed { index, id ->
                Text("• ${ANCHOR_LABELS[index]} handle → hex $id")
            }
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = { onPlaced(anchors) },
            ) {
                Text("Looks right")
            }
            TextButton(onClick = onBack) { Text("Retake photo") }
        }
    }
}
