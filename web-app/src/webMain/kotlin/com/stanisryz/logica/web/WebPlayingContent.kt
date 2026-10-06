package com.stanisryz.logica.web

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.stanisryz.logica.puzzle.core.balance.BalanceGameStatus
import com.stanisryz.logica.puzzle.core.balance.hasMeaningfulProgress
import com.stanisryz.logica.puzzle.core.crowns.CrownsGameStatus
import com.stanisryz.logica.puzzle.core.crowns.hasMeaningfulProgress
import com.stanisryz.logica.puzzle.core.game2048.hasMeaningfulProgress
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import com.stanisryz.logica.puzzle.core.sudoku.SudokuGameStatus
import com.stanisryz.logica.puzzle.core.sudoku.hasMeaningfulProgress
import com.stanisryz.logica.puzzle.core.word.WordGameStatus
import com.stanisryz.logica.puzzle.core.word.hasMeaningfulProgress
import com.stanisryz.logica.shared.ui.generated.resources.Res
import com.stanisryz.logica.shared.ui.generated.resources.daily_marker
import com.stanisryz.logica.ui.balance.BalanceGameContent
import com.stanisryz.logica.ui.components.BoardDragCallbacks
import com.stanisryz.logica.ui.components.ContinueOfferKind
import com.stanisryz.logica.ui.components.GameKey
import com.stanisryz.logica.ui.components.starsForWordAttempts
import com.stanisryz.logica.ui.crowns.CrownsGameContent
import com.stanisryz.logica.ui.game2048.Game2048Content
import com.stanisryz.logica.ui.game2048.formatGame2048Number
import com.stanisryz.logica.ui.sudoku.SudokuGameContent
import com.stanisryz.logica.ui.word.WordGameContent
import com.stanisryz.logica.web.generated.resources.web_score_final
import com.stanisryz.logica.web.generated.resources.web_word_answer
import com.stanisryz.logica.web.generated.resources.web_word_guessed
import com.stanisryz.logica.web.generated.resources.web_word_level_solved
import kotlinx.coroutines.flow.Flow
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import com.stanisryz.logica.web.generated.resources.Res as WebRes

@Composable
internal fun PlayingBalanceContent(
    state: WebBalanceState.Playing,
    controller: WebBalanceController,
    hintCount: Int?,
    onOpenStore: () -> Unit,
    onExitBalance: () -> Unit,
    onSolvedNextLevel: (() -> Unit) -> Unit,
) {
    val livesGuard = LocalWebLives.current.guard
    val transitionAd = LocalWebTransitionAd.current
    Column(Modifier.fillMaxSize()) {
        WebGameplayHeader(
            puzzleType = PuzzleType.BALANCE,
            isDaily = state.source.isDaily,
            hasMeaningfulProgress = state.game.hasMeaningfulProgress || controller.secondChanceOffered,
            onExit = if (state.source.isDaily) onExitBalance else controller::showDifficultySelector,
        )
        BalanceGameContent(
            puzzle = state.puzzle,
            game = state.game,
            difficulty = state.source.difficulty,
            levelNumber = state.source.catalogLevelNumberOrNull,
            contextBadgeLabel = state.source.contextBadgeLabelOrNull(),
            selectedValue = state.selectedValue,
            isPencilMode = state.isPencilMode,
            isHintLoading = state.isHintLoading,
            gameplayEnabled = state.game.status == BalanceGameStatus.IN_PROGRESS,
            onCellTapped = controller::onCellTapped,
            onSelectValue = controller::selectValue,
            onTogglePencil = controller::togglePencilMode,
            onHint = controller::requestHint,
            modifier = Modifier.weight(1f),
            hintCount = hintCount,
            drag =
                remember(controller) {
                    BoardDragCallbacks(controller::onDragStart, controller::onDragCell, controller::onDragEnd)
                },
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
            puzzleType = PuzzleType.BALANCE,
            visible = state.game.status.isTerminal && !controller.secondChanceOffered,
            difficulty = state.source.difficulty,
            mistakesUsed = state.game.mistakesUsed,
            hintsUsed = state.game.hintsUsed,
            solved = state.game.status == BalanceGameStatus.SOLVED,
            completion = controller.dailyCompletionState,
            onRetry = { livesGuard { transitionAd(controller::retry) } },
            onRetrySave = controller::retryDailySave,
            onExit = { transitionAd(onExitBalance) },
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
            solved = state.game.status == BalanceGameStatus.SOLVED,
            completion = controller.completionState,
            onNextLevel = { livesGuard { onSolvedNextLevel { controller.nextLevel() } } },
            onRetry = { livesGuard { transitionAd(controller::retry) } },
            onRetrySave = controller::retrySave,
            onBack = { transitionAd(controller::showDifficultySelector) },
        )
    }
}

@Composable
internal fun PlayingCrownsContent(
    state: WebCrownsState.Playing,
    controller: WebCrownsController,
    hintCount: Int?,
    onOpenStore: () -> Unit,
    onExitCrowns: () -> Unit,
    onSolvedNextLevel: (() -> Unit) -> Unit,
) {
    val livesGuard = LocalWebLives.current.guard
    val transitionAd = LocalWebTransitionAd.current
    Column(Modifier.fillMaxSize()) {
        WebGameplayHeader(
            puzzleType = PuzzleType.CROWNS,
            isDaily = state.source.isDaily,
            hasMeaningfulProgress = state.game.hasMeaningfulProgress || controller.secondChanceOffered,
            onExit = if (state.source.isDaily) onExitCrowns else controller::showDifficultySelector,
        )
        CrownsGameContent(
            puzzle = state.puzzle,
            game = state.game,
            difficulty = state.source.difficulty,
            levelNumber = state.source.catalogLevelNumberOrNull,
            contextBadgeLabel = state.source.contextBadgeLabelOrNull(),
            selectedValue = state.selectedValue,
            isPencilMode = state.isPencilMode,
            isHintLoading = state.isHintLoading,
            gameplayEnabled = state.game.status == CrownsGameStatus.IN_PROGRESS,
            onCellTapped = controller::onCellTapped,
            onSelectValue = controller::selectValue,
            onTogglePencil = controller::togglePencilMode,
            onHint = controller::requestHint,
            modifier = Modifier.weight(1f),
            hintCount = hintCount,
            drag =
                remember(controller) {
                    BoardDragCallbacks(controller::onDragStart, controller::onDragCell, controller::onDragEnd)
                },
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
            puzzleType = PuzzleType.CROWNS,
            visible = state.game.status.isTerminal && !controller.secondChanceOffered,
            difficulty = state.source.difficulty,
            mistakesUsed = state.game.mistakesUsed,
            hintsUsed = state.game.hintsUsed,
            solved = state.game.status == CrownsGameStatus.SOLVED,
            completion = controller.dailyCompletionState,
            onRetry = { livesGuard { transitionAd(controller::retry) } },
            onRetrySave = controller::retryDailySave,
            onExit = { transitionAd(onExitCrowns) },
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
            solved = state.game.status == CrownsGameStatus.SOLVED,
            completion = controller.completionState,
            onNextLevel = { livesGuard { onSolvedNextLevel { controller.nextLevel() } } },
            onRetry = { livesGuard { transitionAd(controller::retry) } },
            onRetrySave = controller::retrySave,
            onBack = { transitionAd(controller::showDifficultySelector) },
        )
    }
}

@Composable
internal fun PlayingWordContent(
    state: WebWordState.Playing,
    controller: WebWordController,
    hardwareKeys: Flow<GameKey>,
    onExitWord: () -> Unit,
    onSolvedNextLevel: (() -> Unit) -> Unit,
) {
    val livesGuard = LocalWebLives.current.guard
    val transitionAd = LocalWebTransitionAd.current
    Column(Modifier.fillMaxSize()) {
        WebGameplayHeader(
            puzzleType = PuzzleType.WORD,
            isDaily = state.source.isDaily,
            hasMeaningfulProgress = state.game.hasMeaningfulProgress,
            onExit = if (state.source.isDaily) onExitWord else controller::showDifficultySelector,
        )
        WordGameContent(
            puzzle = state.puzzle,
            game = state.game,
            levelNumber = state.source.catalogLevelNumberOrNull,
            contextBadgeLabel = state.source.contextBadgeLabelOrNull(),
            rejection = state.rejection,
            rejectionRevision = state.rejectionRevision,
            acceptedAttemptRevision = state.acceptedAttemptRevision,
            gameplayEnabled = state.game.status == WordGameStatus.IN_PROGRESS,
            onLetter = controller::setLetter,
            onClearLetter = controller::clearLetter,
            onSubmit = controller::submit,
            onDismissRejection = controller::dismissRejection,
            onAcceptedAttemptRevealed = controller::onAcceptedAttemptRevealed,
            modifier = Modifier.weight(1f),
            hardwareKeys = hardwareKeys,
        )
    }

    if (state.source.isDaily) {
        WebDailyOrdinaryTerminalDialog(
            puzzleType = PuzzleType.WORD,
            visible = state.isTerminalRevealReady,
            difficulty = state.source.difficulty,
            solved = state.game.status == WordGameStatus.SOLVED,
            // Spoiler-free: the Daily dialog never reveals the answer, unlike the Catalog one.
            scoreDetail = pluralStringResource(WebRes.plurals.web_word_guessed, state.game.attempts.size, state.game.attempts.size),
            stars = starsForWordAttempts(state.game.attempts.size),
            completion = controller.dailyCompletionState,
            onRetry = { livesGuard { transitionAd(controller::retry) } },
            onRetrySave = controller::retryDailySave,
            onExit = { transitionAd(onExitWord) },
        )
    } else {
        WebCatalogSaveErrorBanner(
            completion = controller.completionState,
            onRetrySave = controller::retrySave,
        )
        WebOrdinaryCatalogTerminalDialog(
            visible = state.isTerminalRevealReady,
            difficulty = state.source.difficulty,
            levelNumber = requireNotNull(state.source.catalogLevelNumberOrNull),
            solved = state.game.status == WordGameStatus.SOLVED,
            completion = controller.completionState,
            solvedDetail = pluralStringResource(WebRes.plurals.web_word_level_solved, state.game.attempts.size, state.game.attempts.size),
            stars = starsForWordAttempts(state.game.attempts.size),
            failedDetail = stringResource(WebRes.string.web_word_answer, state.puzzle.language.displayUppercase(state.puzzle.answer)),
            onNextLevel = { livesGuard { onSolvedNextLevel { controller.nextLevel() } } },
            onRetry = { livesGuard { transitionAd(controller::retry) } },
            onRetrySave = controller::retrySave,
            onBack = { transitionAd(controller::showDifficultySelector) },
        )
    }
}

@Composable
internal fun PlayingSudokuContent(
    state: WebSudokuState.Playing,
    controller: WebSudokuController,
    hintCount: Int?,
    onOpenStore: () -> Unit,
    onExitSudoku: () -> Unit,
    onSolvedNextLevel: (() -> Unit) -> Unit,
) {
    val livesGuard = LocalWebLives.current.guard
    val transitionAd = LocalWebTransitionAd.current
    Column(Modifier.fillMaxSize()) {
        WebGameplayHeader(
            puzzleType = PuzzleType.SUDOKU,
            isDaily = state.source.isDaily,
            hasMeaningfulProgress = state.game.hasMeaningfulProgress || controller.secondChanceOffered,
            onExit = if (state.source.isDaily) onExitSudoku else controller::showDifficultySelector,
        )
        val gameplayEnabled = state.game.status == SudokuGameStatus.IN_PROGRESS
        SudokuGameContent(
            puzzle = state.puzzle,
            game = state.game,
            selectedCell = state.selectedCell,
            isPencilMode = state.isPencilMode,
            levelNumber = state.source.catalogLevelNumberOrNull,
            contextBadgeLabel = state.source.contextBadgeLabelOrNull(),
            gameplayEnabled = gameplayEnabled,
            // Digits work without a selected cell too: they pick the digit-first active digit.
            inputEnabled = gameplayEnabled,
            onCellSelected = controller::onCellTapped,
            onDigit = controller::inputDigit,
            onTogglePencil = controller::togglePencilMode,
            onErase = controller::eraseSelectedCell,
            canUndo = controller.canUndo,
            onUndo = controller::undo,
            onHint = controller::requestHint,
            modifier = Modifier.weight(1f),
            hintCount = hintCount,
            activeDigit = state.activeDigit,
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
            puzzleType = PuzzleType.SUDOKU,
            visible = state.game.status.isTerminal && !controller.secondChanceOffered,
            difficulty = state.source.difficulty,
            mistakesUsed = state.game.mistakesUsed,
            hintsUsed = state.game.hintsUsed,
            solved = state.game.status == SudokuGameStatus.SOLVED,
            completion = controller.dailyCompletionState,
            onRetry = { livesGuard { transitionAd(controller::retry) } },
            onRetrySave = controller::retryDailySave,
            onExit = { transitionAd(onExitSudoku) },
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
            solved = state.game.status == SudokuGameStatus.SOLVED,
            completion = controller.completionState,
            onNextLevel = { livesGuard { onSolvedNextLevel { controller.nextLevel() } } },
            onRetry = { livesGuard { transitionAd(controller::retry) } },
            onRetrySave = controller::retrySave,
            onBack = { transitionAd(controller::showDifficultySelector) },
        )
    }
}

@Composable
internal fun PlayingGame2048Content(
    state: Web2048State.Playing,
    controller: Web2048Controller,
    onExitGame2048: () -> Unit,
    onSolvedNextLevel: (() -> Unit) -> Unit,
) {
    val livesGuard = LocalWebLives.current.guard
    val transitionAd = LocalWebTransitionAd.current
    Column(Modifier.fillMaxSize()) {
        WebGameplayHeader(
            puzzleType = PuzzleType.GAME_2048,
            isDaily = state.source.isDaily,
            hasMeaningfulProgress =
                controller.undoOffered ||
                    !state.finishedByPlayer &&
                    state.game.hasMeaningfulProgress(
                        levelCleared = !state.source.isDaily && state.game.goalReached,
                        completionSaved = controller.completionState is WebCatalogCompletionState.Saved,
                    ),
            onExit = if (state.source.isDaily) onExitGame2048 else controller::showDifficultySelector,
        )
        // Catalog-only: the save banner and cleared marker belong to Catalog progression.
        if (!state.source.isDaily) {
            WebCatalogSaveErrorBanner(
                completion = controller.completionState,
                onRetrySave = controller::retrySave,
            )
        }
        Game2048Content(
            game = state.game,
            difficulty = state.source.difficulty,
            levelNumber = state.source.catalogLevelNumberOrNull,
            contextBadgeLabel = state.source.contextBadgeLabelOrNull(),
            levelCleared = !state.source.isDaily && controller.completionState is WebCatalogCompletionState.Saved,
            motionRevision = state.motionRevision,
            motionTrace = state.motionTrace,
            gameplayEnabled = !state.isOver,
            canUndo = controller.canUndo,
            onMove = controller::move,
            onUndo = controller::undo,
            onMotionFinished = controller::finishMotion,
            modifier = Modifier.weight(1f),
            hardwareKeys = LocalWebKeyboard.current?.keys,
            onFinish = if (state.finishedByPlayer) null else controller::finish,
        )
    }

    // A dead end before the target first offers to take the losing move back for an ad, once per attempt.
    if (controller.undoOffered) {
        WebSecondChanceDialog(
            onContinue = controller::undoLosingMoveAfterAd,
            onDecline = controller::declineUndoOffer,
            kind = ContinueOfferKind.UNDO_LAST_MOVE,
        )
    }
    if (state.source.isDaily) {
        WebDailyOrdinaryTerminalDialog(
            puzzleType = PuzzleType.GAME_2048,
            visible = state.isOver && state.motionTrace == null && !controller.undoOffered,
            difficulty = state.source.difficulty,
            solved = state.game.goalReached,
            scoreDetail = stringResource(WebRes.string.web_score_final, formatGame2048Number(state.game.score)),
            completion = controller.dailyCompletionState,
            onRetry = { livesGuard { transitionAd(controller::retry) } },
            onRetrySave = controller::retryDailySave,
            onExit = { transitionAd(onExitGame2048) },
        )
    } else {
        Web2048CatalogTerminalDialog(
            visible = state.isOver && state.motionTrace == null && !controller.undoOffered,
            difficulty = state.source.difficulty,
            levelNumber = requireNotNull(state.source.catalogLevelNumberOrNull),
            goalReached = state.game.goalReached,
            score = formatGame2048Number(state.game.score),
            completion = controller.completionState,
            onNextLevel = { livesGuard { onSolvedNextLevel { controller.nextLevel() } } },
            onRetry = { livesGuard { transitionAd(controller::retry) } },
            onRetrySave = controller::retrySave,
            onBack = { transitionAd(controller::showDifficultySelector) },
        )
    }
}

/** Compact Daily marker instead of a Catalog level number; Catalog keeps its normal metadata. */
@Composable
internal fun WebGameplaySource.contextBadgeLabelOrNull(): String? = if (isDaily) stringResource(Res.string.daily_marker) else null
