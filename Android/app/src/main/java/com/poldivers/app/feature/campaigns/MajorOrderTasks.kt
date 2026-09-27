package com.poldivers.app.feature.campaigns

import com.poldivers.app.core.i18n.tr
import com.poldivers.app.data.hd2.model.Assignment
import com.poldivers.app.data.hd2.model.Planet
import com.poldivers.app.data.hd2.model.Task
import com.poldivers.app.data.hd2.model.factionForRace
import com.poldivers.app.data.hd2.targetPlanetIndex
import com.poldivers.app.ui.common.factionLabel

/** A Major Order objective resolved into something we can render. */
data class TaskView(
    val label: String,
    /** Planet the task is about, if it targets one. */
    val planet: Planet?,
    val progress: Long,
    /** Target count, or null for yes/no objectives (liberate/hold a planet). */
    val goal: Long?,
    val task: Task? = null,
    /** Enemy faction the objective is about (English API key), if known. */
    val faction: String? = null,
) {
    val isDone: Boolean get() = if (goal != null && goal > 0) progress >= goal else progress >= 1
    val fraction: Float get() = if (goal != null && goal > 0) (progress.toFloat() / goal).coerceIn(0f, 1f) else if (isDone) 1f else 0f
}

/**
 * The API leaves task semantics undocumented; the type/valueType meanings come from the
 * community-maintained helldivers-2/json repo. Anything unrecognised still renders as a
 * generic "progress / goal" row, and the order's own (localized) description text is shown
 * above the tasks anyway, so nothing is lost if the game introduces a new task type.
 */
fun Assignment.taskViews(planets: Map<Int, Planet>): List<TaskView> = tasks.mapIndexed { i, task ->
    val rawProgress = progress.getOrNull(i) ?: 0
    val goal = task.valueOf(Task.ValueType.GOAL)?.takeIf { it > 0 }
    val planet = task.targetPlanetIndex()?.let { planets[it] }
    // A liberation/control target already held by Super Earth counts as done even if the order's
    // progress flag has not caught up yet (avoids "101 %" on a finished planet).
    val progress = if (
        planet != null && task.type != Task.Type.DEFENSE &&
        planet.currentOwner == "Humans" && planet.event == null
    ) maxOf(rawProgress, 1) else rawProgress
    val factionKey = factionForRace(task.valueOf(Task.ValueType.RACE)) ?: planet?.let { it.event?.faction ?: it.currentOwner }
    val faction = factionForRace(task.valueOf(Task.ValueType.RACE))?.let(::factionLabel)
    val against = faction?.let { " ($it)" }.orEmpty()
    val difficulty = task.valueOf(Task.ValueType.DIFFICULTY)?.takeIf { it > 0 }?.let { tr(", poziom trudności $it+", ", difficulty $it+") }.orEmpty()

    val label = when (task.type) {
        Task.Type.LIBERATION -> planet?.let { tr("Wyzwól planetę ${it.name}", "Liberate ${it.name}") } ?: tr("Wyzwól planety$against", "Liberate planets$against")
        Task.Type.DEFENSE -> planet?.let { tr("Obroń planetę ${it.name}", "Defend ${it.name}") } ?: tr("Obroń planety$against", "Defend planets$against")
        Task.Type.CONTROL -> planet?.let { tr("Utrzymaj kontrolę nad ${it.name}", "Hold ${it.name}") } ?: tr("Utrzymaj kontrolę nad planetami", "Hold the planets")
        Task.Type.ERADICATE -> tr("Zlikwiduj wrogów$against$difficulty", "Kill enemies$against$difficulty")
        Task.Type.EXTRACT -> tr("Ewakuuj się z zasobami$against$difficulty", "Extract with resources$against$difficulty")
        Task.Type.COMPLETE_MISSIONS -> tr("Ukończ misje$against$difficulty", "Complete missions$against$difficulty")
        Task.Type.COMPLETE_OPERATIONS -> tr("Ukończ operacje$against$difficulty", "Complete operations$against$difficulty")
        Task.Type.EXPAND -> tr("Poszerz terytorium Super Ziemi$against", "Expand Super Earth territory$against")
        else -> tr("Cel #${i + 1}$against", "Objective #${i + 1}$against")
    }
    TaskView(
        label = label,
        planet = planet,
        progress = progress,
        goal = if (planet != null && task.type != Task.Type.DEFENSE) null else goal,
        task = task,
        faction = factionKey,
    )
}

fun rewardLabel(type: Int, amount: Long): String = when (type) {
    1 -> tr("$amount medali", "$amount medals")
    2 -> tr("$amount Super Kredytów", "$amount Super Credits")
    3 -> tr("$amount próbek", "$amount samples")
    4 -> tr("$amount zapotrzebowania", "$amount requisition")
    else -> "$amount"
}

/** Enemy faction a Major Order is about: from its task targets, else from the planets it names. */
fun campaignFaction(assignment: Assignment, planets: Map<Int, Planet>): String? {
    assignment.tasks.firstNotNullOfOrNull { factionForRace(it.valueOf(Task.ValueType.RACE)?.takeIf { r -> r > 1 }) }?.let { return it }
    return assignment.tasks.firstNotNullOfOrNull { task ->
        task.targetPlanetIndex()?.let { planets[it] }?.let { p -> p.event?.faction ?: p.currentOwner.takeIf { it != "Humans" } }
    }
}
