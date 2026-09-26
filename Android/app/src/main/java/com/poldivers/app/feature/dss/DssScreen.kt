package com.poldivers.app.feature.dss

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import com.poldivers.app.ui.theme.HudCard
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.poldivers.app.R
import androidx.compose.runtime.produceState
import com.poldivers.app.core.AppContainer
import com.poldivers.app.core.art.GameIcon
import com.poldivers.app.core.art.rememberGameArt
import com.poldivers.app.core.haptics.LocalHaptics
import com.poldivers.app.data.hd2.model.Cost
import com.poldivers.app.data.hd2.model.SpaceStation
import com.poldivers.app.data.hd2.model.TacticalAction
import com.poldivers.app.feature.planets.FactionDot
import com.poldivers.app.feature.planets.PlanetDetailSheet
import com.poldivers.app.feature.planets.PlanetProgress
import com.poldivers.app.feature.planets.Tag
import com.poldivers.app.ui.common.LoadableContent
import com.poldivers.app.ui.common.factionLabel
import com.poldivers.app.core.trends.TrendStore
import com.poldivers.app.ui.common.formatClockIn
import com.poldivers.app.ui.common.formatCompact
import com.poldivers.app.ui.common.formatSeconds
import com.poldivers.app.ui.common.formatNumber
import com.poldivers.app.ui.common.formatPercent
import com.poldivers.app.ui.common.formatRemaining
import com.poldivers.app.ui.common.parseInstant
import com.poldivers.app.ui.common.rememberNow
import com.poldivers.app.ui.theme.StatusGreen
import com.poldivers.app.ui.theme.SuperEarthYellow

@Composable
fun DssScreen() {
    val context = LocalContext.current
    val container = AppContainer.get(context)
    val viewModel: DssViewModel = viewModel(
        factory = viewModelFactory {
            initializer { DssViewModel(container.hd2Repository, container.preferences.language) }
        },
    )
    val haptics = LocalHaptics.current
    val selected by viewModel.selectedPlanet.collectAsStateWithLifecycle()
    // Icons are matched on the English action names; one extra (cached) request.
    val englishNames by produceState(emptyMap<Long, String>()) {
        value = runCatching { container.hd2Repository.getTacticalActionNamesEnglish() }.getOrDefault(emptyMap())
    }

    LoadableContent(viewModel.data) { stations ->
        if (stations.isEmpty()) {
            // Scrollable so pull-to-refresh still works on the empty state.
            Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.dss_none_active),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = 120.dp),
                )
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                stations.forEach { station ->
                    item(key = "station-${station.id32}") {
                        StationHeader(station, onPlanetClick = {
                            haptics.tap()
                            viewModel.selectPlanet(station.planet)
                        })
                    }
                    if (station.tacticalActions.isEmpty()) {
                        item(key = "no-actions-${station.id32}") {
                            Text(
                                "Działania taktyczne stacji są chwilowo niedostępne w API społeczności — pozycja i czas skoku pochodzą bezpośrednio ze statusu wojny.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    val now = java.time.Instant.now()
                    val active = station.tacticalActions.filter { it.phase(now, container.trends) == Phase.ACTIVE }
                    if (active.isNotEmpty()) {
                        item(key = "active-${station.id32}") { SectionTitle("AKTYWNE EFEKTY") }
                        items(active, key = { "a-${station.id32}-${it.id32}" }) { TacticalActionCard(it, container.art.dssActionIcon(englishNames[it.id32] ?: it.name)) }
                    }
                    // Collections in progress first -- that is what players can still influence.
                    val others = station.tacticalActions
                        .filter { it.phase(now, container.trends) != Phase.ACTIVE }
                        .sortedBy { it.phase(now, container.trends).ordinal }
                    if (others.isNotEmpty()) {
                        item(key = "others-${station.id32}") { SectionTitle("DZIAŁANIA TAKTYCZNE") }
                        items(others, key = { "o-${station.id32}-${it.id32}" }) { TacticalActionCard(it, container.art.dssActionIcon(englishNames[it.id32] ?: it.name)) }
                    }
                }
            }
        }
    }

    selected?.let { planet ->
        PlanetDetailSheet(planet = planet, onDismiss = { viewModel.selectPlanet(null) }, hasDss = true)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 6.dp),
    )
}

@Composable
private fun StationHeader(station: SpaceStation, onPlanetClick: () -> Unit) {
    val now by rememberNow()
    val planet = station.planet
    HudCard(
        onClick = onPlanetClick,
        accent = SuperEarthYellow,
        glow = true,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GameIcon(rememberGameArt().icon("DSS_Icon"), size = 30.dp)
                Text("DEMOKRATYCZNA STACJA KOSMICZNA", style = MaterialTheme.typography.headlineSmall, color = SuperEarthYellow)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FactionDot(planet.currentOwner)
                Column(Modifier.weight(1f)) {
                    Text("Na orbicie: ${planet.name}", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Sektor ${planet.sector} · ${factionLabel(planet.currentOwner)} · ${formatNumber(planet.playerCount)} 👤",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (planet.currentOwner != "Humans" || planet.event != null) PlanetProgress(planet)

            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("NASTĘPNY SKOK ZA", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    formatRemaining(station.electionEnd, now),
                    style = MaterialTheme.typography.titleLarge,
                    color = SuperEarthYellow,
                )
                Text(
                    "Cel kolejnego skoku wybierają głosy Helldiverów w grze.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/**
 * What a tactical action is doing right now. Community sources disagree on the meaning of the
 * numeric `status` (helldiverscompanion reads 1 as "active", others as "collecting"), so the
 * phase is derived from the data itself: are resources still being collected, is the timer
 * running, is anything being donated.
 */
private enum class Phase { COLLECTING, PAUSED, ACTIVE, COOLDOWN, IDLE }

private fun TacticalAction.phase(now: java.time.Instant, trends: TrendStore): Phase {
    val goals = costs.filter { it.targetValue > 0 }
    val full = goals.isNotEmpty() && goals.all { it.currentValue >= it.targetValue }
    val timerRunning = parseInstant(statusExpire)?.isAfter(now) == true
    val donating = goals.any { cost ->
        cost.deltaPerSecond > 0 || (trends.ratePerHour(TrendStore.costKey(id32, cost.id)) ?: 0.0) > 0
    }
    return when {
        goals.isNotEmpty() && !full && donating -> Phase.COLLECTING
        full && timerRunning -> Phase.ACTIVE
        timerRunning && !full && !donating && goals.isNotEmpty() && goals.all { it.currentValue <= 0.0 } -> Phase.COOLDOWN
        goals.isNotEmpty() && !full -> Phase.PAUSED
        timerRunning -> Phase.ACTIVE
        else -> Phase.IDLE
    }
}

@Composable
private fun TacticalActionCard(action: TacticalAction, iconUri: String?) {
    val now by rememberNow()
    val trends = AppContainer.get(LocalContext.current).trends
    val phase = action.phase(now, trends)
    val (statusLabel, statusColor) = when (phase) {
        Phase.ACTIVE -> "AKTYWNE" to StatusGreen
        Phase.COLLECTING -> "ZBIÓRKA ZASOBÓW" to SuperEarthYellow
        Phase.PAUSED -> "ZBIÓRKA WSTRZYMANA" to MaterialTheme.colorScheme.onSurfaceVariant
        Phase.COOLDOWN -> "ODNOWIENIE" to MaterialTheme.colorScheme.onSurfaceVariant
        Phase.IDLE -> "NIEAKTYWNE" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    val expires = parseInstant(action.statusExpire)?.takeIf { it.isAfter(now) }

    HudCard(
        accent = when (phase) {
            Phase.ACTIVE -> StatusGreen
            Phase.COLLECTING -> SuperEarthYellow
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        glow = phase == Phase.ACTIVE || phase == Phase.COLLECTING,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GameIcon(iconUri, size = 40.dp)
                Text(action.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Tag(statusLabel, statusColor)
            }
            if (expires != null && phase != Phase.COLLECTING) {
                val prefix = when (phase) {
                    Phase.ACTIVE -> "Działa jeszcze"
                    Phase.COOLDOWN -> "Dostępne ponownie za"
                    else -> "Zmiana stanu za"
                }
                val left = java.time.Duration.between(now, expires).seconds
                Text(
                    "$prefix: ${formatSeconds(left)} (≈ ${formatClockIn(left, now)})",
                    style = MaterialTheme.typography.labelLarge,
                    color = statusColor,
                )
            }
            if (action.strategicDescription.isNotBlank()) {
                Text(action.strategicDescription, style = MaterialTheme.typography.bodyMedium)
            }
            if (action.description.isNotBlank() && action.description != action.strategicDescription) {
                Text(
                    action.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (phase == Phase.COLLECTING || phase == Phase.PAUSED) {
                action.costs.filter { it.targetValue > 0 }.forEach { cost ->
                    // API's own rate first; our observed history as a fallback.
                    val perSecond = cost.deltaPerSecond.takeIf { it > 0 }
                        ?: trends.ratePerHour(TrendStore.costKey(action.id32, cost.id))?.let { it / 3600 }?.takeIf { it > 0 }
                    CostProgress(cost, perSecond, now)
                }
                if (phase == Phase.PAUSED) {
                    Text(
                        "Brak wpłat — zwykle gdy inne działanie jest aktywne albo wszyscy wyczerpali dzienny limit.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun CostProgress(cost: Cost, perSecond: Double?, now: java.time.Instant) {
    val fraction = (cost.currentValue / cost.targetValue).coerceIn(0.0, 1.0)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        LinearProgressIndicator(
            progress = { fraction.toFloat() },
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)),
            color = SuperEarthYellow,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                "${formatNumber(cost.currentValue.toLong())} / ${formatNumber(cost.targetValue)} (${formatPercent(fraction * 100, 1)}%)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (perSecond != null) {
                Text(
                    "+${formatCompact((perSecond * 3600).toLong())}/h",
                    style = MaterialTheme.typography.labelSmall,
                    color = StatusGreen,
                )
            }
        }
        if (fraction < 1.0) {
            if (perSecond != null) {
                val seconds = ((cost.targetValue - cost.currentValue) / perSecond).toLong()
                Text(
                    "Uzbierają za ~${formatSeconds(seconds)} (≈ ${formatClockIn(seconds, now)}) — wtedy działanie się aktywuje",
                    style = MaterialTheme.typography.labelLarge,
                    color = SuperEarthYellow,
                )
            } else {
                Text(
                    "Czas zebrania: liczę tempo wpłat…",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
