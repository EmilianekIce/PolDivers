package com.poldivers.app.feature.planets

import com.poldivers.app.core.i18n.tr
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.poldivers.app.core.art.rememberGameArt
import com.poldivers.app.data.hd2.PlanetEffect
import com.poldivers.app.data.hd2.model.Planet
import com.poldivers.app.ui.anim.LocalAnimations
import com.poldivers.app.ui.anim.pressScale
import com.poldivers.app.ui.common.factionColor
import com.poldivers.app.ui.theme.FactionAutomaton
import com.poldivers.app.ui.theme.FactionHuman
import com.poldivers.app.ui.theme.FactionIlluminate
import com.poldivers.app.ui.theme.FactionTerminid
import com.poldivers.app.ui.theme.StatusRed
import com.poldivers.app.ui.theme.SuperEarthYellow
import com.poldivers.app.ui.theme.hudPanel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

private const val MIN_ZOOM = 1f
private const val MAX_ZOOM = 10f
private const val DOUBLE_TAP_ZOOM = 3f
private const val FOCUS_ZOOM = 2.6f

/** Zoom level from which every planet gets a name label (active fronts are always labeled). */
private const val LABEL_ALL_ZOOM = 2.6f

/** The Gloom is a sickly amber haze in-game. */
internal val GloomColor = Color(0xFFD8A945)
internal val VariantColor = Color(0xFFFF7A45)
private val WaveColor = Color(0xFF3FA9FF)

/** Destroyed worlds: Meridia collapsed into a black hole, which then shattered these three. */
internal val BLACK_HOLES = setOf("Meridia")
internal val FRACTURED = setOf("Angel's Venture", "Moradesh", "Ivis")

/**
 * 2D galactic war map. Planet positions from the API are roughly in [-1, 1] on both axes.
 *
 * Performance: the map is drawn once in "world" coordinates and panned/zoomed by the GPU through a
 * graphics layer, so dragging never re-runs the drawing code. The static layer (sectors, supply
 * lines, planets) is redrawn only when the zoom crosses a step (to keep line widths and planet
 * sizes readable); a second, light layer carries everything animated (defense pulses, the Gloom,
 * attack dashes, the black hole, the Super Earth reveal wave).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GalaxyMap(
    data: PlanetsData,
    selectedIndex: Int?,
    hideOurs: Boolean = false,
    onPlanetClick: (Planet) -> Unit,
    onGesture: () -> Unit,
    onSwitchDimension: (() -> Unit)? = null,
) {
    val animate = LocalAnimations.current
    // A world without a position sits at (0, 0) and would show up as a dot on Super Earth.
    val planets = remember(data.planets) {
        data.planets.filter { it.index == 0 || abs(it.position.x) > 0.004 || abs(it.position.y) > 0.004 }
    }
    val byIndex = remember(planets) { planets.associateBy { it.index } }
    val art = rememberGameArt()
    val scope = rememberCoroutineScope()

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var showLegend by remember { mutableStateOf(false) }
    val cameraJob = remember { arrayOfNulls<Job>(1) }
    // Zoom in steps of 2^(1/3): the static layer only redraws when this changes.
    val zoomStep by remember { derivedStateOf { 2f.pow((log2(scale) * 3f).roundToInt() / 3f) } }

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
    // Up to three droplets per planet: enemy variants, hazards, sites (TCS, megafactories...) and
    // Heavy SEAF Presence. The Gloom itself is drawn as fog.
    val effectIcons = remember(data.effects, byIndex) {
        data.effects.mapValues { (index, effects) ->
            effects
                .filter { e ->
                    (e.kind == PlanetEffect.Kind.ENEMY_VARIANT || e.kind == PlanetEffect.Kind.HAZARD || e.kind == PlanetEffect.Kind.SITE ||
                        e.originalName.contains("SEAF", ignoreCase = true)) &&
                        !e.originalName.contains("GLOOM", ignoreCase = true) &&
                        !e.originalName.contains("FRACTURED", ignoreCase = true) &&
                        !e.originalName.contains("BLACK HOLE", ignoreCase = true)
                }
                .mapNotNull { e -> art.effectIconBitmap(e)?.let { it to e } }
                .take(3)
                .map { (icon, e) -> icon to dropletColor(e, byIndex[index]) }
        }.filterValues { it.isNotEmpty() }
    }
    val sectorCentroids = remember(planets) { sectorCentroids(planets) }
    // Supply network colour per planet, blended with its neighbours so the lines flow from
    // Super Earth blue into the enemy's colour across the front instead of flipping per segment.
    val nodeColors = remember(planets) { supplyNodeColors(planets) }
    val sectorLabels = remember(sectorCentroids, textMeasurer) {
        sectorCentroids.keys.associateWith { name ->
            textMeasurer.measure(
                name.uppercase(),
                TextStyle(color = Color.White.copy(alpha = 0.30f), fontSize = 9.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold),
            )
        }
    }
    // The game's sector cells as vectors (cut from its sector border art), coloured by owner.
    val context = androidx.compose.ui.platform.LocalContext.current
    val cells = remember { loadSectorCells(context) }
    val cellColors = remember(cells, planets) { cellOwnerColors(cells, planets) }
    val blackHoles = remember(planets) {
        planets.filter { art.englishPlanetName(it.index) in BLACK_HOLES }.map { it.index }.toSet()
    }
    val fractured = remember(planets, data.effects) {
        planets.filter { p ->
            art.englishPlanetName(p.index) in FRACTURED ||
                data.effects[p.index].orEmpty().any { it.originalName.contains("FRACTURED", ignoreCase = true) }
        }.map { it.index }.toSet() - blackHoles
    }
    val dssIcon = remember { art.iconBitmap("DSS_Icon") }
    // Planet artwork -- decoded off the main thread.
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
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(24_000, easing = LinearEasing)),
        label = "phase",
    )

    // Super Earth reveal / hide wave (the button in the planets tab).
    val wave = remember { Animatable(1f) }
    val firstWave = remember { booleanArrayOf(true) }
    LaunchedEffect(hideOurs) {
        if (firstWave[0]) {
            firstWave[0] = false
            return@LaunchedEffect
        }
        if (!animate) {
            wave.snapTo(1f)
            return@LaunchedEffect
        }
        wave.snapTo(0f)
        wave.animateTo(1f, tween(1500, easing = FastOutSlowInEasing))
    }
    // Opening sweep: a radar ring rolling out from Super Earth.
    val intro = remember { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(Unit) { if (animate) intro.animateTo(1f, tween(1400, easing = FastOutSlowInEasing)) }

    fun unitScale(size: Size) = minOf(size.width, size.height) / 2f * 0.92f
    fun baseOf(unit: Offset, size: Size) = Offset(size.width / 2f + unit.x * unitScale(size), size.height / 2f - unit.y * unitScale(size))
    fun basePosition(p: Planet, size: Size) = baseOf(Offset(p.position.x.toFloat(), p.position.y.toFloat()), size)

    fun clampOffset(candidate: Offset, s: Float): Offset {
        val w = canvasSize.width.toFloat()
        val h = canvasSize.height.toFloat()
        return Offset(candidate.x.coerceIn(w * (1 - s), 0f), candidate.y.coerceIn(h * (1 - s), 0f))
    }

    fun zoomTo(newScale: Float, focus: Offset, pan: Offset = Offset.Zero) {
        cameraJob[0]?.cancel()
        val s = newScale.coerceIn(MIN_ZOOM, MAX_ZOOM)
        val newOffset = focus - (focus - offset) * (s / scale) + pan
        scale = s
        offset = clampOffset(newOffset, s)
    }

    /** Glides the camera to [targetScale] / [targetOffset] (instantly with animations off). */
    fun flyTo(targetScale: Float, targetOffset: Offset) {
        cameraJob[0]?.cancel()
        val s1 = targetScale.coerceIn(MIN_ZOOM, MAX_ZOOM)
        val o1 = clampOffset(targetOffset, s1)
        if (!animate) {
            scale = s1
            offset = o1
            return
        }
        val s0 = scale
        val o0 = offset
        cameraJob[0] = scope.launch {
            val t = Animatable(0f)
            t.animateTo(1f, tween(650, easing = FastOutSlowInEasing)) {
                scale = s0 + (s1 - s0) * value
                offset = o0 + (o1 - o0) * value
            }
        }
    }

    fun focusOn(planet: Planet, minScale: Float = FOCUS_ZOOM) {
        val area = Size(canvasSize.width.toFloat(), canvasSize.height.toFloat())
        if (area.width <= 0f) return
        val s1 = maxOf(scale, minScale)
        // Upper third: the details sheet covers the bottom of the screen.
        val anchor = Offset(area.width / 2f, area.height * 0.32f)
        flyTo(s1, anchor - basePosition(planet, area) * s1)
    }

    LaunchedEffect(selectedIndex) {
        val planet = selectedIndex?.let { byIndex[it] } ?: return@LaunchedEffect
        focusOn(planet)
    }

    /** 1 = drawn, 0 = hidden; quiet worlds of ours fade in/out as the Super Earth wave passes them. */
    fun visibility(p: Planet, waveValue: Float): Float {
        if (p.index == selectedIndex || !data.isQuietOurs(p)) return 1f
        val d = sqrt((p.position.x * p.position.x + p.position.y * p.position.y).toFloat())
        val reached = ((waveValue * 1.25f - d) / 0.12f).coerceIn(0f, 1f)
        return if (hideOurs) 1f - reached else reached
    }

    val cellPaths = remember(cells, canvasSize) {
        val area = Size(canvasSize.width.toFloat(), canvasSize.height.toFloat())
        cells.map { pts ->
            Path().apply {
                var i = 0
                while (i < pts.size) {
                    val p = baseOf(Offset(pts[i], pts[i + 1]), area)
                    if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
                    i += 2
                }
                close()
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .clipToBounds()
            .onSizeChanged { canvasSize = it }
            .pointerInput(Unit) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    zoomTo(scale * zoom, centroid, pan)
                }
            }
            .pointerInput(planets, hideOurs, selectedIndex) {
                detectTapGestures(
                    onDoubleTap = { tap ->
                        onGesture()
                        val target = if (scale > MIN_ZOOM * 1.5f) MIN_ZOOM else DOUBLE_TAP_ZOOM
                        flyTo(target, tap - (tap - offset) * (target / scale))
                    },
                    onTap = { tap ->
                        val area = Size(size.width.toFloat(), size.height.toFloat())
                        val nearest = planets.filter { visibility(it, wave.value) > 0.5f }.minByOrNull { p ->
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
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    transformOrigin = TransformOrigin(0f, 0f)
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                    alpha = (0.35f + intro.value * 0.65f).coerceAtMost(1f)
                },
        ) {
            // ---- Static layer ------------------------------------------------------------
            Canvas(Modifier.fillMaxSize()) {
                val k = zoomStep
                val unit = unitScale(size)
                val waveValue = wave.value
                fun world(p: Planet) = basePosition(p, size)
                fun px(dp: Float) = dp * density.density / k
                val zoomFactor = sqrt(k)

                // Sectors: enemy ones hatched, tinted and outlined with a glow; all cell borders faint.
                val stripe = px(7f)
                cellPaths.forEachIndexed { i, path ->
                    val color = cellColors.getOrNull(i) ?: return@forEachIndexed
                    drawPath(path, color.copy(alpha = 0.10f))
                    drawPath(
                        path,
                        Brush.linearGradient(
                            0f to color.copy(alpha = 0.26f),
                            0.35f to color.copy(alpha = 0.26f),
                            0.35f to Color.Transparent,
                            1f to Color.Transparent,
                            start = Offset.Zero,
                            end = Offset(stripe, stripe),
                            tileMode = TileMode.Repeated,
                        ),
                    )
                }
                cellPaths.forEach { drawPath(it, Color.White.copy(alpha = 0.16f), style = Stroke(width = px(1.4f))) }
                cellPaths.forEachIndexed { i, path ->
                    val color = cellColors.getOrNull(i) ?: return@forEachIndexed
                    drawPath(path, color.copy(alpha = 0.22f), style = Stroke(width = px(7f), join = StrokeJoin.Round))
                    drawPath(path, color.copy(alpha = 0.95f), style = Stroke(width = px(2.6f), join = StrokeJoin.Round))
                }

                // Supply lines with bloom: ours blue, the enemy's in its colour, a gradient between.
                planets.forEach { planet ->
                    val va = visibility(planet, waveValue)
                    val from = world(planet)
                    planet.waypoints.forEach { targetIndex ->
                        val target = byIndex[targetIndex] ?: return@forEach
                        if (targetIndex < planet.index && planet.index in target.waypoints) return@forEach
                        val alpha = minOf(va, visibility(target, waveValue)).coerceAtLeast(0.25f)
                        val to = world(target)
                        val a = nodeColors[planet.index] ?: supplyColor(planet.currentOwner)
                        val b = nodeColors[targetIndex] ?: supplyColor(target.currentOwner)
                        val brush: Brush = if (a == b) SolidColor(a) else Brush.linearGradient(listOf(a, b), start = from, end = to)
                        drawLine(brush, from, to, strokeWidth = px(9f), alpha = 0.10f * alpha, cap = StrokeCap.Round)
                        drawLine(brush, from, to, strokeWidth = px(4.5f), alpha = 0.22f * alpha, cap = StrokeCap.Round)
                        drawLine(brush, from, to, strokeWidth = px(1.8f), alpha = alpha, cap = StrokeCap.Round)
                    }
                }

                // Sector names (mid zoom).
                if (k in 1.25f..4.1f) {
                    sectorCentroids.forEach { (name, c) ->
                        val label = sectorLabels[name] ?: return@forEach
                        drawScaledText(label, baseOf(c, size), k, centerVertically = true)
                    }
                }

                // Planets.
                val baseRadius = px(3.5f) * zoomFactor
                planets.forEach { planet ->
                    if (planet.index in blackHoles || planet.index in fractured) return@forEach
                    val v = visibility(planet, waveValue)
                    if (v <= 0.01f) return@forEach
                    val center = world(planet)
                    val isFront = planet.index in data.campaignPlanets
                    val radius = if (isFront) baseRadius * 1.6f else baseRadius
                    val owner = factionColor(planet.currentOwner)
                    val bitmap = planetBitmaps[planet.index]
                    val isSuperEarth = planet.index == 0
                    val artShown = bitmap != null && (isSuperEarth || isFront || k >= 1.8f)
                    val ringR = if (artShown) radius * 1.5f else radius

                    val dropSize = px(6.5f) * zoomFactor.coerceAtMost(1.8f)
                    val drops = if (isSuperEarth) null else effectIcons[planet.index]
                    drops?.let { icons -> drawEffectDroplets(icons, center, ringR, dropSize, v) }
                    if (bitmap != null && artShown) {
                        val d = (if (isSuperEarth) radius * 4f else radius * 2.8f)
                        if (!isSuperEarth) drawCircle(owner.copy(alpha = 0.35f), radius = d / 2f + px(1.5f), center = center, alpha = v)
                        drawBitmapCentered(bitmap, center, d, alpha = v)
                    } else {
                        drawCircle(owner, radius = radius, center = center, alpha = v)
                        drawCircle(Color.White.copy(alpha = 0.35f), radius = radius, center = center, style = Stroke(px(0.8f)), alpha = v)
                    }
                    if (isFront && planet.event == null) {
                        drawCircle(Color.White.copy(alpha = 0.55f), radius = ringR + px(3f), center = center, style = Stroke(width = px(1f)))
                    }
                    if (planet.index in data.majorOrderPlanets) {
                        drawCircle(SuperEarthYellow, radius = ringR + px(8f), center = center, style = Stroke(width = px(1.5f)))
                    }
                    if (isFront || k >= LABEL_ALL_ZOOM || planet.index == data.dssPlanet) {
                        labels[planet.index]?.let { label ->
                            // Below the droplets when the planet has any.
                            val top = center.y + ringR + px(4f) + if (drops != null) dropSize * 2.9f else 0f
                            drawScaledText(label, Offset(center.x, top), k, alpha = v)
                            if (k >= 1.5f) {
                                variantLabels[planet.index]?.let { vl ->
                                    drawScaledText(vl, Offset(center.x, top + label.size.height / k), k, alpha = v)
                                }
                            }
                        }
                    }
                }
            }

            // ---- Animated layer ----------------------------------------------------------
            Canvas(Modifier.fillMaxSize()) {
                val k = zoomStep
                val unit = unitScale(size)
                fun world(p: Planet) = basePosition(p, size)
                fun px(dp: Float) = dp * density.density / k
                val zoomFactor = sqrt(k)
                val baseRadius = px(3.5f) * zoomFactor
                val t = if (animate) phase else 0f
                val beat = if (animate) pulse else 0.8f

                // The Gloom: drifting amber haze.
                gloomPlanets.forEach { index ->
                    val planet = byIndex[index] ?: return@forEach
                    val c = world(planet)
                    for (i in 0 until 3) {
                        val a = t + i * (2 * PI / 3).toFloat() + index
                        val drift = Offset(cos(a), sin(a * 0.7f)) * (0.03f * unit)
                        val r = (0.10f + 0.025f * i) * unit
                        drawCircle(
                            Brush.radialGradient(
                                listOf(GloomColor.copy(alpha = 0.28f), GloomColor.copy(alpha = 0.10f), Color.Transparent),
                                center = c + drift,
                                radius = r,
                            ),
                            radius = r,
                            center = c + drift,
                        )
                    }
                }

                // Enemy attacks: marching dashes from attacker to target.
                val dash = PathEffect.dashPathEffect(floatArrayOf(px(6f), px(4f)), phase = -t * px(20f))
                planets.forEach { attacker ->
                    attacker.attacking.forEach { targetIndex ->
                        val target = byIndex[targetIndex] ?: return@forEach
                        val color = factionColor(attacker.currentOwner)
                        drawLine(color.copy(alpha = 0.25f), world(attacker), world(target), strokeWidth = px(6f), cap = StrokeCap.Round)
                        drawLine(color, world(attacker), world(target), strokeWidth = px(2f), pathEffect = dash)
                    }
                }

                // Defended worlds pulse red.
                planets.forEach { planet ->
                    if (planet.event == null) return@forEach
                    val isFront = planet.index in data.campaignPlanets
                    val radius = if (isFront) baseRadius * 1.6f else baseRadius
                    val ringR = radius * 1.5f
                    val c = world(planet)
                    drawCircle(StatusRed.copy(alpha = beat), radius = ringR + px(5f), center = c, style = Stroke(width = px(2f)))
                    drawCircle(StatusRed.copy(alpha = (1f - beat) * 0.5f), radius = ringR + px(5f) + px(8f) * beat, center = c, style = Stroke(width = px(1.5f)))
                }

                // Black hole: dark core, spinning accretion disk, purple halo.
                blackHoles.forEach { index ->
                    val planet = byIndex[index] ?: return@forEach
                    val c = world(planet)
                    val r = baseRadius * 1.8f
                    drawCircle(
                        Brush.radialGradient(listOf(Color(0xFFB45CFF).copy(alpha = 0.45f), Color.Transparent), center = c, radius = r * 4f),
                        radius = r * 4f,
                        center = c,
                    )
                    rotate(degrees = t * 57.3f * 3f, pivot = c) {
                        drawCircle(
                            Brush.sweepGradient(
                                listOf(Color(0xFFFFB347), Color(0xFFB45CFF), Color.Transparent, Color(0xFFFF6FD8), Color(0xFFFFB347)),
                                center = c,
                            ),
                            radius = r * 1.55f,
                            center = c,
                            style = Stroke(width = r * 0.55f),
                        )
                    }
                    drawCircle(Color.Black, radius = r, center = c)
                    drawCircle(Color.White.copy(alpha = 0.55f), radius = r, center = c, style = Stroke(width = px(0.8f)))
                }

                // Fractured worlds: a slowly turning field of rubble.
                fractured.forEach { index ->
                    val planet = byIndex[index] ?: return@forEach
                    val c = world(planet)
                    val r = baseRadius * 1.9f
                    val rocks = rubble(index)
                    rocks.forEach { rock ->
                        val a = rock.angle + t * rock.speed
                        val p = c + Offset(cos(a), sin(a)) * (r * rock.distance)
                        drawCircle(rock.color, radius = r * rock.size, center = p)
                        drawCircle(Color.White.copy(alpha = 0.25f), radius = r * rock.size * 0.45f, center = p - Offset(r * rock.size * 0.3f, r * rock.size * 0.3f))
                    }
                }

                // DSS: pulsing emblem next to its planet.
                data.dssPlanet?.let { byIndex[it] }?.let { planet ->
                    val c = world(planet)
                    val isFront = planet.index in data.campaignPlanets
                    val ringR = (if (isFront) baseRadius * 1.6f else baseRadius) * 1.5f
                    val d = px(18f) * zoomFactor.coerceAtMost(1.8f)
                    val at = c + Offset(ringR + px(6f), -ringR - d - px(2f))
                    drawCircle(SuperEarthYellow.copy(alpha = 0.20f + 0.30f * beat), radius = d * 0.8f, center = at + Offset(d / 2f, d / 2f))
                    if (dssIcon != null) {
                        drawBitmapCentered(dssIcon, at + Offset(d / 2f, d / 2f), d, colorFilter = ColorFilter.tint(SuperEarthYellow))
                    }
                }

                // Selected planet: rotating brackets.
                selectedIndex?.let { byIndex[it] }?.let { planet ->
                    val c = world(planet)
                    val isFront = planet.index in data.campaignPlanets
                    val ringR = (if (isFront) baseRadius * 1.6f else baseRadius) * 1.5f + px(11f)
                    rotate(degrees = t * 57.3f * 4f, pivot = c) {
                        for (q in 0 until 4) {
                            drawArc(
                                Color.White,
                                startAngle = q * 90f + 15f,
                                sweepAngle = 60f,
                                useCenter = false,
                                topLeft = c - Offset(ringR, ringR),
                                size = Size(ringR * 2, ringR * 2),
                                style = Stroke(width = px(2f), cap = StrokeCap.Round),
                            )
                        }
                    }
                }

                // Super Earth wave (reveal / hide our worlds) and the opening radar sweep.
                val se = byIndex[0]?.let { world(it) } ?: Offset(size.width / 2f, size.height / 2f)
                if (wave.value < 1f) {
                    val r = wave.value * 1.25f * unit
                    val fade = 1f - wave.value
                    drawCircle(WaveColor.copy(alpha = 0.10f * fade), radius = r, center = se)
                    drawCircle(WaveColor.copy(alpha = 0.25f * fade), radius = r, center = se, style = Stroke(width = px(14f)))
                    drawCircle(WaveColor.copy(alpha = 0.95f * fade), radius = r, center = se, style = Stroke(width = px(2.5f)))
                }
                if (intro.value < 1f) {
                    val r = intro.value * 1.1f * unit
                    val fade = 1f - intro.value
                    drawCircle(SuperEarthYellow.copy(alpha = 0.6f * fade), radius = r, center = se, style = Stroke(width = px(2f)))
                    drawCircle(SuperEarthYellow.copy(alpha = 0.15f * fade), radius = r, center = se, style = Stroke(width = px(18f)))
                }
            }
        }

        // Controls: reset zoom (top right), legend toggle (bottom left).
        AnimatedVisibility(
            visible = scale > MIN_ZOOM + 0.01f,
            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
            enter = scaleIn() + fadeIn(),
            exit = scaleOut() + fadeOut(),
        ) {
            val interaction = remember { MutableInteractionSource() }
            FilledTonalIconButton(
                onClick = {
                    onGesture()
                    flyTo(MIN_ZOOM, Offset.Zero)
                },
                interactionSource = interaction,
                modifier = Modifier.pressScale(interaction),
            ) { Icon(Icons.Filled.ZoomOutMap, contentDescription = tr("Resetuj widok", "Reset view")) }
        }
        val legendInteraction = remember { MutableInteractionSource() }
        FilledTonalIconButton(
            onClick = {
                onGesture()
                showLegend = !showLegend
            },
            interactionSource = legendInteraction,
            modifier = Modifier.align(Alignment.BottomStart).padding(8.dp).pressScale(legendInteraction),
        ) { Icon(Icons.Filled.Info, contentDescription = tr("Legenda", "Legend")) }

        if (onSwitchDimension != null) {
            DimensionButton(label = "3D", onClick = { onGesture(); onSwitchDimension() }, modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp))
        }

        AnimatedVisibility(
            visible = showLegend,
            modifier = Modifier.align(Alignment.BottomCenter).padding(start = 60.dp, end = 8.dp, bottom = 8.dp),
            enter = slideInVertically { it / 2 } + fadeIn() + scaleIn(initialScale = 0.9f),
            exit = slideOutVertically { it / 2 } + fadeOut(),
        ) {
            MapLegendPanel(threeD = false)
        }
    }
}

/** "3D" / "2D" switch in the map's corner. */
@Composable
internal fun DimensionButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    androidx.compose.material3.FilledIconButton(
        onClick = onClick,
        interactionSource = interaction,
        colors = androidx.compose.material3.IconButtonDefaults.filledIconButtonColors(containerColor = SuperEarthYellow, contentColor = Color.Black),
        modifier = modifier.pressScale(interaction),
    ) { Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold) }
}

/** Legend card shared by the 2D and the 3D map. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MapLegendPanel(threeD: Boolean) {
    Column(
        Modifier
            .fillMaxWidth()
            .hudPanel(SuperEarthYellow, glow = true)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(tr("LEGENDA", "LEGEND"), style = MaterialTheme.typography.labelLarge, color = SuperEarthYellow)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            LegendItem(FactionHuman, tr("Super Ziemia", "Super Earth"))
            LegendItem(FactionTerminid, tr("Terminidzi", "Terminids"))
            LegendItem(FactionAutomaton, tr("Automatony", "Automatons"))
            LegendItem(FactionIlluminate, tr("Iluminaci", "Illuminate"))
            LegendItem(StatusRed, tr("Obrona", "Defense"), ring = true)
            LegendItem(Color.White, tr("Aktywny front", "Active front"), ring = true)
            LegendItem(SuperEarthYellow, tr("Cel rozkazu", "Order target"), ring = true)
            LegendItem(VariantColor, tr("Wariant wroga", "Enemy variant"))
            LegendItem(GloomColor, tr("Mrok (mgła)", "Gloom (fog)"))
            LegendItem(SuperEarthYellow, "DSS")
        }
        Text(
            tr("Sektor ma kolor wroga, jeśli ten ma w nim choć jedną planetę; nasze sektory są przezroczyste. ", "A sector takes the enemy's color if it holds at least one planet there; our sectors are transparent. ") +
                tr("Linie: niebieskie = nasze, kolor wroga = jego szlaki, przejście kolorów = linia frontu. ", "Lines: blue = ours, enemy color = its routes, color blend = front line. ") +
                tr("Przerywana linia = atak. Czarna dziura i gruz to zniszczone światy (Meridia, Angel's Venture, Moradesh, Ivis).", "Dashed line = attack. The black hole and rubble are destroyed worlds (Meridia, Angel's Venture, Moradesh, Ivis).") +
                if (threeD) {
                    tr(
                        " Mapa 3D: jeden palec = przesuwanie, dwa palce = zoom, obrót i pochylenie (przesuń oba w górę/dół). Łuki to ataki wroga.",
                        " 3D map: one finger = pan, two fingers = zoom, rotate and tilt (move both up/down). Arcs are enemy attacks.",
                    )
                } else {
                    ""
                },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Text drawn at a world position, counter-scaled so it keeps its on-screen size at zoom step [k]. */
private fun DrawScope.drawScaledText(
    label: TextLayoutResult,
    anchor: Offset,
    k: Float,
    alpha: Float = 1f,
    centerVertically: Boolean = false,
) {
    withTransform({ scale(1f / k, 1f / k, pivot = anchor) }) {
        val top = if (centerVertically) anchor.y - label.size.height / 2f else anchor.y
        drawText(label, topLeft = Offset(anchor.x - label.size.width / 2f, top), alpha = alpha)
    }
}

/**
 * Draws [image] centred on [center], [size] wide, at sub-pixel precision. (Integer destination
 * rectangles in world space get magnified by the zoom and drift off their planets.)
 */
private fun DrawScope.drawBitmapCentered(
    image: ImageBitmap,
    center: Offset,
    size: Float,
    alpha: Float = 1f,
    colorFilter: ColorFilter? = null,
) {
    if (size <= 0f || image.width <= 0 || image.height <= 0) return
    withTransform({
        translate(center.x - size / 2f, center.y - size / 2f)
        scale(size / image.width, size / image.height, pivot = Offset.Zero)
    }) {
        drawImage(image, alpha = alpha, colorFilter = colorFilter)
    }
}

/**
 * Effects as plain droplets hanging under the planet (drawn before it, so their tips tuck
 * behind it): one flat colour per faction -- Terminid orange, Automaton red, Illuminate purple,
 * Super Earth blue, sites grey -- with the emblem inside.
 */
private fun DrawScope.drawEffectDroplets(
    icons: List<Pair<ImageBitmap, Color>>,
    center: Offset,
    planetRadius: Float,
    b: Float,
    alpha: Float,
) {
    val spread = 34f
    val first = 90f - spread * (icons.size - 1) / 2f
    icons.forEachIndexed { i, (icon, color) ->
        val angle = ((first + i * spread) * PI / 180).toFloat()
        val u = Offset(cos(angle), sin(angle))
        val n = Offset(-u.y, u.x)
        val tip = center + u * (planetRadius * 0.75f)
        val c2 = center + u * (planetRadius + b * 1.5f)
        val drop = Path().apply {
            moveTo(tip.x, tip.y)
            val r1 = c2 + n * b
            val r2 = c2 - n * b
            cubicTo(
                (tip + n * (b * 0.25f)).x, (tip + n * (b * 0.25f)).y,
                (r1 - u * (b * 0.9f)).x, (r1 - u * (b * 0.9f)).y,
                r1.x, r1.y,
            )
            // Round bottom: two quarter curves through the far point of the bulb.
            val bottom = c2 + u * b
            cubicTo(
                (r1 + u * (b * 0.55f)).x, (r1 + u * (b * 0.55f)).y,
                (bottom + n * (b * 0.55f)).x, (bottom + n * (b * 0.55f)).y,
                bottom.x, bottom.y,
            )
            cubicTo(
                (bottom - n * (b * 0.55f)).x, (bottom - n * (b * 0.55f)).y,
                (r2 + u * (b * 0.55f)).x, (r2 + u * (b * 0.55f)).y,
                r2.x, r2.y,
            )
            cubicTo(
                (r2 - u * (b * 0.9f)).x, (r2 - u * (b * 0.9f)).y,
                (tip - n * (b * 0.25f)).x, (tip - n * (b * 0.25f)).y,
                tip.x, tip.y,
            )
            close()
        }
        drawPath(drop, color, alpha = alpha)
        drawPath(drop, Color.Black.copy(alpha = 0.5f), style = Stroke(width = b * 0.12f), alpha = alpha)
        drawBitmapCentered(icon, c2, b * 1.35f, alpha = alpha, colorFilter = ColorFilter.tint(Color(0xFF0B0D10).copy(alpha = 0.9f)))
    }
}

private fun supplyColor(owner: String): Color = when (owner) {
    "Humans" -> Color(0xFF4FB4FF)
    else -> factionColor(owner)
}

/**
 * Colour of every planet in the supply network: its owner's colour smoothed with its neighbours'
 * (two diffusion steps over the supply lines). Worlds deep in our space stay blue, deep enemy
 * space keeps the faction colour, and the front in between becomes a gradual blend -- so every
 * line is a gradient between two slightly different shades.
 */
internal fun supplyNodeColors(planets: List<Planet>): Map<Int, Color> {
    val byIndex = planets.associateBy { it.index }
    val neighbours = HashMap<Int, MutableSet<Int>>()
    planets.forEach { p ->
        p.waypoints.forEach { w ->
            if (w in byIndex) {
                neighbours.getOrPut(p.index) { mutableSetOf() }.add(w)
                neighbours.getOrPut(w) { mutableSetOf() }.add(p.index)
            }
        }
    }
    fun isEnemy(p: Planet) = p.currentOwner != "Humans" && p.currentOwner.isNotBlank()
    // The enemy each planet borders: its own owner, else the most common among 2-hop neighbours.
    val enemyOf = planets.associate { p ->
        val faction = if (isEnemy(p)) {
            p.currentOwner
        } else {
            val near = neighbours[p.index].orEmpty().flatMap { n -> listOf(n) + neighbours[n].orEmpty() }
                .mapNotNull { byIndex[it] }.filter(::isEnemy)
            near.groupingBy { it.currentOwner }.eachCount().maxByOrNull { it.value }?.key ?: p.event?.faction
        }
        p.index to faction
    }
    var heat = planets.associate { p -> p.index to if (isEnemy(p)) 1f else if (p.event != null) 0.35f else 0f }
    repeat(2) {
        heat = planets.associate { p ->
            val own = heat.getValue(p.index)
            val ns = neighbours[p.index].orEmpty().mapNotNull { heat[it] }
            val blended = if (ns.isEmpty()) own else own * 0.5f + ns.average().toFloat() * 0.5f
            // Keep ownership readable: ours never past half-way, the enemy's never below it.
            p.index to if (isEnemy(p)) blended.coerceIn(0.6f, 1f) else blended.coerceIn(0f, 0.5f)
        }
    }
    return planets.associate { p ->
        val faction = enemyOf[p.index]
        val color = if (faction == null) supplyColor("Humans") else lerpColor(supplyColor("Humans"), supplyColor(faction), heat.getValue(p.index))
        p.index to color
    }
}

private class Rock(val angle: Float, val distance: Float, val size: Float, val speed: Float, val color: Color)

private val rubbleCache = HashMap<Int, List<Rock>>()

/** Deterministic debris cloud for a shattered world. */
private fun rubble(seed: Int): List<Rock> = rubbleCache.getOrPut(seed) {
    val rnd = java.util.Random(seed * 7919L)
    val palette = listOf(Color(0xFF8A8F98), Color(0xFF6E6358), Color(0xFFA39A8C), Color(0xFF5C6470))
    List(16) {
        Rock(
            angle = (rnd.nextFloat() * 2 * PI).toFloat(),
            distance = 0.15f + rnd.nextFloat() * 1.35f,
            size = 0.10f + rnd.nextFloat() * 0.22f,
            speed = 0.4f + rnd.nextFloat() * 0.9f,
            color = palette[rnd.nextInt(palette.size)],
        )
    }
}

/**
 * Droplet colour by who the effect belongs to: Terminids orange, Automatons red, Illuminate
 * purple, Super Earth (SEAF...) blue; hazards take the faction fighting over the planet.
 */
internal fun dropletColor(effect: PlanetEffect, planet: Planet?): Color {
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
internal fun sectorOwner(members: List<Planet>): String {
    val enemies = members.filter { it.currentOwner != "Humans" && it.currentOwner.isNotBlank() }
    if (enemies.isEmpty()) return "Humans"
    return enemies.groupingBy { it.currentOwner }.eachCount().maxBy { it.value }.key
}

/** Sector cells (assets/sector_regions.json, built by tools/build_sector_regions.py): x,y pairs in map units. */
internal fun loadSectorCells(context: android.content.Context): List<FloatArray> = runCatching {
    val text = context.assets.open("sector_regions.json").bufferedReader().use { it.readText() }
    kotlinx.serialization.json.Json.decodeFromString<List<List<Float>>>(text).map { it.toFloatArray() }
}.getOrDefault(emptyList())

/** Fill colour per cell: the enemy holding any world of the cell's sector, null for ours / empty cells. */
internal fun cellOwnerColors(cells: List<FloatArray>, planets: List<Planet>): List<Color?> {
    val owners = planets.filter { it.sector.isNotBlank() }.groupBy { it.sector }.mapValues { (_, m) -> sectorOwner(m) }
    return cells.map { pts ->
        val sector = planets
            .filter { it.sector.isNotBlank() && pointInPolygon(it.position.x.toFloat(), it.position.y.toFloat(), pts) }
            .groupingBy { it.sector }.eachCount().maxByOrNull { it.value }?.key
        val owner = sector?.let { owners[it] }
        if (owner == null || owner == "Humans") null else factionColor(owner)
    }
}

internal fun pointInPolygon(x: Float, y: Float, pts: FloatArray): Boolean {
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


/** Label position for every sector: the average of its planets, in map units. */
internal fun sectorCentroids(planets: List<Planet>): Map<String, Offset> =
    planets.filter { it.sector.isNotBlank() }.groupBy { it.sector }.mapValues { (_, members) ->
        Offset(members.map { it.position.x }.average().toFloat(), members.map { it.position.y }.average().toFloat())
    }

@Composable
internal fun LegendItem(color: Color, label: String, ring: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Canvas(Modifier.size(10.dp)) {
            if (ring) drawCircle(color, style = Stroke(width = 1.5.dp.toPx())) else drawCircle(color)
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
