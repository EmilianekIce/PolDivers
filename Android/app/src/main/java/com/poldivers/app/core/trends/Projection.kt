package com.poldivers.app.core.trends

/**
 * Where a 0..100 % meter (liberation, defense, MO objective) is heading at the current pace.
 *
 * @param percent current value, 0..100
 * @param ratePerHour change in percentage points per hour, null while we have too little history
 * @param secondsLeft time until the deadline (defense end, Major Order expiry), if any
 */
data class Projection(
    val percent: Double,
    val ratePerHour: Double?,
    val secondsLeft: Long?,
) {
    /** Seconds until 100 % at the current pace; null when unknown or not progressing. */
    val etaSeconds: Long?
        get() {
            val rate = ratePerHour ?: return null
            if (percent >= 100.0) return 0
            if (rate <= RATE_EPSILON) return null
            return ((100.0 - percent) / rate * 3600).toLong()
        }

    /** Value expected at the deadline, clamped to 0..100. */
    val percentAtDeadline: Double?
        get() {
            val rate = ratePerHour ?: return null
            val left = secondsLeft ?: return null
            return (percent + rate * left / 3600.0).coerceIn(0.0, 100.0)
        }

    /** Pace needed to reach 100 % exactly at the deadline. */
    val requiredRatePerHour: Double?
        get() {
            val left = secondsLeft?.takeIf { it > 0 } ?: return null
            return (100.0 - percent).coerceAtLeast(0.0) / (left / 3600.0)
        }

    val outcome: Outcome
        get() = when {
            percent >= 100.0 -> Outcome.DONE
            ratePerHour == null -> Outcome.UNKNOWN
            secondsLeft == null -> if (ratePerHour > RATE_EPSILON) Outcome.ON_TRACK else Outcome.STALLED
            (percentAtDeadline ?: 0.0) >= 100.0 -> Outcome.ON_TRACK
            else -> Outcome.FAILING
        }

    enum class Outcome { DONE, ON_TRACK, FAILING, STALLED, UNKNOWN }

    companion object {
        /** Below ~0.01 %/h a meter is effectively not moving. */
        const val RATE_EPSILON = 0.01
    }
}
