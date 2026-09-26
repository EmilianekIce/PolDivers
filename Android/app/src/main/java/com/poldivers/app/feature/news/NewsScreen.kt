package com.poldivers.app.feature.news

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import com.poldivers.app.ui.theme.HudCard
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.OutlinedButton
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
import com.poldivers.app.ui.common.gameText
import com.poldivers.app.ui.theme.glow

@Composable
fun NewsScreen() {
    val context = LocalContext.current
    val container = AppContainer.get(context)
    val viewModel: NewsViewModel = viewModel(
        factory = viewModelFactory {
            initializer { NewsViewModel(container.hd2Repository, container.preferences.language) }
        },
    )

    val navigator = container.navigator
    LoadableContent(viewModel.data) { data ->
        var shown by rememberSaveable { mutableIntStateOf(FIRST_PAGE) }
        LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(data.dispatches.take(shown), key = { it.id }) { dispatch ->
                DispatchCard(
                    dispatch = dispatch,
                    planetNames = data.planetsByName.keys,
                    onPlanet = { name -> data.planetsByName[name.lowercase()]?.let(navigator::openPlanet) },
                    onTerm = navigator::openArchive,
                )
            }
            if (data.dispatches.size > shown) {
                item(key = "more") {
                    OutlinedButton(
                        onClick = { container.haptics.tap(); shown += PAGE },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("POKAŻ STARSZE (${data.dispatches.size - shown})") }
                }
            }
        }
    }
}

private const val FIRST_PAGE = 6
private const val PAGE = 10

@Composable
private fun DispatchCard(
    dispatch: Dispatch,
    planetNames: Collection<String>,
    onPlanet: (String) -> Unit,
    onTerm: (String) -> Unit,
) {
    val content = parseDispatchMessage(dispatch.message)
    val context = LocalContext.current
    val polish = AppContainer.get(context).preferences.language.value.tag.startsWith("pl")
    val newsTerms = remember(polish) { com.poldivers.app.data.hd2.gameTermNames(polish) }
    HudCard(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            content.headline?.let {
                Text(it, style = MaterialTheme.typography.headlineSmall.glow(MaterialTheme.colorScheme.primary, 12f), color = MaterialTheme.colorScheme.primary)
            }
            val highlight = MaterialTheme.colorScheme.primary
            val body = remember(content, planetNames) {
                gameText(content.rawBody, highlight, planetNames, onPlanet = onPlanet, onTerm = onTerm, terms = newsTerms)
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
