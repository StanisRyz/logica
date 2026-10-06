package com.stanisryz.logica.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stanisryz.logica.R
import com.stanisryz.logica.daily.ArchiveDayUiState
import com.stanisryz.logica.daily.DailyArchiveUiState
import com.stanisryz.logica.daily.DailyArchiveViewModel
import com.stanisryz.logica.daily.DailyArchiveViewModelFactory
import com.stanisryz.logica.daily.DailyChallengeRepository
import com.stanisryz.logica.daily.DailyEntryState
import com.stanisryz.logica.daily.DailyGameLaunch
import com.stanisryz.logica.daily.DailyResultRepository
import com.stanisryz.logica.daily.formatDailyDateLabel
import com.stanisryz.logica.economy.EconomyRepository
import com.stanisryz.logica.economy.PlayerEconomy
import com.stanisryz.logica.ui.components.LoadingState
import com.stanisryz.logica.ui.components.RetryableErrorState
import com.stanisryz.logica.ui.daily.DailyArchiveDay
import com.stanisryz.logica.ui.daily.DailyArchiveDayContent
import com.stanisryz.logica.ui.daily.DailyArchiveList
import com.stanisryz.logica.ui.daily.DailyHubEntry
import com.stanisryz.logica.ui.daily.DailyHubEntryState
import com.stanisryz.logica.ui.daily.DailyRewardedAdState

/** The Daily archive's days, newest first; a tap opens the day's own screen. */
@Composable
internal fun DailyArchiveRoute(
    dailyChallengeRepository: DailyChallengeRepository,
    dailyResultRepository: DailyResultRepository,
    economyRepository: EconomyRepository,
    onOpenDay: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = rememberArchiveViewModel(dailyChallengeRepository, dailyResultRepository, economyRepository, onlyDay = null)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    when (val current = state) {
        DailyArchiveUiState.Loading -> LoadingState(modifier)
        DailyArchiveUiState.Error ->
            RetryableErrorState(
                stringResource(R.string.daily_load_error),
                stringResource(R.string.retry),
                viewModel::refresh,
                modifier,
            )
        is DailyArchiveUiState.Content ->
            DailyArchiveList(
                days =
                    current.days.map { day ->
                        DailyArchiveDay(
                            epochDay = day.epochDay,
                            dateLabel = formatDailyDateLabel(day.date),
                            completedCount = day.completedCount,
                            totalCount = day.entries.size,
                            unlocked = day.unlocked || day.started,
                        )
                    },
                onOpenDay = onOpenDay,
                modifier = modifier,
            )
    }
}

/**
 * One archive day: its entries, playable once the day is open. Opening it costs gems here or a
 * rewarded ad through the shell ([onWatchAd]); a day the player started on its own date is free.
 */
@Composable
internal fun DailyArchiveDayRoute(
    epochDay: Long,
    dailyChallengeRepository: DailyChallengeRepository,
    dailyResultRepository: DailyResultRepository,
    economyRepository: EconomyRepository,
    economy: PlayerEconomy,
    adState: DailyRewardedAdState,
    onOpenDaily: (DailyGameLaunch) -> Unit,
    onWatchAd: (Long) -> Unit,
    onRewardedOfferVisible: (Boolean) -> Unit,
    onRestoreLife: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = rememberArchiveViewModel(dailyChallengeRepository, dailyResultRepository, economyRepository, onlyDay = epochDay)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel, onOpenDaily) { viewModel.launches.collect(onOpenDaily) }
    val day = (state as? DailyArchiveUiState.Content)?.days?.firstOrNull()
    val locked = day != null && day.unlockPrice > 0
    LaunchedEffect(locked) { onRewardedOfferVisible(locked) }
    DisposableEffect(Unit) { onDispose { onRewardedOfferVisible(false) } }
    when {
        state == DailyArchiveUiState.Loading -> LoadingState(modifier)
        day == null ->
            RetryableErrorState(
                stringResource(R.string.daily_load_error),
                stringResource(R.string.retry),
                viewModel::refresh,
                modifier,
            )
        else ->
            DailyArchiveDayContent(
                dateLabel = formatDailyDateLabel(day.date),
                entries = day.hubEntries(),
                unlocked = day.unlocked,
                unlockPrice = day.unlockPrice,
                gems = economy.gems,
                adState = adState,
                gameplayAllowed = true,
                onUnlockWithGems = { viewModel.unlockWithGems(epochDay) },
                onWatchAd = { onWatchAd(epochDay) },
                // A lost Daily attempt costs a life, so starting one needs a life, like today's Daily.
                onStart = { puzzleType -> if (economy.isGameplayAllowed) viewModel.start(epochDay, puzzleType) else onRestoreLife() },
                modifier = modifier,
            )
    }
}

@Composable
private fun rememberArchiveViewModel(
    dailyChallengeRepository: DailyChallengeRepository,
    dailyResultRepository: DailyResultRepository,
    economyRepository: EconomyRepository,
    onlyDay: Long?,
): DailyArchiveViewModel {
    val factory =
        remember(dailyChallengeRepository, dailyResultRepository, economyRepository, onlyDay) {
            DailyArchiveViewModelFactory(dailyChallengeRepository, dailyResultRepository, economyRepository, onlyDay)
        }
    val viewModel: DailyArchiveViewModel = viewModel(key = "daily-archive-$onlyDay", factory = factory)
    val lifecycleOwner = LocalLifecycleOwner.current
    // Every return to the screen, from gameplay or the background, reads the durable state again.
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh()
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return viewModel
}

private fun ArchiveDayUiState.hubEntries(): List<DailyHubEntry> =
    entries.map { entry ->
        DailyHubEntry(
            puzzleType = entry.puzzleType,
            difficulty = entry.difficulty,
            state =
                when (entry.state) {
                    DailyEntryState.AVAILABLE -> DailyHubEntryState.AVAILABLE
                    DailyEntryState.RETRY -> DailyHubEntryState.RETRY
                    DailyEntryState.COMPLETED -> DailyHubEntryState.COMPLETED
                },
        )
    }
