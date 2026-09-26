package com.poldivers.app.core.i18n

/**
 * Terms the community API only returns in English (they come from its static data, not from the
 * game's localised feed). Polish wording taken from the game's own pl localisation table.
 */
object GameTerms {
    private val HAZARDS = mapOf(
        "acid storms" to "Burze kwasu",
        "blizzards" to "Zamiecie",
        "extreme cold" to "Ekstremalny chłód",
        "fire tornadoes" to "Ogniste tornada",
        "fire tornados" to "Ogniste tornada",
        "intense heat" to "Intensywny upał",
        "ion storms" to "Burze jonowe",
        "meteor storms" to "Burze meteorów",
        "rainstorms" to "Ulewy",
        "sandstorms" to "Burze piaskowe",
        "thick fog" to "Gęsta mgła",
        "tremors" to "Wstrząsy",
        "volcanic activity" to "Aktywność wulkaniczna",
    )

    /** Polish name of an environmental condition; the English original when unknown. */
    fun hazard(name: String): String = HAZARDS[name.trim().lowercase()] ?: name
}
