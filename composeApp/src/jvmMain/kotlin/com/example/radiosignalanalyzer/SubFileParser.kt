package com.example.radiosignalanalyzer

import java.io.File

object SubFileParser {

    sealed class Result {
        data class Success(val file: SubFile) : Result()
        data class Error(val message: String) : Result()
    }

    fun parse(file: File): Result = try {
        parseLines(file.readLines())
    } catch (e: Exception) {
        Result.Error("Failed to read file: ${e.message}")
    }

    fun parse(path: String): Result = parse(File(path))

    private fun parseLines(lines: List<String>): Result {
        var filetype: String? = null
        var version: Int? = null
        var frequencyHz: Long? = null
        var preset: String? = null
        var protocol: String? = null
        val rawValues = mutableListOf<Int>()

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue

            val colonIdx = trimmed.indexOf(':')
            if (colonIdx < 0) continue

            val key = trimmed.substring(0, colonIdx).trim()
            val value = trimmed.substring(colonIdx + 1).trim()

            when (key) {
                "Filetype" -> filetype = value
                "Version" -> version = value.toIntOrNull()
                    ?: return Result.Error("Invalid Version: $value")
                "Frequency" -> frequencyHz = value.toLongOrNull()
                    ?: return Result.Error("Invalid Frequency: $value")
                "Preset" -> preset = value
                "Protocol" -> protocol = value
                "RAW_Data" -> {
                    val parsed = parseRawDataLine(value)
                        ?: return Result.Error("Invalid RAW_Data values in: $value")
                    rawValues.addAll(parsed)
                }
            }
        }

        if (filetype == null) return Result.Error("Missing field: Filetype")
        if (version == null) return Result.Error("Missing field: Version")
        if (frequencyHz == null) return Result.Error("Missing field: Frequency")
        if (preset == null) return Result.Error("Missing field: Preset")
        if (protocol == null) return Result.Error("Missing field: Protocol")
        if (rawValues.isEmpty()) return Result.Error("No RAW_Data found")

        return Result.Success(
            SubFile(
                filetype = filetype,
                version = version,
                frequencyHz = frequencyHz,
                preset = preset,
                protocol = protocol,
                rawData = rawValues.toIntArray()
            )
        )
    }

    private fun parseRawDataLine(value: String): List<Int>? {
        if (value.isBlank()) return emptyList()
        val parts = value.trim().split(Regex("\\s+"))
        val result = mutableListOf<Int>()
        for (part in parts) {
            val num = part.toIntOrNull() ?: return null
            if (num == 0) return null  // zero duration is invalid
            result.add(num)
        }
        return result
    }
}
