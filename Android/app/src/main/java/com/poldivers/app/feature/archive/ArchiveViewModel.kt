package com.poldivers.app.feature.archive

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.poldivers.app.data.wiki.WikiArticle
import com.poldivers.app.data.wiki.WikiRepository
import com.poldivers.app.data.wiki.WikiSearchResult
import com.poldivers.app.ui.common.UiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Every network call here is a direct response to a user action (typing a search, tapping a
 * result or a link inside an article) -- there is no background crawling or bulk caching of
 * the wiki.
 */
class ArchiveViewModel(private val repository: WikiRepository) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _results = MutableStateFlow<UiState<List<WikiSearchResult>>?>(null)
    val results: StateFlow<UiState<List<WikiSearchResult>>?> = _results.asStateFlow()

    /** Articles opened in this reading session, newest last (back = pop). */
    private val stack = mutableListOf<String>()

    private val _article = MutableStateFlow<UiState<WikiArticle>?>(null)
    val article: StateFlow<UiState<WikiArticle>?> = _article.asStateFlow()

    private var searchJob: Job? = null
    private var articleJob: Job? = null

    fun onQueryChange(newQuery: String) {
        _query.value = newQuery
        searchJob?.cancel()
        if (newQuery.isBlank()) {
            _results.value = null
            return
        }
        searchJob = viewModelScope.launch {
            delay(350)
            _results.value = UiState.Loading
            _results.value = try {
                UiState.Success(repository.search(newQuery))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                UiState.Error(e.message ?: "unknown error")
            }
        }
    }

    fun openArticle(title: String) {
        stack += title
        load(title)
    }

    /** @return false when there is nothing to go back to (the reader was closed). */
    fun back(): Boolean {
        if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex)
        val previous = stack.lastOrNull()
        if (previous == null) {
            closeArticle()
            return false
        }
        load(previous)
        return true
    }

    /** Number of articles in the current reading session. */
    val depth: Int get() = stack.size

    fun closeArticle() {
        stack.clear()
        articleJob?.cancel()
        _article.value = null
    }

    fun retry() {
        stack.lastOrNull()?.let(::load)
    }

    private fun load(title: String) {
        articleJob?.cancel()
        articleJob = viewModelScope.launch {
            _article.value = UiState.Loading
            _article.value = try {
                UiState.Success(repository.getArticle(title))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                UiState.Error(e.message ?: "unknown error")
            }
        }
    }
}
