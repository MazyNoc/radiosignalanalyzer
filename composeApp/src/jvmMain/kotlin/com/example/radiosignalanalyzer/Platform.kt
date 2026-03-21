package com.example.radiosignalanalyzer

class JVMPlatform {
    val name: String = "Java ${System.getProperty("java.version")}"
}

fun getPlatform() = JVMPlatform()

/**
 * Detects the OS dark-mode preference using AWT SystemColor, which JVM updates
 * automatically when the OS appearance changes on macOS and Windows.
 */
fun isSystemInDarkMode(): Boolean {
    val c = java.awt.SystemColor.window
    val luminance = (0.299 * c.red + 0.587 * c.green + 0.114 * c.blue) / 255.0
    return luminance < 0.5
}