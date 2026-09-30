package com.seooki.ddokddok.ui.apps

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seooki.ddokddok.data.AppCatalog
import com.seooki.ddokddok.data.AppEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AppsViewModel(catalog: AppCatalog) : ViewModel() {
    private val allApps = MutableStateFlow<List<AppEntry>?>(null)
    private val _query = MutableStateFlow("")

    val query: StateFlow<String> = _query.asStateFlow()

    /** 검색어에 맞는 앱. 아직 불러오는 중이면 null. */
    val visibleApps: StateFlow<List<AppEntry>?> = combine(allApps, _query) { apps, query ->
        val needle = query.trim()
        if (apps == null || needle.isEmpty()) apps else apps.filter { it.label.contains(needle, ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    init {
        viewModelScope.launch { allApps.value = catalog.launcherApps() }
    }

    fun setQuery(value: String) {
        _query.value = value
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
