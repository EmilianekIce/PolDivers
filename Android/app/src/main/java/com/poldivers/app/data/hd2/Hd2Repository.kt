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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.time.Duration
import java.time.Instant

class Hd2Repository(
    private val api: Hd2ApiService,
    /** Local history used to turn snapshots into rates / ETAs (see [TrendStore]). */
    val trends: TrendStore,
    private val effectCatalog: PlanetEffectCatalog,
    /** Current Accept-Language tag; cached responses are per language. */
    private val language: () -> String = { "" },
) {
    private var lastSaveMs = 0L

    private class Entry(val value: Any?, val timeMs: Long, val language: String)
    private val cache = ConcurrentHashMap<String, Entry>()
    private val locks = ConcurrentHashMap<String, Mutex>()

    /**
     * Every screen shares one copy of each endpoint for [ttlMs] (the API itself only syncs with
     * the game every ~20 s), and concurrent callers wait for the same request instead of firing
     * their own -- the API allows just 5 requests per 10 s.
     */
    @Suppress("UNCHECKED_CAST")
    private suspend fun <T> cached(key: String, ttlMs: Long, fetch: suspend () -> T): T {
        val mutex = locks.getOrPut(key) { Mutex() }
        return mutex.withLock {
            val lang = language()
            val now = System.currentTimeMillis()
            val hit = cache[key]?.takeIf { it.language == lang && now - it.timeMs < ttlMs }
            if (hit != null) {
                hit.value as T
            } else {
                fetch().also { cache[key] = Entry(it, System.currentTimeMillis(), lang) }
            }
        }
    }

    suspend fun getWar(): War = cached("war", SLOW_TTL_MS) { api.getWar() }

    suspend fun getPlanets(): List<Planet> = cached("planets", LIVE_TTL_MS) {
        api.getPlanets().also { planets -> track { planets.forEach { recordPlanet(it) } } }
    }

    suspend fun getPlanetsSortedByPlayers(): List<Planet> =
        getPlanets().sortedByDescending { it.playerCount }

    suspend fun getCampaigns(): List<Campaign> = cached("campaigns", LIVE_TTL_MS) {
        api.getCampaigns().sortedByDescending { it.planet.playerCount }.also { campaigns ->
            track { campaigns.forEach { recordPlanet(it.planet, withRegions = true) } }
        }
    }

    suspend fun getAssignments(): List<Assignment> = cached("assignments", LIVE_TTL_MS) {
        api.getAssignments().also { assignments ->
            track {
                assignments.forEach { assignment ->
                    assignment.progress.forEachIndexed { i, value ->
                        trends.record(TrendStore.taskKey(assignment.id, i), value.toDouble())
                    }
                }
            }
        }
    }

    /** Assignments in English -- campaign / phase names are parsed from the English title. */
    suspend fun getAssignmentsEnglish(): List<Assignment> =
        if (language().startsWith("en")) {
            getAssignments()
        } else {
            cached("assignments-en", LIVE_TTL_MS) { api.getAssignments("en-US") }
        }

    /** English planet name (wiki file names use it), fetched only when a planet's details are opened. */
    suspend fun getPlanetEnglishName(index: Int): String =
        cached("planet-en-$index", 24 * 60 * 60_000L) { api.getPlanet(index, "en-US").name }

    /** English tactical action names by id (icons are matched on the English name). */
    suspend fun getTacticalActionNamesEnglish(): Map<Long, String> = cached("stations-en", SLOW_TTL_MS) {
        api.getSpaceStations("en-US").flatMap { it.tacticalActions }.associate { it.id32 to it.name }
    }

    /** Active galactic effects per planet index (enemy variants, Gloom, augmentations...). */
    suspend fun getPlanetEffects(): Map<Int, List<PlanetEffect>> = cached("effects", LIVE_TTL_MS) {
        api.getRawWarStatus().planetActiveEffects
            .groupBy({ it.index }, { it.galacticEffectId })
            .mapValues { (_, ids) -> effectCatalog.resolve(ids) }
            .filterValues { it.isNotEmpty() }
    }

    suspend fun getDispatches(): List<Dispatch> = cached("dispatches", SLOW_TTL_MS) {
        api.getDispatches().sortedByDescending { it.published }
    }

    suspend fun getSpaceStations(): List<SpaceStation> = cached("stations", LIVE_TTL_MS) {
        api.getSpaceStations().also { stations ->
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

    private companion object {
        const val LIVE_TTL_MS = 45_000L
        const val SLOW_TTL_MS = 5 * 60_000L
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
