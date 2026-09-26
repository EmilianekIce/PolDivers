package com.poldivers.app.feature.campaigns

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
    val progress = progress.getOrNull(i) ?: 0
    val goal = task.valueOf(Task.ValueType.GOAL)?.takeIf { it > 0 }
    val planet = task.targetPlanetIndex()?.let { planets[it] }
    val faction = factionForRace(task.valueOf(Task.ValueType.RACE))?.let(::factionLabel)
    val against = faction?.let { " ($it)" }.orEmpty()
    val difficulty = task.valueOf(Task.ValueType.DIFFICULTY)?.takeIf { it > 0 }?.let { ", poziom trudności $it+" }.orEmpty()

    val label = when (task.type) {
        Task.Type.LIBERATION -> planet?.let { "Wyzwól planetę ${it.name}" } ?: "Wyzwól planety$against"
        Task.Type.DEFENSE -> planet?.let { "Obroń planetę ${it.name}" } ?: "Obroń planety$against"
        Task.Type.CONTROL -> planet?.let { "Utrzymaj kontrolę nad ${it.name}" } ?: "Utrzymaj kontrolę nad planetami"
        Task.Type.ERADICATE -> "Zlikwiduj wrogów$against$difficulty"
        Task.Type.EXTRACT -> "Ewakuuj się z zasobami$against$difficulty"
        Task.Type.COMPLETE_MISSIONS -> "Ukończ misje$against$difficulty"
        Task.Type.COMPLETE_OPERATIONS -> "Ukończ operacje$against$difficulty"
        Task.Type.EXPAND -> "Poszerz terytorium Super Ziemi$against"
        else -> "Cel #${i + 1}$against"
    }
    TaskView(
        label = label,
        planet = planet,
        progress = progress,
        goal = if (planet != null && task.type != Task.Type.DEFENSE) null else goal,
    )
}

fun rewardLabel(type: Int, amount: Long): String = when (type) {
    1 -> "$amount medali"
    else -> "$amount"
}
