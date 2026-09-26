package com.poldivers.app.feature.archive

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
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
import com.poldivers.app.data.wiki.WikiRepository
import com.poldivers.app.data.wiki.WikiSearchResult
import com.poldivers.app.ui.common.UiState

/** One-tap starting points; each still triggers a single on-demand search, nothing is prefetched. */
private val QUICK_SEARCHES = listOf(
    "Stratagem", "Primary weapon", "Terminids", "Automatons", "Illuminate", "Armor", "Booster", "Warbond",
)

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
                .padding(start = 12.dp, end = 12.dp, top = 12.dp),
            placeholder = { Text(stringResource(R.string.archive_search_hint)) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = {
                        haptics.tap()
                        viewModel.onQueryChange("")
                    }) { Icon(Icons.Filled.Close, contentDescription = "Wyczyść") }
                }
            },
            singleLine = true,
        )
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            QUICK_SEARCHES.forEach { term ->
                AssistChip(
                    onClick = {
                        haptics.tap()
                        viewModel.onQueryChange(term)
                    },
                    label = { Text(term) },
                )
            }
        }

        when (val current = results) {
            null -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.archive_empty_state),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }

            is UiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

            is UiState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.error_generic))
            }

            is UiState.Success -> if (current.data.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Brak wyników.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                ResultsList(current.data) {
                    haptics.tap()
                    viewModel.openPage(it.title)
                }
            }
        }
    }

    if (selectedPage != null) {
        PageDetailSheet(state = selectedPage, onDismiss = viewModel::closePage)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PageDetailSheet(state: UiState<WikiPage>?, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val haptics = LocalHaptics.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (state) {
                is UiState.Loading, null -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }

                is UiState.Error -> Text(stringResource(R.string.error_generic))

                is UiState.Success -> {
                    val page = state.data
                    Text(page.title, style = MaterialTheme.typography.titleLarge)
                    page.thumbnail?.let { thumb ->
                        AsyncImage(
                            model = thumb.source,
                            contentDescription = page.title,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 260.dp)
                                .clip(RoundedCornerShape(10.dp)),
                        )
                    }
                    Text(page.extract.ifBlank { "Brak opisu." }, style = MaterialTheme.typography.bodyMedium)
                    OutlinedButton(
                        onClick = {
                            haptics.tap()
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(WikiRepository.pageUrl(page.title)))
                            runCatching { context.startActivity(intent) }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                        Text("  Pełny wpis na Helldivers Wiki")
                    }
                    Text(
                        "Źródło: Helldivers Wiki (helldivers.wiki.gg), treść na licencji CC BY-SA 4.0.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun stripHtml(input: String): String =
    input.replace(Regex("<[^>]*>"), "").replace("&quot;", "\"").replace("&amp;", "&").replace("&#039;", "'")
