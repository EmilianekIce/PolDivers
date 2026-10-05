package com.poldivers.app.feature.planets

import android.content.Context
import androidx.compose.ui.graphics.Color
import com.poldivers.app.core.art.GameArt
import com.poldivers.app.data.hd2.PlanetEffect
import com.poldivers.app.ui.common.factionColor
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The galaxy map as plain data for the shared 3D renderer (assets/map3d/galaxy3d.js) -- the same
 * model the website builds in Web/js/map.js, computed with the same rules as the 2D map: sector
 * colours, supply-line gradient colours, effect droplets, the Gloom and the destroyed worlds.
 */
internal fun buildMap3dModel(context: Context, data: PlanetsData, art: GameArt): JsonObject {
    val planets = data.planets.filter { it.index == 0 || abs(it.position.x) > 0.004 || abs(it.position.y) > 0.004 }
    val byIndex = planets.associateBy { it.index }
    val supply = supplyNodeColors(planets)
    val cells = loadSectorCells(context)
    val cellColors = cellOwnerColors(cells, planets)

    return buildJsonObject {
        putJsonArray("planets") {
            planets.forEach { p ->
                val effects = data.effects[p.index].orEmpty()
                val english = art.englishPlanetName(p.index)
                val fractured = english in FRACTURED || effects.any { it.originalName.contains("FRACTURED", ignoreCase = true) }
                val front = p.index in data.campaignPlanets
                addJsonObject {
                    put("i", p.index)
                    put("name", p.name)
                    put("x", p.position.x)
                    put("y", p.position.y)
                    put("owner", p.currentOwner)
                    put("sector", p.sector)
                    put("ev", p.event?.faction?.let(::JsonPrimitive) ?: JsonNull)
                    put("players", p.playerCount)
                    put("front", front)
                    put("mo", p.index in data.majorOrderPlanets)
                    put("quiet", data.isQuietOurs(p))
                    put("art", art.planetIcon(p.index, effects)?.let { JsonPrimitive(assetUrl(it)) } ?: JsonNull)
                    putJsonArray("drops") {
                        effects
                            .filter { e ->
                                (e.kind == PlanetEffect.Kind.ENEMY_VARIANT || e.kind == PlanetEffect.Kind.HAZARD || e.kind == PlanetEffect.Kind.SITE ||
                                    e.originalName.contains("SEAF", ignoreCase = true)) &&
                                    !e.originalName.contains("GLOOM", ignoreCase = true) &&
                                    !e.originalName.contains("FRACTURED", ignoreCase = true) &&
                                    !e.originalName.contains("BLACK HOLE", ignoreCase = true)
                            }
                            .mapNotNull { e -> art.effectIcon(e)?.let { e to it } }
                            .take(3)
                            .forEach { (e, icon) ->
                                addJsonObject {
                                    put("c", dropletColor(e, p).hex())
                                    put("icon", assetUrl(icon))
                                }
                            }
                    }
                    putJsonArray("links") { p.waypoints.filter { it in byIndex }.forEach { add(it) } }
                    putJsonArray("atk") { p.attacking.filter { it in byIndex }.forEach { add(it) } }
                    put("sup", (supply[p.index] ?: factionColor("Humans")).hex())
                    put("kind", if (english in BLACK_HOLES) "hole" else if (fractured) "rubble" else "planet")
                    put("gloom", effects.any { it.originalName.contains("GLOOM", ignoreCase = true) })
                }
            }
        }
        putJsonArray("cells") {
            cells.forEachIndexed { i, pts ->
                addJsonObject {
                    put("pts", JsonArray(pts.map { JsonPrimitive(it) }))
                    put("c", cellColors.getOrNull(i)?.let { JsonPrimitive(it.hex()) } ?: JsonNull)
                }
            }
        }
        putJsonArray("sectors") {
            sectorCentroids(planets).forEach { (name, at) ->
                add(buildJsonArray { add(name); add(at.x); add(at.y) })
            }
        }
        put("dss", data.dssPlanet?.let(::JsonPrimitive) ?: JsonNull)
        put("dssIcon", art.icon("DSS_Icon")?.let { JsonPrimitive(assetUrl(it)) } ?: JsonNull)
    }
}

/** Host the 3D map's WebView serves the app's assets from (GalaxyMap3D intercepts it). */
internal const val MAP3D_HOST = "poldivers.local"

/** "file:///android_asset/planets/x.webp" -> "/planets/x.webp" on the map page's own host. */
private fun assetUrl(url: String): String = "/" + url.removePrefix("file:///android_asset/").trimStart('/')

private fun Color.hex(): String =
    "#%02X%02X%02X".format((red * 255).roundToInt(), (green * 255).roundToInt(), (blue * 255).roundToInt())
