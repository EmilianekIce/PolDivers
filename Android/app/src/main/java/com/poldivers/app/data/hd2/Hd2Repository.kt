package com.poldivers.app.data.hd2

import com.poldivers.app.data.hd2.model.Assignment
import com.poldivers.app.data.hd2.model.Campaign
import com.poldivers.app.data.hd2.model.Dispatch
import com.poldivers.app.data.hd2.model.Planet
import com.poldivers.app.data.hd2.model.SpaceStation
import com.poldivers.app.data.hd2.model.Task
import com.poldivers.app.data.hd2.model.War

class Hd2Repository(private val api: Hd2ApiService) {

    suspend fun getWar(): War = api.getWar()

    suspend fun getPlanets(): List<Planet> = api.getPlanets()

    suspend fun getPlanetsSortedByPlayers(): List<Planet> =
        getPlanets().sortedByDescending { it.playerCount }

    suspend fun getCampaigns(): List<Campaign> =
        api.getCampaigns().sortedByDescending { it.planet.playerCount }

    suspend fun getAssignments(): List<Assignment> = api.getAssignments()

    suspend fun getDispatches(): List<Dispatch> =
        api.getDispatches().sortedByDescending { it.published }

    suspend fun getSpaceStations(): List<SpaceStation> = api.getSpaceStations()
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
