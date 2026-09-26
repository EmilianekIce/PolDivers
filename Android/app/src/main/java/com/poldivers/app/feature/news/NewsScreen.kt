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
import androidx.compose.runtime.remember
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.poldivers.app.core.AppContainer
import com.poldivers.app.data.hd2.model.Dispatch
import com.poldivers.app.ui.common.LoadableContent
import com.poldivers.app.ui.common.formatAgo

@Composable
fun NewsScreen() {
    val context = LocalContext.current
    val container = AppContainer.get(context)
    val viewModel: NewsViewModel = viewModel(
        factory = viewModelFactory {
            initializer { NewsViewModel(container.hd2Repository, container.preferences.language) }
        },
    )

    LoadableContent(viewModel.data) { dispatches ->
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
            val highlight = MaterialTheme.colorScheme.primary
            val body = remember(content) {
                buildAnnotatedString {
                    content.body.forEach { span ->
                        when (span.style) {
                            null -> append(span.text)
                            3 -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(span.text) }
                            else -> withStyle(SpanStyle(color = highlight, fontWeight = FontWeight.SemiBold)) { append(span.text) }
                        }
                    }
                }
            }
            Text(body, style = MaterialTheme.typography.bodyMedium)
            Text(
                formatAgo(dispatch.published),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
