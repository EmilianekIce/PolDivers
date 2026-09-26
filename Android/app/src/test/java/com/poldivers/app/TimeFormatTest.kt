package com.poldivers.app

import com.poldivers.app.ui.common.formatDuration
import com.poldivers.app.ui.common.formatRemaining
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration
import java.time.Instant

class TimeFormatTest {

    @Test
    fun formatsDurations() {
        assertEquals("2d 3h", formatDuration(Duration.ofHours(51)))
        assertEquals("5h 12min", formatDuration(Duration.ofMinutes(312)))
        assertEquals("7min 3s", formatDuration(Duration.ofSeconds(423)))
        assertEquals("9s", formatDuration(Duration.ofSeconds(9)))
        assertNull(formatDuration(Duration.ofSeconds(-1)))
    }

    @Test
    fun remainingUsesGivenClock() {
        val now = Instant.parse("2026-01-01T00:00:00Z")
        assertEquals("1h 30min", formatRemaining("2026-01-01T01:30:00Z", now))
        assertEquals("zakończone", formatRemaining("2025-12-31T23:00:00Z", now))
        assertEquals("?", formatRemaining("garbage", now))
    }
}
