package com.example.radiosignalanalyzer

import kotlin.math.abs

/**
 * A single tick mark to draw on the x-axis.
 * [posUs] — absolute position in microseconds.
 * [label] — text to draw below the tick, or null for a minor tick (no label).
 */
data class TickMark(val posUs: Long, val label: String?)

/**
 * Strategy for computing which tick marks to display on the signal graph.
 *
 * @param displayName  Human-readable name shown in the UI dropdown.
 */
sealed class TickStrategy(val displayName: String) {

    /**
     * Compute the list of tick marks to render.
     *
     * @param viewOffsetUs   Left-edge of the visible viewport in µs.
     * @param visibleEndUs   Right-edge of the visible viewport in µs.
     * @param tickIntervalUs Configured tick spacing (used by strategies that honour it).
     * @param rawData        Raw signal data from the .sub file.
     * @param originUs       Reference origin for relative labels (typically the start marker).
     */
    abstract fun computeTicks(
        viewOffsetUs: Long,
        visibleEndUs: Long,
        tickIntervalUs: Int,
        rawData: IntArray,
        originUs: Long,
    ): List<TickMark>

    // ── Static ────────────────────────────────────────────────────────────────
    /** Evenly-spaced ticks aligned to the origin, one every tick interval µs. Every 4th tick carries a label. */
    object Static : TickStrategy("Static") {
        override fun computeTicks(
            viewOffsetUs: Long,
            visibleEndUs: Long,
            tickIntervalUs: Int,
            rawData: IntArray,
            originUs: Long,
        ): List<TickMark> {
            if (tickIntervalUs <= 0) return emptyList()
            val interval = tickIntervalUs.toLong()
            val labelEvery = 4 * interval
            val relStart = viewOffsetUs - originUs
            var tickRel = (Math.floorDiv(relStart, interval)) * interval
            val ticks = mutableListOf<TickMark>()
            while (originUs + tickRel <= visibleEndUs) {
                val posUs = originUs + tickRel
                ticks += TickMark(
                    posUs = posUs,
                    label = if (tickRel % labelEvery == 0L) "$tickRel" else null
                )
                tickRel += interval
            }
            return ticks
        }
    }

    // ── Dynamic ───────────────────────────────────────────────────────────────
    /**
     * Places a labeled tick at every signal transition (state change), working both
     * forward and backward from the origin. Between transitions, unlabeled ticks are
     * inserted at the configured tick interval. When a transition falls close to a
     * regular grid slot, that grid slot is suppressed to avoid crowding.
     * Counting always restarts from the transition, so drift never accumulates.
     */
    object Dynamic : TickStrategy("Dynamic") {
        override fun computeTicks(
            viewOffsetUs: Long,
            visibleEndUs: Long,
            tickIntervalUs: Int,
            rawData: IntArray,
            originUs: Long,
        ): List<TickMark> {
            val interval = tickIntervalUs.toLong()
            if (interval <= 0) return emptyList()

            val ticks = mutableListOf<TickMark>()

            fun emitTick(posUs: Long, labeled: Boolean) {
                if (posUs in viewOffsetUs..visibleEndUs) {
                    ticks += TickMark(posUs, if (labeled) "${posUs - originUs}" else null)
                }
            }

            // Collect all transition boundaries once
            val transitions = buildList {
                var acc = 0L
                for (sample in rawData) {
                    acc += abs(sample).toLong()
                    add(acc)
                }
            }

            // ── Forward pass: originUs → end ──────────────────────────────────
            var phase = originUs
            for (T in transitions) {
                if (T <= phase) continue
                if (phase > visibleEndUs) break

                val k = Math.floorDiv(T - phase, interval)
                val prevSlot = phase + k * interval
                val nextSlot = prevSlot + interval

                var t = phase + interval
                while (t < prevSlot) { emitTick(t, labeled = false); t += interval }

                if (prevSlot > phase && T - prevSlot <= nextSlot - T) {
                    emitTick(T, labeled = true)
                } else {
                    if (prevSlot > phase) emitTick(prevSlot, labeled = false)
                    emitTick(T, labeled = true)
                }
                phase = T
            }
            // Tail ticks after last transition
            var t = phase + interval
            while (t <= visibleEndUs) { emitTick(t, labeled = false); t += interval }

            // ── Backward pass: originUs → start ───────────────────────────────
            var backPhase = originUs
            for (i in transitions.indices.reversed()) {
                val T = transitions[i]
                if (T >= backPhase) continue
                if (backPhase < viewOffsetUs) break

                val dist = backPhase - T
                val k = Math.floorDiv(dist, interval)
                // rightSlot >= T (the scheduled tick closer to backPhase)
                val rightSlot = backPhase - k * interval
                // leftSlot < T (the scheduled tick further from backPhase)
                val leftSlot = rightSlot - interval

                var t2 = backPhase - interval
                while (t2 > rightSlot) { emitTick(t2, labeled = false); t2 -= interval }

                if (rightSlot < backPhase && rightSlot - T <= T - leftSlot) {
                    emitTick(T, labeled = true)
                } else {
                    if (rightSlot < backPhase) emitTick(rightSlot, labeled = false)
                    emitTick(T, labeled = true)
                }
                backPhase = T
            }
            // Head ticks before first transition
            var t3 = backPhase - interval
            while (t3 >= viewOffsetUs) { emitTick(t3, labeled = false); t3 -= interval }

            return ticks
        }
    }

    companion object {
        val all: List<TickStrategy> get() = listOf(Static, Dynamic)
        fun fromName(name: String): TickStrategy? = all.firstOrNull { it.displayName == name }
    }
}
