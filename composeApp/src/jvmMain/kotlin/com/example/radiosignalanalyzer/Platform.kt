package com.example.radiosignalanalyzer

import java.util.prefs.Preferences

class JVMPlatform {
    val name: String = "Java ${System.getProperty("java.version")}"
}

fun getPlatform() = JVMPlatform()

private val prefs: Preferences = Preferences.userRoot().node("com.example.radiosignalanalyzer")

fun loadDarkModePref(): Boolean = prefs.getBoolean("darkMode", false)
fun saveDarkModePref(isDark: Boolean) { prefs.putBoolean("darkMode", isDark) }
