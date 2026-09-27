package com.poldivers.app

import com.poldivers.app.core.trends.Projection
import com.poldivers.app.core.trends.TrendStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import java.nio.file.Files

class TrendsTest {

    @Test
    fun rateNeedsEnoughHistory() {
        val store = TrendStore(null)
        store.record("k", 10.0, timeMs = 0)
        assertNull(store.ratePerHour("k", nowMs = 0))
        store.record("k", 10.5, timeMs = 10_000)
        assertNull(store.ratePerHour("k", nowMs = 10_000)) // only ten seconds observed
    }

    @Test
    fun linearRatePerHour() {
        val store = TrendStore(null)
        // +0.5 % every minute -> 30 %/h
        for (minute in 0..10) store.record("k", 10.0 + minute * 0.5, timeMs = minute * 60_000L)
        assertEquals(30.0, store.ratePerHour("k", nowMs = 600_000)!!, 0.001)
    }

    @Test
    fun flatSeriesStaysCompactAndZero() {
        val store = TrendStore(null)
        for (minute in 0..30) store.record("k", 42.0, timeMs = minute * 60_000L)
        assertEquals(0.0, store.ratePerHour("k", nowMs = 1_800_000)!!, 0.0)
        assertEquals(1_800_000L, store.observedMs("k"))
    }

    @Test
    fun survivesRestart() {
        val file = File(Files.createTempDirectory("trends").toFile(), "trends.json")
        val now = System.currentTimeMillis()
        TrendStore(file).apply {
            record("k", 1.0, now - 600_000)
            record("k", 2.0, now)
            save()
        }
        assertEquals(6.0, TrendStore(file).ratePerHour("k", nowMs = now)!!, 0.001)
    }

    @Test
    fun liberationEta() {
        val p = Projection(percent = 90.0, ratePerHour = 5.0, secondsLeft = null)
        assertEquals(2 * 3600L, p.etaSeconds)
        assertEquals(Projection.Outcome.ON_TRACK, p.outcome)
    }

    @Test
    fun defenseThatWillFail() {
        // 50 % held, +10 %/h, 3 h left -> 80 % at the end
        val p = Projection(percent = 50.0, ratePerHour = 10.0, secondsLeft = 3 * 3600L)
        assertEquals(80.0, p.percentAtDeadline!!, 0.001)
        assertEquals(Projection.Outcome.FAILING, p.outcome)
        assertEquals(50.0 / 3, p.requiredRatePerHour!!, 0.001)
    }

    @Test
    fun losingGroundHasNoEta() {
        val p = Projection(percent = 40.0, ratePerHour = -2.0, secondsLeft = null)
        assertNull(p.etaSeconds)
        assertEquals(Projection.Outcome.STALLED, p.outcome)
    }
}
