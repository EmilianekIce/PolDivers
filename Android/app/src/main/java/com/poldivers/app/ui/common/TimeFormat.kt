package com.poldivers.app.ui.common

import com.poldivers.app.core.i18n.tr
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
    return formatDuration(Duration.between(now, target)) ?: tr("zakończone", "ended")
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
    if (elapsed.isNegative) return tr("teraz", "now")

    val days = elapsed.toDays()
    val hours = elapsed.toHours() % 24
    val minutes = elapsed.toMinutes() % 60

    return when {
        days > 0 -> tr("$days d temu", "${days}d ago")
        hours > 0 -> tr("$hours godz. temu", "${hours}h ago")
        else -> tr("$minutes min temu", "$minutes min ago")
    }
}

private val clockFormat = java.time.format.DateTimeFormatter.ofPattern("HH:mm")
private val dayClockFormat = java.time.format.DateTimeFormatter.ofPattern("EEE HH:mm", java.util.Locale("pl", "PL"))
private val dayClockFormatEn = java.time.format.DateTimeFormatter.ofPattern("EEE HH:mm", java.util.Locale.US)

/** Local wall-clock time of [now] + [seconds]: "18:40", or "pt. 18:40" when not today. */
fun formatClockIn(seconds: Long, now: Instant = Instant.now()): String {
    val zone = java.time.ZoneId.systemDefault()
    val target = now.plusSeconds(seconds).atZone(zone)
    val today = now.atZone(zone).toLocalDate()
    return if (target.toLocalDate() == today) clockFormat.format(target) else (if (com.poldivers.app.core.i18n.UiLang.english) dayClockFormatEn else dayClockFormat).format(target)
}

fun formatSeconds(seconds: Long): String = formatDuration(Duration.ofSeconds(seconds)) ?: "0s"
