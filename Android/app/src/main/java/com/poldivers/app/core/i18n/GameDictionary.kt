package com.poldivers.app.core.i18n

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Official game wording, built from the game's own English and Polish localisation tables
 * (same string ids in both): English -> Polish for names/descriptions the community data only
 * has in English, and Polish -> English to look game terms up in the (English) wiki.
 */
class GameDictionary(context: Context) {

    @Serializable
    private data class Dict(val en2pl: Map<String, String> = emptyMap(), val pl2en: Map<String, String> = emptyMap())

    private val assets = context.applicationContext.assets
    private val dict: Dict by lazy {
        runCatching {
            Json { ignoreUnknownKeys = true }
                .decodeFromString<Dict>(assets.open("game_dict_pl.json").bufferedReader().use { it.readText() })
        }.getOrDefault(Dict())
    }

    private fun variants(text: String): List<String> {
        val t = text.trim().lowercase().replace(Regex("""\s*\(enemies\)"""), "")
        return listOf(t, "the $t", t.removePrefix("the "))
    }

    /** Official Polish text for an English game string, or null. */
    fun toPolish(english: String): String? = variants(english).firstNotNullOfOrNull { dict.en2pl[it] }

    /** Official English text for a Polish game term (e.g. "Korpus spalania" -> "THE INCINERATION CORPS"). */
    fun toEnglish(polish: String): String? =
        dict.pl2en[polish.trim().lowercase()]?.removePrefix("THE ")?.removePrefix("The ")

    companion object {
        /** "BRYGADA ODRZUTOWA" -> "Brygada odrzutowa" (game strings are often all caps). */
        fun sentenceCase(text: String): String =
            if (text != text.uppercase() || text.none { it.isLetter() }) {
                text
            } else {
                text.lowercase().replaceFirstChar { it.titlecase() }
            }
    }
}
