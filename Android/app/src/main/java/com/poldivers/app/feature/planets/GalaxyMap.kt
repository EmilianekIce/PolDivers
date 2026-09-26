package com.poldivers.app.feature.planets

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.poldivers.app.R
import com.poldivers.app.core.art.rememberGameArt
import com.poldivers.app.data.hd2.PlanetEffect
import com.poldivers.app.data.hd2.model.Planet
import com.poldivers.app.ui.common.factionColor
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.FilterQuality
import com.poldivers.app.ui.theme.FactionAutomaton
import com.poldivers.app.ui.theme.FactionHuman
import com.poldivers.app.ui.theme.FactionIlluminate
import com.poldivers.app.ui.theme.FactionTerminid
import com.poldivers.app.ui.theme.StatusRed
import com.poldivers.app.ui.theme.SuperEarthYellow
import com.poldivers.app.ui.theme.hudPanel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

private const val MIN_ZOOM = 1f
private const val MAX_ZOOM = 10f
private const val DOUBLE_TAP_ZOOM = 3f

/** Zoom level from which every planet gets a name label (active fronts are always labeled). */
private const val LABEL_ALL_ZOOM = 2.6f

/** The Gloom is a sickly amber haze in-game. */
private val GloomColor = Color(0xFFD8A945)
private val VariantColor = Color(0xFFFF7A45)

/** A sector's area (convex hull of its planets, in map units) and who holds most of it. */
private class SectorArea(val name: String, val hull: List<Offset>, val owner: String, val centroid: Offset)

/**
 * 2D galactic war map. Planet positions from the API are roughly in [-1, 1] on both axes.
 * Layers: sector territory by controlling faction, sector borders, supply lines, the Gloom as
 * drifting fog, enemy attacks, planets (artwork when bundled), their active effects, the DSS.
 * Pinch to zoom, drag to pan, double tap to zoom in/out, tap a planet for its details.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GalaxyMap(
    data: PlanetsData,
    selectedIndex: Int?,
    hideOurs: Boolean = false,
    onPlanetClick: (Planet) -> Unit,
    onGesture: () -> Unit,
) {
    val planets = data.planets
    val byIndex = remember(planets) { planets.associateBy { it.index } }
    val art = rememberGameArt()

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var showLegend by remember { mutableStateOf(false) }

    val density = LocalDensity.current
    val tapSlopPx = with(density) { 18.dp.toPx() }

    val textMeasurer = rememberTextMeasurer(cacheSize = 0)
    val labelStyle = TextStyle(color = Color.White.copy(alpha = 0.92f), fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
    val labels = remember(planets, textMeasurer) {
        planets.associate { it.index to textMeasurer.measure(it.name, labelStyle) }
    }
    val variantLabels = remember(data.effects, textMeasurer) {
        data.effects.mapNotNull { (index, effects) ->
            val variants = effects.filter { it.kind == PlanetEffect.Kind.ENEMY_VARIANT }
            if (variants.isEmpty()) {
                null
            } else {
                index to textMeasurer.measure(
                    variants.joinToString(" · ") { it.shortName.uppercase() },
                    TextStyle(color = VariantColor, fontSize = 9.sp, fontWeight = FontWeight.Bold),
                )
            }
        }.toMap()
    }
    val gloomPlanets = remember(data.effects) {
        data.effects.filterValues { list -> list.any { it.originalName.contains("GLOOM", ignoreCase = true) } }.keys
    }
    // Up to three droplets per planet: enemy variants, hazards and sites (TCS, megafactories...).
    // Support effects (arsenal augmentations, SEAF...) and the Gloom itself (drawn as fog) are
    // only listed in the planet details.
    val effectIcons = remember(data.effects, byIndex) {
        data.effects.mapValues { (index, effects) ->
            effects
                .filter { e ->
                    (e.kind == PlanetEffect.Kind.ENEMY_VARIANT || e.kind == PlanetEffect.Kind.HAZARD || e.kind == PlanetEffect.Kind.SITE ||
                        e.originalName.contains("SEAF", ignoreCase = true)) &&
                        !e.originalName.contains("GLOOM", ignoreCase = true)
                }
                .mapNotNull { e -> art.effectIconBitmap(e)?.let { it to e } }
                .take(3)
                .map { (icon, e) -> icon to dropletColor(e, byIndex[index]) }
        }.filterValues { it.isNotEmpty() }
    }
    val sectors = remember(planets) { sectorAreas(planets) }
    val sectorLabels = remember(sectors, textMeasurer) {
        sectors.associate { s ->
            s.name to textMeasurer.measure(
                s.name.uppercase(),
                TextStyle(color = Color.White.copy(alpha = 0.28f), fontSize = 9.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold),
            )
        }
    }
    // The game's sector cells as vectors (cut from its sector border art), coloured by owner.
    val context = androidx.compose.ui.platform.LocalContext.current
    val cells = remember { loadSectorCells(context) }
    val cellColors = remember(cells, planets) { cellOwnerColors(cells, planets) }
    // Asteroid fields, black holes and other non-planet objects: always drawn as their artwork.
    val specialObjects = remember(planets) {
        planets.filter { art.englishPlanetName(it.index) in SPECIAL_OBJECTS }.map { it.index }.toSet()
    }
    val dssIcon = remember { art.iconBitmap("DSS_Icon") }
    // Planet artwork (only if the image dump import shipped it) -- decoded off the main thread.
    val planetBitmaps by produceState<Map<Int, ImageBitmap>>(emptyMap(), planets, data.effects) {
        if (art.hasPlanetIcons) {
            value = withContext(Dispatchers.IO) {
                planets.mapNotNull { p ->
                    art.planetIconBitmap(p.index, data.effects[p.index].orEmpty())?.let { p.index to it }
                }.toMap()
            }
        }
    }

    val transition = rememberInfiniteTransition(label = "map")
    val pulse by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse),
        label = "pulse",
    )
    val fogPhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(24_000, easing = LinearEasing)),
        label = "fog",
    )

    fun unitScale(size: Size) = minOf(size.width, size.height) / 2f * 0.92f
    fun baseOf(unit: Offset, size: Size) = Offset(size.width / 2f + unit.x * unitScale(size), size.height / 2f - unit.y * unitScale(size))
    fun basePosition(p: Planet, size: Size) = baseOf(Offset(p.position.x.toFloat(), p.position.y.toFloat()), size)

    fun clampOffset(candidate: Offset, s: Float): Offset {
        val w = canvasSize.width.toFloat()
        val h = canvasSize.height.toFloat()
        return Offset(candidate.x.coerceIn(w * (1 - s), 0f), candidate.y.coerceIn(h * (1 - s), 0f))
    }

    fun zoomTo(newScale: Float, focus: Offset, pan: Offset = Offset.Zero) {
        val s = newScale.coerceIn(MIN_ZOOM, MAX_ZOOM)
        val newOffset = focus - (focus - offset) * (s / scale) + pan
        scale = s
        offset = clampOffset(newOffset, s)
    }

    Box(Modifier.fillMaxSize()) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds()
                .pointerInput(Unit) {
                    canvasSize = size
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        canvasSize = size
                        zoomTo(scale * zoom, centroid, pan)
                    }
                }
                .pointerInput(planets, hideOurs) {
                    detectTapGestures(
                        onDoubleTap = { tap ->
                            onGesture()
                            canvasSize = size
                            if (scale > MIN_ZOOM * 1.5f) zoomTo(MIN_ZOOM, tap) else zoomTo(DOUBLE_TAP_ZOOM, tap)
                        },
                        onTap = { tap ->
                            val area = Size(size.width.toFloat(), size.height.toFloat())
                            val nearest = planets.filter { !(hideOurs && data.isQuietOurs(it)) }.minByOrNull { p ->
                                (basePosition(p, area) * scale + offset - tap).getDistanceSquared()
                            }
                            if (nearest != null &&
                                (basePosition(nearest, area) * scale + offset - tap).getDistance() <= tapSlopPx
                            ) {
                                onPlanetClick(nearest)
                            }
                        },
                    )
                },
        ) {
            val s = scale
            val o = offset
            val unit = unitScale(size)
            fun toScreen(u: Offset) = baseOf(u, size) * s + o
            fun screen(p: Planet) = basePosition(p, size) * s + o
            val zoomFactor = sqrt(s)

            // 1-2. Sectors: enemy sectors hatched and outlined in the faction colour (an enemy
            // holding any world makes the sector theirs), every cell border faintly drawn.
            val cellPaths = cells.map { pts ->
                Path().apply {
                    var i = 0
                    while (i < pts.size) {
                        val p = toScreen(Offset(pts[i], pts[i + 1]))
                        if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
                        i += 2
                    }
                    close()
                }
            }
            val hatchGap = 7.dp.toPx()
            cellPaths.forEachIndexed { i, path ->
                val color = cellColors.getOrNull(i) ?: return@forEachIndexed
                drawPath(path, color.copy(alpha = 0.13f))
                val box = path.getBounds()
                val left = maxOf(box.left, 0f)
                val right = minOf(box.right, size.width)
                val top = maxOf(box.top, 0f)
                val bottom = minOf(box.bottom, size.height)
                if (right > left && bottom > top) {
                    clipPath(path) {
                        var x = left - (bottom - top)
                        while (x < right) {
                            drawLine(color.copy(alpha = 0.22f), Offset(x, bottom), Offset(x + (bottom - top), top), strokeWidth = 2.dp.toPx())
                            x += hatchGap
                        }
                    }
                }
            }
            val faint = Color.White.copy(alpha = 0.10f)
            cellPaths.forEach { drawPath(it, faint, style = Stroke(width = 1.dp.toPx())) }
            cellPaths.forEachIndexed { i, path ->
                val color = cellColors.getOrNull(i) ?: return@forEachIndexed
                drawPath(path, color.copy(alpha = 0.85f), style = Stroke(width = 1.6.dp.toPx(), join = StrokeJoin.Round))
            }

            // 3. Supply lines.
            // Ours-ours blue, enemy-enemy in the enemy's colour, contested links blend between them.
            val lineWidth = (1.4.dp.toPx() * zoomFactor.coerceAtMost(1.6f))
            planets.forEach { planet ->
                val from = screen(planet)
                planet.waypoints.forEach { targetIndex ->
                    val target = byIndex[targetIndex] ?: return@forEach
                    if (targetIndex < planet.index && planet.index in (target.waypoints)) return@forEach
                    val to = screen(target)
                    val a = supplyColor(planet.currentOwner)
                    val b = supplyColor(target.currentOwner)
                    val brush = if (a == b) SolidColor(a) else Brush.linearGradient(listOf(a, b), start = from, end = to)
                    drawLine(brush, from, to, strokeWidth = lineWidth * 3f, alpha = 0.18f)
                    drawLine(brush, from, to, strokeWidth = lineWidth)
                }
            }

            // 4. The Gloom: drifting, overlapping haze around affected worlds.
            gloomPlanets.forEach { index ->
                val planet = byIndex[index] ?: return@forEach
                val c = screen(planet)
                for (i in 0 until 5) {
                    val a = fogPhase + i * (2 * PI / 5).toFloat() + index
                    val drift = Offset(cos(a), sin(a * 0.7f)) * (0.03f * unit * s)
                    val r = (0.09f + 0.018f * i) * unit * s
                    drawCircle(
                        Brush.radialGradient(
                            listOf(GloomColor.copy(alpha = 0.30f), GloomColor.copy(alpha = 0.12f), Color.Transparent),
                            center = c + drift,
                            radius = r,
                        ),
                        radius = r,
                        center = c + drift,
                    )
                }
            }

            // 5. Enemy attacks: dashed line from attacker to target.
            val dash = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()), phase = -fogPhase * 20)
            planets.forEach { attacker ->
                attacker.attacking.forEach { targetIndex ->
                    val target = byIndex[targetIndex] ?: return@forEach
                    drawLine(
                        color = factionColor(attacker.currentOwner).copy(alpha = 0.85f),
                        start = screen(attacker),
                        end = screen(target),
                        strokeWidth = 2.dp.toPx(),
                        pathEffect = dash,
                    )
                }
            }

            // 6. Sector names (mid zoom).
            if (s in 1.3f..4f) {
                sectors.forEach { sector ->
                    val label = sectorLabels[sector.name] ?: return@forEach
                    val c = toScreen(sector.centroid)
                    drawText(label, topLeft = Offset(c.x - label.size.width / 2f, c.y - label.size.height / 2f))
                }
            }

            // 7. Planets and their decorations.
            val baseRadius = 3.5.dp.toPx() * zoomFactor
            planets.forEach { planet ->
                val center = screen(planet)
                if (center.x < -60 || center.y < -60 || center.x > size.width + 60 || center.y > size.height + 60) return@forEach
                if (hideOurs && data.isQuietOurs(planet) && planet.index != selectedIndex) return@forEach
                val isFront = planet.index in data.campaignPlanets
                val special = planet.index in specialObjects
                val radius = if (isFront) baseRadius * 1.6f else baseRadius
                val owner = factionColor(planet.currentOwner)

                val bitmap = planetBitmaps[planet.index]
                val isSuperEarth = planet.index == 0
                val artShown = bitmap != null && (special || isSuperEarth || isFront || s >= 1.8f)
                val ringR = if (artShown) radius * 1.5f else radius
                // Effect droplets sit behind the planet, peeking out of its rim.
                if (!isSuperEarth) effectIcons[planet.index]?.let { icons -> drawEffectDroplets(icons, center, ringR, zoomFactor) }
                if (bitmap != null && isSuperEarth) {
                    val d = (radius * 4f).toInt()
                    drawImage(bitmap, dstOffset = IntOffset((center.x - d / 2).toInt(), (center.y - d / 2).toInt()), dstSize = IntSize(d, d))
                } else if (bitmap != null && special) {
                    val d = (radius * 3.6f).toInt()
                    drawImage(bitmap, dstOffset = IntOffset((center.x - d / 2).toInt(), (center.y - d / 2).toInt()), dstSize = IntSize(d, d))
                } else if (bitmap != null && (isFront || s >= 1.8f)) {
                    val d = (radius * 2.8f).toInt()
                    drawCircle(owner.copy(alpha = 0.35f), radius = d / 2f + 1.5.dp.toPx(), center = center)
                    drawImage(bitmap, dstOffset = IntOffset((center.x - d / 2).toInt(), (center.y - d / 2).toInt()), dstSize = IntSize(d, d))
                } else {
                    drawCircle(owner, radius = radius, center = center)
                    drawCircle(Color.White.copy(alpha = 0.35f), radius = radius, center = center, style = Stroke(0.8.dp.toPx()))
                }

                if (planet.event != null) {
                    drawCircle(StatusRed.copy(alpha = pulse), radius = ringR + 5.dp.toPx(), center = center, style = Stroke(width = 2.dp.toPx()))
                } else if (isFront) {
                    drawCircle(Color.White.copy(alpha = 0.55f), radius = ringR + 3.dp.toPx(), center = center, style = Stroke(width = 1.dp.toPx()))
                }
                if (planet.index in data.majorOrderPlanets) {
                    drawCircle(SuperEarthYellow, radius = ringR + 8.dp.toPx(), center = center, style = Stroke(width = 1.5.dp.toPx()))
                }
                if (planet.index == selectedIndex) {
                    drawCircle(Color.White, radius = ringR + 11.dp.toPx(), center = center, style = Stroke(width = 2.dp.toPx()))
                }


                if (planet.index == data.dssPlanet) {
                    val d = (18.dp.toPx() * zoomFactor.coerceAtMost(1.8f)).toInt()
                    val at = center + Offset(ringR + 6.dp.toPx(), -ringR - d - 2.dp.toPx())
                    drawCircle(SuperEarthYellow.copy(alpha = 0.25f + 0.25f * pulse), radius = d * 0.75f, center = at + Offset(d / 2f, d / 2f))
                    if (dssIcon != null) {
                        drawImage(dssIcon, dstOffset = IntOffset(at.x.toInt(), at.y.toInt()), dstSize = IntSize(d, d), colorFilter = ColorFilter.tint(SuperEarthYellow))
                    } else {
                        drawRect(SuperEarthYellow, topLeft = at, size = Size(d.toFloat(), d.toFloat()))
                    }
                }

                if (isFront || s >= LABEL_ALL_ZOOM || planet.index == data.dssPlanet) {
                    labels[planet.index]?.let { label ->
                        val top = center.y + ringR + 4.dp.toPx()
                        drawText(label, topLeft = Offset(center.x - label.size.width / 2f, top))
                        if (s >= 1.5f) {
                            variantLabels[planet.index]?.let { v ->
                                drawText(v, topLeft = Offset(center.x - v.size.width / 2f, top + label.size.height))
                            }
                        }
                    }
                }
            }
        }

        // Controls: reset zoom (top right), legend toggle (bottom left).
        if (scale > MIN_ZOOM) {
            FilledTonalIconButton(
                onClick = {
                    onGesture()
                    scale = MIN_ZOOM
                    offset = Offset.Zero
                },
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
            ) { Icon(Icons.Filled.ZoomOutMap, contentDescription = "Resetuj widok") }
        }
        FilledTonalIconButton(
            onClick = {
                onGesture()
                showLegend = !showLegend
            },
            modifier = Modifier.align(Alignment.BottomStart).padding(8.dp),
        ) { Icon(Icons.Filled.Info, contentDescription = "Legenda") }

        AnimatedVisibility(
            visible = showLegend,
            modifier = Modifier.align(Alignment.BottomCenter).padding(start = 60.dp, end = 8.dp, bottom = 8.dp),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .hudPanel(SuperEarthYellow, glow = true)
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("LEGENDA", style = MaterialTheme.typography.labelLarge, color = SuperEarthYellow)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    LegendItem(FactionHuman, "Super Ziemia")
                    LegendItem(FactionTerminid, "Terminidzi")
                    LegendItem(FactionAutomaton, "Automatony")
                    LegendItem(FactionIlluminate, "Iluminaci")
                    LegendItem(StatusRed, "Obrona", ring = true)
                    LegendItem(Color.White, "Aktywny front", ring = true)
                    LegendItem(SuperEarthYellow, "Cel rozkazu", ring = true)
                    LegendItem(VariantColor, "Wariant wroga")
                    LegendItem(GloomColor, "Mrok (mgła)")
                    LegendItem(SuperEarthYellow, "DSS")
                }
                Text(
                    "Sektor ma kolor wroga, jeśli ten ma w nim choć jedną planetę; nasze sektory są przezroczyste. Linie: niebieskie = nasze, kolor wroga = jego szlaki. Przerywana linia = atak. Szczypnij, aby przybliżyć.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Effects as coloured droplets tucked behind the planet (drawn before it), each with its emblem:
 * Terminid orange, Automaton red, Illuminate purple, Super Earth blue, sites grey.
 */
private fun DrawScope.drawEffectDroplets(
    icons: List<Pair<ImageBitmap, Color>>,
    center: Offset,
    planetRadius: Float,
    zoomFactor: Float,
) {
    val b = 7.dp.toPx() * zoomFactor.coerceAtMost(1.8f)
    val dist = planetRadius + b * 0.55f
    val start = (-135.0 * PI / 180).toFloat()
    val step = (46.0 * PI / 180).toFloat()
    icons.forEachIndexed { i, (icon, color) ->
        val angle = start + i * step
        val c2 = center + Offset(cos(angle), sin(angle)) * dist
        drawCircle(
            Brush.radialGradient(listOf(color.copy(alpha = 0.40f), Color.Transparent), center = c2, radius = b * 1.9f),
            radius = b * 1.9f,
            center = c2,
        )
        drawCircle(color, radius = b, center = c2)
        drawCircle(Color.Black.copy(alpha = 0.45f), radius = b, center = c2, style = Stroke(width = 1.dp.toPx()))
        val d = (b * 1.35f).toInt()
        drawImage(
            icon,
            dstOffset = IntOffset((c2.x - d / 2f).toInt(), (c2.y - d / 2f).toInt()),
            dstSize = IntSize(d, d),
            colorFilter = ColorFilter.tint(Color(0xFF0B0D10).copy(alpha = 0.9f)),
        )
    }
}

private fun supplyColor(owner: String): Color = when (owner) {
    "Humans" -> Color(0xFFA9D8FF).copy(alpha = 0.8f)
    else -> factionColor(owner).copy(alpha = 0.9f)
}

private val SPECIAL_OBJECTS = setOf("Angel's Venture", "Meridia", "Ivis", "Moradesh")

/**
 * Droplet colour by who the effect belongs to: Terminids orange, Automatons red, Illuminate
 * purple, Super Earth (SEAF...) blue; hazards take the faction fighting over the planet.
 */
private fun dropletColor(effect: PlanetEffect, planet: Planet?): Color {
    val n = effect.originalName.uppercase()
    if ("CONTROL SYSTEM" in n) return DropletSite
    val faction = when {
        effect.kind == PlanetEffect.Kind.SUPPORT || "SEAF" in n -> "Humans"
        TERMINID_KEYS.any { it in n } -> "Terminids"
        AUTOMATON_KEYS.any { it in n } -> "Automaton"
        ILLUMINATE_KEYS.any { it in n } -> "Illuminate"
        planet == null -> ""
        planet.currentOwner != "Humans" -> planet.currentOwner
        else -> planet.event?.faction.orEmpty()
    }
    return when (faction) {
        "Terminids" -> DropletTerminid
        "Automaton" -> DropletAutomaton
        "Illuminate" -> DropletIlluminate
        "Humans" -> DropletHuman
        else -> Color(0xFFB0B6BE)
    }
}

private val TERMINID_KEYS = listOf("TERMINID", "PREDATOR", "SPORE", "RUPTURE", "DRAGONROACH", "HIVE", "RAMPAGE")
private val AUTOMATON_KEYS = listOf("AUTOMATON", "JET BRIGADE", "INCINERATION", "CYBORG", "SURGE", "FACTOR", "STRIDER")
private val ILLUMINATE_KEYS = listOf("ILLUMINATE", "MINDLESS", "APPROPRIATOR", "VOTE SNATCHER", "INVASION FLEET", "GREAT HOST", "OVERSHIP")
private val DropletTerminid = Color(0xFFFFA726)
private val DropletAutomaton = Color(0xFFFF3B30)
private val DropletIlluminate = Color(0xFFB45CFF)
private val DropletHuman = Color(0xFF3FA9FF)
private val DropletSite = Color(0xFF8C939C)

private fun lerpColor(a: Color, b: Color, t: Float) = Color(
    red = a.red + (b.red - a.red) * t,
    green = a.green + (b.green - a.green) * t,
    blue = a.blue + (b.blue - a.blue) * t,
    alpha = a.alpha + (b.alpha - a.alpha) * t,
)

/** Enemy faction if it holds any world of the sector (the most common one), else Super Earth. */
private fun sectorOwner(members: List<Planet>): String {
    val enemies = members.filter { it.currentOwner != "Humans" && it.currentOwner.isNotBlank() }
    if (enemies.isEmpty()) return "Humans"
    return enemies.groupingBy { it.currentOwner }.eachCount().maxBy { it.value }.key
}

/** Sector cells (assets/sector_regions.json, built by tools/build_sector_regions.py): x,y pairs in map units. */
private fun loadSectorCells(context: android.content.Context): List<FloatArray> = runCatching {
    val text = context.assets.open("sector_regions.json").bufferedReader().use { it.readText() }
    kotlinx.serialization.json.Json.decodeFromString<List<List<Float>>>(text).map { it.toFloatArray() }
}.getOrDefault(emptyList())

/** Fill colour per cell: the enemy holding any world of the cell's sector, null for ours / empty cells. */
private fun cellOwnerColors(cells: List<FloatArray>, planets: List<Planet>): List<Color?> {
    val owners = planets.filter { it.sector.isNotBlank() }.groupBy { it.sector }.mapValues { (_, m) -> sectorOwner(m) }
    return cells.map { pts ->
        val sector = planets
            .filter { it.sector.isNotBlank() && pointInPolygon(it.position.x.toFloat(), it.position.y.toFloat(), pts) }
            .groupingBy { it.sector }.eachCount().maxByOrNull { it.value }?.key
        val owner = sector?.let { owners[it] }
        if (owner == null || owner == "Humans") null else factionColor(owner)
    }
}

private fun pointInPolygon(x: Float, y: Float, pts: FloatArray): Boolean {
    var inside = false
    val n = pts.size / 2
    var j = n - 1
    for (i in 0 until n) {
        val xi = pts[2 * i]
        val yi = pts[2 * i + 1]
        val xj = pts[2 * j]
        val yj = pts[2 * j + 1]
        if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) inside = !inside
        j = i
    }
    return inside
}

/** Groups planets by sector; hull + owner per sector (used for the labels). */
private fun sectorAreas(planets: List<Planet>): List<SectorArea> =
    planets.groupBy { it.sector }
        .filterKeys { it.isNotBlank() }
        .map { (name, members) ->
            val points = members.map { Offset(it.position.x.toFloat(), it.position.y.toFloat()) }
            val owner = sectorOwner(members)
            val centroid = Offset(points.map { it.x }.average().toFloat(), points.map { it.y }.average().toFloat())
            SectorArea(name, expandHull(convexHull(points), centroid, 0.035f), owner, centroid)
        }

/** Andrew's monotone chain. */
private fun convexHull(points: List<Offset>): List<Offset> {
    val sorted = points.distinct().sortedWith(compareBy({ it.x }, { it.y }))
    if (sorted.size < 3) return sorted
    fun cross(o: Offset, a: Offset, b: Offset) = (a.x - o.x) * (b.y - o.y) - (a.y - o.y) * (b.x - o.x)
    val lower = mutableListOf<Offset>()
    for (p in sorted) {
        while (lower.size >= 2 && cross(lower[lower.size - 2], lower.last(), p) <= 0) lower.removeAt(lower.lastIndex)
        lower += p
    }
    val upper = mutableListOf<Offset>()
    for (p in sorted.asReversed()) {
        while (upper.size >= 2 && cross(upper[upper.size - 2], upper.last(), p) <= 0) upper.removeAt(upper.lastIndex)
        upper += p
    }
    return lower.dropLast(1) + upper.dropLast(1)
}

/** Pushes hull points outwards so a sector's area covers its planets' markers too. */
private fun expandHull(hull: List<Offset>, centroid: Offset, by: Float): List<Offset> = hull.map { p ->
    val d = p - centroid
    val len = d.getDistance()
    if (len == 0f) p else p + d / len * by
}

@Composable
private fun LegendItem(color: Color, label: String, ring: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Canvas(Modifier.size(10.dp)) {
            if (ring) drawCircle(color, style = Stroke(width = 1.5.dp.toPx())) else drawCircle(color)
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
