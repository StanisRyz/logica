package com.stanisryz.logica.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import com.stanisryz.logica.daily.DailyChallengeRepository
import com.stanisryz.logica.daily.DailyGameLaunch
import com.stanisryz.logica.daily.DailyResultRepository
import com.stanisryz.logica.daily.TodayUiState
import com.stanisryz.logica.daily.TodayViewModel
import com.stanisryz.logica.daily.TodayViewModelFactory
import com.stanisryz.logica.economy.PlayerEconomy
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import com.stanisryz.logica.statistics.StatisticsRepository
import com.stanisryz.logica.ui.components.LogicaCard
import com.stanisryz.logica.ui.components.PuzzleArtwork
import com.stanisryz.logica.ui.components.PuzzleTitle
import com.stanisryz.logica.ui.components.SectionTitle
import com.stanisryz.logica.ui.components.ZeroLivesCard
import com.stanisryz.logica.ui.components.titleResource
import com.stanisryz.logica.ui.theme.LogicaMotion
import com.stanisryz.logica.ui.theme.LogicaSpacing

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
    catalog: List<GameCatalogEntry>,
    economy: PlayerEconomy,
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
    catalog: List<GameCatalogEntry>,
    economy: PlayerEconomy,
    onStartDaily: (PuzzleType) -> Unit,
    onRetryDaily: () -> Unit,
    onRestoreLife: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        state = rememberLazyListState(),
        contentPadding =
            PaddingValues(
                horizontal = LogicaSpacing.screenHorizontal,
                vertical = LogicaSpacing.screenVertical,
            ),
        verticalArrangement = Arrangement.spacedBy(LogicaSpacing.item),
    ) {
        item(key = "daily") {
            DailySection(
                uiState = dailyState,
                gameplayAllowed = economy.isGameplayAllowed,
                onStart = onStartDaily,
                onRetryLoad = onRetryDaily,
            )
        }
        // The zero-life gate itself is unchanged; the hub only shows it once, above both halves.
        item(key = "zero-lives") {
            AnimatedVisibility(
                visible = !economy.isGameplayAllowed,
                enter = fadeIn(tween(LogicaMotion.SHORT_MILLIS)) + expandVertically(tween(LogicaMotion.SCREEN_MILLIS)),
                exit = fadeOut(tween(LogicaMotion.SHORT_MILLIS)) + shrinkVertically(tween(LogicaMotion.SCREEN_MILLIS)),
            ) {
                ZeroLivesCard(economy, onRestoreLife)
            }
        }
        item(key = "games-title") {
            SectionTitle(
                text = stringResource(R.string.puzzles),
                modifier = Modifier.padding(top = LogicaSpacing.text),
            )
        }
        items(catalog, key = { it.puzzleType }) { entry ->
            GameCatalogCard(entry, economy.isGameplayAllowed)
        }
    }
}

/**
 * A regular game as a full-width card: bigger than a Daily card and built for vertical browsing. It
 * leads to the game's difficulty screen, which is where the current level of each difficulty lives.
 */
@Composable
private fun GameCatalogCard(
    entry: GameCatalogEntry,
    gameplayAllowed: Boolean,
) {
    LogicaCard(
        modifier = Modifier.animateContentSize(tween(LogicaMotion.SCREEN_MILLIS)),
        onClick = entry.onPlay.takeIf { gameplayAllowed },
        onClickLabel = stringResource(entry.puzzleType.titleResource()),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(LogicaSpacing.action),
        ) {
            PuzzleArtwork(entry.puzzleType)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center,
            ) {
                PuzzleTitle(stringResource(entry.puzzleType.titleResource()), puzzleType = entry.puzzleType)
            }
        }
    }
}
