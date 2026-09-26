package com.poldivers.app.feature.planets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.poldivers.app.R
import com.poldivers.app.core.AppContainer
import com.poldivers.app.core.haptics.LocalHaptics
import com.poldivers.app.data.hd2.model.Planet
import com.poldivers.app.ui.common.AutoRefresh
import com.poldivers.app.ui.common.LoadableContent
import com.poldivers.app.ui.common.StateContent
import com.poldivers.app.ui.common.UiState
import com.poldivers.app.ui.common.factionColor
import com.poldivers.app.ui.common.formatNumber
import com.poldivers.app.ui.theme.StatusRed
import com.poldivers.app.ui.theme.SuperEarthYellow

@Composable
fun PlanetsScreen() {
    val context = LocalContext.current
    val container = AppContainer.get(context)
    val viewModel: PlanetsViewModel = viewModel(
        factory = viewModelFactory {
            initializer { PlanetsViewModel(container.hd2Repository, container.preferences.language) }
        },
    )

    val view by viewModel.view.collectAsStateWithLifecycle()
    val activeOnly by viewModel.activeOnly.collectAsStateWithLifecycle()
    val selected by viewModel.selectedPlanet.collectAsStateWithLifecycle()
    val state by viewModel.data.state.collectAsStateWithLifecycle()
    val haptics = LocalHaptics.current

    Column(Modifier.fillMaxSize()) {
        PlanetsViewToggle(
            current = view,
            onSelect = {
                haptics.tap()
                viewModel.setView(it)
            },
        )

        Box(Modifier.weight(1f)) {
            when (view) {
                PlanetsView.LIST -> LoadableContent(viewModel.data) { data ->
                    PlanetsList(
                        data = data,
                        activeOnly = activeOnly,
                        onActiveOnlyChange = {
                            haptics.tap()
                            viewModel.setActiveOnly(it)
                        },
                        onClick = {
                            haptics.tap()
                            viewModel.selectPlanet(it)
                        },
                    )
                }

                // The map consumes drag gestures itself, so no pull-to-refresh here.
                PlanetsView.MAP -> StateContent(state = state, onRetry = viewModel.data::refresh) { data ->
                    AutoRefresh(viewModel.data)
                    GalaxyMap(
                        data = data,
                        selectedIndex = selected?.index,
                        onPlanetClick = {
                            haptics.tap()
                            viewModel.selectPlanet(it)
                        },
                        onGesture = haptics::tap,
                    )
                }
            }
        }
    }

    selected?.let { planet ->
        val data = (state as? UiState.Success)?.data
        PlanetDetailSheet(
            planet = planet,
            onDismiss = { viewModel.selectPlanet(null) },
            isMajorOrderTarget = data?.majorOrderPlanets?.contains(planet.index) == true,
            hasDss = data?.dssPlanet == planet.index,
            effects = data?.effects?.get(planet.index).orEmpty(),
        )
    }
}

@Composable
private fun PlanetsViewToggle(current: PlanetsView, onSelect: (PlanetsView) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
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
        contentAlignment = Alignment.Center,
    ) {
        Text(label.uppercase(), color = fg, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
    }
}

@Composable
private fun PlanetsList(
    data: PlanetsData,
    activeOnly: Boolean,
    onActiveOnlyChange: (Boolean) -> Unit,
    onClick: (Planet) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val visible = data.planets.filter { planet ->
        (!activeOnly || planet.index in data.campaignPlanets) &&
            (query.isBlank() || planet.name.contains(query, ignoreCase = true) || planet.sector.contains(query, ignoreCase = true))
    }
    val totalPlayers = data.planets.sumOf { it.playerCount }

    LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item(key = "header") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Szukaj planety lub sektora") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Close, contentDescription = "Wyczyść") }
                        }
                    },
                    singleLine = true,
                )
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = !activeOnly,
                        onClick = { onActiveOnlyChange(false) },
                        label = { Text("Wszystkie") },
                    )
                    FilterChip(
                        selected = activeOnly,
                        onClick = { onActiveOnlyChange(true) },
                        label = { Text("Aktywne fronty (${data.campaignPlanets.size})") },
                    )
                    Text(
                        "${formatNumber(totalPlayers)} 👤",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.End,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        items(visible, key = { it.index }) { planet ->
            PlanetCard(
                planet = planet,
                isMajorOrderTarget = planet.index in data.majorOrderPlanets,
                hasDss = planet.index == data.dssPlanet,
                effects = data.effects[planet.index].orEmpty(),
                onClick = { onClick(planet) },
            )
        }
        if (visible.isEmpty()) {
            item(key = "empty") {
                Text(
                    "Brak planet spełniających kryteria.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun PlanetCard(
    planet: Planet,
    isMajorOrderTarget: Boolean,
    hasDss: Boolean,
    effects: List<com.poldivers.app.data.hd2.PlanetEffect>,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FactionDot(planet.currentOwner)
                Column(Modifier.weight(1f)) {
                    Text(planet.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(planet.sector, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "${formatNumber(planet.playerCount)} 👤",
                        style = MaterialTheme.typography.labelLarge,
                        color = factionColor(planet.currentOwner),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (planet.event != null) Tag("OBRONA", StatusRed)
                        if (isMajorOrderTarget) Tag("ROZKAZ", SuperEarthYellow)
                        if (hasDss) Tag("DSS", SuperEarthYellow)
                    }
                }
            }
            EffectTags(effects)
            if (planet.currentOwner != "Humans" || planet.event != null) {
                PlanetProgress(planet)
            }
        }
    }
}
