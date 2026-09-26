package com.poldivers.app.feature.planets

import androidx.compose.animation.togetherWith
import com.poldivers.app.ui.anim.LocalAnimations
import com.poldivers.app.ui.anim.bouncyClickable
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.lazy.itemsIndexed
import com.poldivers.app.ui.anim.appear
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import com.poldivers.app.ui.theme.HudCard
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
    val hideOurs by viewModel.hideOurs.collectAsStateWithLifecycle()
    val selected by viewModel.selectedPlanet.collectAsStateWithLifecycle()
    val state by viewModel.merged.collectAsStateWithLifecycle()
    val haptics = LocalHaptics.current

    // "Open this planet" from another tab (e.g. a planet name tapped in the news).
    val requestedPlanet by container.navigator.planet.collectAsStateWithLifecycle()
    LaunchedEffect(requestedPlanet, state) {
        val index = requestedPlanet ?: return@LaunchedEffect
        val planets = (state as? UiState.Success)?.data?.planets ?: return@LaunchedEffect
        planets.firstOrNull { it.index == index }?.let {
            viewModel.setView(PlanetsView.MAP)
            viewModel.selectPlanet(it)
        }
        container.navigator.planetHandled()
    }

    Column(Modifier.fillMaxSize()) {
        PlanetsViewToggle(
            current = view,
            onSelect = {
                haptics.tap()
                viewModel.setView(it)
            },
            hideOurs = hideOurs,
            onToggleHideOurs = {
                haptics.tap()
                viewModel.toggleHideOurs()
            },
        )

        val animate = LocalAnimations.current
        androidx.compose.animation.AnimatedContent(
            targetState = view,
            modifier = Modifier.weight(1f),
            transitionSpec = {
                if (!animate) {
                    androidx.compose.animation.EnterTransition.None togetherWith androidx.compose.animation.ExitTransition.None
                } else if (targetState == PlanetsView.MAP) {
                    // Into the map: zoom out of the list into space.
                    (androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(450)) +
                        androidx.compose.animation.scaleIn(androidx.compose.animation.core.tween(500), initialScale = 1.25f)) togetherWith
                        (androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(250)) +
                            androidx.compose.animation.scaleOut(androidx.compose.animation.core.tween(300), targetScale = 0.85f))
                } else {
                    (androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(400)) +
                        androidx.compose.animation.slideInVertically(androidx.compose.animation.core.tween(400)) { h -> h / 6 }) togetherWith
                        (androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(250)) +
                            androidx.compose.animation.scaleOut(androidx.compose.animation.core.tween(300), targetScale = 1.2f))
                }
            },
            label = "planets-view",
        ) { currentView ->
            when (currentView) {
                PlanetsView.LIST -> LoadableContent(viewModel.data) { _ ->
                    val data = (state as? UiState.Success)?.data ?: return@LoadableContent
                    PlanetsList(
                        data = data,
                        activeOnly = activeOnly,
                        hideOurs = hideOurs,
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
                        hideOurs = hideOurs,
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
private fun PlanetsViewToggle(
    current: PlanetsView,
    onSelect: (PlanetsView) -> Unit,
    hideOurs: Boolean,
    onToggleHideOurs: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
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
        // Super Earth emblem: lit while our worlds are shown.
        val art = com.poldivers.app.core.art.rememberGameArt()
        val showing = !hideOurs
        val tint by androidx.compose.animation.animateColorAsState(
            if (showing) MaterialTheme.colorScheme.onPrimary else com.poldivers.app.ui.theme.FactionHuman,
            label = "se-tint",
        )
        val bgColor by androidx.compose.animation.animateColorAsState(
            if (showing) com.poldivers.app.ui.theme.FactionHuman else MaterialTheme.colorScheme.surface,
            androidx.compose.animation.core.tween(450),
            label = "se-bg",
        )
        // The emblem does a full turn each time it is toggled (the wave starts from it on the map).
        val turn by androidx.compose.animation.core.animateFloatAsState(
            if (showing && LocalAnimations.current) 360f else 0f,
            androidx.compose.animation.core.spring(dampingRatio = 0.55f, stiffness = 120f),
            label = "se-turn",
        )
        Box(
            Modifier
                .bouncyClickable(pressedScale = 0.85f, haptic = false, onClick = onToggleHideOurs)
                .clip(RoundedCornerShape(10.dp))
                .background(bgColor)
                .graphicsLayer { rotationY = turn }
                .padding(horizontal = 12.dp, vertical = 7.dp),
            contentAlignment = Alignment.Center,
        ) {
            coil.compose.AsyncImage(
                model = art.icon("Super_Earth_Icon"),
                contentDescription = if (hideOurs) "Pokaż nasze planety" else "Ukryj nasze planety",
                colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(tint),
                modifier = Modifier.size(26.dp),
            )
        }
    }
}

@Composable
private fun ToggleChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val bg by androidx.compose.animation.animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
        androidx.compose.animation.core.tween(350),
        label = "chip-bg",
    )
    val fg by androidx.compose.animation.animateColorAsState(
        if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        androidx.compose.animation.core.tween(350),
        label = "chip-fg",
    )
    Box(
        modifier = modifier
            .bouncyClickable(haptic = false, onClick = onClick)
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
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
    hideOurs: Boolean,
    onActiveOnlyChange: (Boolean) -> Unit,
    onClick: (Planet) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var sort by rememberSaveable { mutableStateOf(PlanetSort.PLAYERS) }
    val haptics = LocalHaptics.current
    val visible = data.planets.filter { planet ->
        (!activeOnly || planet.index in data.campaignPlanets) &&
            (!hideOurs || !data.isQuietOurs(planet)) &&
            (query.isBlank() || planet.name.contains(query, ignoreCase = true) || planet.sector.contains(query, ignoreCase = true))
    }.sortedWith(sort.comparator)
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
                            IconButton(onClick = { haptics.tap(); query = "" }) { Icon(Icons.Filled.Close, contentDescription = "Wyczyść") }
                        }
                    },
                    singleLine = true,
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("SORTUJ:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    PlanetSort.entries.forEach { option ->
                        FilterChip(selected = sort == option, onClick = { haptics.tap(); sort = option }, label = { Text(option.label) })
                    }
                }
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
                    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                        com.poldivers.app.core.art.PlayerCount(totalPlayers, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
        itemsIndexed(visible, key = { _, p -> p.index }) { i, planet ->
            // Cards cascade in, and glide to their new place when the sort changes.
            Box(Modifier.animateItem().appear(i)) {
                PlanetCard(
                    planet = planet,
                    isMajorOrderTarget = planet.index in data.majorOrderPlanets,
                    hasDss = planet.index == data.dssPlanet,
                    effects = data.effects[planet.index].orEmpty(),
                    onClick = { onClick(planet) },
                )
            }
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
    HudCard(
        onClick = onClick,
        accent = factionColor(planet.event?.faction ?: planet.currentOwner),
        glow = planet.event != null || isMajorOrderTarget,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val planetArt = com.poldivers.app.core.art.rememberGameArt().planetIcon(planet.index, effects)
                Box(Modifier.size(46.dp), contentAlignment = Alignment.BottomEnd) {
                    if (planetArt != null) {
                        coil.compose.AsyncImage(model = planetArt, contentDescription = null, modifier = Modifier.size(46.dp))
                        FactionDot(planet.currentOwner, size = 18.dp)
                    } else {
                        FactionDot(planet.currentOwner, size = 30.dp, modifier = Modifier.align(Alignment.Center))
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text(planet.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(planet.sector, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    com.poldivers.app.core.art.PlayerCount(planet.playerCount, color = factionColor(planet.currentOwner))
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

/** Sort orders for the planet list. */
private enum class PlanetSort(val label: String, val comparator: Comparator<Planet>) {
    PLAYERS("Gracze", compareByDescending { it.playerCount }),
    LIBERATION("Wyzwolenie", compareByDescending<Planet> { it.event?.defensePercent ?: it.liberationPercent }.thenByDescending { it.playerCount }),
    RESISTANCE("Opór", compareByDescending<Planet> { if (it.currentOwner == "Humans") Double.NEGATIVE_INFINITY else it.resistancePerHour() }),
    NAME("Nazwa", compareBy { it.name }),
    SECTOR("Sektor", compareBy<Planet> { it.sector }.thenBy { it.name }),
}
