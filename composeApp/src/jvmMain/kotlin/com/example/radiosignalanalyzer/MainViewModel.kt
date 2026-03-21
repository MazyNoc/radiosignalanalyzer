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

class MainViewModel : ViewModel() {

    private val _subFile = MutableStateFlow<SubFile?>(null)
    val subFile: StateFlow<SubFile?> = _subFile.asStateFlow()

    private val _parseError = MutableStateFlow<String?>(null)
    val parseError: StateFlow<String?> = _parseError.asStateFlow()

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

    // Bit decode patterns: what a "1" and "0" look like as (highTicks, lowTicks) pairs
    private val _onePattern  = MutableStateFlow(BitDecodePattern.DEFAULT_ONE)
    private val _zeroPattern = MutableStateFlow(BitDecodePattern.DEFAULT_ZERO)
    val onePattern:  StateFlow<BitDecodePattern> = _onePattern.asStateFlow()
    val zeroPattern: StateFlow<BitDecodePattern> = _zeroPattern.asStateFlow()

    fun setOnePattern(p: BitDecodePattern)  { _onePattern.value = p }
    fun setZeroPattern(p: BitDecodePattern) { _zeroPattern.value = p }

    // Bit pattern decoded from (high, low) tick pairs between dataStart and dataEnd.
    // Each consecutive (high-segment, low-segment) pair is matched against the one/zero patterns.
    // Unrecognised pairs produce '?'.
    val bitPattern: StateFlow<String> = combine(
        combine(_subFile, _dataStartUs, dataEndUs) { f, s, e -> Triple(f, s, e) },
        combine(_tickIntervalUs, _onePattern, _zeroPattern) { t, one, zero -> Triple(t, one, zero) }
    ) { (fileRaw, startRaw, endRaw), (tickInterval, onePattern, zeroPattern) ->
        val file  = fileRaw  ?: return@combine ""
        val start = startRaw ?: return@combine ""
        val end   = endRaw   ?: return@combine ""
        if (start >= end || tickInterval <= 0) return@combine ""
        decodeBits(file.rawData, start, end, tickInterval.toLong(), onePattern, zeroPattern)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, "")

    val hexValue: StateFlow<String> = bitPattern.map { bits ->
        val cleanBits = bits.filter { it == '0' || it == '1' }
        if (cleanBits.isEmpty()) return@map ""
        val padded = cleanBits.padStart((cleanBits.length + 3) / 4 * 4, '0')
        padded.chunked(4).joinToString("") { Integer.parseInt(it, 2).toString(16).uppercase() }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, "")

    // init runs after all properties above are initialized
    init {
        applyDefaults(EXAMPLE_FILE)
    }

    fun loadFile(file: File) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { SubFileParser.parse(file) }
            when (result) {
                is SubFileParser.Result.Success -> {
                    applyDefaults(result.file)
                    _parseError.value = null
                    _viewOffsetUs.value = 0L
                }
                is SubFileParser.Result.Error -> {
                    _parseError.value = result.message
                }
            }
        }
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

    fun setViewOffsetUs(us: Long) {
        _viewOffsetUs.value = us.coerceIn(0L, maxViewOffsetUs())
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
    fun setDataEndFromTickCount(count: Int) { _dataEndTickCount.value = count.coerceAtLeast(0) }

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
     * Converts rawData segments in [dataStart, dataEnd) into (isHigh, tickCount) entries,
     * then pairs consecutive (high, low) entries and matches each pair against [onePattern]
     * and [zeroPattern]. Unrecognised pairs produce '?'.
     */
    private fun decodeBits(
        rawData: IntArray,
        dataStart: Long,
        dataEnd: Long,
        interval: Long,
        onePattern: BitDecodePattern,
        zeroPattern: BitDecodePattern
    ): String {
        // Build list of (isHigh, tickCount) for segments in range
        val segments = mutableListOf<Pair<Boolean, Int>>()
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
            val nextSlot = prevSlot + interval
            val numTicks = (if (prevSlot > phase && T - prevSlot <= nextSlot - T) k else k + 1)
                .toInt().coerceAtLeast(0)
            if (numTicks > 0) segments.add((sample > 0) to numTicks)
            phase = T
            if (segEnd >= dataEnd) break
        }

        // Decode consecutive (high, low) pairs into bits
        val sb = StringBuilder()
        var i = 0
        while (i < segments.size && !segments[i].first) i++ // skip leading lows
        while (i < segments.size) {
            if (!segments[i].first) { i++; continue }
            val highTicks = segments[i].second
            val lowTicks  = if (i + 1 < segments.size && !segments[i + 1].first) segments[i + 1].second else 0
            when {
                highTicks == onePattern.highTicks  && lowTicks == onePattern.lowTicks  -> sb.append('1')
                highTicks == zeroPattern.highTicks && lowTicks == zeroPattern.lowTicks -> sb.append('0')
                else -> sb.append('?')
            }
            i += if (lowTicks > 0) 2 else 1
        }
        return sb.toString()
    }

    private fun maxViewOffsetUs(): Long {
        val total = _subFile.value?.totalDurationUs ?: return 0L
        return (total - viewportDurationUs()).coerceAtLeast(0L)
    }

    private fun applyDefaults(file: SubFile) {
        val minDur = file.rawData.minOf { kotlin.math.abs(it) }

        val paddedRaw = IntArray(file.rawData.size + 2).also { arr ->
            arr[0] = -minDur
            arr[1] = -minDur
            file.rawData.copyInto(arr, destinationOffset = 2)
        }

        _subFile.value = file.copy(rawData = paddedRaw)
        _startMarkerUs.value = 2L * minDur
        _tickIntervalUs.value = minDur
        // slider=0 → fit to window (entire signal fills canvas width)
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

        // Example data for development
        val EXAMPLE_FILE = SubFile(
            filetype = "Flipper SubGhz RAW File",
            version = 1,
            frequencyHz = 315_000_000L,
            preset = "FuriHalSubGhzPreset2FSKDev476Async",
            protocol = "RAW",
            rawData = intArrayOf(
                85, -58, 201, -350, 163, -58, 143, -518, 115, -56,
                371, -86, 171, -404, 421, -234, 129, -202, 113, -202,
                115, -86, 431, -214, 191, -72, 335, -58, 85
            )
        )
    }
}
