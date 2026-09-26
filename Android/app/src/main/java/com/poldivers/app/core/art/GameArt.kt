package com.poldivers.app.core.art

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.poldivers.app.data.hd2.PlanetEffect
import com.poldivers.app.data.hd2.model.Task
import java.net.URLEncoder

/**
 * Game artwork bundled from the helldivers.wiki.gg image dump (see Android/tools/import_wiki_assets.py):
 * icons in assets/icons, campaign headers in assets/campaigns. Lookups are by (normalised)
 * wiki file name, so e.g. "ARSENAL AUGMENTATION: ORBITAL LASER" finds Orbital_Laser_Stratagem_Icon.
 */
class GameArt(context: Context) {

    private val assets = context.applicationContext.assets

    private val icons: Map<String, String> by lazy { index("icons") }
    private val planets: Map<String, String> by lazy { index("planets") }

    /** English planet names by index (community planets.json) -- wiki file names use them. */
    private val englishNames: Map<Int, String> by lazy {
        runCatching {
            val text = assets.open("planet_names_en.json").bufferedReader().use { it.readText() }
            kotlinx.serialization.json.Json.decodeFromString<Map<String, String>>(text)
                .mapNotNull { (k, v) -> k.toIntOrNull()?.let { it to v } }.toMap()
        }.getOrDefault(emptyMap())
    }

    fun englishPlanetName(index: Int): String? = englishNames[index]

    /**
     * Bundled planet artwork (assets/planets). When the planet has an effect with its own art
     * (exostorm class, Gloom, black hole, Void) that variant is used: Keid__Exostorm_C2.webp etc.
     */
    private fun planetFile(index: Int, effects: List<PlanetEffect>): String? {
        val english = englishNames[index] ?: return null
        val base = norm(english.replace(Regex("""\s*\(.*\)"""), ""))
        val variants = effects.mapNotNull { e ->
            val n = e.originalName.uppercase()
            Regex("""CLASS (\d) EXOSTORM""").find(n)?.let { "exostormc" + it.groupValues[1] }
                ?: when {
                    "GLOOM" in n -> "gloom"
                    "BLACK HOLE" in n -> "blackhole"
                    "VOID" in n -> "void"
                    else -> null
                }
        } + if ("(void)" in english.lowercase()) listOf("void") else emptyList()
        return (variants.map { base + it } + base).firstNotNullOfOrNull { planets[it] }
    }

    fun planetIcon(index: Int, effects: List<PlanetEffect> = emptyList()): String? =
        planetFile(index, effects)?.let { "file:///android_asset/planets/$it" }

    fun planetIconBitmap(index: Int, effects: List<PlanetEffect> = emptyList()): ImageBitmap? {
        val file = planetFile(index, effects) ?: return null
        return runCatching { assets.open("planets/$file").use { BitmapFactory.decodeStream(it)?.asImageBitmap() } }.getOrNull()
    }

    val hasPlanetIcons: Boolean get() = planets.isNotEmpty()
    private val campaigns: Map<String, String> by lazy { index("campaigns") }

    private fun index(dir: String): Map<String, String> =
        runCatching { assets.list(dir).orEmpty() }.getOrDefault(emptyArray())
            .filter { it.endsWith(".webp") }
            .associateBy { norm(it.removeSuffix(".webp")) }

    /** Asset URI for Coil, or null if we do not ship that icon. */
    fun icon(fileStem: String): String? = icons[norm(fileStem)]?.let { "file:///android_asset/icons/$it" }

    fun iconBitmap(fileStem: String): ImageBitmap? {
        val file = icons[norm(fileStem)] ?: return null
        return runCatching { assets.open("icons/$file").use { BitmapFactory.decodeStream(it)?.asImageBitmap() } }.getOrNull()
    }

    /**
     * Header art for a campaign ("Counterdissident Hammer"); for a phase, its own variant when the
     * wiki has one (`_A`, `_B`, `_C` or `_Phase_n`), else the campaign header.
     */
    fun campaignHeader(campaignKey: String, phase: Int? = null): String? {
        val base = norm(campaignKey)
        val candidates = buildList {
            if (phase != null) {
                add(base + ('a' + (phase - 1)))
                add(base + "phase$phase")
            }
            add(base)
        }
        return candidates.firstNotNullOfOrNull { campaigns[it] }?.let { "file:///android_asset/campaigns/$it" }
    }

    /**
     * Artwork for a wiki image name ("Galactic War Campaigns Header Census Thunder.png"): the
     * bundled copy when we have it, otherwise the wiki file itself (loaded on demand).
     */
    fun wikiImage(fileName: String?, width: Int = 1280): String? {
        val name = fileName?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val stem = name.substringBeforeLast('.').replace(Regex("(?i)^Galactic[ _]War[ _]Campaigns[ _]Header[ _]"), "")
        campaigns[norm(stem)]?.let { return "file:///android_asset/campaigns/$it" }
        return "https://helldivers.wiki.gg/wiki/Special:FilePath/" +
            URLEncoder.encode(name.replace(' ', '_'), "UTF-8").replace("+", "%20") + "?width=$width"
    }

    /** Wiki campaign reward type ("medal", "stratagem", "primary-weapon", "armor", "cape"...). */
    fun wikiRewardIcon(type: String): String? = icon(
        when (type.lowercase()) {
            "medal", "medals" -> "Medal"
            "stratagem" -> "Stratagem_Permit"
            "primary-weapon", "secondary-weapon", "weapon" -> "Muzzle_Icon"
            "armor", "helmet" -> "Helldiver_Icon"
            "super-credits", "super credits" -> "Super_Credit"
            "requisition" -> "Requisition_Slip"
            else -> "Badge"
        },
    )

    fun rewardIcon(type: Int): String? = icon(
        when (type) {
            1 -> "Medal"
            2 -> "Super_Credit"
            3 -> "Common_Sample_Icon"
            4 -> "Requisition_Slip"
            else -> "Medal"
        },
    )

    fun factionIcon(faction: String): String? = icon(
        when (faction) {
            "Terminids" -> "Terminid_Icon"
            "Automaton" -> "Automaton_Icon"
            "Illuminate" -> "Illuminate_Icon"
            "Humans" -> "Super_Earth_Icon"
            else -> "Unknown_Faction"
        },
    )

    /** Icon for a planet effect: enemy variant emblem, stratagem for arsenal augmentations, etc. */
    fun effectIcon(effect: PlanetEffect): String? = effectIconStem(effect)?.let(::icon)

    fun effectIconBitmap(effect: PlanetEffect): ImageBitmap? = effectIconStem(effect)?.let(::iconBitmap)

    private fun effectIconStem(effect: PlanetEffect): String? {
        val name = effect.originalName.uppercase()
        EFFECT_ICONS.entries.firstOrNull { name.contains(it.key) }?.let { return it.value }
        if (name.startsWith("ARSENAL AUGMENTATION:")) {
            val stratagem = name.substringAfter(':').trim()
            // The wiki names icons without the model designation: "EXO-45 PATRIOT EXOSUIT" -> Patriot_Exosuit.
            val withoutModel = stratagem.replace(Regex("""^\S*\d\S*\s+"""), "")
            return listOf(stratagem, withoutModel)
                .map { "${it}_Stratagem_Icon" }
                .firstOrNull { icons.containsKey(norm(it)) }
                ?: "Mission_Stratagem_Fallback_Icon"
        }
        return null
    }

    /** Environmental condition icon for a planet hazard ("Acid Storms", "Extreme Cold"...). */
    fun hazardIcon(hazardName: String): String? = icon("${hazardName}_Environmental_Condition_Icon")

    fun campaignTypeIcon(isDefense: Boolean, type: Int): String? = icon(
        when {
            isDefense -> "Defense_Campaign_Icon"
            type == 1 -> "Recon_Campaign_Icon"
            else -> "Liberation_Campaign_Icon"
        },
    )

    /** Icon for a Major Order objective, picked from its type and target faction. */
    fun taskIcon(task: Task?, faction: String?): String? = icon(
        when (task?.type) {
            Task.Type.ERADICATE -> when (faction) {
                "Terminids" -> "Eradicate_Terminid_Swarm_Mission_Icon"
                "Automaton" -> "Eradicate_Automaton_Forces_Mission_Icon"
                "Illuminate" -> "Destroy_Illuminate_Warp_Ships_Mission_Icon"
                else -> "Eradicate_Terminid_Swarm_Mission_Icon"
            }
            Task.Type.DEFENSE -> "Defense_Campaign_Icon"
            Task.Type.LIBERATION, Task.Type.EXPAND -> "Liberation_Campaign_Icon"
            Task.Type.CONTROL -> "Locations_Icon"
            Task.Type.EXTRACT -> "Common_Sample_Icon"
            Task.Type.COMPLETE_MISSIONS, Task.Type.COMPLETE_OPERATIONS -> "Operation_Icon"
            else -> "Operation_Icon"
        },
    )

    /** DSS tactical action by its English name ("Eagle Storm", "Orbital Blockade"...). */
    fun dssActionIcon(englishName: String?): String? {
        val n = englishName?.uppercase().orEmpty()
        return icon(
            when {
                "EAGLE" in n -> "DSS_Eagle_Icon"
                "BLOCKADE" in n -> "DSS_Orbital_Blockade_Icon"
                "BOMBARDMENT" in n -> "DSS_Planetary_Bombardment_Icon"
                "ORDNANCE" in n -> "DSS_Heavy_Ordnance_Distribution_Icon"
                else -> "DSS_Action_Fallback_Icon"
            },
        )
    }

    companion object {
        fun norm(s: String) = s.lowercase().filter { it.isLetterOrDigit() }

        /** On-demand planet artwork from the wiki (loaded only when a planet's details are opened). */
        fun planetImageUrl(englishPlanetName: String, width: Int = 480): String =
            "https://helldivers.wiki.gg/wiki/Special:FilePath/" +
                URLEncoder.encode(englishPlanetName.replace(' ', '_') + "_Planet_Icon.png", "UTF-8").replace("+", "%20") +
                "?width=$width"

        private val EFFECT_ICONS = linkedMapOf(
            "JET BRIGADE" to "Jet_Brigade_Icon",
            "PREDATOR STRAIN" to "Predator_Strain_Icon",
            "SPORE BURST" to "Spore_Burst_Strain_Icon",
            "INCINERATION CORPS" to "Incineration_Corps_Icon",
            "RUPTURE STRAIN" to "Rupture_Strain_Icon",
            "DRAGONROACH" to "Dragonroach_Icon",
            "HIVE LORD" to "Hive_Lord_Icon",
            "CYBORG" to "Cyborgs_Icon",
            "MINDLESS MASSES" to "Mindless_Masses_Icon",
            "APPROPRIATORS" to "Appropriators_Icon",
            "VOTE SNATCHERS" to "Vote_Snatchers_Icon",
            "INVASION FLEET" to "Invasion_Fleet_Enemy_Icon",
            "HEAVY SEAF" to "Heavy_SEAF_Presence_Icon",
            "TERMINID CONTROL SYSTEM" to "Terminid_Containment_Icon",
            "DEMOCRACY SPACE STATION" to "DSS_Icon",
            "EAGLE STORM" to "DSS_Eagle_Icon",
            "ORBITAL BLOCKADE" to "DSS_Orbital_Blockade_Icon",
            "PLANETARY BOMBARDMENT" to "DSS_Planetary_Bombardment_Icon",
            "FACTORY HUB" to "Automaton_Megafactory_Icon",
            "MEGAFACTORY" to "Automaton_Megafactory_Icon",
        )
    }
}

/** Small helper: an icon from [GameArt] (asset URI), nothing if we have none. */
@Composable
fun GameIcon(uri: String?, size: Dp = 24.dp, modifier: Modifier = Modifier, contentDescription: String? = null) {
    if (uri == null) return
    AsyncImage(
        model = uri,
        contentDescription = contentDescription,
        contentScale = ContentScale.Fit,
        modifier = modifier.size(size),
    )
}

@Composable
fun rememberGameArt(): GameArt {
    val context = LocalContext.current
    return remember { com.poldivers.app.core.AppContainer.get(context).art }
}

/** Reward as the game shows it: icon + "×amount" (medals, super credits, samples, requisition). */
@Composable
fun RewardChip(type: Int, amount: Long, modifier: Modifier = Modifier, iconSize: Dp = 22.dp) {
    val art = rememberGameArt()
    androidx.compose.foundation.layout.Row(
        modifier,
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp),
    ) {
        GameIcon(art.rewardIcon(type), size = iconSize)
        androidx.compose.material3.Text(
            "×$amount",
            style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
            color = androidx.compose.ui.graphics.Color(0xFFFFC400),
        )
    }
}

/** Player count as in the game: Helldiver emblem + compact number ("12,3 tys."). Never overflows. */
@Composable
fun PlayerCount(
    count: Long,
    modifier: Modifier = Modifier,
    color: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Unspecified,
    style: androidx.compose.ui.text.TextStyle = androidx.compose.material3.MaterialTheme.typography.labelLarge,
) {
    val art = rememberGameArt()
    androidx.compose.foundation.layout.Row(
        modifier,
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(3.dp),
    ) {
        GameIcon(art.icon("Helmet_Currency_Icon"), size = 15.dp)
        androidx.compose.material3.Text(
            com.poldivers.app.ui.common.formatCompact(count),
            style = style,
            color = color,
            maxLines = 1,
            softWrap = false,
        )
    }
}
