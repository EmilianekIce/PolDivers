package com.poldivers.app.feature.planets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.poldivers.app.core.prefs.ApiLanguage
import com.poldivers.app.data.hd2.Hd2Repository
import com.poldivers.app.data.hd2.PlanetEffect
import com.poldivers.app.data.hd2.model.Planet
import com.poldivers.app.data.hd2.targetPlanetIndexes
import com.poldivers.app.ui.common.Loadable
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class PlanetsView { LIST, MAP }

data class PlanetsData(
    /** All planets, most populated first. */
    val planets: List<Planet>,
    /** Planets with an active liberation/defense campaign. */
    val campaignPlanets: Set<Int>,
    /** Planets targeted by a current Major Order. */
    val majorOrderPlanets: Set<Int>,
    /** Planet the Democracy Space Station is orbiting, if any. */
    val dssPlanet: Int?,
    /** Active effects per planet (Jet Brigade, Gloom, ...). */
    val effects: Map<Int, List<PlanetEffect>> = emptyMap(),
)

class PlanetsViewModel(
    private val repository: Hd2Repository,
    language: Flow<ApiLanguage>,
) : ViewModel() {

    /** Extras (effects, DSS, MO targets) arrive after the planets so the list shows up fast. */
    private val _extras = MutableStateFlow(PlanetsData(emptyList(), emptySet(), emptySet(), null))

    val data = Loadable(viewModelScope, language) {
        val result = coroutineScope {
            val planets = async { repository.getPlanetsSortedByPlayers() }
            val campaigns = async { repository.getCampaigns() }
            PlanetsData(
                planets = planets.await(),
                campaignPlanets = campaigns.await().map { it.planet.index }.toSet(),
                majorOrderPlanets = _extras.value.majorOrderPlanets,
                dssPlanet = _extras.value.dssPlanet,
                effects = _extras.value.effects,
            )
        }
        // Stale first paint -> stale extras too (from disk); the live pass reloads them fresh.
        val stale = currentCoroutineContext()[com.poldivers.app.data.hd2.StaleAllowed.Key] ?: kotlin.coroutines.EmptyCoroutineContext
        viewModelScope.launch(stale) { loadExtras() }
        result
    }

    /** Planets data with the latest extras merged in. */
    val merged: StateFlow<com.poldivers.app.ui.common.UiState<PlanetsData>> =
        combine(data.state, _extras) { state, extras ->
            if (state is com.poldivers.app.ui.common.UiState.Success) {
                com.poldivers.app.ui.common.UiState.Success(
                    state.data.copy(
                        majorOrderPlanets = extras.majorOrderPlanets,
                        dssPlanet = extras.dssPlanet,
                        effects = extras.effects,
                    ),
                )
            } else {
                state
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, com.poldivers.app.ui.common.UiState.Loading)

    private suspend fun loadExtras() = coroutineScope {
        // Extras only decorate the list/map -- a failure there must not hide the planets.
        val effects = async { runCatching { repository.getPlanetEffects() }.getOrNull() }
        val assignments = async { runCatching { repository.getAssignments() }.getOrNull() }
        val dssRaw = async { runCatching { repository.getDssLocation() }.getOrNull() }
        val stations = async { runCatching { repository.getSpaceStations() }.getOrDefault(emptyList()) }
        _extras.value = _extras.value.copy(
            effects = effects.await() ?: _extras.value.effects,
            majorOrderPlanets = assignments.await()?.flatMap { it.targetPlanetIndexes() }?.toSet() ?: _extras.value.majorOrderPlanets,
            dssPlanet = dssRaw.await()?.first ?: stations.await().firstOrNull()?.planet?.index ?: _extras.value.dssPlanet,
        )
    }

    private val _view = MutableStateFlow(PlanetsView.LIST)
    val view: StateFlow<PlanetsView> = _view.asStateFlow()

    private val _activeOnly = MutableStateFlow(false)
    val activeOnly: StateFlow<Boolean> = _activeOnly.asStateFlow()

    private val _selectedPlanet = MutableStateFlow<Planet?>(null)
    val selectedPlanet: StateFlow<Planet?> = _selectedPlanet.asStateFlow()

    fun setView(view: PlanetsView) {
        _view.value = view
    }

    fun setActiveOnly(activeOnly: Boolean) {
        _activeOnly.value = activeOnly
    }

    fun selectPlanet(planet: Planet?) {
        _selectedPlanet.value = planet
    }
}
