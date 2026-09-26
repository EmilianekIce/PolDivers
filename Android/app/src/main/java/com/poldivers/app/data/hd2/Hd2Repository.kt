package com.poldivers.app.data.hd2

import com.poldivers.app.data.hd2.model.Assignment
import com.poldivers.app.data.hd2.model.Campaign
import com.poldivers.app.data.hd2.model.Dispatch
import com.poldivers.app.data.hd2.model.Planet
import com.poldivers.app.data.hd2.model.SpaceStation
import com.poldivers.app.data.hd2.model.War

class Hd2Repository(private val api: Hd2ApiService) {

    suspend fun getWar(): War = api.getWar()

    suspend fun getPlanets(): List<Planet> = api.getPlanets()

    suspend fun getPlanetsSortedByPlayers(): List<Planet> =
        getPlanets().sortedByDescending { it.statistics?.playerCount ?: 0 }

    suspend fun getCampaigns(): List<Campaign> = api.getCampaigns()

    suspend fun getAssignments(): List<Assignment> = api.getAssignments()

    suspend fun getDispatches(): List<Dispatch> =
        api.getDispatches().sortedByDescending { it.published }

    suspend fun getSpaceStations(): List<SpaceStation> = api.getSpaceStations()
}
