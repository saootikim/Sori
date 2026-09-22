/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.sori

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ChartLoad<out T> {
    data object Loading : ChartLoad<Nothing>

    data object Failed : ChartLoad<Nothing>

    data class Loaded<T>(
        val chart: SoriChart<T>,
    ) : ChartLoad<T>
}

@HiltViewModel
class SoriChartsViewModel
    @Inject
    constructor() : ViewModel() {
        private val _korea = MutableStateFlow<ChartLoad<SoriChartEntry>>(ChartLoad.Loading)
        val korea: StateFlow<ChartLoad<SoriChartEntry>> = _korea

        private val _global = MutableStateFlow<ChartLoad<SoriChartEntry>>(ChartLoad.Loading)
        val global: StateFlow<ChartLoad<SoriChartEntry>> = _global

        private val _artists = MutableStateFlow<ChartLoad<SoriChartArtist>>(ChartLoad.Loading)
        val artists: StateFlow<ChartLoad<SoriChartArtist>> = _artists

        init {
            load()
        }

        /** Loads (or retries) every chart that is not loaded yet. Results are cached by [SoriCharts]. */
        fun load() {
            fetch(_korea) { SoriCharts.tracks(SoriChartRegion.KOREA) }
            fetch(_global) { SoriCharts.tracks(SoriChartRegion.GLOBAL) }
            fetch(_artists) { SoriCharts.artists(SoriChartRegion.KOREA) }
        }

        private fun <T> fetch(
            state: MutableStateFlow<ChartLoad<T>>,
            load: suspend () -> SoriChart<T>?,
        ) {
            if (state.value is ChartLoad.Loaded) return
            state.value = ChartLoad.Loading
            viewModelScope.launch {
                state.value = load()?.let { ChartLoad.Loaded(it) } ?: ChartLoad.Failed
            }
        }
    }
