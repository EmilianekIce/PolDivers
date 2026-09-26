package com.poldivers.app.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.poldivers.app.R
import com.poldivers.app.core.haptics.LocalHaptics

@Composable
fun <T> StateContent(
    state: UiState<T>,
    onRetry: () -> Unit,
    content: @Composable (T) -> Unit,
) {
    val haptics = LocalHaptics.current
    when (state) {
        is UiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator()
                Text(stringResource(R.string.loading), style = MaterialTheme.typography.bodyMedium)
            }
        }

        is UiState.Error -> {
            LaunchedEffect(state) { haptics.warn() }
            Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        stringResource(R.string.error_generic),
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                    )
                    Button(onClick = {
                        haptics.tap()
                        onRetry()
                    }) { Text(stringResource(R.string.retry)) }
                }
            }
        }

        is UiState.Success -> content(state.data)
    }
}

/**
 * [StateContent] + pull-to-refresh + a thin banner when a background refresh failed, driven by
 * a [Loadable]. [content] must be vertically scrollable for the pull gesture to work.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> LoadableContent(
    loadable: Loadable<T>,
    modifier: Modifier = Modifier,
    content: @Composable (T) -> Unit,
) {
    val haptics = LocalHaptics.current
    val state by loadable.state.collectAsStateWithLifecycle()
    val isRefreshing by loadable.isRefreshing.collectAsStateWithLifecycle()
    val refreshFailed by loadable.refreshFailed.collectAsStateWithLifecycle()

    // Confirm with a short pulse when a refresh the user pulled for has landed.
    var pulled by remember { mutableStateOf(false) }
    LaunchedEffect(isRefreshing) {
        if (!isRefreshing && pulled) {
            pulled = false
            if (refreshFailed) haptics.warn() else haptics.confirm()
        }
    }

    StateContent(state = state, onRetry = loadable::refresh) { data ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                haptics.tap()
                pulled = true
                loadable.refresh()
            },
            modifier = modifier.fillMaxSize(),
        ) {
            content(data)
            AnimatedVisibility(visible = refreshFailed, modifier = Modifier.align(Alignment.BottomCenter)) {
                Text(
                    stringResource(R.string.refresh_failed),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onError,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.error)
                        .padding(6.dp),
                )
            }
        }
    }
}
