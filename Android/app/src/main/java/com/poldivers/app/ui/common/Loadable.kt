package com.poldivers.app.ui.common

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.poldivers.app.data.hd2.StaleAllowed

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

    /** Why the last refresh failed (shown in the banner), and when data last arrived. */
    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()
    private val _updatedAt = MutableStateFlow(0L)
    val updatedAt: StateFlow<Long> = _updatedAt.asStateFlow()
    private var failures = 0

    private var job: Job? = null

    init {
        scope.launch { reloadTrigger.collect { refresh() } }
    }

    /** @param silent background auto-refresh: no pull-to-refresh spinner, skipped if a load is running. */
    fun refresh(silent: Boolean = false) {
        if (silent && job?.isActive == true) return
        job?.cancel()
        job = scope.launch {
            val hasData = _state.value is UiState.Success
            if (hasData) {
                if (!silent) _isRefreshing.value = true
            } else {
                _state.value = UiState.Loading
            }
            try {
                if (!hasData) {
                    // First paint from the last known copy (disk), then the live one below.
                    _state.value = UiState.Success(withContext(StaleAllowed) { load() })
                }
                _state.value = UiState.Success(load())
                _refreshFailed.value = false
                _lastError.value = null
                _updatedAt.value = System.currentTimeMillis()
                failures = 0
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (_state.value is UiState.Success) {
                    // A pulled refresh reports at once; background ones only when they keep
                    // failing. Either way the old data stays and we retry soon.
                    failures++
                    _lastError.value = e.message ?: e.javaClass.simpleName
                    if ((hasData && !silent) || failures >= 2) _refreshFailed.value = true
                    if (failures <= 3) {
                        scope.launch {
                            kotlinx.coroutines.delay(15_000L * failures)
                            refresh(silent = true)
                        }
                    }
                } else {
                    _state.value = UiState.Error(e.message ?: "unknown error")
                }
            } finally {
                _isRefreshing.value = false
            }
        }
    }
}
