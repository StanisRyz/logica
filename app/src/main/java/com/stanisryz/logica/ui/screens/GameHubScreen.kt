package com.stanisryz.logica.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stanisryz.logica.daily.DailyChallengeRepository
import com.stanisryz.logica.daily.DailyGameLaunch
import com.stanisryz.logica.daily.DailyResultRepository
import com.stanisryz.logica.daily.TodayUiState
import com.stanisryz.logica.daily.TodayViewModel
import com.stanisryz.logica.daily.TodayViewModelFactory
import com.stanisryz.logica.daily.toDailyHubUiState
import com.stanisryz.logica.economy.PlayerEconomy
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import com.stanisryz.logica.statistics.StatisticsRepository
import com.stanisryz.logica.ui.components.GameHubContent
import com.stanisryz.logica.ui.components.ZeroLivesCard
import com.stanisryz.logica.ui.daily.DailyHubSection
import com.stanisryz.logica.ui.theme.LogicaMotion

/**
 * The Game hub: the Daily challenge on top and the regular catalog below it, in one place.
 *
 * The two halves keep their own state holders — the Daily run stays in [TodayViewModel] and the
 * active Catalog attempts are observed by the shell — so combining the screens does not combine the
 * domains behind them.
 */
@Composable
internal fun GameHubRoute(
    dailyChallengeRepository: DailyChallengeRepository,
    statisticsRepository: StatisticsRepository,
    dailyResultRepository: DailyResultRepository,
    catalog: List<PuzzleType>,
    economy: PlayerEconomy,
    onGameSelected: (PuzzleType) -> Unit,
    onOpenDaily: (DailyGameLaunch) -> Unit,
    onRestoreLife: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val factory =
        remember(dailyChallengeRepository, statisticsRepository, dailyResultRepository) {
            TodayViewModelFactory(
                dailyChallengeRepository,
                statisticsRepository,
                dailyResultRepository,
            )
        }
    val todayViewModel: TodayViewModel = viewModel(factory = factory)
    val uiState by todayViewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(todayViewModel, onOpenDaily) {
        todayViewModel.launches.collect(onOpenDaily)
    }
    /*
     * The one owner of the Daily load. Attaching the observer replays the current resumed state, so
     * this covers the hub's first frame as well as every later return to it — from gameplay, which
     * re-enters this composition, and from the background — instead of the ViewModel, this effect,
     * and the observer each starting the same load and cancelling the previous one.
     */
    DisposableEffect(lifecycleOwner, todayViewModel) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) todayViewModel.refresh()
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    GameHubScreen(
        dailyState = uiState,
        catalog = catalog,
        economy = economy,
        onStartDaily = todayViewModel::start,
        onRetryDaily = todayViewModel::refresh,
        onRestoreLife = onRestoreLife,
        onGameSelected = onGameSelected,
        modifier = modifier,
    )
}

/**
 * One vertical list carries the whole hub: the Daily block scrolls with the catalog rather than
 * competing with it, so the Daily header stays at roughly a quarter of the screen while the taller
 * game cards below it dominate — at any font scale, on any phone, without a pixel calculation.
 */
@Composable
private fun GameHubScreen(
    dailyState: TodayUiState,
    catalog: List<PuzzleType>,
    economy: PlayerEconomy,
    onStartDaily: (PuzzleType) -> Unit,
    onRetryDaily: () -> Unit,
    onRestoreLife: () -> Unit,
    onGameSelected: (PuzzleType) -> Unit,
    modifier: Modifier = Modifier,
) {
    GameHubContent(
        puzzleTypes = catalog,
        catalogEnabled = economy.isGameplayAllowed,
        onGameSelected = onGameSelected,
        modifier = modifier,
        headerContent = {
            DailyHubSection(
                uiState = dailyState.toDailyHubUiState(),
                gameplayAllowed = economy.isGameplayAllowed,
                onStart = onStartDaily,
                onRetryLoad = onRetryDaily,
            )
        },
        // The zero-life gate itself is unchanged; the hub only shows it once, above both halves.
        statusContent = {
            AnimatedVisibility(
                visible = !economy.isGameplayAllowed,
                enter = fadeIn(tween(LogicaMotion.SHORT_MILLIS)) + expandVertically(tween(LogicaMotion.SCREEN_MILLIS)),
                exit = fadeOut(tween(LogicaMotion.SHORT_MILLIS)) + shrinkVertically(tween(LogicaMotion.SCREEN_MILLIS)),
            ) {
                ZeroLivesCard(economy, onRestoreLife)
            }
        },
    )
}
