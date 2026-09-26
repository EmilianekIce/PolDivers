package com.poldivers.app.data.hd2

import com.poldivers.app.data.hd2.model.Assignment
import com.poldivers.app.data.hd2.model.Campaign
import com.poldivers.app.data.hd2.model.Dispatch
import com.poldivers.app.data.hd2.model.Planet
import com.poldivers.app.data.hd2.model.SpaceStation
import com.poldivers.app.data.hd2.model.War
import retrofit2.http.GET
import retrofit2.http.Path

/**
 * https://api.helldivers2.dev -- community wrapper around Arrowhead's Helldivers 2 backend.
 * Docs: https://helldivers-2.github.io/api/
 */
interface Hd2ApiService {

    @GET("api/v1/war")
    suspend fun getWar(): War

    @GET("api/v1/planets")
    suspend fun getPlanets(): List<Planet>

    @GET("api/v1/planets/{index}")
    suspend fun getPlanet(@Path("index") index: Int): Planet

    @GET("api/v1/campaigns")
    suspend fun getCampaigns(): List<Campaign>

    @GET("api/v1/assignments")
    suspend fun getAssignments(): List<Assignment>

    @GET("api/v1/dispatches")
    suspend fun getDispatches(): List<Dispatch>

    @GET("api/v2/space-stations")
    suspend fun getSpaceStations(): List<SpaceStation>
}
