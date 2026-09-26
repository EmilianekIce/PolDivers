package com.poldivers.app.feature.dss

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.poldivers.app.data.hd2.Hd2Repository
import com.poldivers.app.data.hd2.model.SpaceStation
import com.poldivers.app.ui.common.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DssViewModel(private val repository: Hd2Repository) : ViewModel() {

    private val _state = MutableStateFlow<UiState<List<SpaceStation>>>(UiState.Loading)
    val state: StateFlow<UiState<List<SpaceStation>>> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = UiState.Loading
            _state.value = runCatching { repository.getSpaceStations() }
                .fold(
                    onSuccess = { UiState.Success(it) },
                    onFailure = { UiState.Error(it.message ?: "unknown error") },
                )
        }
    }
}
