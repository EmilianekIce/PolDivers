package com.poldivers.app.feature.news

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import com.poldivers.app.data.hd2.model.Dispatch
import com.poldivers.app.ui.common.StateContent
import com.poldivers.app.ui.common.formatAgo

@Composable
fun NewsScreen() {
    val context = LocalContext.current
    val container = AppContainer.get(context)
    val viewModel: NewsViewModel = viewModel(
        factory = viewModelFactory { initializer { NewsViewModel(container.hd2Repository) } },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()

    StateContent(state = state, onRetry = viewModel::refresh) { dispatches ->
        LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(dispatches, key = { it.id }) { DispatchCard(it) }
        }
    }
}

@Composable
private fun DispatchCard(dispatch: Dispatch) {
    val content = parseDispatchMessage(dispatch.message)
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            content.headline?.let {
                Text(it, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            }
            Text(content.body, style = MaterialTheme.typography.bodyMedium)
            Text(
                formatAgo(dispatch.published),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
