package com.poldivers.app.feature.campaigns

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.poldivers.app.core.prefs.ApiLanguage
import com.poldivers.app.data.hd2.CampaignHistoryStore
import com.poldivers.app.data.hd2.CampaignPhase
import com.poldivers.app.data.hd2.Hd2Repository
import com.poldivers.app.data.hd2.campaignPhaseOf
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
    /** Campaign/phase for each assignment that belongs to a named Galactic War campaign. */
    val phases: Map<Long, CampaignPhase> = emptyMap(),
) {
    val majorOrderPlanets: Set<Int> = assignments.flatMap { it.targetPlanetIndexes() }.toSet()
}

class CampaignsViewModel(
    private val repository: Hd2Repository,
    language: Flow<ApiLanguage>,
    private val history: CampaignHistoryStore,
) : ViewModel() {

    val data = Loadable(viewModelScope, language) {
        coroutineScope {
            val war = async { runCatching { repository.getWar() }.getOrNull() }
            val assignments = async { repository.getAssignments() }
            val campaigns = async { repository.getCampaigns() }
            val planets = async { runCatching { repository.getPlanets() }.getOrDefault(emptyList()) }
            val effects = async { runCatching { repository.getPlanetEffects() }.getOrDefault(emptyMap()) }
            val english = async { runCatching { repository.getAssignmentsEnglish() }.getOrDefault(emptyList()) }
            val assignmentList = assignments.await()
            val englishById = english.await().associateBy { it.id }
            val phases = assignmentList.mapNotNull { a -> campaignPhaseOf(a, englishById[a.id])?.let { a.id to it } }.toMap()
            val planetMap = planets.await().associateBy { it.index }
            remember(assignmentList, phases, planetMap)
            CampaignsData(
                war = war.await(),
                assignments = assignmentList,
                campaigns = campaigns.await(),
                planets = planetMap,
                effects = effects.await(),
                phases = phases,
            )
        }
    }

    /** Records each seen campaign phase so the timeline survives the MO leaving the API. */
    private suspend fun remember(assignments: List<Assignment>, phases: Map<Long, CampaignPhase>, planets: Map<Int, Planet>) =
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            assignments.forEach { a ->
                val phase = phases[a.id] ?: return@forEach
                val views = a.taskViews(planets)
                history.update(
                    CampaignHistoryStore.PhaseRecord(
                        assignmentId = a.id,
                        campaign = phase.campaign,
                        campaignKey = phase.campaignKey,
                        phase = phase.phase,
                        phaseName = phase.phaseName,
                        briefing = a.briefing,
                        reward = a.rewards.ifEmpty { listOfNotNull(a.reward) }.joinToString { rewardLabel(it.type, it.amount) },
                        expiration = a.expiration,
                        firstSeenMs = now,
                        lastSeenMs = now,
                        tasksDone = views.count { it.isDone },
                        tasksTotal = views.size,
                        faction = campaignFaction(a, planets).orEmpty(),
                    ),
                )
            }
            history.save()
        }

    fun phasesOf(campaignKey: String) = history.phasesOf(campaignKey)

    private val _selectedPlanet = MutableStateFlow<Planet?>(null)
    val selectedPlanet: StateFlow<Planet?> = _selectedPlanet.asStateFlow()

    fun selectPlanet(planet: Planet?) {
        _selectedPlanet.value = planet
    }
}
