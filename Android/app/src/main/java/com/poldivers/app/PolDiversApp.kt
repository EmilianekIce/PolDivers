package com.poldivers.app

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
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
        navController.navigate(route) {
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
                            modifier = Modifier.size(34.dp).clip(RoundedCornerShape(6.dp)),
                        )
                        Column {
                            Text(
                                "POLDIVERS",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                (current?.let { stringResource(it.labelRes) } ?: stringResource(R.string.app_name)).uppercase(),
                                style = MaterialTheme.typography.headlineSmall.glow(MaterialTheme.colorScheme.primary, 10f),
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = androidx.compose.ui.graphics.Color(0xF00A0D11)),
                actions = {
                    IconButton(onClick = {
                        haptics.tap()
                        showSettings = true
                    }) {
                        Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.settings))
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
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            val uri = container.art.icon(destination.gameIcon)
                            if (uri != null) {
                                Icon(rememberAsyncImagePainter(uri), contentDescription = null, modifier = Modifier.size(26.dp))
                            } else {
                                Icon(destination.icon, contentDescription = null)
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
            NavHost(
                navController = navController,
                startDestination = AppDestination.PLANETS.route,
                modifier = Modifier.weight(1f),
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
