package com.poldivers.app.core

import android.content.Context
import com.poldivers.app.core.haptics.Haptics
import com.poldivers.app.core.network.NetworkModule
import com.poldivers.app.core.prefs.AppPreferences
import com.poldivers.app.core.trends.TrendStore
import com.poldivers.app.core.update.UpdateManager
import java.io.File
import com.poldivers.app.data.hd2.CampaignHistoryStore
import com.poldivers.app.data.hd2.Hd2Repository
import com.poldivers.app.data.hd2.PlanetEffectCatalog
import com.poldivers.app.data.wiki.WikiRepository

/**
 * Minimal hand-rolled DI container. The app is small enough (5 screens, 2 repositories)
 * that pulling in Hilt/Koin would add more ceremony than it saves.
 */
class AppContainer(context: Context) {

    val preferences = AppPreferences(context)
    val haptics = Haptics(context, preferences)

    val trends = TrendStore(File(context.filesDir, "trends.json"))
    val campaignHistory = CampaignHistoryStore(File(context.filesDir, "campaign_history.json"))

    val hd2Repository: Hd2Repository by lazy {
        Hd2Repository(
            NetworkModule.provideHd2Api(preferences),
            trends,
            PlanetEffectCatalog(context),
            language = { preferences.language.value.tag },
        )
    }

    val updates: UpdateManager by lazy { UpdateManager(context, NetworkModule.plainClient) }

    val wikiRepository: WikiRepository by lazy {
        WikiRepository(NetworkModule.provideWikiApi())
    }

    companion object {
        @Volatile private var instance: AppContainer? = null

        fun get(context: Context): AppContainer =
            instance ?: synchronized(this) {
                instance ?: AppContainer(context.applicationContext).also { instance = it }
            }
    }
}
