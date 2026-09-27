package com.poldivers.app.core.trends

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Local history of values the API only reports as snapshots (liberation %, defense %, Major
 * Order progress, DSS donations). The API has no "rate" or history endpoint, so -- like the
 * community trackers -- we remember what we saw and extrapolate from how much it moved in the
 * last minutes. History survives app restarts (a small JSON file) and is pruned to [KEEP_MS].
 */
class TrendStore(private val file: File?) {

    @Serializable
    private data class Sample(val t: Long, val v: Double)

    private val lock = Any()
    private val series: MutableMap<String, MutableList<Sample>> = load()

    fun record(key: String, value: Double, timeMs: Long = System.currentTimeMillis()) = synchronized(lock) {
        val list = series.getOrPut(key) { mutableListOf() }
        val last = list.lastOrNull()
        val beforeLast = list.getOrNull(list.lastIndex - 1)
        when {
            // Several screens load the same data; keep one sample per MIN_GAP_MS.
            last != null && timeMs - last.t < MIN_GAP_MS -> list[list.lastIndex] = Sample(last.t, value)
            // A flat stretch only needs its two end points -- keeps idle planets tiny on disk.
            last != null && beforeLast != null && last.v == value && beforeLast.v == value ->
                list[list.lastIndex] = Sample(timeMs, value)
            else -> list += Sample(timeMs, value)
        }
        list.removeAll { timeMs - it.t > KEEP_MS }
        while (list.size > MAX_SAMPLES) list.removeAt(0)
    }

    /**
     * Change per hour, least-squares over the samples of the last [windowMs]; null until we
     * have at least [MIN_SPAN_MS] of observations. Falls back to the whole kept history when the
     * recent window is too short (e.g. right after reopening the app).
     */
    fun ratePerHour(key: String, nowMs: Long = System.currentTimeMillis(), windowMs: Long = WINDOW_MS): Double? =
        synchronized(lock) {
            val all = series[key] ?: return null
            val recent = all.filter { nowMs - it.t <= windowMs }
            val samples = if (span(recent) >= MIN_SPAN_MS) recent else all
            if (samples.size < 2 || span(samples) < MIN_SPAN_MS) return null
            slopePerMs(samples) * 3_600_000.0
        }

    /**
     * Merges an outside history (e.g. a tracker's 5-minute snapshots) into [key], so a pace is
     * available the moment the app opens instead of after minutes of our own sampling.
     */
    fun seed(key: String, samples: List<Pair<Long, Double>>, nowMs: Long = System.currentTimeMillis()) = synchronized(lock) {
        if (samples.isEmpty()) return@synchronized
        val merged = (series[key].orEmpty() + samples.map { Sample(it.first, it.second) })
            .filter { nowMs - it.t <= KEEP_MS }
            .sortedBy { it.t }
        val compact = mutableListOf<Sample>()
        for (s in merged) {
            val last = compact.lastOrNull()
            if (last != null && s.t - last.t < MIN_GAP_MS) compact[compact.lastIndex] = s else compact += s
        }
        while (compact.size > MAX_SAMPLES) compact.removeAt(0)
        series[key] = compact
    }

    /** Latest sample (time ms, value) for [key]. */
    fun last(key: String): Pair<Long, Double>? = synchronized(lock) {
        series[key]?.lastOrNull()?.let { it.t to it.v }
    }

    /** How long we have been watching [key], for "based on the last X min" hints. */
    fun observedMs(key: String): Long = synchronized(lock) { span(series[key].orEmpty()) }

    fun save() {
        val target = file ?: return
        val snapshot = synchronized(lock) { series.mapValues { it.value.toList() } }
        runCatching {
            val tmp = File(target.parentFile, target.name + ".tmp")
            tmp.writeText(json.encodeToString(snapshot))
            tmp.renameTo(target)
        }
    }

    private fun load(): MutableMap<String, MutableList<Sample>> {
        val source = file?.takeIf { it.exists() } ?: return mutableMapOf()
        val now = System.currentTimeMillis()
        return runCatching {
            json.decodeFromString<Map<String, List<Sample>>>(source.readText())
                .mapValues { (_, samples) -> samples.filter { now - it.t <= KEEP_MS }.toMutableList() }
                .filterValues { it.isNotEmpty() }
                .toMutableMap()
        }.getOrDefault(mutableMapOf())
    }

    private fun span(samples: List<Sample>): Long =
        if (samples.size < 2) 0 else samples.last().t - samples.first().t

    private fun slopePerMs(samples: List<Sample>): Double {
        val t0 = samples.first().t
        val xs = samples.map { (it.t - t0).toDouble() }
        val ys = samples.map { it.v }
        val mx = xs.average()
        val my = ys.average()
        var num = 0.0
        var den = 0.0
        for (i in xs.indices) {
            num += (xs[i] - mx) * (ys[i] - my)
            den += (xs[i] - mx) * (xs[i] - mx)
        }
        return if (den == 0.0) 0.0 else num / den
    }

    companion object {
        // The game's API is fast enough to sample every ~15 s: a first pace after ~25 s.
        const val MIN_GAP_MS = 8_000L
        const val MIN_SPAN_MS = 25_000L
        const val WINDOW_MS = 60 * 60_000L
        const val KEEP_MS = 4 * 60 * 60_000L
        const val MAX_SAMPLES = 900

        private val json = Json { ignoreUnknownKeys = true }

        fun planetKey(index: Int) = "planet:$index"
        fun eventKey(index: Int) = "event:$index"
        fun regionKey(planet: Int, region: Int) = "region:$planet:$region"
        fun taskKey(assignmentId: Long, task: Int) = "mo:$assignmentId:$task"
        fun costKey(actionId: Long, costId: String) = "dss:$actionId:$costId"
    }
}
