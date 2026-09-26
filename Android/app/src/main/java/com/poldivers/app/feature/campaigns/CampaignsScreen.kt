package com.poldivers.app.feature.campaigns

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.poldivers.app.feature.archive.ArchiveViewModel
import com.poldivers.app.ui.wiki.WikiReader
import androidx.compose.foundation.background
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
import com.poldivers.app.ui.theme.HudCard
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
import com.poldivers.app.core.art.GameIcon
import com.poldivers.app.core.art.rememberGameArt
import com.poldivers.app.core.trends.Projection
import com.poldivers.app.ui.common.formatClockIn
import com.poldivers.app.ui.common.formatCompact
import com.poldivers.app.ui.common.formatPercent
import com.poldivers.app.ui.common.formatSeconds
import com.poldivers.app.ui.common.gameText
import com.poldivers.app.ui.common.parseInstant
import java.time.Instant
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

private enum class CampaignsTab(val label: String) { FRONT("Rozkazy"), WAR_CAMPAIGNS("Kampanie wojenne") }

@Composable
fun CampaignsScreen() {
    val haptics = LocalHaptics.current
    var tab by rememberSaveable { mutableStateOf(CampaignsTab.FRONT) }
    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tab.ordinal, containerColor = MaterialTheme.colorScheme.background) {
            CampaignsTab.entries.forEach { t ->
                Tab(
                    selected = tab == t,
                    onClick = {
                        haptics.tap()
                        tab = t
                    },
                    text = { Text(t.label.uppercase(), style = MaterialTheme.typography.labelLarge) },
                )
            }
        }
        Box(Modifier.weight(1f)) {
            when (tab) {
                CampaignsTab.FRONT -> CampaignsFront()
                CampaignsTab.WAR_CAMPAIGNS -> WarCampaignsTab()
            }
        }
    }
}

@Composable
private fun CampaignsFront() {
    val context = LocalContext.current
    val container = AppContainer.get(context)
    val viewModel: CampaignsViewModel = viewModel(
        factory = viewModelFactory {
            initializer { CampaignsViewModel(container.hd2Repository, container.preferences.language, container.campaignHistory) }
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
        LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            data.war?.let { war -> item(key = "war") { WarSummary(war, data.campaigns.size) } }

            item(key = "mo-header") { SectionHeader("ROZKAZY DOWÓDZTWA") }
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

            // Fronts live in the Planets tab (sortable); this tab is about orders only.
        }
    }

    selected?.let { planet ->
        val data = (state as? UiState.Success)?.data
        PlanetDetailSheet(
            planet = planet,
            onDismiss = { viewModel.selectPlanet(null) },
            isMajorOrderTarget = data?.majorOrderPlanets?.contains(planet.index) == true,
            effects = data?.effects?.get(planet.index).orEmpty(),
        )
    }
}

/**
 * Same ordering as helldiverscompanion: defenses first (soonest to expire), then the fronts that
 * moved the most (planet or leading region), then by player count.
 */
private val attentionOrder: Comparator<Campaign> = compareBy<Campaign> { it.planet.event == null }
    .thenBy { parseInstant(it.planet.event?.endTime)?.toEpochMilli() ?: Long.MAX_VALUE }
    .thenByDescending { maxOf(it.planet.liberationPercent, it.planet.leadingRegion?.liberationPercent ?: 0.0) }
    .thenByDescending { it.planet.playerCount }

@Composable
private fun SectionHeader(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun WarSummary(war: War, fronts: Int) {
    val stats = war.statistics ?: return
    HudCard(
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
internal fun AssignmentCard(
    assignment: Assignment,
    planets: Map<Int, Planet>,
    onPlanetClick: (Planet) -> Unit,
    /** false = objectives, prognosis and deadline only (the campaign view shows the text itself). */
    showText: Boolean = true,
) {
    val now by rememberNow()
    val tasks = assignment.taskViews(planets)
    val repository = AppContainer.get(LocalContext.current).hd2Repository
    val outlook = assignment.outlook(tasks, repository, now)
    HudCard(
        accent = SuperEarthYellow,
        glow = true,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                if (showText) assignment.title.ifBlank { "ROZKAZ GŁÓWNY" } else "CELE ROZKAZU",
                style = MaterialTheme.typography.labelLarge,
                color = SuperEarthYellow,
            )
            if (showText && assignment.briefing.isNotBlank()) {
                Text(
                    gameText(assignment.briefing, SuperEarthYellow, planets.values.map { it.name }),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            if (showText && assignment.description.isNotBlank() && assignment.description != assignment.briefing) {
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
                tasks.forEachIndexed { i, task -> TaskRow(task, outlook.tasks.getOrNull(i), now, onPlanetClick) }
                OutlookBanner(outlook)
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    "Koniec za: ${formatRemaining(assignment.expiration, now)}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val rewards = assignment.rewards.ifEmpty { listOfNotNull(assignment.reward) }
                if (rewards.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        rewards.forEach { com.poldivers.app.core.art.RewardChip(it.type, it.amount) }
                    }
                }
            }
        }
    }
}

@Composable
private fun OutlookBanner(outlook: OrderOutlook) {
    val (label, color) = when (outlook.verdict) {
        OrderOutlook.Verdict.COMPLETE -> "ROZKAZ WYKONANY" to StatusGreen
        OrderOutlook.Verdict.ON_TRACK -> "PRZEWIDYWANY SUKCES" to StatusGreen
        OrderOutlook.Verdict.AT_RISK -> "ROZKAZ ZAGROŻONY" to StatusRed
        OrderOutlook.Verdict.UNKNOWN -> "ZBIERAM DANE DO PROGNOZY" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = color)
            outlook.predictedPercent?.let {
                Text("${formatPercent(it, 0)}%", style = MaterialTheme.typography.headlineMedium, color = color)
            }
        }
        if (outlook.verdict == OrderOutlook.Verdict.UNKNOWN) {
            Text(
                "Pierwsza prognoza po ok. 2 minutach z otwartą apką.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TaskRow(task: TaskView, projection: Projection?, now: Instant, onPlanetClick: (Planet) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(
                if (task.isDone) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (task.isDone) StatusGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            GameIcon(rememberGameArt().taskIcon(task.task, task.faction), size = 26.dp)
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
            task.planet != null -> HudCard(
                onClick = { onPlanetClick(task.planet) },
                accent = if (task.isDone) StatusGreen else factionColor(task.planet.event?.faction ?: task.planet.currentOwner),
                modifier = Modifier.fillMaxWidth().padding(start = 26.dp),
            ) {
                Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            task.planet.name,
                            style = MaterialTheme.typography.labelLarge,
                            color = factionColor(task.planet.currentOwner),
                            modifier = Modifier.weight(1f),
                        )
                        com.poldivers.app.core.art.PlayerCount(task.planet.playerCount, style = MaterialTheme.typography.labelSmall)
                    }
                    if (task.isDone) {
                        Text("✓ CEL WYKONANY", style = MaterialTheme.typography.labelLarge, color = StatusGreen)
                    } else {
                        PlanetProgress(task.planet)
                    }
                }
            }

            task.goal != null -> Column(Modifier.padding(start = 26.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                LinearProgressIndicator(
                    progress = { task.fraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp)),
                    color = SuperEarthYellow,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
                if (!task.isDone && projection != null) CountedTaskOutlook(task.goal, projection, now)
            }
        }
        if (task.planet != null && !task.isDone && projection != null && projection.ratePerHour != null) {
            // A planet can't be more than liberated -> capped at 100 % (only counted goals exceed it).
            val atEnd = projection.percentAtDeadline
            if (atEnd != null) {
                Text(
                    "Prognoza na koniec rozkazu: ${formatPercent(atEnd, 0)}%" + if (atEnd >= 100.0) " — zdążymy" else " — nie zdążymy",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (atEnd >= 100.0) StatusGreen else StatusRed,
                    modifier = Modifier.padding(start = 26.dp),
                )
            }
        }
    }
}

@Composable
private fun CountedTaskOutlook(goal: Long, projection: Projection, now: Instant) {
    val rate = projection.ratePerHour
    val text = when {
        rate == null -> "tempo: liczę…"
        rate <= Projection.RATE_EPSILON -> "Brak postępu w ostatnich minutach"
        else -> {
            val perHour = rate / 100.0 * goal
            val eta = projection.etaSeconds
            val atEnd = projection.projectedAtDeadline
            buildString {
                append("+${formatCompact(perHour.toLong())}/h")
                if (eta != null) append(" · cel za ~${formatSeconds(eta)} (≈ ${formatClockIn(eta, now)})")
                if (atEnd != null) append(" · prognoza ${formatPercent(atEnd, 0)}%")
            }
        }
    }
    val color = when (projection.outcome) {
        Projection.Outcome.ON_TRACK, Projection.Outcome.DONE -> StatusGreen
        Projection.Outcome.FAILING -> StatusRed
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(text, style = MaterialTheme.typography.labelSmall, color = color)
}

@Composable
private fun CampaignCard(
    campaign: Campaign,
    effects: List<com.poldivers.app.data.hd2.PlanetEffect>,
    isMajorOrderTarget: Boolean,
    onClick: () -> Unit,
) {
    val planet = campaign.planet
    HudCard(
        onClick = onClick,
        accent = factionColor(planet.event?.faction ?: planet.currentOwner),
        glow = planet.event != null || isMajorOrderTarget,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GameIcon(rememberGameArt().campaignTypeIcon(planet.event != null, campaign.type), size = 34.dp)
                Column(Modifier.weight(1f)) {
                    Text(planet.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(planet.sector, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    com.poldivers.app.core.art.PlayerCount(planet.playerCount)
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
            com.poldivers.app.feature.planets.EffectTags(effects)
            PlanetProgress(planet)
        }
    }
}
