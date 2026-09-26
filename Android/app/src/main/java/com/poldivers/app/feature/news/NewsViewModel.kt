package com.poldivers.app.feature.news

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.poldivers.app.core.prefs.ApiLanguage
import com.poldivers.app.data.hd2.Hd2Repository
import com.poldivers.app.data.hd2.model.Dispatch
import com.poldivers.app.ui.common.Loadable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.poldivers.app.data.hd2.StaleAllowed
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

    val data: Loadable<NewsData> = Loadable(viewModelScope, language) {
        val dispatches = repository.getDispatches()
        // Planet names only make links -- never wait for the (big, slow) planet list here.
        val planets = repository.cachedPlanets()
        if (planets.isEmpty()) {
            viewModelScope.launch {
                val ok = runCatching { withContext(StaleAllowed) { repository.getPlanets() } }.isSuccess
                if (ok) data.refresh(silent = true)
            }
        }
        NewsData(dispatches, planets.associate { it.name.lowercase() to it.index })
    }
}
