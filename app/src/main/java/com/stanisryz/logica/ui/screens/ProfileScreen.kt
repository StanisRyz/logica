package com.stanisryz.logica.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stanisryz.logica.statistics.StatisticsRepository
import com.stanisryz.logica.statistics.StatisticsViewModel
import com.stanisryz.logica.statistics.StatisticsViewModelFactory
import com.stanisryz.logica.statistics.toProfileUiState
import com.stanisryz.logica.ui.profile.ProfileContent

/** Android host for shared Profile presentation; Room, lifecycle, and retry stay platform-owned. */
@Composable
internal fun ProfileRoute(
    repository: StatisticsRepository,
    modifier: Modifier = Modifier,
) {
    val factory = remember(repository) { StatisticsViewModelFactory(repository) }
    val statisticsViewModel: StatisticsViewModel = viewModel(factory = factory)
    val statisticsState by statisticsViewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, statisticsViewModel) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) statisticsViewModel.refresh()
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    ProfileContent(
        uiState = statisticsState.toProfileUiState(),
        onRetry = statisticsViewModel::refresh,
        modifier = modifier,
    )
}
