package com.poldivers.app.feature.archive

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import com.poldivers.app.ui.theme.HudCard
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import coil.compose.AsyncImage
import com.poldivers.app.core.art.GameIcon
import com.poldivers.app.core.art.rememberGameArt
import com.poldivers.app.R
import com.poldivers.app.core.AppContainer
import com.poldivers.app.core.haptics.LocalHaptics
import com.poldivers.app.data.wiki.WikiSearchResult
import com.poldivers.app.ui.common.UiState
import com.poldivers.app.ui.wiki.WikiReader

/** A browsable section of the wiki: page title, Polish label, bundled game icon. */
private data class Category(val page: String, val label: String, val icon: String)

/** One-tap starting points; each opens a single wiki page on demand, nothing is prefetched. */
private val CATEGORIES = listOf(
    Category("Stratagems", "Stratagemy", "Eagle_500kg_Bomb_Stratagem_Icon"),
    Category("Primary Weapons", "Bronie główne", "Muzzle_Icon"),
    Category("Secondary Weapons", "Bronie boczne", "Magazine_Icon"),
    Category("Throwables", "Granaty", "Underbarrel_Icon"),
    Category("Armor", "Pancerze", "Helldiver_Icon"),
    Category("Boosters", "Wzmacniacze", "Armed_Resupply_Pods_Booster_Icon"),
    Category("Terminids", "Terminidzi", "Terminid_Icon"),
    Category("Automatons", "Automatony", "Automaton_Icon"),
    Category("Illuminate", "Iluminaci", "Illuminate_Icon"),
    Category("Warbonds", "Obligacje wojenne", "Medal"),
    Category("Missions", "Misje", "Operation_Icon"),
    Category("Planets", "Planety", "Locations_Icon"),
    Category("Environmental Conditions", "Warunki środowiskowe", "Blizzards_Environmental_Condition_Icon"),
    Category("Democracy Space Station", "Stacja DSS", "DSS_Icon"),
    Category("Ship Modules", "Moduły niszczyciela", "Bridge_Ship_Module_Icon"),
    Category("Campaigns", "Kampanie wojenne", "Liberation_Campaign_Icon"),
    Category("Major Orders", "Rozkazy główne", "Defense_Campaign_Icon"),
    Category("Ministry of Truth", "Ministerstwa", "Ministry_of_Truth_Icon"),
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
    val article by viewModel.article.collectAsStateWithLifecycle()

    val requested by container.navigator.archive.collectAsStateWithLifecycle()
    LaunchedEffect(requested) {
        val query = requested ?: return@LaunchedEffect
        viewModel.closeArticle()
        viewModel.onQueryChange(query)
        container.navigator.archiveHandled()
    }

    article?.let { state ->
        BackHandler { viewModel.back() }
        WikiReader(
            state = state,
            onBack = { viewModel.back() },
            onOpenArticle = viewModel::openArticle,
            onRetry = viewModel::retry,
        )
        return
    }

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
        when (val current = results) {
            null -> CategoryGrid { category ->
                haptics.tap()
                viewModel.openArticle(category.page)
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
                    viewModel.openArticle(it.title)
                }
            }
        }
    }
}

@Composable
private fun ResultsList(results: List<WikiSearchResult>, onClick: (WikiSearchResult) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(results, key = { it.pageid }) { result ->
            HudCard(
                onClick = { onClick(result) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (result.thumbnail != null) {
                            AsyncImage(
                                model = result.thumbnail,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            Icon(Icons.Filled.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(result.title, style = MaterialTheme.typography.titleMedium)
                        Text(
                            stripHtml(result.snippet),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

private fun stripHtml(input: String): String =
    input.replace(Regex("<[^>]*>"), "").replace("&quot;", "\"").replace("&amp;", "&").replace("&#039;", "'")

@Composable
private fun CategoryGrid(onClick: (Category) -> Unit) {
    val art = rememberGameArt()
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 150.dp),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text(
                stringResource(R.string.archive_empty_state),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        items(CATEGORIES) { category ->
            HudCard(onClick = { onClick(category) }, modifier = Modifier.fillMaxWidth()) {
                Row(
                    Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    GameIcon(art.icon(category.icon), size = 34.dp)
                    Text(category.label.uppercase(), style = MaterialTheme.typography.labelLarge, maxLines = 2)
                }
            }
        }
    }
}
