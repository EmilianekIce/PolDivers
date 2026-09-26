package com.poldivers.app.data.hd2

import com.poldivers.app.data.hd2.model.Assignment
import com.poldivers.app.data.hd2.model.Campaign
import com.poldivers.app.data.hd2.model.Dispatch
import com.poldivers.app.data.hd2.model.Planet
import com.poldivers.app.data.hd2.model.SpaceStation
import com.poldivers.app.data.hd2.model.Task
import com.poldivers.app.data.hd2.model.War
import com.poldivers.app.data.hd2.model.Region
import com.poldivers.app.core.trends.Projection
import com.poldivers.app.core.trends.TrendStore
import com.poldivers.app.ui.common.parseInstant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Instant

class Hd2Repository(
    private val api: Hd2ApiService,
    /** Local history used to turn snapshots into rates / ETAs (see [TrendStore]). */
    val trends: TrendStore,
) {
    private var lastSaveMs = 0L

    suspend fun getWar(): War = api.getWar()

    suspend fun getPlanets(): List<Planet> = api.getPlanets().also { planets ->
        track { planets.forEach { recordPlanet(it) } }
    }

    suspend fun getPlanetsSortedByPlayers(): List<Planet> =
        getPlanets().sortedByDescending { it.playerCount }

    suspend fun getCampaigns(): List<Campaign> =
        api.getCampaigns().sortedByDescending { it.planet.playerCount }.also { campaigns ->
            track { campaigns.forEach { recordPlanet(it.planet, withRegions = true) } }
        }

    suspend fun getAssignments(): List<Assignment> = api.getAssignments().also { assignments ->
        track {
            assignments.forEach { assignment ->
                assignment.progress.forEachIndexed { i, value ->
                    trends.record(TrendStore.taskKey(assignment.id, i), value.toDouble())
                }
            }
        }
    }

    suspend fun getDispatches(): List<Dispatch> =
        api.getDispatches().sortedByDescending { it.published }

    suspend fun getSpaceStations(): List<SpaceStation> = api.getSpaceStations().also { stations ->
        track {
            stations.forEach { station ->
                recordPlanet(station.planet)
                station.tacticalActions.forEach { action ->
                    action.costs.forEach { cost ->
                        trends.record(TrendStore.costKey(action.id32, cost.id), cost.currentValue)
                    }
                }
            }
        }
    }

    /** Liberation (or, during an attack, defense) of [planet] and where it is heading. */
    fun projectionFor(planet: Planet, now: Instant = Instant.now()): Projection {
        val event = planet.event
        return if (event != null) {
            Projection(
                percent = event.defensePercent,
                ratePerHour = trends.ratePerHour(TrendStore.eventKey(planet.index)),
                secondsLeft = parseInstant(event.endTime)?.let { Duration.between(now, it).seconds.coerceAtLeast(0) },
            )
        } else {
            Projection(
                percent = planet.liberationPercent,
                ratePerHour = trends.ratePerHour(TrendStore.planetKey(planet.index)),
                secondsLeft = null,
            )
        }
    }

    fun projectionFor(planet: Planet, region: Region): Projection = Projection(
        percent = region.liberationPercent ?: 0.0,
        ratePerHour = trends.ratePerHour(TrendStore.regionKey(planet.index, region.id)),
        secondsLeft = null,
    )

    private fun recordPlanet(planet: Planet, withRegions: Boolean = false) {
        trends.record(TrendStore.planetKey(planet.index), planet.liberationPercent)
        planet.event?.let { trends.record(TrendStore.eventKey(planet.index), it.defensePercent) }
        if (withRegions) {
            planet.regions.forEach { region ->
                region.liberationPercent?.let { trends.record(TrendStore.regionKey(planet.index, region.id), it) }
            }
        }
    }

    private suspend fun track(block: () -> Unit) = withContext(Dispatchers.IO) {
        block()
        val now = System.currentTimeMillis()
        if (now - lastSaveMs > 60_000) {
            lastSaveMs = now
            trends.save()
        }
    }
}

/** Planet indexes a Major Order explicitly targets (liberate / defend / hold a given planet). */
fun Assignment.targetPlanetIndexes(): Set<Int> = tasks.mapNotNull { it.targetPlanetIndex() }.toSet()

/**
 * The planet a liberation/defense/control task points at. Other task types (kill X enemies,
 * collect samples...) carry a location too, but there 0/0 means "anywhere", not Super Earth.
 */
fun Task.targetPlanetIndex(): Int? {
    if (type != Task.Type.LIBERATION && type != Task.Type.DEFENSE && type != Task.Type.CONTROL) return null
    val index = valueOf(Task.ValueType.LOCATION_INDEX) ?: return null
    val locationType = valueOf(Task.ValueType.LOCATION_TYPE)
    if (index == 0L && (locationType == null || locationType == 0L)) return null
    return index.toInt()
}
