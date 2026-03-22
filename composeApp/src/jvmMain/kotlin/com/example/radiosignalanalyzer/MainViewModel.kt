package com.example.radiosignalanalyzer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.exp
import kotlin.math.ln

data class BitRegion(val startUs: Long, val endUs: Long, val bit: Char)

class MainViewModel : ViewModel() {

    private val _subFile = MutableStateFlow<SubFile?>(null)
    val subFile: StateFlow<SubFile?> = _subFile.asStateFlow()

    private val _sourceFile = MutableStateFlow<File?>(null)

    private val _parseError = MutableStateFlow<String?>(null)
    val parseError: StateFlow<String?> = _parseError.asStateFlow()

    // Show-bits overlay toggle
    private val _showBits = MutableStateFlow(false)
    val showBits: StateFlow<Boolean> = _showBits.asStateFlow()
    fun setShowBits(v: Boolean) { _showBits.value = v }

    // Zoom expressed as a 0..1 slider; 0 = fit to window, 1 = 100 µs visible.
    private val _zoomSlider = MutableStateFlow(0f)
    val zoomSlider: StateFlow<Float> = _zoomSlider.asStateFlow()

    // Canvas-size-independent zoom: fraction of canvas width that one microsecond occupies.
    // The canvas multiplies this by its own width to get pixels — so resize is free.
    //   viewFractionPerUs = 1 / viewportUs
    val viewFractionPerUs: StateFlow<Float> = combine(_zoomSlider, _subFile) { slider, file ->
        val totalUs = file?.totalDurationUs?.toFloat() ?: return@combine 0f
        if (totalUs <= 0f) return@combine 0f
        1f / sliderToViewportUs(slider, totalUs)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, 0f)

    // Pan offset in microseconds (left edge of viewport)
    private val _viewOffsetUs = MutableStateFlow(0L)
    val viewOffsetUs: StateFlow<Long> = _viewOffsetUs.asStateFlow()

    // Tick interval in µs
    private val _tickIntervalUs = MutableStateFlow(100)
    val tickIntervalUs: StateFlow<Int> = _tickIntervalUs.asStateFlow()

    // Tick rendering strategy
    private val _tickMode = MutableStateFlow<TickStrategy>(TickStrategy.Static)
    val tickMode: StateFlow<TickStrategy> = _tickMode.asStateFlow()

    // Start marker offset in µs — null when not set
    private val _startMarkerUs = MutableStateFlow<Long?>(null)
    val startMarkerUs: StateFlow<Long?> = _startMarkerUs.asStateFlow()

    // Header end marker in µs — null when not set
    private val _dataStartUs = MutableStateFlow<Long?>(null)
    val dataStartUs: StateFlow<Long?> = _dataStartUs.asStateFlow()

    // Data end stored as tick count from dataStart — follows start/interval/mode changes automatically
    private val _dataEndTickCount = MutableStateFlow<Int?>(null)
    val dataEndTickCount: StateFlow<Int?> = _dataEndTickCount.asStateFlow()

    // dataEndUs derived: recomputes when tick count, dataStart, tickInterval, mode, or file changes
    val dataEndUs: StateFlow<Long?> = combine(
        combine(_dataEndTickCount, _dataStartUs, _tickIntervalUs) { count, start, interval -> Triple(count, start, interval) },
        combine(_tickMode, _subFile) { mode, file -> Pair(mode, file) }
    ) { (countRaw, startRaw, intervalInt), (mode, file) ->
        val count = countRaw ?: return@combine null
        val fromUs = startRaw ?: return@combine null
        if (file == null || intervalInt <= 0) return@combine null
        when (mode) {
            TickStrategy.Static -> (fromUs + count * intervalInt.toLong()).coerceIn(0L, file.totalDurationUs)
            TickStrategy.Dynamic -> {
                val positions = forwardDynamicTicks(fromUs, file.rawData, intervalInt.toLong())
                positions.getOrElse(count) { positions.lastOrNull() ?: fromUs }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // Bit decode patterns stored as raw user input strings (e.g. "3,1" or "1110")
    private val _onePattern  = MutableStateFlow("3,1")
    private val _zeroPattern = MutableStateFlow("1,3")
    val onePattern:  StateFlow<String> = _onePattern.asStateFlow()
    val zeroPattern: StateFlow<String> = _zeroPattern.asStateFlow()

    fun setOnePattern(s: String)  { _onePattern.value = s }
    fun setZeroPattern(s: String) { _zeroPattern.value = s }

    // Flat tick string: one char per tick ('1'=high, '0'=low) between dataStart and dataEnd.
    // Recomputes whenever file, markers, tick interval, or tick mode change.
    val rawTickString: StateFlow<String> = combine(
        combine(_subFile, _dataStartUs, dataEndUs) { f, s, e -> Triple(f, s, e) },
        _tickIntervalUs
    ) { (fileRaw, startRaw, endRaw), tickInterval ->
        val file  = fileRaw  ?: return@combine ""
        val start = startRaw ?: return@combine ""
        val end   = endRaw   ?: return@combine ""
        if (start >= end || tickInterval <= 0) return@combine ""
        buildRawTickString(file.rawData, start, end, tickInterval.toLong())
    }.stateIn(viewModelScope, SharingStarted.Eagerly, "")

    // Bit pattern: scan rawTickString with onePattern / zeroPattern tick strings.
    // Match at current position → emit '1' or '0' and advance by pattern length.
    // No match → emit '?' and advance by 1.
    val bitPattern: StateFlow<String> = combine(rawTickString, _onePattern, _zeroPattern) { raw, oneStr, zeroStr ->
        if (raw.isEmpty()) return@combine ""
        val oneTick  = patternToTickString(oneStr)  ?: return@combine ""
        val zeroTick = patternToTickString(zeroStr) ?: return@combine ""
        val sb = StringBuilder()
        var i = 0
        while (i < raw.length) {
            when {
                raw.startsWith(oneTick,  i) -> { sb.append('1'); i += oneTick.length }
                raw.startsWith(zeroTick, i) -> { sb.append('0'); i += zeroTick.length }
                else -> { sb.append('?'); i++ }
            }
        }
        sb.toString()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, "")

    // Time-annotated regions for each decoded bit — used to draw colored overlays in the graph.
    val bitRegions: StateFlow<List<BitRegion>> = combine(
        combine(rawTickString, _onePattern, _zeroPattern) { r, o, z -> Triple(r, o, z) },
        combine(_dataStartUs, _tickIntervalUs) { s, t -> Pair(s, t) }
    ) { (raw, oneStr, zeroStr), (dataStart, tickInterval) ->
        if (raw.isEmpty() || dataStart == null || tickInterval <= 0) return@combine emptyList()
        val oneTick  = patternToTickString(oneStr)  ?: return@combine emptyList()
        val zeroTick = patternToTickString(zeroStr) ?: return@combine emptyList()
        val regions = mutableListOf<BitRegion>()
        var i = 0
        while (i < raw.length) {
            when {
                raw.startsWith(oneTick,  i) -> {
                    regions.add(BitRegion(dataStart + i * tickInterval.toLong(), dataStart + (i + oneTick.length) * tickInterval.toLong(), '1'))
                    i += oneTick.length
                }
                raw.startsWith(zeroTick, i) -> {
                    regions.add(BitRegion(dataStart + i * tickInterval.toLong(), dataStart + (i + zeroTick.length) * tickInterval.toLong(), '0'))
                    i += zeroTick.length
                }
                else -> i++
            }
        }
        regions
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val hexValue: StateFlow<String> = bitPattern.map { bits ->
        val cleanBits = bits.filter { it == '0' || it == '1' }
        if (cleanBits.isEmpty()) return@map ""
        val padded = cleanBits.padStart((cleanBits.length + 3) / 4 * 4, '0')
        padded.chunked(4).joinToString("") { Integer.parseInt(it, 2).toString(16).uppercase() }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, "")

    // init runs after all properties above are initialized
    init { }

    /** Accepts either a .sub or a .sam file. For .sam, resolves the sibling .sub first. */
    fun loadAny(file: File) {
        if (file.extension == "sam") {
            val sub = file.resolveSibling(file.nameWithoutExtension + ".sub")
            if (sub.exists()) loadFile(sub)
            else _parseError.value = "No matching .sub file found for ${file.name}"
        } else {
            loadFile(file)
        }
    }

    fun loadFile(file: File) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { SubFileParser.parse(file) }
            when (result) {
                is SubFileParser.Result.Success -> {
                    _sourceFile.value = file
                    applyDefaults(result.file)
                    _parseError.value = null
                    _viewOffsetUs.value = 0L
                    // Auto-load sidecar if present
                    val sam = file.resolveSibling(file.nameWithoutExtension + ".sam")
                    withContext(Dispatchers.IO) { if (sam.exists()) loadSam(sam) }
                }
                is SubFileParser.Result.Error -> {
                    _parseError.value = result.message
                }
            }
        }
    }

    fun saveSam() {
        val src = _sourceFile.value ?: return
        val samFile = src.resolveSibling(src.nameWithoutExtension + ".sam")
        SamFileParser.write(
            file        = samFile,
            zoom        = _zoomSlider.value,
            viewoffset  = _viewOffsetUs.value,
            ticksize    = _tickIntervalUs.value,
            tickmode    = _tickMode.value.displayName,
            start       = _startMarkerUs.value,
            datastart   = _dataStartUs.value,
            dataend     = _dataEndTickCount.value,
            onepattern  = _onePattern.value,
            zeropattern = _zeroPattern.value,
            showbits    = _showBits.value,
        )
    }

    private fun loadSam(sam: File) {
        val d = SamFileParser.read(sam)
        d.zoom?.let        { _zoomSlider.value      = it }
        d.viewoffset?.let  { _viewOffsetUs.value    = it }
        d.ticksize?.let    { _tickIntervalUs.value  = it }
        d.tickmode?.let    { TickStrategy.fromName(it)?.let { s -> _tickMode.value = s } }
        d.start?.let       { _startMarkerUs.value   = it }
        d.datastart?.let   { _dataStartUs.value     = it }
        d.dataend?.let     { _dataEndTickCount.value = it }
        d.onepattern?.let  { _onePattern.value      = it }
        d.zeropattern?.let { _zeroPattern.value     = it }
        d.showbits?.let    { _showBits.value        = it }
    }

    fun setZoomSlider(value: Float) {
        val newSlider = value.coerceIn(0f, 1f)
        val totalUs = _subFile.value?.totalDurationUs?.toFloat()
        if (totalUs == null || totalUs <= 0f) {
            _zoomSlider.value = newSlider
            return
        }

        // Capture center of current viewport before zoom changes
        val oldFrac = viewFractionPerUs.value
        val oldViewportUs = if (oldFrac > 0f) (1f / oldFrac).toLong() else 0L
        val centerUs = _viewOffsetUs.value + oldViewportUs / 2L

        _zoomSlider.value = newSlider

        // Compute new viewport duration directly (StateFlow may lag)
        val newViewportUs = sliderToViewportUs(newSlider, totalUs).toLong()

        val maxOffset = (totalUs.toLong() - newViewportUs).coerceAtLeast(0L)
        if (_viewOffsetUs.value == 0L) {
            // Beginning of file is pinned — don't shift the view
            _viewOffsetUs.value = 0L
        } else {
            // Reposition so the same center µs stays centered
            _viewOffsetUs.value = (centerUs - newViewportUs / 2L).coerceIn(0L, maxOffset)
        }
    }

    fun pan(deltaUs: Long) {
        _viewOffsetUs.value = (_viewOffsetUs.value + deltaUs).coerceIn(0L, maxViewOffsetUs())
    }

    fun setTickInterval(us: Int) {
        _tickIntervalUs.value = us.coerceIn(30, 2000)
    }

    fun setTickMode(mode: TickStrategy) {
        _tickMode.value = mode
    }

    fun setStartMarker(us: Long) {
        _startMarkerUs.value = snapToNearestTransition(us)
    }

    fun setStartMarkerFromText(text: String) {
        text.toLongOrNull()?.let { _startMarkerUs.value = snapToNearestTransition(it) }
    }

    fun setDataStart(us: Long) { _dataStartUs.value = snapToNearestTransition(us) }
    fun setDataStartFromText(text: String) { text.toLongOrNull()?.let { _dataStartUs.value = snapToNearestTransition(it) } }

    fun setDataEnd(us: Long) {
        val fromUs = _dataStartUs.value ?: return
        val file = _subFile.value ?: return
        val interval = _tickIntervalUs.value.toLong()
        if (interval <= 0) return
        _dataEndTickCount.value = when (_tickMode.value) {
            TickStrategy.Static -> ((us - fromUs + interval / 2) / interval).toInt().coerceAtLeast(0)
            TickStrategy.Dynamic -> {
                val positions = forwardDynamicTicks(fromUs, file.rawData, interval)
                positions.indices.minByOrNull { kotlin.math.abs(positions[it] - us) } ?: 0
            }
        }
    }
    fun setDataEndFromText(text: String) { text.toIntOrNull()?.let { _dataEndTickCount.value = it.coerceAtLeast(0) } }

    /** Computes tick positions going forward from [fromUs], using the dynamic snapping algorithm. */
    private fun forwardDynamicTicks(fromUs: Long, rawData: IntArray, interval: Long): List<Long> {
        val ticks = mutableListOf(fromUs)
        var phase = fromUs
        var accumUs = 0L
        for (sample in rawData) {
            accumUs += kotlin.math.abs(sample).toLong()
            val T = accumUs
            if (T <= phase) continue
            val k = Math.floorDiv(T - phase, interval)
            val prevSlot = phase + k * interval
            val nextSlot = prevSlot + interval
            var t = phase + interval
            while (t < prevSlot) { ticks.add(t); t += interval }
            if (prevSlot > phase && T - prevSlot <= nextSlot - T) {
                ticks.add(T)
            } else {
                if (prevSlot > phase) ticks.add(prevSlot)
                ticks.add(T)
            }
            phase = T
        }
        return ticks
    }

    /** Snaps [us] to the nearest signal transition boundary in rawData. */
    private fun snapToNearestTransition(us: Long): Long {
        val raw = _subFile.value?.rawData ?: return us
        var accumUs = 0L
        var bestUs = 0L
        var bestDist = Long.MAX_VALUE
        for (sample in raw) {
            val dist = kotlin.math.abs(accumUs - us)
            if (dist < bestDist) { bestDist = dist; bestUs = accumUs }
            accumUs += kotlin.math.abs(sample).toLong()
        }
        // Also check the final boundary (end of last sample)
        val dist = kotlin.math.abs(accumUs - us)
        if (dist < bestDist) bestUs = accumUs
        return bestUs
    }

    fun clearError() {
        _parseError.value = null
    }

    // Viewport duration is canvas-size-independent: 1 / viewFractionPerUs
    fun viewportDurationUs(): Long {
        val frac = viewFractionPerUs.value
        return if (frac > 0f) (1f / frac).toLong() else 0L
    }

    /**
     * Builds a flat binary string from [rawData] in [[dataStart], [dataEnd]).
     * Each character represents one tick interval: '1' = high, '0' = low.
     * The dynamic tick algorithm is used to count how many ticks each signal segment spans.
     */
    private fun buildRawTickString(rawData: IntArray, dataStart: Long, dataEnd: Long, interval: Long): String {
        val sb = StringBuilder()
        var accumUs = 0L
        var phase = dataStart
        for (sample in rawData) {
            val segStart = accumUs
            accumUs += kotlin.math.abs(sample).toLong()
            val segEnd = accumUs
            if (segEnd <= dataStart) continue
            if (segStart >= dataEnd) break
            val T = minOf(segEnd, dataEnd)
            val k = Math.floorDiv(T - phase, interval)
            val prevSlot = phase + k * interval
            val numTicks = (if (prevSlot > phase && T - prevSlot <= prevSlot + interval - T) k else k + 1)
                .toInt().coerceAtLeast(0)
            val ch = if (sample > 0) '1' else '0'
            repeat(numTicks) { sb.append(ch) }
            phase = T
            if (segEnd >= dataEnd) break
        }
        return sb.toString()
    }

    private fun maxViewOffsetUs(): Long {
        val total = _subFile.value?.totalDurationUs ?: return 0L
        return (total - viewportDurationUs()).coerceAtLeast(0L)
    }

    private fun applyDefaults(file: SubFile) {
        val minDur = file.rawData.minOf { kotlin.math.abs(it) }
        _subFile.value = file
        _tickIntervalUs.value = minDur
        _zoomSlider.value = 0f
    }

    companion object {
        // Viewport width in µs shown at maximum zoom (slider = 1)
        const val MAX_ZOOM_VIEWPORT_US = 100f

        /**
         * Maps slider (0..1) to viewport duration in µs.
         *   slider = 0 → totalUs  (fit to window)
         *   slider = 1 → MAX_ZOOM_VIEWPORT_US (100 µs)
         * Logarithmic interpolation keeps the feel uniform across large file ranges.
         */
        fun sliderToViewportUs(slider: Float, totalUs: Float): Float {
            val safeTotal = totalUs.coerceAtLeast(MAX_ZOOM_VIEWPORT_US * 2f)
            return exp(
                ln(safeTotal) + slider.coerceIn(0f, 1f) * (ln(MAX_ZOOM_VIEWPORT_US) - ln(safeTotal))
            )
        }

    }
}
