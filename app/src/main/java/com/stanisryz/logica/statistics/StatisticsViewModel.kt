package com.stanisryz.logica.statistics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

internal sealed interface StatisticsUiState {
    data object Loading : StatisticsUiState

    data class Ready(
        val statistics: GameStatistics,
    ) : StatisticsUiState

    data object Error : StatisticsUiState
}

internal class StatisticsViewModel(
    private val repository: StatisticsRepository,
    private val dateProvider: () -> LocalDate = LocalDate::now,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow<StatisticsUiState>(StatisticsUiState.Loading)
    val uiState: StateFlow<StatisticsUiState> = mutableUiState.asStateFlow()

    private var collectionJob: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        collectionJob?.cancel()
        collectionJob =
            viewModelScope.launch {
                mutableUiState.value = StatisticsUiState.Loading
                try {
                    repository.observe(dateProvider()).collect { snapshot ->
                        mutableUiState.value = StatisticsUiState.Ready(snapshot.statistics)
                    }
                } catch (exception: CancellationException) {
                    throw exception
                } catch (_: Exception) {
                    mutableUiState.value = StatisticsUiState.Error
                }
            }
    }
}

internal class StatisticsViewModelFactory(
    private val repository: StatisticsRepository,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(StatisticsViewModel::class.java)) {
            "Unknown ViewModel class: ${modelClass.name}"
        }
        @Suppress("UNCHECKED_CAST")
        return StatisticsViewModel(repository) as T
    }
}
