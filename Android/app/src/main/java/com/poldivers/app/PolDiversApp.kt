package com.poldivers.app

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.graphicsLayer
import com.poldivers.app.ui.anim.LocalAnimations
import com.poldivers.app.ui.anim.zoomIn
import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.poldivers.app.core.AppContainer
import com.poldivers.app.core.haptics.LocalHaptics
import com.poldivers.app.feature.archive.ArchiveScreen
import com.poldivers.app.feature.campaigns.CampaignsScreen
import com.poldivers.app.feature.dss.DssScreen
import com.poldivers.app.feature.news.NewsScreen
import com.poldivers.app.feature.planets.PlanetsScreen
import com.poldivers.app.ui.nav.AppDestination
import com.poldivers.app.ui.settings.SettingsDialog
import com.poldivers.app.ui.update.UpdateBanner
import com.poldivers.app.ui.theme.glow
import coil.compose.rememberAsyncImagePainter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PolDiversApp() {
    val context = LocalContext.current
    val container = remember { AppContainer.get(context) }
    val haptics = LocalHaptics.current
    val navController = rememberNavController()
    var showSettings by remember { mutableStateOf(false) }

    val backStackEntry by navController.currentBackStackEntryAsState()

    // Cross-tab jumps requested by screens (news -> planet on map, term -> archive).
    val requestedTab by container.navigator.tab.collectAsStateWithLifecycle()
    LaunchedEffect(requestedTab) {
        val route = requestedTab ?: return@LaunchedEffect
        if (navController.currentDestination?.route != route) navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
        container.navigator.tabHandled()
    }
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    val current = AppDestination.entries.firstOrNull { it.route == currentRoute }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Image(
                            painterResource(R.mipmap.ic_launcher_foreground),
                            contentDescription = null,
                            modifier = Modifier.size(34.dp).clip(RoundedCornerShape(6.dp)).zoomIn(0.3f),
                        )
                        Column {
                            Text(
                                "POLDIVERS",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            // Tab title slides in from the side the new tab is on.
                            val title = (current?.let { stringResource(it.labelRes) } ?: stringResource(R.string.app_name)).uppercase()
                            val animateTitle = LocalAnimations.current
                            androidx.compose.animation.AnimatedContent(
                                targetState = title to (current?.ordinal ?: 0),
                                transitionSpec = {
                                    if (!animateTitle) {
                                        androidx.compose.animation.EnterTransition.None togetherWith androidx.compose.animation.ExitTransition.None
                                    } else {
                                        val dir = if (targetState.second >= initialState.second) 1 else -1
                                        (slideInVertically { h -> h * dir } + fadeIn()) togetherWith (slideOutVertically { h -> -h * dir } + fadeOut())
                                    }
                                },
                                label = "title",
                            ) { (text, _) ->
                                Text(
                                    text,
                                    style = MaterialTheme.typography.headlineSmall.glow(MaterialTheme.colorScheme.primary, 10f),
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = androidx.compose.ui.graphics.Color(0xF00A0D11)),
                actions = {
                    // Settings cog spins when tapped.
                    val spin = remember { androidx.compose.animation.core.Animatable(0f) }
                    val scope = rememberCoroutineScope()
                    val animateCog = LocalAnimations.current
                    IconButton(onClick = {
                        haptics.tap()
                        showSettings = true
                        if (animateCog) scope.launch { spin.animateTo(spin.value + 180f, androidx.compose.animation.core.tween(500)) }
                    }) {
                        Icon(
                            Icons.Filled.Settings,
                            contentDescription = stringResource(R.string.settings),
                            modifier = Modifier.graphicsLayer { rotationZ = spin.value },
                        )
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                AppDestination.entries.forEach { destination ->
                    NavigationBarItem(
                        selected = currentRoute == destination.route,
                        onClick = {
                            haptics.tap()
                            if (currentRoute == destination.route) {
                                // Re-tapping the open tab takes it back to its starting screen.
                                navController.navigate(destination.route) {
                                    popUpTo(destination.route) { inclusive = true }
                                    launchSingleTop = false
                                }
                            } else {
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        icon = {
                            // Selected tab icon pops up and glows.
                            val selected = currentRoute == destination.route
                            val animateIcon = LocalAnimations.current
                            val lift by androidx.compose.animation.core.animateFloatAsState(
                                targetValue = if (selected && animateIcon) 1f else 0f,
                                animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.45f, stiffness = 380f),
                                label = "tab",
                            )
                            val uri = container.art.icon(destination.gameIcon)
                            val iconModifier = Modifier
                                .size(26.dp)
                                .graphicsLayer {
                                    val s = 1f + 0.22f * lift
                                    scaleX = s
                                    scaleY = s
                                    translationY = -4.dp.toPx() * lift
                                }
                            if (uri != null) {
                                Icon(rememberAsyncImagePainter(uri), contentDescription = null, modifier = iconModifier)
                            } else {
                                Icon(destination.icon, contentDescription = null, modifier = iconModifier)
                            }
                        },
                        label = {
                            Text(
                                stringResource(destination.labelRes),
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                softWrap = false,
                            )
                        },
                    )
                }
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            HorizontalDivider(thickness = 2.dp, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
            UpdateBanner(container.updates)
            FirstLaunchNotice(container)
            // Tabs slide towards the side they sit on in the bottom bar, with a slight zoom.
            val animateTabs = LocalAnimations.current
            fun order(route: String?) = AppDestination.entries.indexOfFirst { it.route == route }
            NavHost(
                navController = navController,
                startDestination = AppDestination.PLANETS.route,
                modifier = Modifier.weight(1f),
                enterTransition = {
                    if (!animateTabs) {
                        androidx.compose.animation.EnterTransition.None
                    } else {
                        val dir = if (order(targetState.destination.route) >= order(initialState.destination.route)) 1 else -1
                        slideInHorizontally(androidx.compose.animation.core.tween(380)) { w -> w / 3 * dir } +
                            fadeIn(androidx.compose.animation.core.tween(380)) +
                            scaleIn(androidx.compose.animation.core.tween(380), initialScale = 0.94f)
                    }
                },
                exitTransition = {
                    if (!animateTabs) {
                        androidx.compose.animation.ExitTransition.None
                    } else {
                        val dir = if (order(targetState.destination.route) >= order(initialState.destination.route)) 1 else -1
                        slideOutHorizontally(androidx.compose.animation.core.tween(380)) { w -> -w / 3 * dir } +
                            fadeOut(androidx.compose.animation.core.tween(300)) +
                            scaleOut(androidx.compose.animation.core.tween(380), targetScale = 0.94f)
                    }
                },
                popEnterTransition = { if (animateTabs) fadeIn(androidx.compose.animation.core.tween(300)) + scaleIn(initialScale = 0.9f) else androidx.compose.animation.EnterTransition.None },
                popExitTransition = { if (animateTabs) fadeOut(androidx.compose.animation.core.tween(250)) + scaleOut(targetScale = 1.05f) else androidx.compose.animation.ExitTransition.None },
            ) {
                composable(AppDestination.PLANETS.route) { PlanetsScreen() }
                composable(AppDestination.CAMPAIGNS.route) { CampaignsScreen() }
                composable(AppDestination.NEWS.route) { NewsScreen() }
                composable(AppDestination.DSS.route) { DssScreen() }
                composable(AppDestination.ARCHIVE.route) { ArchiveScreen() }
            }
        }
    }

    if (showSettings) {
        SettingsDialog(container = container, onDismiss = { showSettings = false })
    }
}

/**
 * The very first start downloads everything from scratch (API data, planet list, fonts...) and
 * is slow; say so, so nobody thinks the app is broken. Hidden for good once planets have loaded.
 */
@Composable
private fun FirstLaunchNotice(container: com.poldivers.app.core.AppContainer) {
    var visible by remember { mutableStateOf(container.preferences.isFirstLaunch) }
    if (!visible) return
    LaunchedEffect(Unit) {
        while (container.hd2Repository.cachedPlanets().isEmpty()) kotlinx.coroutines.delay(1000)
        container.preferences.markFirstLaunchDone()
        kotlinx.coroutines.delay(1500)
        visible = false
    }
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            "PIERWSZE URUCHOMIENIE",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            "Pobieram dane wojny i biblioteki aplikacji — za pierwszym razem może to potrwać do minuty. " +
                "Kolejne uruchomienia będą od razu pokazywać ostatnie dane.",
            style = MaterialTheme.typography.bodySmall,
        )
        androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth())
    }
}
