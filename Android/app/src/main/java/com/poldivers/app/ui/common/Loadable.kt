package com.poldivers.app.ui.common

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Loading/refresh state shared by every API-backed screen.
 *
 * [reloadTrigger] is collected for the lifetime of [scope]: every emission (the first one
 * included) reloads the data. Screens pass the API language flow here, so switching the
 * language in settings re-fetches already opened tabs with the new Accept-Language.
 *
 * Once data has been shown it is never replaced by a full-screen spinner or error again:
 * later reloads only toggle [isRefreshing], and a failed reload keeps the old data and sets
 * [refreshFailed] instead.
 */
class Loadable<T>(
    private val scope: CoroutineScope,
    reloadTrigger: Flow<*>,
    private val load: suspend () -> T,
) {
    private val _state = MutableStateFlow<UiState<T>>(UiState.Loading)
    val state: StateFlow<UiState<T>> = _state.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _refreshFailed = MutableStateFlow(false)
    val refreshFailed: StateFlow<Boolean> = _refreshFailed.asStateFlow()

    private var job: Job? = null

    init {
        scope.launch { reloadTrigger.collect { refresh() } }
    }

    fun refresh() {
        job?.cancel()
        job = scope.launch {
            val hasData = _state.value is UiState.Success
            if (hasData) _isRefreshing.value = true else _state.value = UiState.Loading
            try {
                _state.value = UiState.Success(load())
                _refreshFailed.value = false
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (hasData) {
                    _refreshFailed.value = true
                } else {
                    _state.value = UiState.Error(e.message ?: "unknown error")
                }
            } finally {
                _isRefreshing.value = false
            }
        }
    }
}
