package com.poldivers.app.core.i18n

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Planet biomes and environmental conditions.
 *
 * The API only reports some conditions (e.g. not Super Earth's rainstorms) and only in English,
 * so we bundle the community planet data (assets/planet_conditions.json) plus names and
 * descriptions (assets/game_terms.json): Polish wording is taken from the game's own pl
 * localisation table, everything else falls back to English (translatable in the UI).
 */
class GameTerms(context: Context) {

    @Serializable
    data class Text(val name: String = "", val description: String? = null)

    @Serializable
    private data class Entry(val pl: Text? = null, val en: Text? = null)

    @Serializable
    private data class Terms(val hazards: Map<String, Entry> = emptyMap(), val biomes: Map<String, Entry> = emptyMap())

    @Serializable
    private data class PlanetConditions(val biome: String? = null, val conditions: List<String> = emptyList())

    /** A displayable term; [official] = wording from the game, not our fallback. */
    data class Term(val key: String, val name: String, val description: String?, val englishName: String, val official: Boolean)

    private val assets = context.applicationContext.assets
    private val json = Json { ignoreUnknownKeys = true }

    private val terms: Terms by lazy {
        runCatching { json.decodeFromString<Terms>(read("game_terms.json")) }.getOrDefault(Terms())
    }
    private val planets: Map<Int, PlanetConditions> by lazy {
        runCatching {
            json.decodeFromString<Map<String, PlanetConditions>>(read("planet_conditions.json"))
                .mapNotNull { (k, v) -> k.toIntOrNull()?.let { it to v } }.toMap()
        }.getOrDefault(emptyMap())
    }

    private fun read(name: String) = assets.open(name).bufferedReader().use { it.readText() }

    private fun term(key: String, entry: Entry?, languageTag: String): Term? {
        entry ?: return null
        val en = entry.en
        val local = if (languageTag.startsWith("pl")) entry.pl else null
        val text = local ?: en ?: return null
        return Term(key, text.name, text.description?.takeIf { it.isNotBlank() } ?: en?.description, en?.name ?: text.name, local != null)
    }

    /** All environmental conditions of a planet (environmentals + weather). */
    fun conditions(planetIndex: Int, languageTag: String): List<Term> =
        planets[planetIndex]?.conditions.orEmpty().mapNotNull { term(it, terms.hazards[it], languageTag) }

    fun biome(planetIndex: Int, languageTag: String): Term? =
        planets[planetIndex]?.biome?.let { term(it, terms.biomes[it], languageTag) }

    /** Polish name for an English condition name coming from the API. */
    fun hazardName(englishName: String, languageTag: String): String {
        if (!languageTag.startsWith("pl")) return englishName
        return terms.hazards.values.firstOrNull { it.en?.name.equals(englishName, ignoreCase = true) }?.pl?.name ?: englishName
    }
}
