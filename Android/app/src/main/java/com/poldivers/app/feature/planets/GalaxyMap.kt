package com.poldivers.app.feature.planets

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.poldivers.app.data.hd2.model.Planet
import com.poldivers.app.ui.common.factionColor
import com.poldivers.app.ui.theme.FactionAutomaton
import com.poldivers.app.ui.theme.FactionHuman
import com.poldivers.app.ui.theme.FactionIlluminate
import com.poldivers.app.ui.theme.FactionTerminid
import com.poldivers.app.ui.theme.StatusRed
import com.poldivers.app.ui.theme.SuperEarthYellow
import kotlin.math.sqrt

private const val MIN_ZOOM = 1f
private const val MAX_ZOOM = 10f
private const val DOUBLE_TAP_ZOOM = 3f

/** Zoom level from which every planet gets a name label (active fronts are always labeled). */
private const val LABEL_ALL_ZOOM = 2.6f

/**
 * 2D galactic map. Planet positions from the API are roughly in [-1, 1] on both axes.
 * Pinch to zoom, drag to pan, double tap to zoom in/out, tap a planet to open its details.
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

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    val density = LocalDensity.current
    val tapSlopPx = with(density) { 18.dp.toPx() }

    val textMeasurer = rememberTextMeasurer(cacheSize = 0)
    val labelStyle = TextStyle(color = Color.White.copy(alpha = 0.9f), fontSize = 10.sp)
    val labels = remember(planets, textMeasurer) {
        planets.associate { it.index to textMeasurer.measure(it.name, labelStyle) }
    }

    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse),
        label = "pulseAlpha",
    )

    fun basePosition(p: Planet, size: Size): Offset {
        val radius = minOf(size.width, size.height) / 2f * 0.92f
        return Offset(
            x = size.width / 2f + p.position.x.toFloat() * radius,
            y = size.height / 2f - p.position.y.toFloat() * radius,
        )
    }

    fun clampOffset(candidate: Offset, s: Float): Offset {
        val w = canvasSize.width.toFloat()
        val h = canvasSize.height.toFloat()
        return Offset(
            x = candidate.x.coerceIn(w * (1 - s), 0f),
            y = candidate.y.coerceIn(h * (1 - s), 0f),
        )
    }

    /** Zooms to [newScale] keeping the content under [focus] (screen coords) in place. */
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
            fun screen(p: Planet) = basePosition(p, size) * s + o
            val zoomFactor = sqrt(s)

            // Outline of the galactic disc.
            drawCircle(
                color = Color.White.copy(alpha = 0.05f),
                radius = minOf(size.width, size.height) / 2f * 0.97f * s,
                center = Offset(size.width / 2f, size.height / 2f) * s + o,
                style = Stroke(width = 1.dp.toPx()),
            )

            // Supply lines between planets.
            val supplyColor = Color.White.copy(alpha = 0.10f)
            planets.forEach { planet ->
                val from = screen(planet)
                planet.waypoints.forEach { targetIndex ->
                    val target = byIndex[targetIndex] ?: return@forEach
                    drawLine(supplyColor, from, screen(target), strokeWidth = 1.dp.toPx())
                }
            }

            // Active enemy attacks: dashed line from the attacker to the defended planet.
            val dash = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))
            planets.forEach { attacker ->
                attacker.attacking.forEach { targetIndex ->
                    val target = byIndex[targetIndex] ?: return@forEach
                    drawLine(
                        color = factionColor(attacker.currentOwner).copy(alpha = 0.8f),
                        start = screen(attacker),
                        end = screen(target),
                        strokeWidth = 2.dp.toPx(),
                        pathEffect = dash,
                    )
                }
            }

            val baseRadius = 3.5.dp.toPx() * zoomFactor
            planets.forEach { planet ->
                val center = screen(planet)
                if (center.x < -40 || center.y < -40 || center.x > size.width + 40 || center.y > size.height + 40) {
                    return@forEach
                }
                val isFront = planet.index in data.campaignPlanets
                val radius = if (isFront) baseRadius * 1.6f else baseRadius
                drawCircle(factionColor(planet.currentOwner), radius = radius, center = center)

                if (planet.event != null) {
                    drawCircle(
                        StatusRed.copy(alpha = pulse),
                        radius = radius + 5.dp.toPx(),
                        center = center,
                        style = Stroke(width = 2.dp.toPx()),
                    )
                } else if (isFront) {
                    drawCircle(
                        Color.White.copy(alpha = 0.55f),
                        radius = radius + 3.dp.toPx(),
                        center = center,
                        style = Stroke(width = 1.dp.toPx()),
                    )
                }
                if (planet.index in data.majorOrderPlanets) {
                    drawCircle(
                        SuperEarthYellow,
                        radius = radius + 8.dp.toPx(),
                        center = center,
                        style = Stroke(width = 1.5.dp.toPx()),
                    )
                }
                if (planet.index == data.dssPlanet) {
                    val d = 4.dp.toPx()
                    val satellite = center + Offset(radius + 6.dp.toPx(), -(radius + 6.dp.toPx()))
                    drawRect(SuperEarthYellow, topLeft = satellite - Offset(d / 2, d / 2), size = Size(d, d))
                }
                if (planet.index == selectedIndex) {
                    drawCircle(Color.White, radius = radius + 11.dp.toPx(), center = center, style = Stroke(width = 2.dp.toPx()))
                }

                if (isFront || s >= LABEL_ALL_ZOOM) {
                    labels[planet.index]?.let { label ->
                        drawText(
                            label,
                            topLeft = Offset(
                                center.x - label.size.width / 2f,
                                center.y + radius + 3.dp.toPx(),
                            ),
                        )
                    }
                }
            }
        }

        if (scale > MIN_ZOOM) {
            FilledTonalIconButton(
                onClick = {
                    onGesture()
                    scale = MIN_ZOOM
                    offset = Offset.Zero
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
            ) {
                Icon(Icons.Filled.ZoomOutMap, contentDescription = "Resetuj widok")
            }
        } else {
            Text(
                "Szczypnij, aby przybliżyć · dotknij planety",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 6.dp),
            )
        }

        FlowRow(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(8.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            LegendItem(FactionHuman, "Super Ziemia")
            LegendItem(FactionTerminid, "Terminidzi")
            LegendItem(FactionAutomaton, "Automatony")
            LegendItem(FactionIlluminate, "Iluminaci")
            LegendItem(StatusRed, "Obrona", ring = true)
            LegendItem(SuperEarthYellow, "Cel rozkazu", ring = true)
        }
    }
}

@Composable
private fun LegendItem(color: Color, label: String, ring: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Canvas(Modifier.size(10.dp)) {
            if (ring) {
                drawCircle(color, style = Stroke(width = 1.5.dp.toPx()))
            } else {
                drawCircle(color)
            }
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
