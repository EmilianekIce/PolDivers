package com.poldivers.app.feature.campaigns

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.poldivers.app.data.hd2.Hd2Repository
import com.poldivers.app.data.hd2.model.Assignment
import com.poldivers.app.data.hd2.model.Campaign
import com.poldivers.app.ui.common.UiState
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class CampaignsData(
    val assignments: List<Assignment>,
    val campaigns: List<Campaign>,
)

class CampaignsViewModel(private val repository: Hd2Repository) : ViewModel() {

    private val _state = MutableStateFlow<UiState<CampaignsData>>(UiState.Loading)
    val state: StateFlow<UiState<CampaignsData>> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = UiState.Loading
            _state.value = runCatching {
                coroutineScope {
                    val assignments = async { repository.getAssignments() }
                    val campaigns = async { repository.getCampaigns() }
                    CampaignsData(assignments.await(), campaigns.await())
                }
            }.fold(
                onSuccess = { UiState.Success(it) },
                onFailure = { UiState.Error(it.message ?: "unknown error") },
            )
        }
    }
}
