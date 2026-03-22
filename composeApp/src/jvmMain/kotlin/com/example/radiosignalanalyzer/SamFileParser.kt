package com.example.radiosignalanalyzer

import java.io.File

data class SamData(
    val zoom: Float? = null,
    val viewoffset: Long? = null,
    val ticksize: Int? = null,
    val tickmode: String? = null,
    val start: Long? = null,
    val datastart: Long? = null,
    val dataend: Int? = null,
    val onepattern: String? = null,
    val zeropattern: String? = null,
    val showbits: Boolean? = null,
)

object SamFileParser {

    fun read(file: File): SamData {
        val map = file.readLines()
            .mapNotNull { line ->
                val idx = line.indexOf(": ")
                if (idx < 0) null else line.substring(0, idx) to line.substring(idx + 2)
            }
            .toMap()
        return SamData(
            zoom        = map["zoom"]?.toFloatOrNull(),
            viewoffset  = map["viewoffset"]?.toLongOrNull(),
            ticksize    = map["ticksize"]?.toIntOrNull(),
            tickmode    = map["tickmode"],
            start       = map["start"]?.toLongOrNull(),
            datastart   = map["datastart"]?.toLongOrNull(),
            dataend     = map["dataend"]?.toIntOrNull(),
            onepattern  = map["onepattern"],
            zeropattern = map["zeropattern"],
            showbits    = map["showbits"]?.toBooleanStrictOrNull(),
        )
    }

    fun write(
        file: File,
        zoom: Float,
        viewoffset: Long,
        ticksize: Int,
        tickmode: String,
        start: Long?,
        datastart: Long?,
        dataend: Int?,
        onepattern: String,
        zeropattern: String,
        showbits: Boolean,
    ) {
        val sb = StringBuilder()
        sb.appendLine("zoom: $zoom")
        sb.appendLine("viewoffset: $viewoffset")
        sb.appendLine("ticksize: $ticksize")
        sb.appendLine("tickmode: $tickmode")
        if (start     != null) sb.appendLine("start: $start")
        if (datastart != null) sb.appendLine("datastart: $datastart")
        if (dataend   != null) sb.appendLine("dataend: $dataend")
        sb.appendLine("onepattern: $onepattern")
        sb.appendLine("zeropattern: $zeropattern")
        sb.appendLine("showbits: $showbits")
        file.writeText(sb.toString())
    }
}
