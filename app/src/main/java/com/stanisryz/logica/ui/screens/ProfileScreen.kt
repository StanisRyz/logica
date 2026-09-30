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
import com.stanisryz.logica.economy.AchievementRewardsViewModel
import com.stanisryz.logica.economy.AchievementRewardsViewModelFactory
import com.stanisryz.logica.economy.DailyRewardsRepository
import com.stanisryz.logica.statistics.StatisticsRepository
import com.stanisryz.logica.statistics.StatisticsViewModel
import com.stanisryz.logica.statistics.StatisticsViewModelFactory
import com.stanisryz.logica.statistics.toProfileUiState
import com.stanisryz.logica.ui.components.LoadingState
import com.stanisryz.logica.ui.profile.AchievementRewards
import com.stanisryz.logica.ui.profile.AchievementsScreenContent
import com.stanisryz.logica.ui.profile.ProfileContent
import com.stanisryz.logica.ui.profile.ProfileUiState

/** Android host for shared Profile presentation; Room, lifecycle, and retry stay platform-owned. */
@Composable
internal fun ProfileRoute(
    repository: StatisticsRepository,
    rewardsRepository: DailyRewardsRepository,
    onOpenGames: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenAchievements: () -> Unit = {},
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
        onOpenGames = onOpenGames,
        onOpenAchievements = onOpenAchievements,
        achievementRewards = rememberAchievementRewards(rewardsRepository),
    )
}

/** The paid achievement ids and the way to pay one, or null until the ledger has been read. */
@Composable
private fun rememberAchievementRewards(rewardsRepository: DailyRewardsRepository): AchievementRewards? {
    val viewModel: AchievementRewardsViewModel =
        viewModel(factory = remember(rewardsRepository) { AchievementRewardsViewModelFactory(rewardsRepository) })
    val claimed by viewModel.claimed.collectAsStateWithLifecycle()
    return claimed?.let { ids -> remember(ids) { AchievementRewards(ids, viewModel::claim) } }
}

/** The achievements list opened from the Profile, over the same durable statistics. */
@Composable
internal fun AchievementsRoute(
    repository: StatisticsRepository,
    rewardsRepository: DailyRewardsRepository,
    modifier: Modifier = Modifier,
) {
    val rewards = rememberAchievementRewards(rewardsRepository)
    val factory = remember(repository) { StatisticsViewModelFactory(repository) }
    val statisticsViewModel: StatisticsViewModel = viewModel(factory = factory)
    val statisticsState by statisticsViewModel.uiState.collectAsStateWithLifecycle()
    when (val state = statisticsState.toProfileUiState()) {
        is ProfileUiState.Ready -> AchievementsScreenContent(state.statistics, modifier, rewards)
        else -> LoadingState(modifier)
    }
}
