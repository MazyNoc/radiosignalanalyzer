package com.example.radiosignalanalyzer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.DragData
import androidx.compose.ui.draganddrop.dragData
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import java.net.URI
import kotlin.math.abs
import kotlin.math.roundToLong

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SignalGraphView(
    subFile: SubFile?,
    viewFractionPerUs: Float,  // fraction of canvas width per µs — canvas-size-independent
    viewOffsetUs: Long,
    tickIntervalUs: Int,
    tickMode: TickStrategy,
    startMarkerUs: Long?,
    dataStartUs: Long?,
    dataEndUs: Long?,
    onPan: (deltaUs: Long) -> Unit,
    onSetStartMarker: (us: Long) -> Unit,
    onSetDataStart: (us: Long) -> Unit,
    onSetDataEnd: (us: Long) -> Unit,
    onLoadFile: (File) -> Unit,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current

    // Drag-and-drop state
    var isDragHovering by remember { mutableStateOf(false) }
    val dropTarget = remember {
        object : DragAndDropTarget {
            override fun onDrop(event: DragAndDropEvent): Boolean {
                isDragHovering = false
                val data = event.dragData()
                if (data is DragData.FilesList) {
                    data.readFiles()
                        .mapNotNull { runCatching { File(URI(it)) }.getOrNull() }
                        .firstOrNull { it.extension == "sub" }
                        ?.let { onLoadFile(it) }
                    return true
                }
                return false
            }
            override fun onStarted(event: DragAndDropEvent) { isDragHovering = true }
            override fun onEntered(event: DragAndDropEvent) { isDragHovering = true }
            override fun onExited(event: DragAndDropEvent) { isDragHovering = false }
            override fun onEnded(event: DragAndDropEvent) { isDragHovering = false }
        }
    }

    // Context menu state (ephemeral UI state — lives in composable)
    var contextMenuVisible by remember { mutableStateOf(false) }
    var contextMenuDpOffset by remember { mutableStateOf(DpOffset.Zero) }
    var contextMenuPressUs by remember { mutableStateOf(0L) }

    // Local canvas width — only needed for pan delta and long-press position conversion
    var localCanvasWidthPx by remember { mutableStateOf(0f) }

    // Scrollbar width
    var scrollbarBarWidthPx by remember { mutableStateOf(0f) }
    var canvasBoxHeightDp by remember { mutableStateOf(0f) }

    // rememberUpdatedState so drag lambdas always see latest values without restarting gesture
    val currentViewFractionPerUs by rememberUpdatedState(viewFractionPerUs)
    val currentLocalCanvasWidthPx by rememberUpdatedState(localCanvasWidthPx)
    val currentViewOffsetUs by rememberUpdatedState(viewOffsetUs)

    Column(modifier = modifier) {
        // ── Signal canvas ────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .onSizeChanged { canvasBoxHeightDp = with(density) { it.height.toDp().value } }
                .dragAndDropTarget(
                    shouldStartDragAndDrop = { it.dragData() is DragData.FilesList },
                    target = dropTarget
                )
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { localCanvasWidthPx = it.width.toFloat() }
                    // Drag-to-pan: convert pixel delta to µs using current ppu
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            val ppu = currentViewFractionPerUs * currentLocalCanvasWidthPx
                            if (ppu > 0f) onPan((-dragAmount.x / ppu).roundToLong())
                        }
                    }
                    // Long-press for context menu
                    .pointerInput(viewFractionPerUs, viewOffsetUs) {
                        detectTapGestures(
                            onLongPress = { offset ->
                                with(density) {
                                    contextMenuDpOffset = DpOffset(
                                        offset.x.toDp(),
                                        offset.y.toDp() - canvasBoxHeightDp.dp
                                    )
                                }
                                val ppu = viewFractionPerUs * localCanvasWidthPx
                                contextMenuPressUs = if (ppu > 0f)
                                    viewOffsetUs + (offset.x / ppu).roundToLong()
                                else viewOffsetUs
                                contextMenuVisible = true
                            }
                        )
                    }
            ) {
                val w = size.width
                val h = size.height
                // ppu is computed locally from fraction × canvas width
                val ppu = viewFractionPerUs * w

                val topPad = 20f
                val leftPad = 28f
                val tickStyle = TextStyle(color = Color(0xFF9999CC), fontSize = 11.sp)
                val tickLabelHeight = textMeasurer.measure("0", style = tickStyle).size.height
                val bottomPad = tickLabelHeight + 16f  // 8px gap above + 8px margin below
                val signalH = h - topPad - bottomPad
                val signalInset = 10f         // gap between tick extent and signal line
                val tickTopY = topPad
                val tickBottomY = topPad + signalH
                val highY = tickTopY + signalInset
                val lowY = tickBottomY - signalInset

                // Background
                drawRect(Color(0xFF1E1E2E), size = size)

                // Y-axis labels
                val labelStyle = TextStyle(color = Color(0xFF888888), fontSize = 10.sp)
                drawText(textMeasurer, "1", style = labelStyle, topLeft = Offset(4f, highY - 8f))
                drawText(textMeasurer, "0", style = labelStyle, topLeft = Offset(4f, lowY - 8f))

                // Tick marks — computed by the active TickStrategy
                if (ppu > 0f && subFile != null) {
                    val originUs = startMarkerUs ?: 0L
                    val visibleEndUs = viewOffsetUs + (w / ppu).toLong()
                    val ticks = tickMode.computeTicks(
                        viewOffsetUs = viewOffsetUs,
                        visibleEndUs = visibleEndUs,
                        tickIntervalUs = tickIntervalUs,
                        rawData = subFile.rawData,
                        originUs = originUs,
                    )
                    for (tick in ticks) {
                        val x = (tick.posUs - viewOffsetUs) * ppu
                        if (x >= 0f) {
                            drawLine(
                                color = Color(0xFF8888CC),
                                start = Offset(x, tickTopY),
                                end = Offset(x, tickBottomY),
                                strokeWidth = 1f
                            )
                            if (tick.label != null) {
                                drawText(
                                    textMeasurer,
                                    tick.label,
                                    style = tickStyle,
                                    topLeft = Offset(x + 2f, tickBottomY + 8f)
                                )
                            }
                        }
                    }
                }

                // Waveform
                if (ppu > 0f && subFile != null && subFile.rawData.isNotEmpty()) {
                    val raw = subFile.rawData

                    // Find the first visible segment
                    var accumUs = 0L
                    var startIdx = 0
                    var partialOffsetUs = 0L
                    for (i in raw.indices) {
                        val dur = abs(raw[i]).toLong()
                        if (accumUs + dur > viewOffsetUs) {
                            startIdx = i
                            partialOffsetUs = viewOffsetUs - accumUs
                            break
                        }
                        accumUs += dur
                    }

                    var currentX = -partialOffsetUs * ppu
                    val signalColor = Color(0xFF00FF88)
                    val strokeW = 2f

                    for (i in startIdx until raw.size) {
                        val sample = raw[i]
                        val dur = abs(sample).toLong()
                        val nextX = currentX + dur * ppu
                        val y = if (sample > 0) highY else lowY

                        val drawStart = currentX.coerceAtLeast(leftPad)
                        val drawEnd = nextX.coerceAtMost(w)
                        if (drawEnd > drawStart) {
                            drawLine(
                                color = signalColor,
                                start = Offset(drawStart, y),
                                end = Offset(drawEnd, y),
                                strokeWidth = strokeW
                            )
                        }

                        if (i + 1 < raw.size && nextX in leftPad..w) {
                            val nextY = if (raw[i + 1] > 0) highY else lowY
                            if (nextY != y) {
                                drawLine(
                                    color = signalColor,
                                    start = Offset(nextX, y),
                                    end = Offset(nextX, nextY),
                                    strokeWidth = strokeW
                                )
                            }
                        }

                        currentX = nextX
                        if (currentX > w) break
                    }
                }

                // Start marker
                if (ppu > 0f && startMarkerUs != null && subFile != null) {
                    val markerX = (startMarkerUs - viewOffsetUs) * ppu
                    if (markerX in 0f..w) {
                        drawLine(
                            color = Color(0xFFFFAA00),
                            start = Offset(markerX, 0f),
                            end = Offset(markerX, h),
                            strokeWidth = 2f
                        )
                        drawText(
                            textMeasurer,
                            "▲ Start",
                            style = TextStyle(color = Color(0xFFFFAA00), fontSize = 9.sp),
                            topLeft = Offset(markerX + 3f, 2f)
                        )
                    }
                }

                // Header end marker
                if (ppu > 0f && dataStartUs != null && subFile != null) {
                    val markerX = (dataStartUs - viewOffsetUs) * ppu
                    if (markerX in 0f..w) {
                        drawLine(
                            color = Color(0xFF00CCFF),
                            start = Offset(markerX, 0f),
                            end = Offset(markerX, h),
                            strokeWidth = 2f
                        )
                        drawText(
                            textMeasurer,
                            "▲ Data Start",
                            style = TextStyle(color = Color(0xFF00CCFF), fontSize = 9.sp),
                            topLeft = Offset(markerX + 3f, 2f)
                        )
                    }
                }

                // Data end marker
                if (ppu > 0f && dataEndUs != null && subFile != null) {
                    val markerX = (dataEndUs - viewOffsetUs) * ppu
                    if (markerX in 0f..w) {
                        drawLine(
                            color = Color(0xFFFF5555),
                            start = Offset(markerX, 0f),
                            end = Offset(markerX, h),
                            strokeWidth = 2f
                        )
                        drawText(
                            textMeasurer,
                            "▲ Data End",
                            style = TextStyle(color = Color(0xFFFF5555), fontSize = 9.sp),
                            topLeft = Offset(markerX + 3f, 2f)
                        )
                    }
                }

                // Empty state hint
                if (subFile == null && !isDragHovering) {
                    val hint = "Open a .sub file to begin"
                    val measured = textMeasurer.measure(hint)
                    drawText(
                        textMeasurer,
                        hint,
                        style = TextStyle(color = Color(0xFF666666), fontSize = 14.sp),
                        topLeft = Offset(
                            (w - measured.size.width) / 2f,
                            (h - measured.size.height) / 2f
                        )
                    )
                }

                // Drag-hover overlay
                if (isDragHovering) {
                    drawRect(Color(0x330088FF), size = size)
                    val dropHint = "Drop .sub file here"
                    val measured = textMeasurer.measure(dropHint)
                    drawText(
                        textMeasurer,
                        dropHint,
                        style = TextStyle(color = Color(0xFF88CCFF), fontSize = 18.sp),
                        topLeft = Offset(
                            (w - measured.size.width) / 2f,
                            (h - measured.size.height) / 2f
                        )
                    )
                }
            }

            // Context menu
            DropdownMenu(
                expanded = contextMenuVisible,
                onDismissRequest = { contextMenuVisible = false },
                offset = contextMenuDpOffset
            ) {
                DropdownMenuItem(
                    text = { Text("Set Start (${contextMenuPressUs} µs)") },
                    onClick = {
                        onSetStartMarker(contextMenuPressUs)
                        contextMenuVisible = false
                    }
                )
                DropdownMenuItem(
                    text = { Text("Set Data Start (${contextMenuPressUs} µs)") },
                    onClick = {
                        onSetDataStart(contextMenuPressUs)
                        contextMenuVisible = false
                    }
                )
                DropdownMenuItem(
                    text = { Text("Set Data End (${contextMenuPressUs} µs)") },
                    onClick = {
                        onSetDataEnd(contextMenuPressUs)
                        contextMenuVisible = false
                    }
                )
            }
        }

        // ── Scrollbar ────────────────────────────────────────────────────────
        if (subFile != null) {
            val totalUs = subFile.totalDurationUs.toFloat()
            // viewportUs is canvas-size-independent: 1 / viewFractionPerUs
            val viewportUs = if (viewFractionPerUs > 0f) 1f / viewFractionPerUs else totalUs
            val thumbFraction = if (totalUs > 0f) (viewportUs / totalUs).coerceIn(0f, 1f) else 1f
            val thumbStart = if (totalUs > 0f) (viewOffsetUs / totalUs).coerceIn(0f, 1f - thumbFraction) else 0f

            val currentTotalUs by rememberUpdatedState(totalUs)
            val currentBarWidthPx by rememberUpdatedState(scrollbarBarWidthPx)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(14.dp)
                    .padding(horizontal = 4.dp, vertical = 2.dp)
                    .background(Color(0xFF2A2A3A), RoundedCornerShape(4.dp))
                    .onSizeChanged { scrollbarBarWidthPx = it.width.toFloat() }
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            val deltaUs = (dragAmount.x / currentBarWidthPx * currentTotalUs).roundToLong()
                            onPan(deltaUs)
                        }
                    },
                contentAlignment = Alignment.CenterStart
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val thumbX = thumbStart * size.width
                    val thumbW = (thumbFraction * size.width).coerceAtLeast(12f)
                    drawRoundRect(
                        color = Color(0xFF5555AA),
                        topLeft = Offset(thumbX, 0f),
                        size = Size(thumbW, size.height),
                        cornerRadius = CornerRadius(4f)
                    )
                }
            }
        }
    }
}
