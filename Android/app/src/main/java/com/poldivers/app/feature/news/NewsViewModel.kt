package com.poldivers.app.feature.news

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.poldivers.app.core.prefs.ApiLanguage
import com.poldivers.app.data.hd2.Hd2Repository
import com.poldivers.app.data.hd2.model.Dispatch
import com.poldivers.app.ui.common.Loadable
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow

data class NewsData(
    val dispatches: List<Dispatch>,
    /** Planet index by (localised) name, to make planet names in dispatches tappable. */
    val planetsByName: Map<String, Int>,
)

class NewsViewModel(
    private val repository: Hd2Repository,
    language: Flow<ApiLanguage>,
) : ViewModel() {

    val data = Loadable(viewModelScope, language) {
        coroutineScope {
            val dispatches = async { repository.getDispatches() }
            val planets = async { runCatching { repository.getPlanets() }.getOrDefault(emptyList()) }
            NewsData(dispatches.await(), planets.await().associate { it.name.lowercase() to it.index })
        }
    }
}
