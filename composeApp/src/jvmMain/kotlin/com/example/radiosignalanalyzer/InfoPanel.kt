package com.example.radiosignalanalyzer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

@Composable
fun InfoPanel(
    subFile: SubFile,
    markerError: String? = null,
    bitPattern: String = "",
    hexValue: String = "",
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        tonalElevation = 2.dp,
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                InfoChip(label = "Frequency", value = formatFrequency(subFile.frequencyHz))
                InfoChip(label = "Protocol", value = subFile.protocol)
                InfoChip(label = "Preset", value = subFile.preset, maxWidth = 260.dp)
                InfoChip(label = "Samples", value = subFile.sampleCount.toString())
                InfoChip(label = "Duration", value = formatDuration(subFile.totalDurationUs))
            }
            if (markerError != null) {
                Text(
                    text = "⚠ $markerError",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
            // Bit pattern + hex row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Bit pattern", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (bitPattern.isNotEmpty()) {
                            Text(
                                "(${bitPattern.length} bits)", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    SelectionContainer {
                        Text(
                            text = if (bitPattern.isEmpty()) "—"
                            else bitPattern.chunked(4).joinToString(" "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            softWrap = true,
                            maxLines = 2
                        )
                    }
                }
                val scope = rememberCoroutineScope()
                var copied by remember { mutableStateOf(false) }
                Column(
                    modifier = Modifier
                        .widthIn(min = 80.dp, max = 300.dp)
                        .then(if (hexValue.isNotEmpty()) Modifier
                            .pointerHoverIcon(PointerIcon.Hand)
                            .clickable {
                                Toolkit.getDefaultToolkit().systemClipboard
                                    .setContents(StringSelection(hexValue), null)
                                scope.launch {
                                    copied = true
                                    delay(1500)
                                    copied = false
                                }
                            }
                        else Modifier)
                ) {
                    Text(
                        text = if (copied) "Hex ✓ copied" else "Hex",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (copied) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = hexValue.ifEmpty { "—" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        softWrap = true,
                        maxLines = 2
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoChip(label: String, value: String, maxWidth: androidx.compose.ui.unit.Dp = 200.dp) {
    Column(horizontalAlignment = Alignment.Start) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.widthIn(max = maxWidth),
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
    }
}

private fun formatFrequency(hz: Long): String {
    return when {
        hz >= 1_000_000 -> "%.3f MHz".format(hz / 1_000_000.0)
        hz >= 1_000 -> "%.3f kHz".format(hz / 1_000.0)
        else -> "$hz Hz"
    }
}

private fun formatDuration(us: Long): String {
    return when {
        us >= 1_000_000 -> "%.2f s".format(us / 1_000_000.0)
        us >= 1_000 -> "%.2f ms".format(us / 1_000.0)
        else -> "$us µs"
    }
}
