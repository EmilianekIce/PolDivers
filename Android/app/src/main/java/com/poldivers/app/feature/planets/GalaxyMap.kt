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
    val sectorMap = ImageBitmap.imageResource(R.drawable.sector_map)
    // Territory like the in-game map; recomputed only when someone's planet changes hands.
    val ownership = remember(planets) { planets.joinToString("") { it.currentOwner.take(1) } }
    val context = androidx.compose.ui.platform.LocalContext.current
    val territory by produceState<ImageBitmap?>(null, ownership, planets.size) {
        value = withContext(Dispatchers.Default) {
            loadSectorRegions(context)?.let { (map, n) -> territoryBitmap(planets, map, n) }
        }
    }
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

            // 1. Territory: every sector in the colour of the enemy holding any of its worlds,
            // Super Earth blue only when all of them are ours.
            val mapTopLeft = Offset(size.width / 2f - unit, size.height / 2f - unit) * s + o
            val mapSize = (2 * unit * s).toInt()
            territory?.let { image ->
                drawImage(
                    image = image,
                    dstOffset = IntOffset(mapTopLeft.x.toInt(), mapTopLeft.y.toInt()),
                    dstSize = IntSize(mapSize, mapSize),
                    filterQuality = FilterQuality.Low,
                )
            }
            planets.forEach { planet ->
                val c = screen(planet)
                val r = 0.075f * unit * s
                drawCircle(
                    Brush.radialGradient(listOf(factionColor(planet.currentOwner).copy(alpha = 0.11f), Color.Transparent), center = c, radius = r),
                    radius = r,
                    center = c,
                )
            }

            // 2. Sector borders artwork, aligned to [-1, 1].
            drawImage(
                image = sectorMap,
                dstOffset = IntOffset(mapTopLeft.x.toInt(), mapTopLeft.y.toInt()),
                dstSize = IntSize(mapSize, mapSize),
                alpha = 0.14f,
            )

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
                    if (a == b) {
                        drawLine(a, from, to, strokeWidth = lineWidth)
                    } else {
                        drawLine(Brush.linearGradient(listOf(a, b), start = from, end = to), from, to, strokeWidth = lineWidth)
                    }
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
                if (bitmap != null && special) {
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

                val ringR = if (bitmap != null && (special || isFront || s >= 1.8f)) radius * 1.5f else radius
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

                val shownBitmap = if (bitmap != null && !special && (isFront || s >= 1.8f)) bitmap else null
                effectIcons[planet.index]?.let { icons -> drawEffectDroplets(icons, center, ringR, zoomFactor, owner, shownBitmap) }

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
 * Effects as droplets budding off the planet, as if they grew out of it: a soft neck that blends
 * from the planet's colour into the effect's, a glossy bubble with a glow, and the emblem on top.
 */
private fun DrawScope.drawEffectDroplets(
    icons: List<Pair<ImageBitmap, Color>>,
    center: Offset,
    planetRadius: Float,
    zoomFactor: Float,
    planetColor: Color,
    planetBitmap: ImageBitmap?,
) {
    val b = 7.5.dp.toPx() * zoomFactor.coerceAtMost(1.8f)
    val dist = planetRadius + b * 1.25f
    val start = (-140.0 * PI / 180).toFloat()
    val step = (42.0 * PI / 180).toFloat()
    icons.forEachIndexed { i, (icon, color) ->
        val angle = start + i * step
        val u = Offset(cos(angle), sin(angle))
        val n = Offset(-u.y, u.x)
        val c2 = center + u * dist

        // Glow behind the droplet.
        drawCircle(
            Brush.radialGradient(listOf(color.copy(alpha = 0.45f), Color.Transparent), center = c2, radius = b * 2.1f),
            radius = b * 2.1f,
            center = c2,
        )

        // Neck: wide where it leaves the planet, pinched in the middle, rounding into the bubble.
        val base = (62.0 * PI / 180).toFloat()
        val tip = (70.0 * PI / 180).toFloat()
        val p1 = center + (u * cos(base) + n * sin(base)) * (planetRadius * 0.92f)
        val q1 = center + (u * cos(base) - n * sin(base)) * (planetRadius * 0.92f)
        val p2 = c2 + (-u * cos(tip) + n * sin(tip)) * b
        val q2 = c2 + (-u * cos(tip) - n * sin(tip)) * b
        val waist = minOf(planetRadius, b) * 0.42f
        val m = center + u * (planetRadius + (dist - planetRadius - b) * 0.5f)
        val neck = Path().apply {
            moveTo(p1.x, p1.y)
            cubicTo(
                (p1 + u * (b * 0.5f)).x, (p1 + u * (b * 0.5f)).y,
                (m + n * waist).x, (m + n * waist).y,
                p2.x, p2.y,
            )
            lineTo(q2.x, q2.y)
            cubicTo(
                (m - n * waist).x, (m - n * waist).y,
                (q1 + u * (b * 0.5f)).x, (q1 + u * (b * 0.5f)).y,
                q1.x, q1.y,
            )
            close()
        }
        // The neck grows out of the planet: every pixel along the cut rim runs in its own
        // gradient from the planet's colour there into the droplet's colour.
        clipPath(neck) {
            val slices = 16
            val across = maxOf(planetRadius * sin(base), b) * 2f / slices * 1.7f
            for (k in 0 until slices) {
                val t = -1f + (k + 0.5f) * 2f / slices
                val phi = angle + t * base
                val rimPt = center + Offset(cos(phi), sin(phi)) * (planetRadius * 0.8f)
                val end = c2 + n * (t * b * 0.75f)
                val px = rimPixel(planetBitmap, phi, planetColor)
                drawLine(
                    Brush.linearGradient(0f to px, 0.3f to px, 1f to color, start = rimPt, end = end),
                    rimPt,
                    end,
                    strokeWidth = across,
                )
            }
        }

        // Glossy bubble: light spot upper-left, darker rim.
        drawCircle(
            Brush.radialGradient(
                listOf(lerpColor(color, Color.White, 0.45f), color, lerpColor(color, Color.Black, 0.35f)),
                center = c2 + Offset(-b * 0.35f, -b * 0.35f),
                radius = b * 1.5f,
            ),
            radius = b,
            center = c2,
        )
        val d = (b * 1.3f).toInt()
        drawImage(
            icon,
            dstOffset = IntOffset((c2.x - d / 2f).toInt(), (c2.y - d / 2f).toInt()),
            dstSize = IntSize(d, d),
            colorFilter = ColorFilter.tint(Color(0xFF0B0D10).copy(alpha = 0.9f)),
        )
    }
}

/** Colour of the planet artwork just inside its rim in the direction of [angle]. */
private fun rimPixel(bitmap: ImageBitmap?, angle: Float, fallback: Color): Color {
    if (bitmap == null) return fallback
    return runCatching {
        val b = bitmap.asAndroidBitmap()
        val cx = b.width / 2f
        val cy = b.height / 2f
        val r = minOf(cx, cy)
        // Walk inwards from the edge to the first opaque pixel of the disc.
        var f = 0.97f
        while (f > 0.5f) {
            val x = (cx + cos(angle) * r * f).toInt().coerceIn(0, b.width - 1)
            val y = (cy + sin(angle) * r * f).toInt().coerceIn(0, b.height - 1)
            val px = b.getPixel(x, y)
            if (android.graphics.Color.alpha(px) >= 200) {
                return@runCatching Color(android.graphics.Color.red(px), android.graphics.Color.green(px), android.graphics.Color.blue(px))
            }
            f -= 0.03f
        }
        fallback
    }.getOrDefault(fallback)
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

/**
 * The game's own sector shapes: assets/sector_regions.png labels every pixel of the [-1, 1]
 * map square with the id of the cell it lies in (cut out of the sector border artwork).
 */
private fun loadSectorRegions(context: android.content.Context): Pair<IntArray, Int>? = runCatching {
    val bmp = context.assets.open("sector_regions.png").use { android.graphics.BitmapFactory.decodeStream(it) }
    val n = bmp.width
    val px = IntArray(n * n)
    bmp.getPixels(px, 0, n, 0, 0, n, n)
    IntArray(n * n) { px[it] and 0xFF } to n
}.getOrNull()

/**
 * Sector territories like the in-game map: every cell goes to the sector of the planets inside
 * it; a sector takes the colour of an enemy holding any of its worlds (hatched fill + bright
 * outline), ours stay clear.
 */
private fun territoryBitmap(planets: List<Planet>, regions: IntArray, n: Int): ImageBitmap? {
    if (planets.isEmpty()) return null
    val count = (regions.maxOrNull() ?: 0) + 1
    val votes = Array(count) { HashMap<String, Int>() }
    planets.filter { it.sector.isNotBlank() }.forEach { p ->
        val x = ((p.position.x + 1) / 2 * n).toInt().coerceIn(0, n - 1)
        val y = ((1 - p.position.y) / 2 * n).toInt().coerceIn(0, n - 1)
        val id = regions[y * n + x]
        if (id > 0) votes[id].merge(p.sector, 1, Int::plus)
    }
    val owners = planets.groupBy { it.sector }.mapValues { (_, m) -> sectorOwner(m) }
    val sectorOf = Array(count) { id -> votes[id].maxByOrNull { it.value }?.key }
    val colorOf = IntArray(count) { id ->
        val owner = sectorOf[id]?.let { owners[it] }
        if (owner == null || owner == "Humans") 0 else factionColor(owner).toArgb()
    }
    val pixels = IntArray(n * n)
    fun other(i: Int, id: Int): Boolean {
        val o = regions[i]
        return o != id && (o == 0 || sectorOf[o] != sectorOf[id])
    }
    for (i in regions.indices) {
        val id = regions[i]
        if (id == 0 || colorOf[id] == 0) continue
        val x = i % n
        val y = i / n
        var border = false
        for (d in 1..2) {
            if ((x + d < n && other(i + d, id)) || (x - d >= 0 && other(i - d, id)) ||
                (y + d < n && other(i + d * n, id)) || (y - d >= 0 && other(i - d * n, id))
            ) {
                border = true
                break
            }
        }
        val alpha = when {
            border -> 0.85f
            (x + y) / 4 % 2 == 0 -> 0.30f
            else -> 0.17f
        }
        pixels[i] = ((alpha * 255).toInt() shl 24) or (colorOf[id] and 0x00FFFFFF)
    }
    return android.graphics.Bitmap.createBitmap(pixels, n, n, android.graphics.Bitmap.Config.ARGB_8888).asImageBitmap()
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
