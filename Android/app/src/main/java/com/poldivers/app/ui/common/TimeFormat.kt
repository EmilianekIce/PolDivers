package com.poldivers.app.ui.common

import java.time.Duration
import java.time.Instant

/** "expiration"/"published" fields from the API are ISO-8601 instants. */
fun formatRemaining(isoInstant: String): String {
    val target = runCatching { Instant.parse(isoInstant) }.getOrNull() ?: return "?"
    val remaining = Duration.between(Instant.now(), target)
    if (remaining.isNegative) return "zakończone"

    val days = remaining.toDays()
    val hours = remaining.toHours() % 24
    val minutes = remaining.toMinutes() % 60

    return when {
        days > 0 -> "${days}d ${hours}h"
        hours > 0 -> "${hours}h ${minutes}min"
        else -> "${minutes}min"
    }
}

fun formatAgo(isoInstant: String): String {
    val source = runCatching { Instant.parse(isoInstant) }.getOrNull() ?: return "?"
    val elapsed = Duration.between(source, Instant.now())
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
