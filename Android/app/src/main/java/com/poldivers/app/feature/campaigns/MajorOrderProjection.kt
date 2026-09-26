package com.poldivers.app.feature.campaigns

import com.poldivers.app.core.trends.Projection
import com.poldivers.app.core.trends.TrendStore
import com.poldivers.app.data.hd2.Hd2Repository
import com.poldivers.app.data.hd2.model.Assignment
import com.poldivers.app.data.hd2.model.Task
import com.poldivers.app.ui.common.parseInstant
import java.time.Duration
import java.time.Instant

/** Major Order outlook at the current pace, the way the community trackers present it. */
data class OrderOutlook(
    val tasks: List<Projection>,
    /** Average expected completion of all objectives at expiry (can exceed 100 %); null without history. */
    val predictedPercent: Double?,
    val verdict: Verdict,
) {
    enum class Verdict { COMPLETE, ON_TRACK, AT_RISK, UNKNOWN }
}

/**
 * Per objective:
 * - counted objectives (kill X, complete N missions...) -> progress / goal, pace from the local
 *   history of that objective's progress value;
 * - planet objectives -> that planet's liberation (or defense) pace;
 * and every projection is capped at the order's expiry.
 */
fun Assignment.outlook(
    views: List<TaskView>,
    repository: Hd2Repository,
    now: Instant = Instant.now(),
): OrderOutlook {
    val secondsLeft = parseInstant(expiration)?.let { Duration.between(now, it).seconds.coerceAtLeast(0) }
    val projections = views.mapIndexed { i, view ->
        val task = tasks.getOrNull(i)
        val planet = view.planet
        when {
            view.isDone -> Projection(100.0, 0.0, secondsLeft)
            planet != null && task?.type != Task.Type.DEFENSE -> {
                val p = repository.projectionFor(planet, now)
                // A liberation objective must finish before the order expires, not before a
                // planet's own event timer.
                Projection(if (planet.event != null) planet.liberationPercent else p.percent,
                    if (planet.event != null) null else p.ratePerHour, secondsLeft)
            }
            planet != null -> repository.projectionFor(planet, now).let { p ->
                Projection(p.percent, p.ratePerHour, listOfNotNull(p.secondsLeft, secondsLeft).minOrNull())
            }
            view.goal != null -> {
                val goal = view.goal.toDouble()
                val rate = repository.trends.ratePerHour(TrendStore.taskKey(id, i))
                Projection(
                    percent = (view.progress / goal * 100).coerceIn(0.0, 100.0),
                    ratePerHour = rate?.let { it / goal * 100 },
                    secondsLeft = secondsLeft,
                )
            }
            else -> Projection(if (view.isDone) 100.0 else 0.0, null, secondsLeft)
        }
    }

    val known = projections.all { it.percent >= 100.0 || it.ratePerHour != null }
    val predicted = if (projections.isEmpty() || !known) {
        null
    } else {
        projections.mapIndexed { i, p ->
            when {
                p.percent >= 100.0 -> 100.0
                else -> p.projectedAtDeadline ?: p.percent
            }
        }.average()
    }
    val verdict = when {
        projections.isNotEmpty() && projections.all { it.outcome == Projection.Outcome.DONE } -> OrderOutlook.Verdict.COMPLETE
        projections.any { it.outcome == Projection.Outcome.FAILING } -> OrderOutlook.Verdict.AT_RISK
        projections.any { it.outcome == Projection.Outcome.UNKNOWN } -> OrderOutlook.Verdict.UNKNOWN
        else -> OrderOutlook.Verdict.ON_TRACK
    }
    return OrderOutlook(projections, predicted, verdict)
}
