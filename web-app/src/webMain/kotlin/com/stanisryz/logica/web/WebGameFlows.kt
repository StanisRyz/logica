package com.stanisryz.logica.web

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.stanisryz.logica.puzzle.core.balance.hasMeaningfulProgress
import com.stanisryz.logica.puzzle.core.blocksudoku.BlockSudokuStatus
import com.stanisryz.logica.puzzle.core.crowns.hasMeaningfulProgress
import com.stanisryz.logica.puzzle.core.game2048.hasMeaningfulProgress
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import com.stanisryz.logica.puzzle.core.nonogram.NonogramGameStatus
import com.stanisryz.logica.puzzle.core.sudoku.hasMeaningfulProgress
import com.stanisryz.logica.puzzle.core.word.hasMeaningfulProgress
import com.stanisryz.logica.shared.ui.generated.resources.Res
import com.stanisryz.logica.shared.ui.generated.resources.block_sudoku_score
import com.stanisryz.logica.ui.blocksudoku.BlockSudokuContent
import com.stanisryz.logica.ui.components.GameKey
import com.stanisryz.logica.ui.nonogram.NonogramGallerySheet
import com.stanisryz.logica.ui.nonogram.NonogramGameContent
import com.stanisryz.logica.ui.nonogram.nonogramResultArtwork
import kotlinx.coroutines.flow.Flow
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun NonogramFlow(
    state: WebNonogramState,
    controller: WebNonogramController,
    hintCount: Int?,
    onOpenStore: () -> Unit,
    onExitNonogram: () -> Unit,
    onSolvedNextLevel: (() -> Unit) -> Unit,
) {
    when (state) {
        WebNonogramState.DifficultySelection ->
            DifficultyContent(
                puzzleType = PuzzleType.NONOGRAM,
                onBack = onExitNonogram,
                onStart = controller::selectDifficulty,
                gallery = { onDismiss ->
                    val stars = LocalWebCatalogStars.current
                    val lives = LocalWebLives.current
                    val progress = LocalWebRating.current.progress
                    NonogramGallerySheet(
                        clearedLevels = progress.clearedLevels(PuzzleType.NONOGRAM),
                        // Levels below the V1 bucket's level are Level Pack V1 levels, the rest V2.
                        loadPicture = { difficulty, level ->
                            controller.galleryPicture(
                                difficulty,
                                level,
                                progress.bucketForLevel(PuzzleType.NONOGRAM, difficulty, level).packVersion,
                            )
                        },
                        starsOf = { difficulty, level ->
                            stars.starsOf(progress.bucketForLevel(PuzzleType.NONOGRAM, difficulty, level), level)
                        },
                        onDismiss = onDismiss,
                        onReplay = { difficulty, level ->
                            onDismiss()
                            lives.guard { controller.replayLevel(difficulty, level) }
                        },
                        dailyPictures = LocalWebDailyPictures.current,
                    )
                },
            )
        is WebNonogramState.Loading ->
            WebCatalogLoadingContent(
                difficulty = state.difficulty,
                levelNumber = state.levelNumber?.value,
                onBack = if (state.launch.isDaily) onExitNonogram else controller::showDifficultySelector,
                isDaily = state.launch.isDaily,
            )
        is WebNonogramState.Error ->
            WebCatalogLevelErrorContent(
                levelNumber = state.levelNumber?.value,
                failure = state.failure,
                onRetry = controller::retryLoading,
                onBack = if (state.launch.isDaily) onExitNonogram else controller::showDifficultySelector,
                isDaily = state.launch.isDaily,
            )
        is WebNonogramState.Playing -> {
            val livesGuard = LocalWebLives.current.guard
            val transitionAd = LocalWebTransitionAd.current
            Column(Modifier.fillMaxSize()) {
                WebGameplayHeader(
                    puzzleType = PuzzleType.NONOGRAM,
                    isDaily = state.source.isDaily,
                    hasMeaningfulProgress = state.hasMeaningfulProgress || controller.secondChanceOffered,
                    onExit = if (state.source.isDaily) onExitNonogram else controller::showDifficultySelector,
                )
                NonogramGameContent(
                    puzzle = state.puzzle,
                    game = state.game,
                    difficulty = state.source.difficulty,
                    levelNumber = state.source.catalogLevelNumberOrNull,
                    contextBadgeLabel = state.source.contextBadgeLabelOrNull(),
                    selectedTool = state.selectedTool,
                    gameplayEnabled = state.game.status == NonogramGameStatus.IN_PROGRESS,
                    onCell = controller::onCell,
                    onSelectTool = controller::selectTool,
                    onHint = controller::requestHint,
                    modifier = Modifier.weight(1f),
                    hintCount = hintCount,
                )
            }
            // The third mistake waits here for the one ad-paid second chance.
            if (controller.secondChanceOffered) {
                WebSecondChanceDialog(onContinue = controller::continueAfterAd, onDecline = controller::declineSecondChance)
            }
            if (controller.hintsExhaustedNotice) {
                WebHintsExhaustedDialog(
                    onOpenStore = {
                        controller.dismissHintsExhaustedNotice()
                        onOpenStore()
                    },
                    onDismiss = controller::dismissHintsExhaustedNotice,
                )
            }
            if (state.source.isDaily) {
                WebDailyOrdinaryTerminalDialog(
                    puzzleType = PuzzleType.NONOGRAM,
                    visible = state.game.status.isTerminal && !controller.secondChanceOffered,
                    difficulty = state.source.difficulty,
                    mistakesUsed = state.game.mistakesUsed,
                    hintsUsed = state.game.hintsUsed,
                    solved = state.game.status == NonogramGameStatus.SOLVED,
                    completion = controller.dailyCompletionState,
                    onRetry = { livesGuard { transitionAd(controller::retry) } },
                    onRetrySave = controller::retryDailySave,
                    onExit = { transitionAd(onExitNonogram) },
                    artwork = nonogramResultArtwork(state.puzzle),
                )
            } else {
                WebCatalogSaveErrorBanner(
                    completion = controller.completionState,
                    onRetrySave = controller::retrySave,
                )
                WebOrdinaryCatalogTerminalDialog(
                    visible = state.game.status.isTerminal && !controller.secondChanceOffered,
                    difficulty = state.source.difficulty,
                    mistakesUsed = state.game.mistakesUsed,
                    hintsUsed = state.game.hintsUsed,
                    levelNumber = requireNotNull(state.source.catalogLevelNumberOrNull),
                    solved = state.game.status == NonogramGameStatus.SOLVED,
                    completion = controller.completionState,
                    onNextLevel = { livesGuard { onSolvedNextLevel { controller.nextLevel() } } },
                    onRetry = { livesGuard { transitionAd(controller::retry) } },
                    onRetrySave = controller::retrySave,
                    onBack = { transitionAd(controller::showDifficultySelector) },
                    artwork = nonogramResultArtwork(state.puzzle),
                )
            }
        }
    }
}

@Composable
internal fun BlockSudokuFlow(
    state: WebBlockSudokuState,
    controller: WebBlockSudokuController,
    onSolvedNextLevel: (() -> Unit) -> Unit,
    onExit: () -> Unit,
) {
    when (state) {
        WebBlockSudokuState.DifficultySelection ->
            DifficultyContent(
                puzzleType = PuzzleType.BLOCK_SUDOKU,
                onBack = onExit,
                onStart = controller::selectDifficulty,
            )
        is WebBlockSudokuState.Loading ->
            WebCatalogLoadingContent(
                difficulty = state.difficulty,
                levelNumber = state.levelNumber?.value,
                onBack = if (state.launch.isDaily) onExit else controller::showDifficultySelector,
                isDaily = state.launch.isDaily,
            )
        is WebBlockSudokuState.Error ->
            WebCatalogLevelErrorContent(
                levelNumber = state.levelNumber?.value,
                failure = state.failure,
                onRetry = controller::retryLoading,
                onBack = if (state.launch.isDaily) onExit else controller::showDifficultySelector,
                isDaily = state.launch.isDaily,
            )
        is WebBlockSudokuState.Playing -> {
            val livesGuard = LocalWebLives.current.guard
            val transitionAd = LocalWebTransitionAd.current
            Column(Modifier.fillMaxSize()) {
                WebGameplayHeader(
                    puzzleType = PuzzleType.BLOCK_SUDOKU,
                    isDaily = state.source.isDaily,
                    hasMeaningfulProgress = state.hasMeaningfulProgress,
                    onExit = if (state.source.isDaily) onExit else controller::showDifficultySelector,
                )
                BlockSudokuContent(
                    state = state.game,
                    difficulty = state.source.difficulty,
                    levelNumber = state.source.catalogLevelNumberOrNull,
                    contextBadgeLabel = state.source.contextBadgeLabelOrNull(),
                    gameplayEnabled = state.game.status == BlockSudokuStatus.IN_PROGRESS,
                    onPlace = controller::place,
                    modifier = Modifier.weight(1f),
                )
            }
            val scoreDetail = "${stringResource(Res.string.block_sudoku_score)}: ${state.game.score}"
            if (state.source.isDaily) {
                WebDailyOrdinaryTerminalDialog(
                    puzzleType = PuzzleType.BLOCK_SUDOKU,
                    visible = state.game.status.isTerminal,
                    difficulty = state.source.difficulty,
                    solved = state.game.status == BlockSudokuStatus.SOLVED,
                    completion = controller.dailyCompletionState,
                    scoreDetail = scoreDetail,
                    onRetry = { livesGuard { transitionAd(controller::retry) } },
                    onRetrySave = controller::retryDailySave,
                    onExit = { transitionAd(onExit) },
                )
                return
            }
            WebCatalogSaveErrorBanner(
                completion = controller.completionState,
                onRetrySave = controller::retrySave,
            )
            WebOrdinaryCatalogTerminalDialog(
                visible = state.game.status.isTerminal,
                difficulty = state.source.difficulty,
                levelNumber = requireNotNull(state.source.catalogLevelNumberOrNull),
                solved = state.game.status == BlockSudokuStatus.SOLVED,
                solvedDetail = scoreDetail,
                failedDetail = scoreDetail,
                completion = controller.completionState,
                onNextLevel = { livesGuard { onSolvedNextLevel { controller.nextLevel() } } },
                onRetry = { livesGuard { transitionAd(controller::retry) } },
                onRetrySave = controller::retrySave,
                onBack = { transitionAd(controller::showDifficultySelector) },
            )
        }
    }
}

@Composable
internal fun BalanceFlow(
    state: WebBalanceState,
    controller: WebBalanceController,
    hintCount: Int?,
    onOpenStore: () -> Unit,
    onExitBalance: () -> Unit,
    onSolvedNextLevel: (() -> Unit) -> Unit,
) {
    when (state) {
        WebBalanceState.DifficultySelection ->
            DifficultyContent(
                puzzleType = PuzzleType.BALANCE,
                onBack = onExitBalance,
                onStart = controller::selectDifficulty,
                onReplay = controller::replayLevel,
            )
        is WebBalanceState.Loading ->
            WebCatalogLoadingContent(
                difficulty = state.difficulty,
                levelNumber = state.levelNumber?.value,
                onBack =
                    if (state.launch.isDaily) {
                        onExitBalance
                    } else {
                        controller::showDifficultySelector
                    },
                isDaily = state.launch.isDaily,
            )
        is WebBalanceState.Error ->
            WebCatalogLevelErrorContent(
                levelNumber = state.levelNumber?.value,
                failure = state.failure,
                onRetry = controller::retryLoading,
                onBack =
                    if (state.launch.isDaily) {
                        onExitBalance
                    } else {
                        controller::showDifficultySelector
                    },
                isDaily = state.launch.isDaily,
            )
        is WebBalanceState.Playing ->
            PlayingBalanceContent(
                state = state,
                controller = controller,
                hintCount = hintCount,
                onOpenStore = onOpenStore,
                onExitBalance = onExitBalance,
                onSolvedNextLevel = onSolvedNextLevel,
            )
    }
}

@Composable
internal fun CrownsFlow(
    state: WebCrownsState,
    controller: WebCrownsController,
    hintCount: Int?,
    onOpenStore: () -> Unit,
    onExitCrowns: () -> Unit,
    onSolvedNextLevel: (() -> Unit) -> Unit,
) {
    when (state) {
        WebCrownsState.DifficultySelection ->
            DifficultyContent(
                puzzleType = PuzzleType.CROWNS,
                onBack = onExitCrowns,
                onStart = controller::selectDifficulty,
                onReplay = controller::replayLevel,
            )
        is WebCrownsState.Loading ->
            WebCatalogLoadingContent(
                difficulty = state.difficulty,
                levelNumber = state.levelNumber?.value,
                onBack =
                    if (state.launch.isDaily) {
                        onExitCrowns
                    } else {
                        controller::showDifficultySelector
                    },
                isDaily = state.launch.isDaily,
            )
        is WebCrownsState.Error ->
            WebCatalogLevelErrorContent(
                levelNumber = state.levelNumber?.value,
                failure = state.failure,
                onRetry = controller::retryLoading,
                onBack =
                    if (state.launch.isDaily) {
                        onExitCrowns
                    } else {
                        controller::showDifficultySelector
                    },
                isDaily = state.launch.isDaily,
            )
        is WebCrownsState.Playing ->
            PlayingCrownsContent(
                state = state,
                controller = controller,
                hintCount = hintCount,
                onOpenStore = onOpenStore,
                onExitCrowns = onExitCrowns,
                onSolvedNextLevel = onSolvedNextLevel,
            )
    }
}

@Composable
internal fun WordFlow(
    state: WebWordState,
    controller: WebWordController,
    hardwareKeys: Flow<GameKey>,
    onExitWord: () -> Unit,
    onSolvedNextLevel: (() -> Unit) -> Unit,
) {
    when (state) {
        WebWordState.DifficultySelection ->
            DifficultyContent(
                puzzleType = PuzzleType.WORD,
                onBack = onExitWord,
                onStart = controller::selectDifficulty,
            )
        is WebWordState.Loading ->
            WebCatalogLoadingContent(
                difficulty = state.difficulty,
                levelNumber = state.levelNumber?.value,
                onBack =
                    if (state.launch.isDaily) {
                        onExitWord
                    } else {
                        controller::showDifficultySelector
                    },
                isDaily = state.launch.isDaily,
            )
        is WebWordState.Error ->
            WebCatalogLevelErrorContent(
                levelNumber = state.levelNumber?.value,
                failure = state.failure,
                onRetry = controller::retryLoading,
                onBack =
                    if (state.launch.isDaily) {
                        onExitWord
                    } else {
                        controller::showDifficultySelector
                    },
                isDaily = state.launch.isDaily,
            )
        is WebWordState.Playing ->
            PlayingWordContent(
                state = state,
                controller = controller,
                hardwareKeys = hardwareKeys,
                onExitWord = onExitWord,
                onSolvedNextLevel = onSolvedNextLevel,
            )
    }
}

@Composable
internal fun SudokuFlow(
    state: WebSudokuState,
    controller: WebSudokuController,
    hintCount: Int?,
    onOpenStore: () -> Unit,
    onExitSudoku: () -> Unit,
    onSolvedNextLevel: (() -> Unit) -> Unit,
) {
    when (state) {
        WebSudokuState.DifficultySelection ->
            DifficultyContent(
                puzzleType = PuzzleType.SUDOKU,
                onBack = onExitSudoku,
                onStart = controller::selectDifficulty,
                onReplay = controller::replayLevel,
            )
        is WebSudokuState.Loading ->
            WebCatalogLoadingContent(
                difficulty = state.difficulty,
                levelNumber = state.levelNumber?.value,
                onBack =
                    if (state.launch.isDaily) {
                        onExitSudoku
                    } else {
                        controller::showDifficultySelector
                    },
                isDaily = state.launch.isDaily,
            )
        is WebSudokuState.Error ->
            WebCatalogLevelErrorContent(
                levelNumber = state.levelNumber?.value,
                failure = state.failure,
                onRetry = controller::retryLoading,
                onBack =
                    if (state.launch.isDaily) {
                        onExitSudoku
                    } else {
                        controller::showDifficultySelector
                    },
                isDaily = state.launch.isDaily,
            )
        is WebSudokuState.Playing ->
            PlayingSudokuContent(
                state = state,
                controller = controller,
                hintCount = hintCount,
                onOpenStore = onOpenStore,
                onExitSudoku = onExitSudoku,
                onSolvedNextLevel = onSolvedNextLevel,
            )
    }
}

@Composable
internal fun Game2048Flow(
    state: Web2048State,
    controller: Web2048Controller,
    onExitGame2048: () -> Unit,
    onSolvedNextLevel: (() -> Unit) -> Unit,
) {
    when (state) {
        Web2048State.DifficultySelection ->
            DifficultyContent(
                puzzleType = PuzzleType.GAME_2048,
                onBack = onExitGame2048,
                onStart = controller::selectDifficulty,
            )
        is Web2048State.Loading ->
            WebCatalogLoadingContent(
                difficulty = state.difficulty,
                levelNumber = state.levelNumber?.value,
                onBack =
                    if (state.launch.isDaily) {
                        onExitGame2048
                    } else {
                        controller::showDifficultySelector
                    },
                isDaily = state.launch.isDaily,
            )
        is Web2048State.Error ->
            WebCatalogLevelErrorContent(
                levelNumber = state.levelNumber?.value,
                failure = state.failure,
                onRetry = controller::retryLoading,
                onBack =
                    if (state.launch.isDaily) {
                        onExitGame2048
                    } else {
                        controller::showDifficultySelector
                    },
                isDaily = state.launch.isDaily,
            )
        is Web2048State.Playing ->
            PlayingGame2048Content(
                state = state,
                controller = controller,
                onExitGame2048 = onExitGame2048,
                onSolvedNextLevel = onSolvedNextLevel,
            )
    }
}
