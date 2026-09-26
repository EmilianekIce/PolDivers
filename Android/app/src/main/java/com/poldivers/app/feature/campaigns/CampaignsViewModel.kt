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
import kotlinx.coroutines.launch

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

    /**
     * Dispatches about Major Orders (new order, order won / failed...), newest first. Loaded
     * apart from the orders so they never slow them down.
     */
    private val _orderDispatches = MutableStateFlow<List<com.poldivers.app.data.hd2.model.Dispatch>>(emptyList())
    val orderDispatches: StateFlow<List<com.poldivers.app.data.hd2.model.Dispatch>> = _orderDispatches.asStateFlow()

    init {
        viewModelScope.launch {
            runCatching { withContext(com.poldivers.app.data.hd2.StaleAllowed) { repository.getDispatches() } }
                .onSuccess { _orderDispatches.value = it.filter(::isOrderDispatch).take(6) }
            runCatching { repository.getDispatches() }
                .onSuccess { _orderDispatches.value = it.filter(::isOrderDispatch).take(6) }
        }
    }

    private fun isOrderDispatch(d: com.poldivers.app.data.hd2.model.Dispatch): Boolean {
        val m = d.message.lowercase()
        return ORDER_KEYS.any { it in m }
    }

    private companion object {
        val ORDER_KEYS = listOf("major order", "rozkaz główny", "rozkazu głównego", "rozkazie głównym", "rozkaz", "orders", "objective")
    }

    private val _selectedPlanet = MutableStateFlow<Planet?>(null)
    val selectedPlanet: StateFlow<Planet?> = _selectedPlanet.asStateFlow()

    fun selectPlanet(planet: Planet?) {
        _selectedPlanet.value = planet
    }
}
