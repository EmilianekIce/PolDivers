package com.poldivers.app.feature.dss

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.poldivers.app.core.prefs.ApiLanguage
import com.poldivers.app.data.hd2.Hd2Repository
import com.poldivers.app.data.hd2.model.Planet
import com.poldivers.app.data.hd2.model.SpaceStation
import com.poldivers.app.ui.common.Loadable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class DssViewModel(
    private val repository: Hd2Repository,
    language: Flow<ApiLanguage>,
) : ViewModel() {

    val data = Loadable(viewModelScope, language) {
        val stations = runCatching { repository.getSpaceStations() }.getOrDefault(emptyList())
        stations.ifEmpty {
            // Wrapper has no v2 data for the current station id: position + jump timer from the
            // game's raw war status still work, tactical actions are then unknown.
            val location = repository.getDssLocation()
            val planet = location?.let { (index, _) -> repository.getPlanets().firstOrNull { it.index == index } }
            if (planet == null) {
                emptyList()
            } else {
                listOf(SpaceStation(id32 = 0, planet = planet, electionEnd = location?.second.orEmpty()))
            }
        }
    }

    private val _selectedPlanet = MutableStateFlow<Planet?>(null)
    val selectedPlanet: StateFlow<Planet?> = _selectedPlanet.asStateFlow()

    fun selectPlanet(planet: Planet?) {
        _selectedPlanet.value = planet
    }
}
