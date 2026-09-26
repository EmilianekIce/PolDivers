package com.poldivers.app.feature.campaigns

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.poldivers.app.core.prefs.ApiLanguage
import com.poldivers.app.data.hd2.Hd2Repository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.poldivers.app.data.hd2.PlanetEffect
import com.poldivers.app.data.hd2.model.Assignment
import com.poldivers.app.data.hd2.model.Campaign
import com.poldivers.app.data.hd2.model.Planet
import com.poldivers.app.data.hd2.model.War
import com.poldivers.app.data.hd2.targetPlanetIndexes
import com.poldivers.app.ui.common.Loadable
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class CampaignsData(
    val war: War?,
    val assignments: List<Assignment>,
    /** Most populated first. */
    val campaigns: List<Campaign>,
    /** Used to resolve planet indexes referenced by Major Order tasks. */
    val planets: Map<Int, Planet>,
    val effects: Map<Int, List<PlanetEffect>> = emptyMap(),
) {
    val majorOrderPlanets: Set<Int> = assignments.flatMap { it.targetPlanetIndexes() }.toSet()
}

class CampaignsViewModel(
    private val repository: Hd2Repository,
    language: Flow<ApiLanguage>,
) : ViewModel() {

    val data = Loadable(viewModelScope, language) {
        coroutineScope {
            val assignments = async { repository.getAssignments() }
            val planets = async { runCatching { repository.getPlanets() }.getOrDefault(emptyList()) }
            val effects = async { runCatching { repository.getPlanetEffects() }.getOrDefault(emptyMap()) }
            val assignmentList = assignments.await()
            val planetMap = planets.await().associateBy { it.index }
            CampaignsData(
                war = null,
                assignments = assignmentList,
                campaigns = emptyList(),
                planets = planetMap,
                effects = effects.await(),
            )
        }
    }

    private val _selectedPlanet = MutableStateFlow<Planet?>(null)
    val selectedPlanet: StateFlow<Planet?> = _selectedPlanet.asStateFlow()

    fun selectPlanet(planet: Planet?) {
        _selectedPlanet.value = planet
    }
}
