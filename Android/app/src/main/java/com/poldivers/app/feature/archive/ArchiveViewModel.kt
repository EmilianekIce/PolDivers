package com.poldivers.app.feature.archive

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.poldivers.app.data.wiki.WikiPage
import com.poldivers.app.data.wiki.WikiRepository
import com.poldivers.app.data.wiki.WikiSearchResult
import com.poldivers.app.ui.common.UiState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Every network call here is a direct response to a user action (typing a search, tapping a
 * result) -- there is no background crawling or bulk caching of the wiki, per the "only fetch
 * what's tapped, then render through our own UI" requirement.
 */
class ArchiveViewModel(private val repository: WikiRepository) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _results = MutableStateFlow<UiState<List<WikiSearchResult>>?>(null)
    val results: StateFlow<UiState<List<WikiSearchResult>>?> = _results.asStateFlow()

    private val _selectedPage = MutableStateFlow<UiState<WikiPage>?>(null)
    val selectedPage: StateFlow<UiState<WikiPage>?> = _selectedPage.asStateFlow()

    private var searchJob: Job? = null

    fun onQueryChange(newQuery: String) {
        _query.value = newQuery
        searchJob?.cancel()
        if (newQuery.isBlank()) {
            _results.value = null
            return
        }
        searchJob = viewModelScope.launch {
            kotlinx.coroutines.delay(350)
            _results.value = UiState.Loading
            _results.value = runCatching { repository.search(newQuery) }
                .fold(
                    onSuccess = { UiState.Success(it) },
                    onFailure = { UiState.Error(it.message ?: "unknown error") },
                )
        }
    }

    fun openPage(title: String) {
        viewModelScope.launch {
            _selectedPage.value = UiState.Loading
            _selectedPage.value = runCatching { repository.getPage(title) }
                .fold(
                    onSuccess = { UiState.Success(it) },
                    onFailure = { UiState.Error(it.message ?: "unknown error") },
                )
        }
    }

    fun closePage() {
        _selectedPage.value = null
    }
}
