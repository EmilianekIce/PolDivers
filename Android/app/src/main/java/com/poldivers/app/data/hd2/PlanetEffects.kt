package com.poldivers.app.data.hd2

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/** A galactic effect active on a planet, resolved to something displayable. */
data class PlanetEffect(
    val id: Int,
    /** Polish name where we have one, otherwise the game's English name. */
    val name: String,
    /** Original English name from the game data. */
    val originalName: String,
    val description: String,
    val kind: Kind,
) {
    enum class Kind { ENEMY_VARIANT, HAZARD, SUPPORT, SITE, OTHER }

    /** Very short label for tags / map badges. */
    val shortName: String get() = (if (':' in name) name.substringAfter(':') else name).trim()
}

/**
 * Names/descriptions come from the community-maintained helldivers-2/json (MIT, bundled as an
 * asset) -- the API only reports effect ids. The game's localized names are not exposed, so the
 * notable enemy variants get Polish names here; everything else keeps the English original.
 */
class PlanetEffectCatalog(context: Context) {

    @Serializable
    private data class Entry(val galacticEffectId: Int = 0, val name: String = "", val description: String = "")

    private val entries: Map<Int, Entry> by lazy {
        runCatching {
            val text = context.applicationContext.assets.open("planet_effects.json").bufferedReader().use { it.readText() }
            Json { ignoreUnknownKeys = true }.decodeFromString<Map<String, Entry>>(text)
                .mapKeys { it.key.toInt() }
        }.getOrDefault(emptyMap())
    }

    /** Effects of one planet, de-duplicated (the game lists e.g. "X" and "X (Enemies)"), hidden markers removed. */
    fun resolve(ids: List<Int>): List<PlanetEffect> = ids
        .mapNotNull { id -> entries[id]?.let { toEffect(id, it) } }
        .filterNot { it.originalName.contains("marker", ignoreCase = true) || it.originalName in HIDDEN }
        .distinctBy { it.name.lowercase() }
        .sortedBy { it.kind.ordinal }

    private fun toEffect(id: Int, entry: Entry): PlanetEffect {
        val original = entry.name.replace(Regex("\\s*\\(enemies\\)", RegexOption.IGNORE_CASE), "").trim()
        val upper = original.uppercase()
        val polish = POLISH.entries.firstOrNull { upper.contains(it.key) }?.value
        val kind = when {
            ENEMY_KEYS.any { upper.contains(it) } -> PlanetEffect.Kind.ENEMY_VARIANT
            HAZARD_KEYS.any { upper.contains(it) } -> PlanetEffect.Kind.HAZARD
            SUPPORT_KEYS.any { upper.contains(it) } -> PlanetEffect.Kind.SUPPORT
            SITE_KEYS.any { upper.contains(it) } -> PlanetEffect.Kind.SITE
            else -> PlanetEffect.Kind.OTHER
        }
        val name = when {
            polish != null -> polish
            upper.startsWith("ARSENAL AUGMENTATION:") -> "Wzmocnienie arsenału: " + original.substringAfter(':').trim()
            else -> original
        }
        return PlanetEffect(id, name, original, entry.description, kind)
    }

    private companion object {
        val HIDDEN = setOf("Regen Override", "CAMPAIGN BLOCKER", "Unreachable")

        val ENEMY_KEYS = listOf(
            "JET BRIGADE", "PREDATOR STRAIN", "SPORE BURST", "INCINERATION CORPS", "RUPTURE STRAIN",
            "DRAGONROACH", "HIVE LORD", "CYBORGS", "MINDLESS MASSES", "APPROPRIATORS", "VOTE SNATCHERS",
            "INVASION FLEET", "GREAT HOST", "SURGE", "RAMPAGE",
        )
        val HAZARD_KEYS = listOf("GLOOM", "BLACK HOLE", "EXOSTORM", "VOID", "FRACTURED", "MOVING PLANET", "VERGE OF DESTRUCTION", "HIVE WORLD")
        val SUPPORT_KEYS = listOf("ARSENAL AUGMENTATION", "EAGLE STORM", "ORBITAL BLOCKADE", "BOMBARDMENT", "DEMOCRACY SPACE STATION", "SEAF", "EXOSUIT RESERVES", "OPERATIONAL SUPPORT")
        val SITE_KEYS = listOf("FACTOR", "CENTER", "CITY", "SITE", "LABORATORY", "HUB", "BASE", "MINE", "FACILIT", "COMPLEX", "PRESERVE", "PARK", "ARRAY", "OUTPOST", "DATA CENTER", "CECOD")

        // Official Polish names from the game's own pl localisation table where it has them.
        // Longest keys first so e.g. "DENSE GLOOM" wins over "GLOOM".
        val POLISH = linkedMapOf(
            "JET BRIGADE FACTORIES" to "Fabryki Brygady Odrzutowej",
            "JET BRIGADE" to "Brygada Odrzutowa",
            "SPORE BURST SCAVENGER RAMPAGE" to "Szał padlinożerców z zarodnikami",
            "SPORE BURST STRAIN" to "Szczep buchnięć zarodników",
            "PREDATOR STRAIN" to "Szczep drapieżców",
            "RUPTURE STRAIN" to "Szczep pęknięć",
            "INCINERATION CORPS" to "Korpus spalania",
            "DRAGONROACHES" to "Smokoluchy",
            "HIVE LORDS" to "Władcy roju",
            "CYBORGS" to "Cyborgi",
            "MINDLESS MASSES" to "Bezrozumne masy",
            "APPROPRIATORS" to "Przywłaszczyciele",
            "VOTE SNATCHERS" to "Przejmujący głosy",
            "INVASION FLEET" to "Flota inwazyjna",
            "THE GREAT HOST" to "Wielki Zastęp",
            "FACTORY STRIDER SURGE" to "Nawała kroczących fabryk",
            "HEAVY ARMOR SURGE" to "Napływ sił pancernych",
            "HULK SURGE" to "Nawała hulków",
            "DEVASTATOR SURGE" to "Nawała dewastatorów",
            "IMPALER RAMPAGE" to "Szał nabijaczy",
            "CHARGER RAMPAGE" to "Szał szarżowników",
            "DENSE GLOOM" to "Gęsty mrok",
            "LIGHT GLOOM" to "Lekki mrok",
            "GLOOM BORDER" to "Granica mroku",
            "GLOOM" to "Mrok",
            "MERIDIAN BLACK HOLE" to "Meridiańska czarna dziura",
            "CONVENTIONAL BLACK HOLE" to "Konwencjonalna czarna dziura",
            "BLACK HOLE" to "Czarna dziura",
            "CLASS 1 EXOSTORM" to "Egzoburza klasy 1",
            "CLASS 2 EXOSTORM" to "Egzoburza klasy 2",
            "CLASS 3 EXOSTORM" to "Egzoburza klasy 3",
            "FRACTURED PLANET" to "Pęknięta planeta",
            "MOVING PLANET" to "Wędrująca planeta",
            "VERGE OF DESTRUCTION" to "Na skraju zagłady",
            "HIVE WORLD" to "Świat-rój",
            "EAGLE STORM" to "Burza Orła",
            "ORBITAL BLOCKADE" to "Orbitalna blokada",
            "PLANETARY BOMBARDMENT" to "Bombardowanie planetarne",
            "DEMOCRACY SPACE STATION" to "Demokratyczna Stacja Kosmiczna",
            "HEAVY SEAF PRESENCE" to "Silna obecność SEAF",
            "EXOSUIT RESERVES" to "Rezerwy egzoszkieletów",
            "TERMINID CONTROL SYSTEM" to "System Kontroli Terminidów",
        )
    }
}
