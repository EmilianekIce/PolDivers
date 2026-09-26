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

private val GloomColor = Color(0xFF8A78A8)
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
    // Up to three effect emblems per planet, enemy variants first.
    val effectIcons = remember(data.effects) {
        data.effects.mapValues { (_, effects) ->
            effects.mapNotNull { e -> art.effectIconBitmap(e)?.let { it to effectColor(e) } }.take(3)
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
    val sectorMap = ImageBitmap.imageResource(R.drawable.sector_map)
    val dssIcon = remember { art.iconBitmap("DSS_Icon") }
    // Planet artwork (only if the image dump import shipped it) -- decoded off the main thread.
    val planetBitmaps by produceState<Map<Int, ImageBitmap>>(emptyMap(), planets) {
        if (art.hasPlanetIcons) {
            value = withContext(Dispatchers.IO) {
                planets.mapNotNull { p -> art.planetIconBitmap(p.index)?.let { p.index to it } }.toMap()
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
                .pointerInput(planets) {
                    detectTapGestures(
                        onDoubleTap = { tap ->
                            onGesture()
                            canvasSize = size
                            if (scale > MIN_ZOOM * 1.5f) zoomTo(MIN_ZOOM, tap) else zoomTo(DOUBLE_TAP_ZOOM, tap)
                        },
                        onTap = { tap ->
                            val area = Size(size.width.toFloat(), size.height.toFloat())
                            val nearest = planets.minByOrNull { p ->
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

            // 1. Territory: sectors filled with the colour of whoever holds most of their worlds.
            sectors.forEach { sector ->
                val color = factionColor(sector.owner)
                if (sector.hull.size >= 3) {
                    val path = Path().apply {
                        sector.hull.forEachIndexed { i, u -> toScreen(u).let { if (i == 0) moveTo(it.x, it.y) else lineTo(it.x, it.y) } }
                        close()
                    }
                    drawPath(path, color.copy(alpha = 0.10f))
                    drawPath(path, color.copy(alpha = 0.22f), style = Stroke(width = 1.dp.toPx()))
                }
            }
            planets.forEach { planet ->
                val c = screen(planet)
                val r = 0.075f * unit * s
                drawCircle(
                    Brush.radialGradient(listOf(factionColor(planet.currentOwner).copy(alpha = 0.20f), Color.Transparent), center = c, radius = r),
                    radius = r,
                    center = c,
                )
            }

            // 2. Sector borders artwork, aligned to [-1, 1].
            val mapTopLeft = Offset(size.width / 2f - unit, size.height / 2f - unit) * s + o
            val mapSize = (2 * unit * s).toInt()
            drawImage(
                image = sectorMap,
                dstOffset = IntOffset(mapTopLeft.x.toInt(), mapTopLeft.y.toInt()),
                dstSize = IntSize(mapSize, mapSize),
                alpha = 0.14f,
            )

            // 3. Supply lines.
            val supplyColor = Color.White.copy(alpha = 0.10f)
            planets.forEach { planet ->
                val from = screen(planet)
                planet.waypoints.forEach { targetIndex ->
                    val target = byIndex[targetIndex] ?: return@forEach
                    drawLine(supplyColor, from, screen(target), strokeWidth = 1.dp.toPx())
                }
            }

            // 4. The Gloom: drifting, overlapping haze around affected worlds.
            gloomPlanets.forEach { index ->
                val planet = byIndex[index] ?: return@forEach
                val c = screen(planet)
                for (i in 0 until 4) {
                    val a = fogPhase + i * (PI / 2).toFloat() + index
                    val drift = Offset(cos(a), sin(a * 0.7f)) * (0.025f * unit * s)
                    val r = (0.085f + 0.02f * i) * unit * s
                    drawCircle(
                        Brush.radialGradient(
                            listOf(GloomColor.copy(alpha = 0.30f), GloomColor.copy(alpha = 0.10f), Color.Transparent),
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
                val isFront = planet.index in data.campaignPlanets
                val radius = if (isFront) baseRadius * 1.6f else baseRadius
                val owner = factionColor(planet.currentOwner)

                val bitmap = planetBitmaps[planet.index]
                if (bitmap != null && (isFront || s >= 1.8f)) {
                    val d = (radius * 2.8f).toInt()
                    drawCircle(owner.copy(alpha = 0.35f), radius = d / 2f + 1.5.dp.toPx(), center = center)
                    drawImage(bitmap, dstOffset = IntOffset((center.x - d / 2).toInt(), (center.y - d / 2).toInt()), dstSize = IntSize(d, d))
                } else {
                    drawCircle(owner, radius = radius, center = center)
                    drawCircle(Color.White.copy(alpha = 0.35f), radius = radius, center = center, style = Stroke(0.8.dp.toPx()))
                }

                val ringR = if (bitmap != null && (isFront || s >= 1.8f)) radius * 1.5f else radius
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

                effectIcons[planet.index]?.let { icons -> drawEffectIcons(icons, center, ringR, zoomFactor) }

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
                    LegendItem(GloomColor, "Mrok")
                    LegendItem(SuperEarthYellow, "DSS")
                }
                Text(
                    "Kolor sektora = frakcja, która kontroluje większość jego planet. Przerywana linia = atak. Szczypnij, aby przybliżyć.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun DrawScope.drawEffectIcons(icons: List<Pair<ImageBitmap, Color>>, center: Offset, radius: Float, zoomFactor: Float) {
    val d = (11.dp.toPx() * zoomFactor.coerceAtMost(2f)).toInt()
    val gap = d * 1.1f
    // Arc above-left of the planet.
    icons.forEachIndexed { i, (icon, color) ->
        val x = center.x - radius - d - i * gap * 0.35f
        val y = center.y - radius - d + i * gap * 0.9f - gap * 0.9f
        drawCircle(Color.Black.copy(alpha = 0.65f), radius = d * 0.62f, center = Offset(x + d / 2f, y + d / 2f))
        drawImage(icon, dstOffset = IntOffset(x.toInt(), y.toInt()), dstSize = IntSize(d, d), colorFilter = ColorFilter.tint(color))
    }
}

/** Groups planets by sector; hull + majority owner per sector. */
private fun sectorAreas(planets: List<Planet>): List<SectorArea> =
    planets.groupBy { it.sector }
        .filterKeys { it.isNotBlank() }
        .map { (name, members) ->
            val points = members.map { Offset(it.position.x.toFloat(), it.position.y.toFloat()) }
            val owner = members.groupingBy { it.currentOwner }.eachCount().maxByOrNull { it.value }?.key.orEmpty()
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
