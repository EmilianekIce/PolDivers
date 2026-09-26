package com.poldivers.app.feature.campaigns

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.poldivers.app.core.AppContainer
import com.poldivers.app.core.haptics.LocalHaptics
import com.poldivers.app.data.hd2.CampaignHistoryStore.PhaseRecord
import com.poldivers.app.data.hd2.CampaignPhase
import com.poldivers.app.data.hd2.model.Assignment
import com.poldivers.app.feature.archive.ArchiveViewModel
import com.poldivers.app.feature.planets.FactionDot
import com.poldivers.app.feature.planets.PlanetDetailSheet
import com.poldivers.app.ui.common.UiState
import com.poldivers.app.ui.common.LoadableContent
import com.poldivers.app.ui.common.factionColor
import com.poldivers.app.ui.common.factionLabel
import com.poldivers.app.ui.theme.StatusGreen
import com.poldivers.app.ui.theme.StatusRed
import com.poldivers.app.ui.theme.SuperEarthYellow
import com.poldivers.app.ui.wiki.WikiReader

/**
 * Galactic War campaigns, truthenforcers-style: the named campaign, its description and faction,
 * the phase being fought now (with objectives and prognosis) and the phases before it with their
 * outcome. Campaign history beyond what this phone has seen comes from the wiki, on request.
 */
@Composable
fun WarCampaignsTab() {
    val context = LocalContext.current
    val container = AppContainer.get(context)
    val viewModel: CampaignsViewModel = viewModel(
        factory = viewModelFactory {
            initializer { CampaignsViewModel(container.hd2Repository, container.preferences.language, container.campaignHistory) }
        },
    )
    val wiki: ArchiveViewModel = viewModel(
        key = "war-campaigns-wiki",
        factory = viewModelFactory { initializer { ArchiveViewModel(container.wikiRepository) } },
    )
    val haptics = LocalHaptics.current
    val article by wiki.article.collectAsStateWithLifecycle()
    val selected by viewModel.selectedPlanet.collectAsStateWithLifecycle()
    val state by viewModel.data.state.collectAsStateWithLifecycle()

    selected?.let { planet ->
        val data = (state as? UiState.Success)?.data
        PlanetDetailSheet(
            planet = planet,
            onDismiss = { viewModel.selectPlanet(null) },
            isMajorOrderTarget = data?.majorOrderPlanets?.contains(planet.index) == true,
            effects = data?.effects?.get(planet.index).orEmpty(),
        )
    }

    article?.let { state ->
        BackHandler { wiki.back() }
        WikiReader(state = state, onBack = { wiki.back() }, onOpenArticle = wiki::openArticle, onRetry = wiki::retry)
        return
    }

    val openWiki: (String) -> Unit = {
        haptics.tap()
        wiki.openArticle(it)
    }

    LoadableContent(viewModel.data) { data ->
        val campaigns = data.assignments
            .mapNotNull { a -> data.phases[a.id]?.let { it to a } }
            .groupBy { it.first.campaignKey }
        val standalone = data.assignments.filter { it.id !in data.phases }

        LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (campaigns.isEmpty()) {
                item(key = "none") {
                    Text(
                        if (data.assignments.isEmpty()) {
                            "Brak aktywnej kampanii — Dowództwo nie wydało rozkazu."
                        } else {
                            "Obecny rozkaz nie należy do nazwanej kampanii wojennej."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            campaigns.forEach { (key, entries) ->
                item(key = "campaign-$key") {
                    val (phase, assignment) = entries.maxBy { it.first.phase }
                    CampaignCard(
                        phase = phase,
                        assignment = assignment,
                        faction = campaignFaction(assignment, data.planets),
                        history = viewModel.phasesOf(key),
                        activeIds = data.assignments.map { it.id }.toSet(),
                        onWiki = { openWiki(key) },
                    )
                }
                entries.sortedBy { it.first.phase }.forEach { (_, assignment) ->
                    item(key = "phase-${assignment.id}") {
                        AssignmentCard(assignment, data.planets, onPlanetClick = { viewModel.selectPlanet(it) })
                    }
                }
            }
            if (standalone.isNotEmpty() && campaigns.isNotEmpty()) {
                item(key = "standalone-h") {
                    Text("POZOSTAŁE ROZKAZY", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
            }
            if (campaigns.isNotEmpty()) {
                standalone.forEach { a ->
                    item(key = "standalone-${a.id}") { AssignmentCard(a, data.planets, onPlanetClick = { viewModel.selectPlanet(it) }) }
                }
            }
            item(key = "history") {
                OutlinedButton(onClick = { openWiki("Campaigns") }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null)
                    Text("  Historia wszystkich kampanii (Helldivers Wiki)")
                }
            }
        }
    }
}

@Composable
private fun CampaignCard(
    phase: CampaignPhase,
    assignment: Assignment,
    faction: String?,
    history: List<PhaseRecord>,
    activeIds: Set<Long>,
    onWiki: () -> Unit,
) {
    val accent = faction?.let(::factionColor) ?: SuperEarthYellow
    val now = System.currentTimeMillis()
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.7f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (faction != null) FactionDot(faction, size = 44.dp)
                Column(Modifier.weight(1f)) {
                    Text("KAMPANIA WOJENNA", style = MaterialTheme.typography.labelSmall, color = accent)
                    Text(phase.campaign.uppercase(), style = MaterialTheme.typography.titleLarge)
                    faction?.let {
                        Text("Front: ${factionLabel(it)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (assignment.briefing.isNotBlank()) {
                Text(assignment.briefing, style = MaterialTheme.typography.bodyMedium)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            Text("FAZY", style = MaterialTheme.typography.labelLarge, color = accent)

            val phases = history.associateBy { it.phase }.toSortedMap().apply {
                putIfAbsent(phase.phase, PhaseRecord(assignment.id, phase.campaign, phase.campaignKey, phase.phase, phase.phaseName))
            }
            val maxPhase = phases.keys.maxOrNull() ?: phase.phase
            (1..maxPhase).forEach { n ->
                val record = phases[n]
                val outcome = when {
                    record == null -> null
                    else -> record.outcome(now, stillActive = record.assignmentId in activeIds)
                }
                PhaseRow(n, record, outcome)
            }

            OutlinedButton(onClick = onWiki, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null)
                Text("  Kampania na Helldivers Wiki")
            }
        }
    }
}

@Composable
private fun PhaseRow(number: Int, record: PhaseRecord?, outcome: PhaseRecord.Outcome?) {
    val (icon, color, label) = when (outcome) {
        PhaseRecord.Outcome.ACTIVE -> Triple(Icons.Filled.PlayArrow, SuperEarthYellow, "W TOKU")
        PhaseRecord.Outcome.SUCCESS -> Triple(Icons.Filled.CheckCircle, StatusGreen, "WYKONANA")
        PhaseRecord.Outcome.FAILED -> Triple(Icons.Filled.Close, StatusRed, "NIEUDANA")
        null -> Triple(null, MaterialTheme.colorScheme.onSurfaceVariant, "BRAK DANYCH")
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
            } else {
                Text("$number", style = MaterialTheme.typography.labelLarge, color = color)
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                "Faza $number" + (record?.phaseName?.takeIf { it.isNotBlank() }?.let { ": $it" } ?: ""),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (outcome == PhaseRecord.Outcome.ACTIVE) FontWeight.Bold else FontWeight.Normal,
            )
            record?.reward?.takeIf { it.isNotBlank() }?.let {
                Text("Nagroda: $it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

