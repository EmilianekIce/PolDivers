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
import androidx.compose.material3.Card
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
import com.poldivers.app.core.AppContainer
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
import com.poldivers.app.ui.common.formatDuration
import com.poldivers.app.ui.common.formatNumber
import com.poldivers.app.ui.common.formatPercent
import com.poldivers.app.ui.common.formatRemaining
import com.poldivers.app.ui.common.parseInstant
import com.poldivers.app.ui.common.rememberNow
import com.poldivers.app.ui.theme.StatusGreen
import com.poldivers.app.ui.theme.SuperEarthYellow
import java.time.Duration

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
                    val active = station.tacticalActions.filter { it.status == TacticalAction.Status.ACTIVE }
                    if (active.isNotEmpty()) {
                        item(key = "active-${station.id32}") { SectionTitle("AKTYWNE EFEKTY") }
                        items(active, key = { "a-${station.id32}-${it.id32}" }) { TacticalActionCard(it) }
                    }
                    val others = station.tacticalActions.filter { it.status != TacticalAction.Status.ACTIVE }
                    if (others.isNotEmpty()) {
                        item(key = "others-${station.id32}") { SectionTitle("DZIAŁANIA TAKTYCZNE") }
                        items(others, key = { "o-${station.id32}-${it.id32}" }) { TacticalActionCard(it) }
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
    Card(
        onClick = onPlanetClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, SuperEarthYellow.copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("DEMOKRATYCZNA STACJA KOSMICZNA", style = MaterialTheme.typography.labelLarge, color = SuperEarthYellow)
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

@Composable
private fun TacticalActionCard(action: TacticalAction) {
    val now by rememberNow()
    val (statusLabel, statusColor) = when (action.status) {
        TacticalAction.Status.ACTIVE -> "AKTYWNE" to StatusGreen
        TacticalAction.Status.PREPARING -> "ZBIÓRKA ZASOBÓW" to SuperEarthYellow
        TacticalAction.Status.COOLDOWN -> "ODNOWIENIE" to MaterialTheme.colorScheme.onSurfaceVariant
        else -> "NIEDOSTĘPNE" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    val expires = parseInstant(action.statusExpire)?.takeIf { it.isAfter(now) }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = if (action.status == TacticalAction.Status.ACTIVE) BorderStroke(1.dp, StatusGreen.copy(alpha = 0.6f)) else null,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(action.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Tag(statusLabel, statusColor)
            }
            if (expires != null) {
                val prefix = when (action.status) {
                    TacticalAction.Status.ACTIVE -> "Działa jeszcze"
                    TacticalAction.Status.COOLDOWN -> "Dostępne za"
                    else -> "Zmiana statusu za"
                }
                Text(
                    "$prefix: ${formatRemaining(action.statusExpire, now)}",
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
            if (action.status == TacticalAction.Status.PREPARING) {
                action.costs.filter { it.targetValue > 0 }.forEach { cost -> CostProgress(cost) }
            }
        }
    }
}

@Composable
private fun CostProgress(cost: Cost) {
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
            if (cost.deltaPerSecond > 0 && fraction < 1.0) {
                val seconds = ((cost.targetValue - cost.currentValue) / cost.deltaPerSecond).toLong()
                formatDuration(Duration.ofSeconds(seconds))?.let {
                    Text("≈ $it przy obecnym tempie", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f))
                }
            }
        }
    }
}
