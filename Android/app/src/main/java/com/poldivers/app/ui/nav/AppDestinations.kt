package com.poldivers.app.ui.nav

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.SatelliteAlt
import androidx.compose.ui.graphics.vector.ImageVector
import com.poldivers.app.R

enum class AppDestination(val route: String, val labelRes: Int, val icon: ImageVector) {
    PLANETS("planets", R.string.tab_planets, Icons.Filled.Public),
    CAMPAIGNS("campaigns", R.string.tab_campaigns, Icons.Filled.Flag),
    NEWS("news", R.string.tab_news, Icons.AutoMirrored.Filled.Article),
    DSS("dss", R.string.tab_dss, Icons.Filled.SatelliteAlt),
    ARCHIVE("archive", R.string.tab_archive, Icons.AutoMirrored.Filled.MenuBook),
}
