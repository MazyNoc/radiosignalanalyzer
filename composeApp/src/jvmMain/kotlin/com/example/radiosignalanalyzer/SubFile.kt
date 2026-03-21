package com.example.radiosignalanalyzer

data class SubFile(
    val filetype: String,
    val version: Int,
    val frequencyHz: Long,
    val preset: String,
    val protocol: String,
    val rawData: IntArray
) {
    val totalDurationUs: Long get() = rawData.sumOf { kotlin.math.abs(it).toLong() }
    val sampleCount: Int get() = rawData.size

    // IntArray doesn't participate in data class equals/hashCode — provide manually
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SubFile) return false
        return filetype == other.filetype &&
                version == other.version &&
                frequencyHz == other.frequencyHz &&
                preset == other.preset &&
                protocol == other.protocol &&
                rawData.contentEquals(other.rawData)
    }

    override fun hashCode(): Int {
        var result = filetype.hashCode()
        result = 31 * result + version
        result = 31 * result + frequencyHz.hashCode()
        result = 31 * result + preset.hashCode()
        result = 31 * result + protocol.hashCode()
        result = 31 * result + rawData.contentHashCode()
        return result
    }
}
