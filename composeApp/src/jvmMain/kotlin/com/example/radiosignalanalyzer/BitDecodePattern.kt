package com.example.radiosignalanalyzer

data class BitDecodePattern(val highTicks: Int, val lowTicks: Int) {
    companion object {
        val DEFAULT_ONE  = BitDecodePattern(3, 1)
        val DEFAULT_ZERO = BitDecodePattern(1, 3)

        /** Parses "H,L" — both values must be non-negative integers, highTicks >= 1. */
        fun parse(s: String): BitDecodePattern? {
            val parts = s.split(",")
            if (parts.size != 2) return null
            val h = parts[0].trim().toIntOrNull() ?: return null
            val l = parts[1].trim().toIntOrNull() ?: return null
            if (h < 1 || l < 0) return null
            return BitDecodePattern(h, l)
        }
    }

    override fun toString() = "$highTicks,$lowTicks"
}
