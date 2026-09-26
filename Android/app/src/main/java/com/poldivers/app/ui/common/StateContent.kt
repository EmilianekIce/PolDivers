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
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.size
import com.poldivers.app.ui.anim.zoomIn
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import com.poldivers.app.R
import com.poldivers.app.core.haptics.LocalHaptics

@Composable
fun <T> StateContent(
    state: UiState<T>,
    onRetry: () -> Unit,
    content: @Composable (T) -> Unit,
) {
    val haptics = LocalHaptics.current
    val animate = com.poldivers.app.ui.anim.LocalAnimations.current
    val key = when (state) {
        is UiState.Loading -> 0
        is UiState.Error -> 1
        is UiState.Success -> 2
    }
    // Loader -> content cross-fades with a small zoom instead of popping.
    androidx.compose.animation.AnimatedContent(
        targetState = key,
        transitionSpec = {
            if (!animate) {
                androidx.compose.animation.EnterTransition.None togetherWith androidx.compose.animation.ExitTransition.None
            } else {
                (androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(450)) +
                    androidx.compose.animation.scaleIn(androidx.compose.animation.core.tween(450), initialScale = 0.96f)) togetherWith
                    (androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(250)) +
                        androidx.compose.animation.scaleOut(androidx.compose.animation.core.tween(250), targetScale = 1.04f))
            }
        },
        label = "state",
    ) { k ->
        when (k) {
            0 -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                RadarLoader(stringResource(R.string.loading))
            }

            1 -> {
                val message = (state as? UiState.Error)?.message.orEmpty()
                LaunchedEffect(message) { haptics.warn() }
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.zoomIn(0.85f),
                    ) {
                        Text(
                            stringResource(R.string.error_generic),
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            friendlyError(message),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                        Button(onClick = {
                            haptics.tap()
                            onRetry()
                        }) { Text(stringResource(R.string.retry)) }
                    }
                }
            }

            else -> (state as? UiState.Success)?.let { content(it.data) }
        }
    }
}

/** Radar sweep with pulsing rings -- the loading screen. */
@Composable
fun RadarLoader(label: String, modifier: Modifier = Modifier) {
    val animate = com.poldivers.app.ui.anim.LocalAnimations.current
    val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "radar")
    val sweep by transition.animateFloat(
        0f,
        360f,
        androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(1600, easing = androidx.compose.animation.core.LinearEasing)),
        label = "sweep",
    )
    val ring by transition.animateFloat(
        0f,
        1f,
        androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(1400)),
        label = "ring",
    )
    val dots by transition.animateFloat(
        0f,
        3.99f,
        androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(1200, easing = androidx.compose.animation.core.LinearEasing)),
        label = "dots",
    )
    val accent = MaterialTheme.colorScheme.primary
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        androidx.compose.foundation.Canvas(Modifier.size(120.dp)) {
            val c = center
            val r = size.minDimension / 2f
            for (i in 1..3) {
                drawCircle(accent.copy(alpha = 0.25f), radius = r * i / 3f, center = c, style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
            }
            drawLine(accent.copy(alpha = 0.2f), c - androidx.compose.ui.geometry.Offset(r, 0f), c + androidx.compose.ui.geometry.Offset(r, 0f), 1.dp.toPx())
            drawLine(accent.copy(alpha = 0.2f), c - androidx.compose.ui.geometry.Offset(0f, r), c + androidx.compose.ui.geometry.Offset(0f, r), 1.dp.toPx())
            if (animate) {
                androidx.compose.ui.graphics.drawscope.rotate(sweep, c) {
                    drawArc(
                        androidx.compose.ui.graphics.Brush.sweepGradient(
                            0f to androidx.compose.ui.graphics.Color.Transparent,
                            0.75f to androidx.compose.ui.graphics.Color.Transparent,
                            1f to accent.copy(alpha = 0.55f),
                            center = c,
                        ),
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = true,
                    )
                }
                drawCircle(accent.copy(alpha = (1f - ring) * 0.8f), radius = r * ring, center = c, style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
            }
            drawCircle(accent, radius = 4.dp.toPx(), center = c)
        }
        Text(
            label.trimEnd('.', '…').uppercase() + ".".repeat(if (animate) dots.toInt() else 3),
            style = MaterialTheme.typography.labelLarge,
            color = accent,
        )
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
    val lastError by loadable.lastError.collectAsStateWithLifecycle()
    val updatedAt by loadable.updatedAt.collectAsStateWithLifecycle()

    // Confirm with a short pulse when a refresh the user pulled for has landed.
    var pulled by remember { mutableStateOf(false) }
    LaunchedEffect(isRefreshing) {
        if (!isRefreshing && pulled) {
            pulled = false
            if (refreshFailed) haptics.warn() else haptics.confirm()
        }
    }

    AutoRefresh(loadable)

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
                val age = if (updatedAt > 0) " · dane sprzed ${((System.currentTimeMillis() - updatedAt) / 60_000).coerceAtLeast(0)} min" else ""
                Text(
                    stringResource(R.string.refresh_failed) + age + (lastError?.let { "\n" + friendlyError(it) } ?: ""),
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

/**
 * Re-fetches every [periodMs] while the screen is visible (and the app in the foreground).
 * Frequent samples are what let [com.poldivers.app.core.trends.TrendStore] compute liberation
 * rates and ETAs; nothing polls in the background.
 */
@Composable
fun AutoRefresh(loadable: Loadable<*>, periodMs: Long = AUTO_REFRESH_MS) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(loadable, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                delay(periodMs)
                loadable.refresh(silent = true)
            }
        }
    }
}

const val AUTO_REFRESH_MS = 60_000L

/** Short, human hint for the most common failures, plus the raw message for bug reports. */
fun friendlyError(message: String): String = when {
    message.contains("429") -> "Serwer API ogranicza liczbę zapytań — spróbuj za chwilę. ($message)"
    message.contains("Unable to resolve host", ignoreCase = true) ||
        message.contains("failed to connect", ignoreCase = true) -> "Brak połączenia z internetem. ($message)"
    message.contains("timeout", ignoreCase = true) -> "Serwer odpowiada zbyt wolno. ($message)"
    else -> "Szczegóły: $message"
}
