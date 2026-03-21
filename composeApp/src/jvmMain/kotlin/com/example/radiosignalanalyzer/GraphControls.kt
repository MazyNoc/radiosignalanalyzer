package com.example.radiosignalanalyzer

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
fun GraphControls(
    enabled: Boolean,
    zoomSlider: Float,
    viewportUs: Long?,
    tickIntervalUs: Int,
    tickMode: TickStrategy,
    startMarkerUs: Long?,
    dataStartUs: Long?,
    dataEndTickCount: Int?,
    onePattern: BitDecodePattern,
    zeroPattern: BitDecodePattern,
    onZoomChange: (Float) -> Unit,
    onTickChange: (Int) -> Unit,
    onTickModeChange: (TickStrategy) -> Unit,
    onStartMarkerTextChanged: (String) -> Unit,
    onDataStartTextChanged: (String) -> Unit,
    onDataEndTextChanged: (String) -> Unit,
    onOnePatternChange: (BitDecodePattern) -> Unit,
    onZeroPatternChange: (BitDecodePattern) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        tonalElevation = 1.dp,
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Zoom slider
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "Zoom",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.width(72.dp)
                )
                Slider(
                    value = zoomSlider,
                    onValueChange = onZoomChange,
                    enabled = enabled,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = if (viewportUs != null) formatViewport(viewportUs) else "—",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.width(100.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Tick interval slider + text input
            var tickText by remember(tickIntervalUs) { mutableStateOf(tickIntervalUs.toString()) }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "Ticks",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.width(72.dp)
                )
                Slider(
                    value = tickIntervalUs.toFloat(),
                    onValueChange = { onTickChange(it.toInt()) },
                    valueRange = 30f..2000f,
                    enabled = enabled,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = tickText,
                    onValueChange = { tickText = it },
                    enabled = enabled,
                    singleLine = true,
                    suffix = { Text("µs") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { tickText.toIntOrNull()?.let(onTickChange) }
                    ),
                    modifier = Modifier
                        .width(100.dp)
                        .onFocusChanged { if (!it.isFocused) tickText.toIntOrNull()?.let(onTickChange) },
                    textStyle = MaterialTheme.typography.bodySmall
                )
            }

            // Tick mode dropdown
            var tickModeExpanded by remember { mutableStateOf(false) }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "Tick mode",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.width(72.dp)
                )
                Box {
                    OutlinedButton(
                        onClick = { if (enabled) tickModeExpanded = true },
                        enabled = enabled
                    ) {
                        Text(tickMode.displayName)
                    }
                    DropdownMenu(
                        expanded = tickModeExpanded,
                        onDismissRequest = { tickModeExpanded = false }
                    ) {
                        TickStrategy.all.forEach { strategy ->
                            DropdownMenuItem(
                                text = { Text(strategy.displayName) },
                                onClick = {
                                    onTickModeChange(strategy)
                                    tickModeExpanded = false
                                }
                            )
                        }
                    }
                }
                Spacer(Modifier.width(100.dp))
            }

            // Marker fields — Start / Data Start / Data End in one row
            var startText by remember(startMarkerUs) { mutableStateOf(startMarkerUs?.toString() ?: "") }
            var dataStartText by remember(dataStartUs) { mutableStateOf(dataStartUs?.toString() ?: "") }
            var dataEndText by remember(dataEndTickCount) { mutableStateOf(dataEndTickCount?.toString() ?: "") }

            @Composable
            fun MarkerField(label: String, value: String, suffix: String, onChange: (String) -> Unit, onCommit: (String) -> Unit) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(label, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(
                        value = value,
                        onValueChange = onChange,
                        enabled = enabled,
                        singleLine = true,
                        suffix = { Text(suffix) },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = { onCommit(value) }),
                        modifier = Modifier.fillMaxWidth().onFocusChanged { if (!it.isFocused) onCommit(value) },
                        textStyle = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                MarkerField("Start", startText, "µs", { startText = it }, onStartMarkerTextChanged)
                MarkerField("Data start", dataStartText, "µs", { dataStartText = it }, onDataStartTextChanged)
                MarkerField("Data end", dataEndText, "ticks", { dataEndText = it }, onDataEndTextChanged)
            }

            // Bit decode patterns
            var oneText  by remember(onePattern)  { mutableStateOf(onePattern.toString()) }
            var zeroText by remember(zeroPattern) { mutableStateOf(zeroPattern.toString()) }

            @Composable
            fun PatternField(label: String, value: String, onChange: (String) -> Unit, onCommit: (String) -> Unit) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(label, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(
                        value = value,
                        onValueChange = onChange,
                        enabled = enabled,
                        singleLine = true,
                        suffix = { Text("H,L ticks") },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { onCommit(value) }),
                        modifier = Modifier.fillMaxWidth().onFocusChanged { if (!it.isFocused) onCommit(value) },
                        textStyle = MaterialTheme.typography.bodySmall,
                        isError = BitDecodePattern.parse(value) == null
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "Bit decode",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.width(72.dp).align(Alignment.CenterVertically)
                )
                PatternField("1 bit", oneText,  { oneText = it },  { BitDecodePattern.parse(it)?.let(onOnePatternChange) })
                PatternField("0 bit", zeroText, { zeroText = it }, { BitDecodePattern.parse(it)?.let(onZeroPatternChange) })
                Spacer(Modifier.weight(1f))
            }
        }
    }
}

private fun formatViewport(us: Long): String = when {
    us >= 1_000_000 -> "%.2f s".format(us / 1_000_000.0)
    us >= 1_000     -> "%.1f ms".format(us / 1_000.0)
    else            -> "$us µs"
}
