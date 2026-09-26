package com.poldivers.app.ui.nav

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.SatelliteAlt
import androidx.compose.ui.graphics.vector.ImageVector
import com.poldivers.app.R

/**
 * [gameIcon] is a bundled game icon (assets/icons) shown in the bottom bar; [icon] is the
 * Material fallback if that asset is missing.
 */
enum class AppDestination(val route: String, val labelRes: Int, val icon: ImageVector, val gameIcon: String) {
    PLANETS("planets", R.string.tab_planets, Icons.Filled.Public, "Locations_Icon"),
    CAMPAIGNS("campaigns", R.string.tab_campaigns, Icons.Filled.Flag, "Liberation_Campaign_Icon"),
    NEWS("news", R.string.tab_news, Icons.AutoMirrored.Filled.Article, "Ministry_of_Truth_Icon"),
    DSS("dss", R.string.tab_dss, Icons.Filled.SatelliteAlt, "DSS_Icon"),
    ARCHIVE("archive", R.string.tab_archive, Icons.AutoMirrored.Filled.MenuBook, "Ministry_of_Science_Icon"),
}
