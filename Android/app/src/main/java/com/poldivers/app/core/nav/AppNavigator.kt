package com.poldivers.app.core.nav

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Cross-tab jumps (tap a planet name in the news -> map with that planet open; tap a highlighted
 * term -> archive). Screens consume and clear the pending request when they show it.
 */
class AppNavigator {
    private val _tab = MutableStateFlow<String?>(null)
    val tab: StateFlow<String?> = _tab.asStateFlow()

    private val _planet = MutableStateFlow<Int?>(null)
    val planet: StateFlow<Int?> = _planet.asStateFlow()

    private val _archive = MutableStateFlow<String?>(null)
    val archive: StateFlow<String?> = _archive.asStateFlow()

    fun openPlanet(index: Int) {
        _planet.value = index
        _tab.value = "planets"
    }

    /** Opens the archive searching for [query]; the archive translates it to English first. */
    fun openArchive(query: String) {
        _archive.value = query
        _tab.value = "archive"
    }

    fun tabHandled() { _tab.value = null }
    fun planetHandled() { _planet.value = null }
    fun archiveHandled() { _archive.value = null }
}
