package com.example.radiosignalanalyzer

/**
 * Converts a user-entered pattern string to a flat binary tick string, or null if invalid.
 *
 * Comma format  "N,M" → N ones followed by M zeros  (e.g. "3,1" → "1110")
 * Binary format "1110" → used as-is (each char is one tick polarity)
 */
fun patternToTickString(s: String): String? =
    if (',' in s) {
        val parts = s.split(",")
        if (parts.size != 2) return null
        val h = parts[0].trim().toIntOrNull() ?: return null
        val l = parts[1].trim().toIntOrNull() ?: return null
        if (h < 0 || l < 0 || (h == 0 && l == 0)) return null
        "1".repeat(h) + "0".repeat(l)
    } else {
        if (s.isEmpty() || s.any { it != '0' && it != '1' }) null else s
    }
