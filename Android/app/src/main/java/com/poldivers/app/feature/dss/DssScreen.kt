package com.poldivers.app.feature.dss

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.poldivers.app.R
import com.poldivers.app.core.AppContainer
import com.poldivers.app.data.hd2.model.SpaceStation
import com.poldivers.app.data.hd2.model.TacticalAction
import com.poldivers.app.ui.common.StateContent
import com.poldivers.app.ui.common.formatRemaining

@Composable
fun DssScreen() {
    val context = LocalContext.current
    val container = AppContainer.get(context)
    val viewModel: DssViewModel = viewModel(
        factory = viewModelFactory { initializer { DssViewModel(container.hd2Repository) } },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()

    StateContent(state = state, onRetry = viewModel::refresh) { stations ->
        if (stations.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.dss_none_active),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(stations, key = { it.id32 }) { StationCard(it) }
            }
        }
    }
}

@Composable
private fun StationCard(station: SpaceStation) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Orbituje wokół: ${station.planet.name}", style = MaterialTheme.typography.titleMedium)
            Text(station.planet.sector, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "Następny skok za: ${formatRemaining(station.electionEnd)}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )

            if (station.tacticalActions.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                Text("DZIAŁANIA I EFEKTY", style = MaterialTheme.typography.labelLarge)
                station.tacticalActions.forEach { action -> TacticalActionRow(action) }
            }
        }
    }
}

@Composable
private fun TacticalActionRow(action: TacticalAction) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(action.name, style = MaterialTheme.typography.bodyLarge)
        if (action.strategicDescription.isNotBlank()) {
            Text(
                action.strategicDescription,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        action.costs.forEach { cost ->
            if (cost.targetValue > 0) {
                val percent = (cost.currentValue / cost.targetValue.toDouble() * 100).coerceIn(0.0, 100.0)
                Text(
                    "Zasoby: ${cost.currentValue.toLong()} / ${cost.targetValue} (${percent.toInt()}%)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
