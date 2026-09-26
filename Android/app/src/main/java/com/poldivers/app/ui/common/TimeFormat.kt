package com.poldivers.app.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant

fun parseInstant(isoInstant: String?): Instant? =
    isoInstant?.let { runCatching { Instant.parse(it) }.getOrNull() }

/**
 * Current time that ticks every [periodMs], so countdowns (DSS jump, Major Order deadline,
 * defense timers) keep moving while the screen is open.
 */
@Composable
fun rememberNow(periodMs: Long = 1_000): State<Instant> = produceState(Instant.now(), periodMs) {
    while (true) {
        delay(periodMs)
        value = Instant.now()
    }
}

/** "expiration"/"published" fields from the API are ISO-8601 instants. */
fun formatRemaining(isoInstant: String?, now: Instant = Instant.now()): String {
    val target = parseInstant(isoInstant) ?: return "?"
    return formatDuration(Duration.between(now, target)) ?: "zakończone"
}

/** "3d 4h", "5h 12min", "7min 3s"; null when the duration is already negative. */
fun formatDuration(duration: Duration): String? {
    if (duration.isNegative) return null
    val days = duration.toDays()
    val hours = duration.toHours() % 24
    val minutes = duration.toMinutes() % 60
    val seconds = duration.seconds % 60

    return when {
        days > 0 -> "${days}d ${hours}h"
        hours > 0 -> "${hours}h ${minutes}min"
        minutes > 0 -> "${minutes}min ${seconds}s"
        else -> "${seconds}s"
    }
}

fun formatAgo(isoInstant: String, now: Instant = Instant.now()): String {
    val source = parseInstant(isoInstant) ?: return "?"
    val elapsed = Duration.between(source, now)
    if (elapsed.isNegative) return "teraz"

    val days = elapsed.toDays()
    val hours = elapsed.toHours() % 24
    val minutes = elapsed.toMinutes() % 60

    return when {
        days > 0 -> "$days d temu"
        hours > 0 -> "$hours godz. temu"
        else -> "$minutes min temu"
    }
}
