package com.example.radiosignalanalyzer

import androidx.compose.runtime.remember
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyShortcut
import androidx.compose.ui.window.MenuBar
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import java.awt.FileDialog
import java.io.File

fun main() = application {
    val viewModel = remember { MainViewModel() }
    val isMac = System.getProperty("os.name").lowercase().contains("mac")

    fun openFile() {
        val dialog = FileDialog(null as java.awt.Frame?, "Open Flipper SubGhz file", FileDialog.LOAD)
        dialog.setFilenameFilter { _, name -> name.endsWith(".sub") || name.endsWith(".sam") }
        dialog.isVisible = true
        val dir = dialog.directory
        val name = dialog.file
        if (dir != null && name != null) viewModel.loadAny(File(dir, name))
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
