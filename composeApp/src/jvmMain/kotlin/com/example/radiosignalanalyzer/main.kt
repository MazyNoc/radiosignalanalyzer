package com.example.radiosignalanalyzer

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyShortcut
import androidx.compose.ui.window.*
import java.awt.Desktop
import java.awt.FileDialog
import java.io.File

fun main() = application {
    val viewModel = remember { MainViewModel() }
    val isMac = System.getProperty("os.name").lowercase().contains("mac")
    var showAbout by remember { mutableStateOf(false) }

    if (isMac && Desktop.isDesktopSupported()) {
        val desktop = Desktop.getDesktop()
        if (desktop.isSupported(Desktop.Action.APP_ABOUT)) {
            SideEffect {
                desktop.setAboutHandler { showAbout = true }
            }
        }
    }

    fun openFile() {
        val dialog = FileDialog(null as java.awt.Frame?, "Open Flipper SubGhz file", FileDialog.LOAD)
        dialog.setFilenameFilter { _, name -> name.endsWith(".sub") || name.endsWith(".sam") }
        dialog.isVisible = true
        val dir = dialog.directory
        val name = dialog.file
        if (dir != null && name != null) viewModel.loadAny(File(dir, name))
    }

    if (showAbout) {
        val isDark = loadDarkModePref()
        DialogWindow(
            onCloseRequest = { showAbout = false },
            title = "About Radio Signal Analyzer",
            state = rememberDialogState(size = DpSize(360.dp, 220.dp)),
            resizable = false,
        ) {
            MaterialTheme(colorScheme = if (isDark) darkColorScheme() else lightColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            "Radio Signal Analyzer",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text("Version 1.0.0", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Flipper Zero SubGhz RAW signal analyzer",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(20.dp))
                        Text(
                            "© 2025",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    Window(
        onCloseRequest = ::exitApplication,
        title = "Radio Signal Analyzer",
    ) {
        MenuBar {
            Menu("File") {
                Item(
                    "Open…",
                    shortcut = if (isMac) KeyShortcut(Key.O, meta = true)
                               else KeyShortcut(Key.O, ctrl = true),
                    onClick = ::openFile
                )
            }
        }
        App(viewModel = viewModel, onOpenFile = ::openFile, onLoadFile = { viewModel.loadAny(it) })
    }
}
