package com.poldivers.app.ui.wiki

import com.poldivers.app.ui.anim.zoomIn
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.poldivers.app.R
import com.poldivers.app.core.haptics.LocalHaptics
import com.poldivers.app.data.wiki.WikiArticle
import com.poldivers.app.data.wiki.WikiRepository
import com.poldivers.app.ui.common.UiState

/** Full-screen wiki article with a back arrow and "open in browser". */
@Composable
fun WikiReader(
    state: UiState<WikiArticle>,
    onBack: (() -> Unit)?,
    onOpenArticle: (String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val haptics = LocalHaptics.current
    val translator = remember { com.poldivers.app.core.AppContainer.get(context).translator }
    val scope = rememberCoroutineScope()
    val article = (state as? UiState.Success)?.data
    // Machine translation of the current article (on demand, per article).
    var translated by remember(article?.title) { mutableStateOf<WikiArticle?>(null) }
    var showTranslated by remember(article?.title) { mutableStateOf(false) }
    var translating by remember(article?.title) { mutableStateOf(false) }
    var translateError by remember(article?.title) { mutableStateOf(false) }
    val auto = com.poldivers.app.ui.common.LocalAutoTranslate.current
    androidx.compose.runtime.LaunchedEffect(article?.title, auto) {
        if (article == null || !auto || !translator.canTranslate || translated != null) return@LaunchedEffect
        translating = true
        runCatching { translator.translateHtml(article.html) }
            .onSuccess {
                translated = article.copy(html = it)
                showTranslated = true
            }
            .onFailure { translateError = true }
        translating = false
    }
    Column(modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                IconButton(onClick = {
                    haptics.tap()
                    onBack()
                }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Wstecz") }
            }
            Text(
                (state as? UiState.Success)?.data?.title ?: "Archiwum",
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
            )
            if (article != null && translator.canTranslate) {
                TextButton(
                    onClick = {
                        haptics.tap()
                        if (translated != null) {
                            showTranslated = !showTranslated
                        } else if (!translating) {
                            translating = true
                            translateError = false
                            scope.launch {
                                runCatching { translator.translateHtml(article.html) }
                                    .onSuccess {
                                        translated = article.copy(html = it)
                                        showTranslated = true
                                    }
                                    .onFailure { translateError = true }
                                translating = false
                            }
                        }
                    },
                ) {
                    Text(
                        when {
                            translating -> "TŁUMACZĘ…"
                            showTranslated -> "ORYGINAŁ"
                            else -> "PRZETŁUMACZ"
                        },
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
            if (state is UiState.Success) {
                IconButton(onClick = {
                    haptics.tap()
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(WikiRepository.pageUrl(state.data.title)))
                    runCatching { context.startActivity(intent) }
                }) { Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Otwórz na wiki") }
            }
        }
        if (translateError) {
            Text(
                "Nie udało się przetłumaczyć (pierwsze użycie pobiera ok. 30 MB modelu językowego — sprawdź internet).",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
        when (state) {
            is UiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                com.poldivers.app.ui.common.RadarLoader("Pobieram archiwum")
            }
            is UiState.Error -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.error_generic))
                    Button(onClick = onRetry) { Text(stringResource(R.string.retry)) }
                }
            }
            // Each article zooms in as it opens.
            is UiState.Success -> androidx.compose.runtime.key(state.data.title) {
                WikiArticleView(
                    article = if (showTranslated) translated ?: state.data else state.data,
                    onOpenArticle = {
                        haptics.tap()
                        onOpenArticle(it)
                    },
                    modifier = Modifier.fillMaxSize().zoomIn(0.9f),
                )
            }
        }
    }
}
