package com.poldivers.app.feature.campaigns

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.poldivers.app.core.AppContainer
import com.poldivers.app.data.hd2.model.Assignment
import com.poldivers.app.data.hd2.model.Campaign
import com.poldivers.app.ui.common.StateContent
import com.poldivers.app.ui.common.factionColor
import com.poldivers.app.ui.common.formatRemaining

@Composable
fun CampaignsScreen() {
    val context = LocalContext.current
    val container = AppContainer.get(context)
    val viewModel: CampaignsViewModel = viewModel(
        factory = viewModelFactory { initializer { CampaignsViewModel(container.hd2Repository) } },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()

    StateContent(state = state, onRetry = viewModel::refresh) { data ->
        LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (data.assignments.isNotEmpty()) {
                item {
                    Text(
                        "WAŻNE ROZKAZY",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                items(data.assignments, key = { "assignment-${it.id}" }) { AssignmentCard(it) }
            }

            item {
                Text(
                    "AKTYWNE KAMPANIE",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            items(data.campaigns, key = { "campaign-${it.id}" }) { CampaignCard(it) }
        }
    }
}

@Composable
private fun AssignmentCard(assignment: Assignment) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(assignment.title, style = MaterialTheme.typography.titleMedium)
            if (assignment.briefing.isNotBlank()) {
                Text(assignment.briefing, style = MaterialTheme.typography.bodyMedium)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    "Koniec za: ${formatRemaining(assignment.expiration)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val reward = assignment.reward ?: assignment.rewards.firstOrNull()
                if (reward != null) {
                    Text(
                        "Nagroda: ${reward.amount}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun CampaignCard(campaign: Campaign) {
    val planet = campaign.planet
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(planet.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    planet.currentOwner,
                    style = MaterialTheme.typography.labelLarge,
                    color = factionColor(planet.currentOwner),
                )
            }
            Text(planet.sector, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Box(Modifier.padding(top = 4.dp)) {
                LinearProgressIndicator(
                    progress = { (planet.liberationPercent / 100.0).toFloat().coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                    color = factionColor(planet.currentOwner),
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            }
        }
    }
}
