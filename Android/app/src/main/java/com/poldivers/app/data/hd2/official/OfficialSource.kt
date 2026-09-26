package com.poldivers.app.data.hd2.official

import android.content.Context
import com.poldivers.app.data.hd2.model.Assignment
import com.poldivers.app.data.hd2.model.Campaign
import com.poldivers.app.data.hd2.model.Dispatch
import com.poldivers.app.data.hd2.model.Planet
import com.poldivers.app.data.hd2.model.PlanetEvent
import com.poldivers.app.data.hd2.model.Position
import com.poldivers.app.data.hd2.model.RawWarStatus
import com.poldivers.app.data.hd2.model.Region
import com.poldivers.app.data.hd2.model.Reward
import com.poldivers.app.data.hd2.model.SpaceStation
import com.poldivers.app.data.hd2.model.Statistics
import com.poldivers.app.data.hd2.model.TacticalAction
import com.poldivers.app.data.hd2.model.factionForRace
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant

/**
 * Builds the app's models (the same ones the community wrapper returns) from Arrowhead's raw war
 * data plus bundled names (assets/war_static.json, from helldivers-2/json, MIT).
 *
 * War clock: raw times are seconds of war time. `now + (t - status.time)` puts them on the phone's
 * clock without trusting either device's time zone or drift.
 */
class OfficialSource(context: Context) {

    @Serializable
    private data class StaticPlanet(
        val en: String = "",
        val pl: String = "",
        val sector: String = "",
        val biome: String = "",
        val env: List<String> = emptyList(),
    )

    @Serializable
    private data class StaticData(
        val planets: Map<String, StaticPlanet> = emptyMap(),
        val regions: Map<String, String> = emptyMap(),
    )

    private val static: StaticData by lazy {
        runCatching {
            val text = context.applicationContext.assets.open("war_static.json").bufferedReader().use { it.readText() }
            Json { ignoreUnknownKeys = true }.decodeFromString(StaticData.serializer(), text)
        }.getOrDefault(StaticData())
    }

    fun englishName(index: Int): String? = static.planets[index.toString()]?.en?.takeIf { it.isNotBlank() }

    private fun warTimeToIso(warSeconds: Long, status: AhStatus): String =
        Instant.now().plusSeconds(warSeconds - status.time).toString()

    fun planets(info: AhWarInfo, status: AhStatus, stats: AhSummary?, language: String): List<Planet> {
        val statusByIndex = status.planetStatus.associateBy { it.index }
        val events = status.planetEvents.associateBy { it.planetIndex }
        val attacks = status.planetAttacks.groupBy({ it.source }, { it.target })
        val regionStatus = status.planetRegions.associateBy { it.planetIndex to it.regionIndex }
        val regionInfo = info.planetRegions.groupBy { it.planetIndex }
        val statsByIndex = stats?.planetsStats?.associateBy { it.planetIndex }.orEmpty()
        val polish = language.startsWith("pl")
        return info.planetInfos.mapNotNull { p ->
            val s = static.planets[p.index.toString()] ?: return@mapNotNull null
            if (s.sector.isBlank()) return@mapNotNull null
            val st = statusByIndex[p.index]
            val e = events[p.index]
            val ps = statsByIndex[p.index]
            Planet(
                index = p.index,
                name = (if (polish) s.pl else s.en).ifBlank { s.en },
                sector = s.sector,
                position = Position(p.position.x, p.position.y),
                waypoints = p.waypoints,
                maxHealth = p.maxHealth,
                health = st?.health ?: p.maxHealth,
                disabled = p.disabled,
                initialOwner = factionForRace(p.initialOwner.toLong()).orEmpty(),
                currentOwner = factionForRace(st?.owner?.toLong()).orEmpty(),
                regenPerSecond = st?.regenPerSecond ?: 0.0,
                event = e?.let {
                    PlanetEvent(
                        id = it.id,
                        eventType = it.eventType,
                        faction = factionForRace(it.race.toLong()).orEmpty(),
                        health = it.health,
                        maxHealth = it.maxHealth,
                        startTime = warTimeToIso(it.startTime, status),
                        endTime = warTimeToIso(it.expireTime, status),
                        campaignId = it.campaignId,
                        jointOperationIds = it.jointOperationIds,
                    )
                },
                statistics = Statistics(
                    missionsWon = ps?.missionsWon ?: 0,
                    missionsLost = ps?.missionsLost ?: 0,
                    missionTime = ps?.missionTime ?: 0,
                    terminidKills = ps?.bugKills ?: 0,
                    automatonKills = ps?.automatonKills ?: 0,
                    illuminateKills = ps?.illuminateKills ?: 0,
                    bulletsFired = ps?.bulletsFired ?: 0,
                    bulletsHit = ps?.bulletsHit ?: 0,
                    timePlayed = ps?.timePlayed ?: 0,
                    deaths = ps?.deaths ?: 0,
                    revives = ps?.revives ?: 0,
                    friendlies = ps?.friendlies ?: 0,
                    missionSuccessRate = (ps?.missionSuccessRate ?: 0).toInt(),
                    accuracy = (ps?.accurracy ?: 0).toInt(),
                    playerCount = st?.players ?: 0,
                ),
                attacking = attacks[p.index].orEmpty(),
                regions = regionInfo[p.index].orEmpty().map { r ->
                    val rs = regionStatus[p.index to r.regionIndex]
                    Region(
                        id = r.regionIndex,
                        name = static.regions[java.lang.Long.toUnsignedString(r.settingsHash)],
                        health = rs?.health,
                        maxHealth = r.maxHealth,
                        size = REGION_SIZES.getOrNull(r.regionSize),
                        regenPerSecond = rs?.regerPerSecond,
                        availabilityFactor = rs?.availabilityFactor,
                        isAvailable = rs?.isAvailable ?: false,
                        players = rs?.players ?: 0,
                    )
                },
            )
        }
    }

    fun campaigns(status: AhStatus, planets: List<Planet>): List<Campaign> {
        val byIndex = planets.associateBy { it.index }
        return status.campaigns.mapNotNull { c ->
            val planet = byIndex[c.planetIndex] ?: return@mapNotNull null
            Campaign(id = c.id, planet = planet, type = c.type, count = c.count, faction = factionForRace(c.race.toLong()).orEmpty())
        }
    }

    fun rawStatus(status: AhStatus): RawWarStatus =
        RawWarStatus(time = status.time, planetActiveEffects = status.planetActiveEffects, spaceStations = status.spaceStations)

    fun assignments(raw: List<AhAssignment>): List<Assignment> = raw.map { a ->
        Assignment(
            id = a.id32,
            progress = a.progress,
            title = a.setting.overrideTitle,
            briefing = a.setting.overrideBrief,
            description = a.setting.taskDescription,
            tasks = a.setting.tasks,
            reward = a.setting.reward?.let { Reward(it.type, it.amount) },
            rewards = a.setting.rewards.filterNotNull().map { Reward(it.type, it.amount) },
            expiration = Instant.now().plusSeconds(a.expiresIn).toString(),
            flags = a.setting.flags,
        )
    }

    fun dispatches(raw: List<AhNewsItem>, status: AhStatus): List<Dispatch> =
        raw.filter { it.message.isNotBlank() }
            .map { Dispatch(id = it.id, published = warTimeToIso(it.published, status), type = it.type, message = it.message) }
            .sortedByDescending { it.published }

    fun spaceStation(raw: AhSpaceStation, status: AhStatus, planets: List<Planet>): SpaceStation? {
        val planet = planets.firstOrNull { it.index == raw.planetIndex } ?: return null
        return SpaceStation(
            id32 = raw.id32,
            planet = planet,
            electionEnd = warTimeToIso(raw.currentElectionEndWarTime, status),
            flags = raw.flags,
            tacticalActions = raw.tacticalActions.map { t ->
                TacticalAction(
                    id32 = t.id32,
                    mediaId32 = t.mediaId32,
                    name = t.name,
                    description = t.description,
                    strategicDescription = t.strategicDescription,
                    status = t.status,
                    statusExpire = warTimeToIso(t.statusExpireAtWarTimeSeconds, status),
                    costs = t.cost,
                    effectIds = t.effectIds,
                )
            },
        )
    }

    private companion object {
        val REGION_SIZES = listOf("Settlement", "Town", "City", "MegaCity")
    }
}
