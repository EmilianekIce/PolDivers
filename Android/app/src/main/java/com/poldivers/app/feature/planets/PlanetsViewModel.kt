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
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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

    val data = Loadable(viewModelScope, language) {
        coroutineScope {
            val planets = async { repository.getPlanetsSortedByPlayers() }
            val campaigns = async { repository.getCampaigns() }
            // Extras only decorate the list/map -- a failure there must not hide the planets.
            val assignments = async { runCatching { repository.getAssignments() }.getOrDefault(emptyList()) }
            val stations = async { runCatching { repository.getSpaceStations() }.getOrDefault(emptyList()) }
            val effects = async { runCatching { repository.getPlanetEffects() }.getOrDefault(emptyMap()) }
            val dssRaw = async { runCatching { repository.getDssLocation() }.getOrNull() }
            PlanetsData(
                planets = planets.await(),
                campaignPlanets = campaigns.await().map { it.planet.index }.toSet(),
                majorOrderPlanets = assignments.await().flatMap { it.targetPlanetIndexes() }.toSet(),
                dssPlanet = stations.await().firstOrNull()?.planet?.index ?: dssRaw.await()?.first,
                effects = effects.await(),
            )
        }
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
