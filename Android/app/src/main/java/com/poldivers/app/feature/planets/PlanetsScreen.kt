package com.poldivers.app.feature.planets

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.poldivers.app.R
import com.poldivers.app.core.AppContainer
import com.poldivers.app.core.haptics.LocalHaptics
import com.poldivers.app.data.hd2.model.Planet
import com.poldivers.app.ui.common.StateContent
import com.poldivers.app.ui.common.factionColor
import kotlin.math.roundToInt

@Composable
fun PlanetsScreen() {
    val context = LocalContext.current
    val container = AppContainer.get(context)
    val viewModel: PlanetsViewModel = viewModel(
        factory = viewModelFactory {
            initializer { PlanetsViewModel(container.hd2Repository) }
        },
    )

    val state by viewModel.state.collectAsStateWithLifecycle()
    val view by viewModel.view.collectAsStateWithLifecycle()
    val selected by viewModel.selectedPlanet.collectAsStateWithLifecycle()
    val haptics = LocalHaptics.current

    Scaffold(
        topBar = {
            PlanetsViewToggle(
                current = view,
                onSelect = {
                    haptics.tap()
                    viewModel.setView(it)
                },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            StateContent(state = state, onRetry = viewModel::refresh) { planets ->
                when (view) {
                    PlanetsView.LIST -> PlanetsList(planets) {
                        haptics.tap()
                        viewModel.selectPlanet(it)
                    }

                    PlanetsView.MAP -> GalaxyMap(planets) {
                        haptics.tap()
                        viewModel.selectPlanet(it)
                    }
                }
            }
        }
    }

    selected?.let { planet ->
        PlanetDetailDialog(planet = planet, onDismiss = { viewModel.selectPlanet(null) })
    }
}

@Composable
private fun PlanetsViewToggle(current: PlanetsView, onSelect: (PlanetsView) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ToggleChip(
            label = stringResource(R.string.planets_view_list),
            selected = current == PlanetsView.LIST,
            onClick = { onSelect(PlanetsView.LIST) },
            modifier = Modifier.weight(1f),
        )
        ToggleChip(
            label = stringResource(R.string.planets_view_map),
            selected = current == PlanetsView.MAP,
            onClick = { onSelect(PlanetsView.MAP) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ToggleChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val bg = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
    val fg = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
    ) {
        Text(label, color = fg, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 8.dp))
    }
}

@Composable
private fun PlanetsList(planets: List<Planet>, onClick: (Planet) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(planets, key = { it.index }) { planet ->
            PlanetCard(planet, onClick = { onClick(planet) })
        }
    }
}

@Composable
private fun PlanetCard(planet: Planet, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text(planet.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(planet.sector, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    text = "${(planet.statistics?.playerCount ?: 0)} 👤",
                    style = MaterialTheme.typography.labelLarge,
                    color = factionColor(planet.currentOwner),
                )
            }
            Box(Modifier.padding(top = 8.dp)) {
                LinearProgressIndicator(
                    progress = { (planet.liberationPercent / 100.0).toFloat().coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp)),
                    color = factionColor(planet.currentOwner),
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            }
            Text(
                text = "${(planet.liberationPercent).roundToOneDecimal()}% wyzwolone",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

private fun Double.roundToOneDecimal(): Double = (this * 10.0).roundToInt() / 10.0

@Composable
private fun GalaxyMap(planets: List<Planet>, onClick: (Planet) -> Unit) {
    val dotRadiusPx = with(LocalDensity.current) { 5.dp.toPx() }
    val tapSlopPx = with(LocalDensity.current) { 16.dp.toPx() }

    // Position values from the API are roughly in [-1, 1] on both axes.
    fun layout(size: Size): Map<Int, Offset> {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val scale = (minOf(size.width, size.height) / 2f) * 0.92f
        return planets.associate { p ->
            p.index to Offset(
                x = cx + (p.position.x.toFloat() * scale),
                y = cy - (p.position.y.toFloat() * scale),
            )
        }
    }

    Box(Modifier.fillMaxSize().padding(12.dp)) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .align(Alignment.Center)
                .pointerInput(planets) {
                    detectTapGestures { tapOffset ->
                        val positions = layout(size.toSize())
                        val nearest = planets.minByOrNull { p ->
                            positions[p.index]?.let { (it - tapOffset).getDistanceSquared() } ?: Float.MAX_VALUE
                        }
                        val nearestOffset = nearest?.let { positions[it.index] }
                        if (nearest != null && nearestOffset != null &&
                            (nearestOffset - tapOffset).getDistance() <= tapSlopPx
                        ) {
                            onClick(nearest)
                        }
                    }
                },
        ) {
            val positions = layout(size)

            planets.forEach { planet ->
                planet.waypoints.forEach { targetIndex ->
                    val from = positions[planet.index]
                    val to = positions[targetIndex]
                    if (from != null && to != null) {
                        drawLine(
                            color = Color.White.copy(alpha = 0.08f),
                            start = from,
                            end = to,
                            strokeWidth = 1.dp.toPx(),
                        )
                    }
                }
            }
            planets.forEach { planet ->
                positions[planet.index]?.let { offset ->
                    drawCircle(color = factionColor(planet.currentOwner), radius = dotRadiusPx, center = offset)
                }
            }
        }
    }
}

@Composable
private fun PlanetDetailDialog(planet: Planet, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Zamknij") } },
        title = { Text(planet.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Sektor: ${planet.sector}")
                Text("Kontroluje: ${planet.currentOwner}")
                Text("Wyzwolenie: ${planet.liberationPercent.roundToOneDecimal()}%")
                Text("Graczy: ${planet.statistics?.playerCount ?: 0}")
                planet.biome?.let { Text("Biom: ${it.name}") }
            }
        },
    )
}
