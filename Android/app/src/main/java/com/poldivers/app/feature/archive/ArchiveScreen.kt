package com.poldivers.app.feature.archive

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import coil.compose.AsyncImage
import com.poldivers.app.R
import com.poldivers.app.core.AppContainer
import com.poldivers.app.core.haptics.LocalHaptics
import com.poldivers.app.data.wiki.WikiPage
import com.poldivers.app.data.wiki.WikiSearchResult
import com.poldivers.app.ui.common.UiState

@Composable
fun ArchiveScreen() {
    val context = LocalContext.current
    val container = AppContainer.get(context)
    val viewModel: ArchiveViewModel = viewModel(
        factory = viewModelFactory { initializer { ArchiveViewModel(container.wikiRepository) } },
    )
    val haptics = LocalHaptics.current

    val query by viewModel.query.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()
    val selectedPage by viewModel.selectedPage.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = viewModel::onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            placeholder = { Text(stringResource(R.string.archive_search_hint)) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
        )

        when (val current = results) {
            null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.archive_empty_state),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            is UiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

            is UiState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.error_generic))
            }

            is UiState.Success -> ResultsList(current.data) {
                haptics.tap()
                viewModel.openPage(it.title)
            }
        }
    }

    if (selectedPage != null) {
        PageDetailDialog(state = selectedPage, onDismiss = viewModel::closePage)
    }
}

@Composable
private fun ResultsList(results: List<WikiSearchResult>, onClick: (WikiSearchResult) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(results, key = { it.pageid }) { result ->
            Card(
                onClick = { onClick(result) },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(result.title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        stripHtml(result.snippet),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun PageDetailDialog(state: UiState<WikiPage>?, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Zamknij") } },
        title = { Text(if (state is UiState.Success) state.data.title else "Archiwum") },
        text = {
            when (state) {
                is UiState.Loading, null -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }

                is UiState.Error -> Text(stringResource(R.string.error_generic))

                is UiState.Success -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.data.thumbnail?.let { thumb ->
                        AsyncImage(
                            model = thumb.source,
                            contentDescription = state.data.title,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Text(state.data.extract.ifBlank { "Brak opisu." }, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Źródło: Helldivers Wiki (CC BY-SA)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
    )
}

private fun stripHtml(input: String): String = input.replace(Regex("<[^>]*>"), "")
