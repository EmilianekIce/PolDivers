package com.poldivers.app

import com.poldivers.app.data.hd2.model.Assignment
import com.poldivers.app.data.hd2.model.Planet
import com.poldivers.app.data.hd2.model.Task
import com.poldivers.app.data.hd2.targetPlanetIndexes
import com.poldivers.app.feature.campaigns.taskViews
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MajorOrderTasksTest {

    private val planets = mapOf(
        0 to Planet(index = 0, name = "Super Earth", sector = "Sol"),
        64 to Planet(index = 64, name = "Meridia", sector = "Umlaut"),
    )

    private fun assignment(tasks: List<Task>, progress: List<Long>) =
        Assignment(id = 1, progress = progress, tasks = tasks, expiration = "2030-01-01T00:00:00Z")

    @Test
    fun liberationTaskResolvesPlanet() {
        val task = Task(type = 11, values = listOf(1, 1, 64), valueTypes = listOf(3, 11, 12))
        val views = assignment(listOf(task), listOf(1)).taskViews(planets)
        assertEquals("Meridia", views.single().planet?.name)
        assertNull(views.single().goal)
        assertTrue(views.single().isDone)
    }

    @Test
    fun eradicateTaskUsesGoalAndFaction() {
        val task = Task(type = 3, values = listOf(2, 0, 1_000_000, 0, 0, 0, 0), valueTypes = listOf(1, 2, 3, 4, 5, 11, 12))
        val view = assignment(listOf(task), listOf(250_000)).taskViews(planets).single()
        assertEquals(1_000_000L, view.goal)
        assertEquals(0.25f, view.fraction, 0.0001f)
        assertFalse(view.isDone)
        assertTrue(view.label.contains("Terminidzi"))
        assertNull(view.planet)
    }

    @Test
    fun anywhereLocationIsNotSuperEarth() {
        val eradicate = Task(type = 3, values = listOf(2, 100, 0, 0), valueTypes = listOf(1, 3, 11, 12))
        val defense = Task(type = 12, values = listOf(5, 0, 0), valueTypes = listOf(3, 11, 12))
        assertTrue(assignment(listOf(eradicate, defense), listOf(0, 0)).targetPlanetIndexes().isEmpty())
    }

    @Test
    fun missingProgressCountsAsZero() {
        val task = Task(type = 99, values = listOf(10), valueTypes = listOf(3))
        val view = assignment(listOf(task), emptyList()).taskViews(planets).single()
        assertEquals(0L, view.progress)
        assertEquals(10L, view.goal)
    }
}
