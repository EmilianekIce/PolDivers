package com.poldivers.app.feature.campaigns

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.poldivers.app.core.AppContainer
import com.poldivers.app.core.haptics.LocalHaptics
import com.poldivers.app.data.hd2.model.Assignment
import com.poldivers.app.data.hd2.model.Campaign
import com.poldivers.app.data.hd2.model.Planet
import com.poldivers.app.data.hd2.model.War
import com.poldivers.app.feature.planets.FactionDot
import com.poldivers.app.feature.planets.PlanetDetailSheet
import com.poldivers.app.feature.planets.PlanetProgress
import com.poldivers.app.feature.planets.Tag
import com.poldivers.app.ui.common.LoadableContent
import com.poldivers.app.ui.common.UiState
import com.poldivers.app.ui.common.factionColor
import com.poldivers.app.ui.common.factionLabel
import com.poldivers.app.ui.common.formatNumber
import com.poldivers.app.ui.common.formatRemaining
import com.poldivers.app.ui.common.rememberNow
import com.poldivers.app.ui.theme.StatusGreen
import com.poldivers.app.ui.theme.StatusRed
import com.poldivers.app.ui.theme.SuperEarthYellow

@Composable
fun CampaignsScreen() {
    val context = LocalContext.current
    val container = AppContainer.get(context)
    val viewModel: CampaignsViewModel = viewModel(
        factory = viewModelFactory {
            initializer { CampaignsViewModel(container.hd2Repository, container.preferences.language) }
        },
    )
    val haptics = LocalHaptics.current
    val selected by viewModel.selectedPlanet.collectAsStateWithLifecycle()
    val state by viewModel.data.state.collectAsStateWithLifecycle()

    val openPlanet: (Planet) -> Unit = {
        haptics.tap()
        viewModel.selectPlanet(it)
    }

    LoadableContent(viewModel.data) { data ->
        // truthenforcers-style grouping: one block per enemy faction, busiest front first.
        val byFaction = data.campaigns
            .groupBy { it.planet.event?.faction ?: it.faction }
            .toList()
            .sortedByDescending { (_, campaigns) -> campaigns.sumOf { it.planet.playerCount } }

        LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            data.war?.let { war -> item(key = "war") { WarSummary(war, data.campaigns.size) } }

            item(key = "mo-header") { SectionHeader("ROZKAZY GŁÓWNE") }
            if (data.assignments.isEmpty()) {
                item(key = "mo-empty") {
                    Text(
                        "Brak aktywnych rozkazów. Czekaj na instrukcje Dowództwa.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(data.assignments, key = { "assignment-${it.id}" }) { assignment ->
                AssignmentCard(assignment, data.planets, onPlanetClick = openPlanet)
            }

            byFaction.forEach { (faction, campaigns) ->
                item(key = "faction-$faction") {
                    Row(
                        Modifier.fillMaxWidth().padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FactionDot(faction)
                        Text(
                            "FRONT: ${factionLabel(faction).uppercase()}",
                            style = MaterialTheme.typography.labelLarge,
                            color = factionColor(faction),
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "${campaigns.size} · ${formatNumber(campaigns.sumOf { it.planet.playerCount })} 👤",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(campaigns, key = { "campaign-${it.id}" }) { campaign ->
                    CampaignCard(
                        campaign = campaign,
                        isMajorOrderTarget = campaign.planet.index in data.majorOrderPlanets,
                        onClick = { openPlanet(campaign.planet) },
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
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun WarSummary(war: War, fronts: Int) {
    val stats = war.statistics ?: return
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            SummaryValue("Helldiverzy w boju", formatNumber(stats.playerCount))
            SummaryValue("Aktywne fronty", fronts.toString())
            SummaryValue("Skuteczność", "${stats.missionSuccessRate}%")
        }
    }
}

@Composable
private fun SummaryValue(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AssignmentCard(assignment: Assignment, planets: Map<Int, Planet>, onPlanetClick: (Planet) -> Unit) {
    val now by rememberNow()
    val tasks = assignment.taskViews(planets)
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, SuperEarthYellow.copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                assignment.title.ifBlank { "ROZKAZ GŁÓWNY" },
                style = MaterialTheme.typography.labelLarge,
                color = SuperEarthYellow,
            )
            if (assignment.briefing.isNotBlank()) {
                Text(assignment.briefing, style = MaterialTheme.typography.bodyLarge)
            }
            if (assignment.description.isNotBlank() && assignment.description != assignment.briefing) {
                Text(
                    assignment.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (tasks.isNotEmpty()) {
                val done = tasks.count { it.isDone }
                Text(
                    "CELE: $done / ${tasks.size}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                tasks.forEach { task -> TaskRow(task, onPlanetClick) }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    "Koniec za: ${formatRemaining(assignment.expiration, now)}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val rewards = assignment.rewards.ifEmpty { listOfNotNull(assignment.reward) }
                if (rewards.isNotEmpty()) {
                    Text(
                        "Nagroda: " + rewards.joinToString { rewardLabel(it.type, it.amount) },
                        style = MaterialTheme.typography.labelLarge,
                        color = SuperEarthYellow,
                    )
                }
            }
        }
    }
}

@Composable
private fun TaskRow(task: TaskView, onPlanetClick: (Planet) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(
                if (task.isDone) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (task.isDone) StatusGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            Text(task.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            if (task.goal != null) {
                Text(
                    "${formatNumber(task.progress)} / ${formatNumber(task.goal)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        when {
            task.planet != null && !task.isDone -> Card(
                onClick = { onPlanetClick(task.planet) },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background),
                modifier = Modifier.fillMaxWidth().padding(start = 26.dp),
            ) {
                Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "${task.planet.name} · ${formatNumber(task.planet.playerCount)} 👤",
                        style = MaterialTheme.typography.labelSmall,
                        color = factionColor(task.planet.currentOwner),
                    )
                    PlanetProgress(task.planet)
                }
            }

            task.goal != null -> LinearProgressIndicator(
                progress = { task.fraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 26.dp)
                    .clip(RoundedCornerShape(6.dp)),
                color = SuperEarthYellow,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
        }
    }
}

@Composable
private fun CampaignCard(campaign: Campaign, isMajorOrderTarget: Boolean, onClick: () -> Unit) {
    val planet = campaign.planet
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(planet.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(planet.sector, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("${formatNumber(planet.playerCount)} 👤", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        when {
                            planet.event != null -> Tag("OBRONA", StatusRed)
                            campaign.type == 1 -> Tag("REKONESANS", MaterialTheme.colorScheme.onSurfaceVariant)
                            campaign.type == 2 -> Tag("FABUŁA", MaterialTheme.colorScheme.onSurfaceVariant)
                            else -> Tag("WYZWOLENIE", factionColor(planet.currentOwner))
                        }
                        if (isMajorOrderTarget) Tag("ROZKAZ", SuperEarthYellow)
                    }
                }
            }
            PlanetProgress(planet)
        }
    }
}
