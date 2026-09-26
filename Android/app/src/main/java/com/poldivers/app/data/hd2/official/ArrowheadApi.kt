package com.poldivers.app.data.hd2.official

import com.poldivers.app.data.hd2.model.Cost
import com.poldivers.app.data.hd2.model.RawPlanetEffect
import com.poldivers.app.data.hd2.model.RawSpaceStation
import com.poldivers.app.data.hd2.model.SafeLongSerializer
import com.poldivers.app.data.hd2.model.Task
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Arrowhead's own war API (https://api.live.prod.thehelldiversgame.com) -- what the game itself
 * and the community wrapper read. Much faster than the wrapper and not limited to 5 requests per
 * 10 s, but raw: numbers and ids only, names come from the bundled war_static.json.
 * Shapes follow the community project's ArrowHead models (helldivers-2/api, MIT).
 */
interface ArrowheadApi {

    @GET("api/WarSeason/current/WarID")
    suspend fun warId(): AhWarId

    @GET("api/WarSeason/{war}/WarInfo")
    suspend fun warInfo(@Path("war") war: Int): AhWarInfo

    @GET("api/WarSeason/{war}/Status")
    suspend fun status(@Path("war") war: Int, @Header("Accept-Language") language: String): AhStatus

    @GET("api/NewsFeed/{war}")
    suspend fun newsFeed(
        @Path("war") war: Int,
        @Query("maxEntries") maxEntries: Int,
        @Query("fromTimestamp") fromTimestamp: Long,
        @Header("Accept-Language") language: String,
    ): List<AhNewsItem>

    @GET("api/v2/Assignment/War/{war}")
    suspend fun assignments(@Path("war") war: Int, @Header("Accept-Language") language: String): List<AhAssignment>

    @GET("api/SpaceStation/{war}/{id}")
    suspend fun spaceStation(
        @Path("war") war: Int,
        @Path("id") id32: Long,
        @Header("Accept-Language") language: String,
    ): AhSpaceStation

    @GET("api/Stats/war/{war}/summary")
    suspend fun summary(@Path("war") war: Int): AhSummary
}

@Serializable
data class AhWarId(val id: Int = 0)

@Serializable
data class AhWarInfo(
    val warId: Int = 0,
    @Serializable(with = SafeLongSerializer::class) val startDate: Long = 0,
    @Serializable(with = SafeLongSerializer::class) val endDate: Long = 0,
    val planetInfos: List<AhPlanetInfo> = emptyList(),
    val planetRegions: List<AhRegionInfo> = emptyList(),
)

@Serializable
data class AhPosition(val x: Double = 0.0, val y: Double = 0.0)

@Serializable
data class AhPlanetInfo(
    val index: Int,
    val position: AhPosition = AhPosition(),
    val waypoints: List<Int> = emptyList(),
    val sector: Int = 0,
    @Serializable(with = SafeLongSerializer::class) val maxHealth: Long = 0,
    val disabled: Boolean = false,
    val initialOwner: Int = 0,
)

@Serializable
data class AhRegionInfo(
    val planetIndex: Int,
    val regionIndex: Int,
    @Serializable(with = SafeLongSerializer::class) val settingsHash: Long = 0,
    @Serializable(with = SafeLongSerializer::class) val maxHealth: Long = 0,
    val regionSize: Int = 0,
)

@Serializable
data class AhStatus(
    val warId: Int = 0,
    @Serializable(with = SafeLongSerializer::class) val time: Long = 0,
    val impactMultiplier: Double = 0.0,
    val planetStatus: List<AhPlanetStatus> = emptyList(),
    val planetAttacks: List<AhAttack> = emptyList(),
    val campaigns: List<AhCampaign> = emptyList(),
    val planetEvents: List<AhEvent> = emptyList(),
    val planetActiveEffects: List<RawPlanetEffect> = emptyList(),
    val spaceStations: List<RawSpaceStation> = emptyList(),
    val planetRegions: List<AhRegionStatus> = emptyList(),
)

@Serializable
data class AhPlanetStatus(
    val index: Int,
    val owner: Int = 0,
    @Serializable(with = SafeLongSerializer::class) val health: Long = 0,
    val regenPerSecond: Double = 0.0,
    @Serializable(with = SafeLongSerializer::class) val players: Long = 0,
)

@Serializable
data class AhAttack(val source: Int, val target: Int)

@Serializable
data class AhCampaign(
    val id: Int,
    val planetIndex: Int,
    val type: Int = 0,
    @Serializable(with = SafeLongSerializer::class) val count: Long = 0,
    val race: Int = 0,
)

@Serializable
data class AhEvent(
    val id: Int,
    val planetIndex: Int,
    val eventType: Int = 0,
    val race: Int = 0,
    @Serializable(with = SafeLongSerializer::class) val health: Long = 0,
    @Serializable(with = SafeLongSerializer::class) val maxHealth: Long = 0,
    @Serializable(with = SafeLongSerializer::class) val startTime: Long = 0,
    @Serializable(with = SafeLongSerializer::class) val expireTime: Long = 0,
    val campaignId: Int? = null,
    val jointOperationIds: List<Int> = emptyList(),
)

@Serializable
data class AhRegionStatus(
    val planetIndex: Int,
    val regionIndex: Int,
    val owner: Int = 0,
    @Serializable(with = SafeLongSerializer::class) val health: Long = 0,
    val regerPerSecond: Double = 0.0,
    val availabilityFactor: Double = 0.0,
    val isAvailable: Boolean = false,
    @Serializable(with = SafeLongSerializer::class) val players: Long = 0,
)

@Serializable
data class AhNewsItem(
    val id: Int,
    @Serializable(with = SafeLongSerializer::class) val published: Long = 0,
    val type: Int = 0,
    val message: String = "",
)

@Serializable
data class AhAssignment(
    @Serializable(with = SafeLongSerializer::class) val id32: Long,
    val progress: List<@Serializable(with = SafeLongSerializer::class) Long> = emptyList(),
    @Serializable(with = SafeLongSerializer::class) val expiresIn: Long = 0,
    val setting: AhSetting = AhSetting(),
)

@Serializable
data class AhSetting(
    val type: Int = 0,
    val overrideTitle: String = "",
    val overrideBrief: String = "",
    val taskDescription: String = "",
    val tasks: List<Task> = emptyList(),
    val reward: AhReward? = null,
    val rewards: List<AhReward?> = emptyList(),
    val flags: Int = 0,
)

@Serializable
data class AhReward(
    val type: Int = 0,
    @Serializable(with = SafeLongSerializer::class) val id32: Long = 0,
    @Serializable(with = SafeLongSerializer::class) val amount: Long = 0,
)

@Serializable
data class AhSpaceStation(
    @Serializable(with = SafeLongSerializer::class) val id32: Long,
    val planetIndex: Int = -1,
    @Serializable(with = SafeLongSerializer::class) val currentElectionEndWarTime: Long = 0,
    val flags: Int = 0,
    val tacticalActions: List<AhTacticalAction> = emptyList(),
)

@Serializable
data class AhTacticalAction(
    @Serializable(with = SafeLongSerializer::class) val id32: Long,
    @Serializable(with = SafeLongSerializer::class) val mediaId32: Long = 0,
    val name: String = "",
    val description: String = "",
    val strategicDescription: String = "",
    val status: Int = 0,
    @Serializable(with = SafeLongSerializer::class) val statusExpireAtWarTimeSeconds: Long = 0,
    val cost: List<Cost> = emptyList(),
    val effectIds: List<Int> = emptyList(),
)

@Serializable
data class AhSummary(
    @SerialName("planets_stats") val planetsStats: List<AhPlanetStats> = emptyList(),
)

@Serializable
data class AhPlanetStats(
    val planetIndex: Int,
    @Serializable(with = SafeLongSerializer::class) val missionsWon: Long = 0,
    @Serializable(with = SafeLongSerializer::class) val missionsLost: Long = 0,
    @Serializable(with = SafeLongSerializer::class) val missionTime: Long = 0,
    @Serializable(with = SafeLongSerializer::class) val bugKills: Long = 0,
    @Serializable(with = SafeLongSerializer::class) val automatonKills: Long = 0,
    @Serializable(with = SafeLongSerializer::class) val illuminateKills: Long = 0,
    @Serializable(with = SafeLongSerializer::class) val bulletsFired: Long = 0,
    @Serializable(with = SafeLongSerializer::class) val bulletsHit: Long = 0,
    @Serializable(with = SafeLongSerializer::class) val timePlayed: Long = 0,
    @Serializable(with = SafeLongSerializer::class) val deaths: Long = 0,
    @Serializable(with = SafeLongSerializer::class) val revives: Long = 0,
    @Serializable(with = SafeLongSerializer::class) val friendlies: Long = 0,
    @Serializable(with = SafeLongSerializer::class) val missionSuccessRate: Long = 0,
    @Serializable(with = SafeLongSerializer::class) val accurracy: Long = 0,
)
