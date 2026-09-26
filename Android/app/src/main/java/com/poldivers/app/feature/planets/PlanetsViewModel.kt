package com.poldivers.app.feature.planets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.poldivers.app.data.hd2.Hd2Repository
import com.poldivers.app.data.hd2.model.Planet
import com.poldivers.app.ui.common.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class PlanetsView { LIST, MAP }

class PlanetsViewModel(private val repository: Hd2Repository) : ViewModel() {

    private val _state = MutableStateFlow<UiState<List<Planet>>>(UiState.Loading)
    val state: StateFlow<UiState<List<Planet>>> = _state.asStateFlow()

    private val _view = MutableStateFlow(PlanetsView.LIST)
    val view: StateFlow<PlanetsView> = _view.asStateFlow()

    private val _selectedPlanet = MutableStateFlow<Planet?>(null)
    val selectedPlanet: StateFlow<Planet?> = _selectedPlanet.asStateFlow()

    init {
        refresh()
    }

    fun setView(view: PlanetsView) {
        _view.value = view
    }

    fun selectPlanet(planet: Planet?) {
        _selectedPlanet.value = planet
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = UiState.Loading
            _state.value = runCatching { repository.getPlanetsSortedByPlayers() }
                .fold(
                    onSuccess = { UiState.Success(it) },
                    onFailure = { UiState.Error(it.message ?: "unknown error") },
                )
        }
    }
}
