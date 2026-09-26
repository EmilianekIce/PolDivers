package com.poldivers.app.feature.news

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.poldivers.app.data.hd2.Hd2Repository
import com.poldivers.app.data.hd2.model.Dispatch
import com.poldivers.app.ui.common.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class NewsViewModel(private val repository: Hd2Repository) : ViewModel() {

    private val _state = MutableStateFlow<UiState<List<Dispatch>>>(UiState.Loading)
    val state: StateFlow<UiState<List<Dispatch>>> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = UiState.Loading
            _state.value = runCatching { repository.getDispatches() }
                .fold(
                    onSuccess = { UiState.Success(it) },
                    onFailure = { UiState.Error(it.message ?: "unknown error") },
                )
        }
    }
}
