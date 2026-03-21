package com.example.radiosignalanalyzer

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(viewModel: MainViewModel, onOpenFile: () -> Unit, onLoadFile: (java.io.File) -> Unit) {

    val subFile by viewModel.subFile.collectAsStateWithLifecycle()
    val parseError by viewModel.parseError.collectAsStateWithLifecycle()
    val zoomSlider by viewModel.zoomSlider.collectAsStateWithLifecycle()
    val viewFractionPerUs by viewModel.viewFractionPerUs.collectAsStateWithLifecycle()
    val viewOffsetUs by viewModel.viewOffsetUs.collectAsStateWithLifecycle()
    val tickIntervalUs by viewModel.tickIntervalUs.collectAsStateWithLifecycle()
    val tickMode by viewModel.tickMode.collectAsStateWithLifecycle()
    val startMarkerUs by viewModel.startMarkerUs.collectAsStateWithLifecycle()
    val dataStartUs by viewModel.dataStartUs.collectAsStateWithLifecycle()
    val dataEndUs by viewModel.dataEndUs.collectAsStateWithLifecycle()
    val dataEndTickCount by viewModel.dataEndTickCount.collectAsStateWithLifecycle()
    val bitPattern by viewModel.bitPattern.collectAsStateWithLifecycle()
    val hexValue by viewModel.hexValue.collectAsStateWithLifecycle()
    val onePattern by viewModel.onePattern.collectAsStateWithLifecycle()
    val zeroPattern by viewModel.zeroPattern.collectAsStateWithLifecycle()

    MaterialTheme(colorScheme = darkColorScheme()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Radio Signal Analyzer") },
                    actions = {
                        TextButton(onClick = onOpenFile) {
                            Text("Open File")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                // Info panel — shown only when a file is loaded
                if (subFile != null) {
                    val markerError = if (dataEndUs != null && dataStartUs != null && dataEndUs!! < dataStartUs!!)
                        "Data End ($dataEndUs µs) is before Data Start ($dataStartUs µs)" else null
                    InfoPanel(subFile = subFile!!, markerError = markerError, bitPattern = bitPattern, hexValue = hexValue)
                }

                // Error banner
                if (parseError != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "Error: $parseError",
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { viewModel.clearError() }) {
                                Text("Dismiss")
                            }
                        }
                    }
                }

                // Signal graph — fills remaining space
                SignalGraphView(
                    subFile = subFile,
                    viewFractionPerUs = viewFractionPerUs,
                    viewOffsetUs = viewOffsetUs,
                    tickIntervalUs = tickIntervalUs,
                    tickMode = tickMode,
                    startMarkerUs = startMarkerUs,
                    dataStartUs = dataStartUs,
                    dataEndUs = dataEndUs,
                    onPan = { viewModel.pan(it) },
                    onSetStartMarker = { viewModel.setStartMarker(it) },
                    onSetDataStart = { viewModel.setDataStart(it) },
                    onSetDataEnd = { viewModel.setDataEnd(it) },
                    onLoadFile = onLoadFile,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )

                // Graph controls — zoom, ticks, offset
                val viewportUs = if (viewFractionPerUs > 0f) (1f / viewFractionPerUs).toLong() else null
                GraphControls(
                    enabled = subFile != null,
                    zoomSlider = zoomSlider,
                    viewportUs = viewportUs,
                    tickIntervalUs = tickIntervalUs,
                    tickMode = tickMode,
                    startMarkerUs = startMarkerUs,
                    dataStartUs = dataStartUs,
                    dataEndTickCount = dataEndTickCount,
                    onePattern = onePattern,
                    zeroPattern = zeroPattern,
                    onZoomChange = { viewModel.setZoomSlider(it) },
                    onTickChange = { viewModel.setTickInterval(it) },
                    onTickModeChange = { viewModel.setTickMode(it) },
                    onStartMarkerTextChanged = { viewModel.setStartMarkerFromText(it) },
                    onDataStartTextChanged = { viewModel.setDataStartFromText(it) },
                    onDataEndTextChanged = { viewModel.setDataEndFromText(it) },
                    onOnePatternChange = { viewModel.setOnePattern(it) },
                    onZeroPatternChange = { viewModel.setZeroPattern(it) }
                )
            }
        }
    }
}

