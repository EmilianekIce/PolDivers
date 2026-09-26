package com.poldivers.app.data.hd2.model

import kotlinx.serialization.Serializable

/**
 * DTOs for the community "helldivers-2/api" wrapper (https://api.helldivers2.dev).
 *
 * All text fields below (name, title, briefing, message, ...) are typed as plain String.
 * That is only valid because we always send a concrete Accept-Language (e.g. "pl-PL") --
 * the API only returns the polymorphic {en-US, de-DE, ...} object when the special
 * "ivl-IV" pseudo-locale is requested, which this app never does.
 */

@Serializable
data class War(
    val started: String,
    val ended: String,
    val now: String,
    val clientVersion: String,
    val factions: List<String> = emptyList(),
    val impactMultiplier: Double = 0.0,
    val statistics: Statistics? = null,
)

@Serializable
data class Statistics(
    val missionsWon: Long = 0,
    val missionsLost: Long = 0,
    val missionTime: Long = 0,
    val terminidKills: Long = 0,
    val automatonKills: Long = 0,
    val illuminateKills: Long = 0,
    val bulletsFired: Long = 0,
    val bulletsHit: Long = 0,
    val timePlayed: Long = 0,
    val deaths: Long = 0,
    val revives: Long = 0,
    val friendlies: Long = 0,
    val missionSuccessRate: Int = 0,
    val accuracy: Int = 0,
    val playerCount: Long = 0,
)

@Serializable
data class Position(val x: Double = 0.0, val y: Double = 0.0)

@Serializable
data class Biome(val name: String = "", val description: String = "")

@Serializable
data class Hazard(val name: String = "", val description: String = "")

/** An ongoing event on a planet -- in practice a defense against an enemy attack. */
@Serializable
data class PlanetEvent(
    val id: Int,
    val eventType: Int = 0,
    val faction: String = "",
    val health: Long = 0,
    val maxHealth: Long = 0,
    val startTime: String? = null,
    val endTime: String? = null,
    val campaignId: Int? = null,
    val jointOperationIds: List<Int> = emptyList(),
) {
    /** Share of the defense already won by Helldivers, 0..100. */
    val defensePercent: Double
        get() = if (maxHealth <= 0) 0.0 else (1.0 - health.toDouble() / maxHealth.toDouble()) * 100.0
}

@Serializable
data class Region(
    val id: Int,
    val hash: Long = 0,
    val name: String? = null,
    val description: String? = null,
    val health: Long? = null,
    val maxHealth: Long = 0,
    val size: String? = null,
    val regenPerSecond: Double? = null,
    val availabilityFactor: Double? = null,
    val isAvailable: Boolean = false,
    val players: Long = 0,
) {
    /** 0..100, or null when the API has no health for this region. */
    val liberationPercent: Double?
        get() {
            val h = health ?: return null
            return if (maxHealth <= 0) null else ((1.0 - h.toDouble() / maxHealth) * 100.0).coerceIn(0.0, 100.0)
        }
}

@Serializable
data class Planet(
    val index: Int,
    val name: String,
    val sector: String,
    val biome: Biome? = null,
    val hazards: List<Hazard> = emptyList(),
    val hash: Long = 0,
    val position: Position = Position(),
    val waypoints: List<Int> = emptyList(),
    val maxHealth: Long = 0,
    val health: Long = 0,
    val disabled: Boolean = false,
    val initialOwner: String = "",
    val currentOwner: String = "",
    val regenPerSecond: Double = 0.0,
    val event: PlanetEvent? = null,
    val statistics: Statistics? = null,
    val attacking: List<Int> = emptyList(),
    val regions: List<Region> = emptyList(),
) {
    val liberationPercent: Double
        get() = if (maxHealth <= 0) 0.0 else (1.0 - health.toDouble() / maxHealth.toDouble()) * 100.0

    val playerCount: Long
        get() = statistics?.playerCount ?: 0

    /**
     * The region players are actually pushing (as helldiverscompanion does): under the region
     * system planet health can sit at 0 % while a city is a quarter taken. Locked regions are
     * skipped; ties go to the region with more players.
     */
    val leadingRegion: Region?
        get() {
            val unlocked = regions.filter { it.isAvailable && !it.name.isNullOrBlank() }
            val pool = unlocked.ifEmpty { regions.filter { (it.liberationPercent ?: 0.0) > 0.0 && !it.name.isNullOrBlank() } }
            return pool.maxWithOrNull(compareBy<Region>({ it.liberationPercent ?: 0.0 }, { it.players }))
        }
}

@Serializable
data class Campaign(
    val id: Int,
    val planet: Planet,
    val type: Int = 0,
    val count: Long = 0,
    val faction: String = "",
)

/**
 * One objective of a Major Order. The API documents neither [type] nor [valueTypes]; the
 * meaning below comes from the community-maintained https://github.com/helldivers-2/json
 * (assignments/tasks/task/{type,valueTypes}.json).
 */
@Serializable
data class Task(
    val type: Int,
    val values: List<Long> = emptyList(),
    val valueTypes: List<Long> = emptyList(),
) {
    /** Returns the value tagged with the given value type, e.g. [ValueType.GOAL]. */
    fun valueOf(valueType: Int): Long? {
        val i = valueTypes.indexOf(valueType.toLong())
        return if (i >= 0) values.getOrNull(i) else null
    }

    object Type {
        const val EXTRACT = 2
        const val ERADICATE = 3
        const val COMPLETE_MISSIONS = 7
        const val COMPLETE_OPERATIONS = 9
        const val LIBERATION = 11
        const val DEFENSE = 12
        const val CONTROL = 13
        const val EXPAND = 15
    }

    object ValueType {
        const val RACE = 1
        const val GOAL = 3
        const val UNIT_ID = 4
        const val ITEM_ID = 5
        const val DIFFICULTY = 9
        const val LOCATION_TYPE = 11
        const val LOCATION_INDEX = 12
    }
}

@Serializable
data class Reward(val type: Int, val amount: Long)

@Serializable
data class Assignment(
    val id: Long,
    val progress: List<Long> = emptyList(),
    val title: String = "",
    val briefing: String = "",
    val description: String = "",
    val tasks: List<Task> = emptyList(),
    val reward: Reward? = null,
    val rewards: List<Reward> = emptyList(),
    val expiration: String,
    val flags: Int = 0,
)

@Serializable
data class Dispatch(
    val id: Int,
    val published: String,
    val type: Int = 0,
    val message: String = "",
)

@Serializable
data class Cost(
    val id: String,
    val itemMixId: Long = 0,
    val targetValue: Long = 0,
    val currentValue: Double = 0.0,
    val deltaPerSecond: Double = 0.0,
    val maxDonationAmmount: Long = 0,
    val maxDonationPeriodSeconds: Long = 0,
)

@Serializable
data class TacticalAction(
    val id32: Long,
    val mediaId32: Long = 0,
    val name: String = "",
    val description: String = "",
    val strategicDescription: String = "",
    val status: Int = 0,
    val statusExpire: String? = null,
    val costs: List<Cost> = emptyList(),
    val effectIds: List<Int> = emptyList(),
) {
    /**
     * [status] is undocumented upstream; these values match what community trackers observe:
     * 1 = collecting resources, 2 = active (effects applied), 3 = cooling down.
     */
    object Status {
        const val PREPARING = 1
        const val ACTIVE = 2
        const val COOLDOWN = 3
    }
}

@Serializable
data class SpaceStation(
    val id32: Long,
    val planet: Planet,
    val electionEnd: String,
    val flags: Int = 0,
    val tacticalActions: List<TacticalAction> = emptyList(),
)

/** Faction names the API uses for owners/attackers, keyed by the game's internal race id. */
fun factionForRace(race: Long?): String? = when (race) {
    1L -> "Humans"
    2L -> "Terminids"
    3L -> "Automaton"
    4L -> "Illuminate"
    else -> null
}

@Serializable
data class RawWarStatus(
    val planetActiveEffects: List<RawPlanetEffect> = emptyList(),
)

@Serializable
data class RawPlanetEffect(
    val index: Int,
    val galacticEffectId: Int,
)
