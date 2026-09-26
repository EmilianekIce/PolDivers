package com.poldivers.app.feature.news

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.poldivers.app.core.prefs.ApiLanguage
import com.poldivers.app.data.hd2.Hd2Repository
import com.poldivers.app.ui.common.Loadable
import kotlinx.coroutines.flow.Flow

class NewsViewModel(
    private val repository: Hd2Repository,
    language: Flow<ApiLanguage>,
) : ViewModel() {

    val data = Loadable(viewModelScope, language) { repository.getDispatches() }
}
