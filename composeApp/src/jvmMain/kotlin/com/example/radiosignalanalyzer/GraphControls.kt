package com.example.radiosignalanalyzer

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
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
    onePattern: String,
    zeroPattern: String,
    onZoomChange: (Float) -> Unit,
    onTickChange: (Int) -> Unit,
    onTickModeChange: (TickStrategy) -> Unit,
    onStartMarkerTextChanged: (String) -> Unit,
    onDataStartTextChanged: (String) -> Unit,
    onDataEndTextChanged: (String) -> Unit,
    onOnePatternChange: (String) -> Unit,
    onZeroPatternChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val textColor = if (enabled) MaterialTheme.colorScheme.onSurface
    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)

    @Composable
    fun compactTextFieldColors() = OutlinedTextFieldDefaults.colors()

    @Composable
    fun CompactField(
        value: String,
        onValueChange: (String) -> Unit,
        modifier: Modifier = Modifier,
        suffix: String = "",
        isError: Boolean = false,
        keyboardType: KeyboardType = KeyboardType.Number,
        onCommit: (String) -> Unit = {}
    ) {
        val interactionSource = remember { MutableInteractionSource() }
        val colors = compactTextFieldColors()
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodySmall.copy(color = textColor),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onCommit(value) }),
            interactionSource = interactionSource,
            modifier = modifier.onFocusChanged { if (!it.isFocused) onCommit(value) },
            decorationBox = { innerTextField ->
                OutlinedTextFieldDefaults.DecorationBox(
                    value = value,
                    innerTextField = innerTextField,
                    enabled = enabled,
                    singleLine = true,
                    visualTransformation = VisualTransformation.None,
                    interactionSource = interactionSource,
                    isError = isError,
                    suffix = if (suffix.isNotEmpty()) ({ Text(suffix, style = MaterialTheme.typography.bodySmall) }) else null,
                    contentPadding = OutlinedTextFieldDefaults.contentPadding(top = 4.dp, bottom = 4.dp),
                    colors = colors,
                    container = {
                        OutlinedTextFieldDefaults.Container(enabled, isError, interactionSource, colors = colors)
                    }
                )
            }
        )
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        tonalElevation = 1.dp,
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
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "Zoom",
                    style = MaterialTheme.typography.labelMedium,
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
                )
            }

            // Tick interval slider + text input
            var tickText by remember(tickIntervalUs) { mutableStateOf(tickIntervalUs.toString()) }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "Ticks",
                    style = MaterialTheme.typography.labelMedium,
                )
                Slider(
                    value = tickIntervalUs.toFloat(),
                    onValueChange = { onTickChange(it.toInt()) },
                    valueRange = 30f..2000f,
                    enabled = enabled,
                    modifier = Modifier.weight(1f)
                )
                CompactField(
                    value = tickText,
                    onValueChange = { tickText = it },
                    suffix = "µs",
                    modifier = Modifier.widthIn(min = 72.dp),
                    onCommit = { tickText.toIntOrNull()?.let(onTickChange) }
                )
            }

            // Tick mode dropdown
            var tickModeExpanded by remember { mutableStateOf(false) }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "Tick mode",
                    style = MaterialTheme.typography.labelMedium,
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
            }

            // All text input fields in one row
            var startText by remember(startMarkerUs) { mutableStateOf(startMarkerUs?.toString() ?: "") }
            var dataStartText by remember(dataStartUs) { mutableStateOf(dataStartUs?.toString() ?: "") }
            var dataEndText by remember(dataEndTickCount) { mutableStateOf(dataEndTickCount?.toString() ?: "") }
            var oneText by remember(onePattern) { mutableStateOf(onePattern) }
            var zeroText by remember(zeroPattern) { mutableStateOf(zeroPattern) }

            @Composable
            fun InputField(
                label: String, value: String, suffix: String,
                isError: Boolean = false,
                keyboardType: KeyboardType = KeyboardType.Number,
                onChange: (String) -> Unit, onCommit: (String) -> Unit
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(label, style = MaterialTheme.typography.labelSmall)
                    CompactField(
                        value = value,
                        onValueChange = onChange,
                        suffix = suffix,
                        isError = isError,
                        keyboardType = keyboardType,
                        modifier = Modifier.fillMaxWidth(),
                        onCommit = onCommit
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                InputField(
                    "Start", startText, "µs",
                    onChange = { startText = it }, onCommit = onStartMarkerTextChanged
                )
                InputField(
                    "Data start", dataStartText, "µs",
                    onChange = { dataStartText = it }, onCommit = onDataStartTextChanged
                )
                InputField(
                    "Data end", dataEndText, "ticks",
                    onChange = { dataEndText = it }, onCommit = onDataEndTextChanged
                )
                InputField(
                    "1 bit", oneText, "H,L or 1110",
                    isError = patternToTickString(oneText) == null,
                    keyboardType = KeyboardType.Ascii,
                    onChange = { oneText = it },
                    onCommit = { if (patternToTickString(it) != null) onOnePatternChange(it) })
                InputField(
                    "0 bit", zeroText, "H,L or 1110",
                    isError = patternToTickString(zeroText) == null,
                    keyboardType = KeyboardType.Ascii,
                    onChange = { zeroText = it },
                    onCommit = { if (patternToTickString(it) != null) onZeroPatternChange(it) })
            }
        }
    }
}

private fun formatViewport(us: Long): String = when {
    us >= 1_000_000 -> "%.2f s".format(us / 1_000_000.0)
    us >= 1_000 -> "%.1f ms".format(us / 1_000.0)
    else -> "$us µs"
}
