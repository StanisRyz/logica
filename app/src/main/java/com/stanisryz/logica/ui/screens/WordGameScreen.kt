package com.stanisryz.logica.ui.screens

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HighlightOff
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stanisryz.logica.R
import com.stanisryz.logica.economy.EconomyRepository
import com.stanisryz.logica.economy.PlayerEconomy
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import com.stanisryz.logica.puzzle.core.word.WordGameState
import com.stanisryz.logica.puzzle.core.word.WordGameStatus
import com.stanisryz.logica.puzzle.core.word.WordGuessRejection
import com.stanisryz.logica.puzzle.core.word.WordPuzzle
import com.stanisryz.logica.puzzle.core.word.WordRules
import com.stanisryz.logica.result.CompletionPersistence
import com.stanisryz.logica.result.GameCompletionRepository
import com.stanisryz.logica.session.GameSessionRepository
import com.stanisryz.logica.ui.components.CompletionActions
import com.stanisryz.logica.ui.components.CompletionCard
import com.stanisryz.logica.ui.components.DifficultyBadge
import com.stanisryz.logica.ui.components.EconomyResultFeedback
import com.stanisryz.logica.ui.components.LoadingState
import com.stanisryz.logica.ui.components.RetryableErrorState
import com.stanisryz.logica.ui.components.SupportingText
import com.stanisryz.logica.ui.components.ZeroLivesCard
import com.stanisryz.logica.ui.components.difficultyLabel
import com.stanisryz.logica.ui.theme.LocalLogicaPalette
import com.stanisryz.logica.ui.theme.LogicaSpacing
import com.stanisryz.logica.ui.word.WordBoard
import com.stanisryz.logica.ui.word.WordKeyboard
import com.stanisryz.logica.word.WordGameContext
import com.stanisryz.logica.word.WordGameError
import com.stanisryz.logica.word.WordGameLaunch
import com.stanisryz.logica.word.WordGameUiState
import com.stanisryz.logica.word.WordGameViewModel
import com.stanisryz.logica.word.WordGameViewModelFactory

@Composable
internal fun WordGameRoute(
    launch: WordGameLaunch,
    sessionRepository: GameSessionRepository,
    completionRepository: GameCompletionRepository,
    economyRepository: EconomyRepository,
    hapticsEnabled: Boolean,
    onBack: () -> Unit,
    onNewPuzzle: (Difficulty) -> Unit,
    onStartNew: () -> Unit,
    onGameHub: () -> Unit,
    onRestoreLife: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val factory =
        remember(launch, sessionRepository, completionRepository, economyRepository) {
            WordGameViewModelFactory(launch, sessionRepository, completionRepository, economyRepository)
        }
    val gameViewModel: WordGameViewModel = viewModel(factory = factory)
    val uiState by gameViewModel.uiState.collectAsStateWithLifecycle()
    val economy by gameViewModel.economy.collectAsStateWithLifecycle()

    WordGameScreen(
        uiState = uiState,
        economy = economy,
        onLetter = gameViewModel::appendLetter,
        onBackspace = gameViewModel::removeLastLetter,
        onSubmit = gameViewModel::submit,
        onDismissRejection = gameViewModel::dismissRejection,
        onRetryCompletion = gameViewModel::retryCompletion,
        onRetryPuzzle = gameViewModel::retry,
        onRestoreLife = onRestoreLife,
        hapticsEnabled = hapticsEnabled,
        onBack = onBack,
        onNewPuzzle = onNewPuzzle,
        onStartNew = onStartNew,
        onGameHub = onGameHub,
        isDaily = launch.context is WordGameContext.Daily,
        modifier = modifier,
    )
}

@Composable
private fun WordGameScreen(
    uiState: WordGameUiState,
    economy: PlayerEconomy,
    onLetter: (Char) -> Unit,
    onBackspace: () -> Unit,
    onSubmit: () -> Unit,
    onDismissRejection: () -> Unit,
    onRetryCompletion: () -> Unit,
    onRetryPuzzle: () -> Unit,
    onRestoreLife: () -> Unit,
    hapticsEnabled: Boolean,
    onBack: () -> Unit,
    onNewPuzzle: (Difficulty) -> Unit,
    onStartNew: () -> Unit,
    onGameHub: () -> Unit,
    isDaily: Boolean,
    modifier: Modifier,
) {
    when (uiState) {
        WordGameUiState.Loading -> LoadingState(modifier, stringResource(R.string.creating_puzzle))
        is WordGameUiState.Error ->
            RetryableErrorState(
                message =
                    stringResource(
                        when (uiState.reason) {
                            WordGameError.MISSING_SAVED_SESSION -> R.string.missing_saved_game
                            WordGameError.INVALID_SAVED_SESSION -> R.string.invalid_saved_game
                            WordGameError.GENERATION -> R.string.puzzle_generation_error
                        },
                    ),
                retryLabel = stringResource(if (isDaily) R.string.to_games else R.string.try_another),
                onRetry = if (isDaily) onGameHub else onStartNew,
                modifier = modifier,
                secondaryLabel = stringResource(R.string.back),
                onSecondary = onBack,
            )
        is WordGameUiState.Ready ->
            WordReadyState(
                puzzle = uiState.puzzle,
                game = uiState.game,
                rejection = uiState.rejection,
                completionPersistence = uiState.completionPersistence,
                economy = economy,
                onLetter = onLetter,
                onBackspace = onBackspace,
                onSubmit = onSubmit,
                onDismissRejection = onDismissRejection,
                onRetryCompletion = onRetryCompletion,
                onRetryPuzzle = onRetryPuzzle,
                onRestoreLife = onRestoreLife,
                hapticsEnabled = hapticsEnabled,
                onNewPuzzle = { onNewPuzzle(uiState.puzzle.id.difficulty) },
                onGameHub = onGameHub,
                isDaily = isDaily,
                modifier = modifier,
            )
    }
}

/**
 * The board scrolls while the keyboard stays anchored to the bottom, so a seven-letter EXPERT game
 * stays playable on a small portrait screen without scrolling the keys out of reach.
 */
@Composable
private fun WordReadyState(
    puzzle: WordPuzzle,
    game: WordGameState,
    rejection: WordGuessRejection?,
    completionPersistence: CompletionPersistence,
    economy: PlayerEconomy,
    onLetter: (Char) -> Unit,
    onBackspace: () -> Unit,
    onSubmit: () -> Unit,
    onDismissRejection: () -> Unit,
    onRetryCompletion: () -> Unit,
    onRetryPuzzle: () -> Unit,
    onRestoreLife: () -> Unit,
    hapticsEnabled: Boolean,
    onNewPuzzle: () -> Unit,
    onGameHub: () -> Unit,
    isDaily: Boolean,
    modifier: Modifier,
) {
    val view = LocalView.current

    LaunchedEffect(rejection) {
        if (rejection != null && hapticsEnabled) {
            view.performHapticFeedback(HapticFeedbackConstants.REJECT)
        }
    }
    // Balance and Crowns already confirm a solve; Word additionally has a terminal failure.
    LaunchedEffect(game.status) {
        if (!hapticsEnabled) return@LaunchedEffect
        when (game.status) {
            WordGameStatus.SOLVED -> view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            WordGameStatus.FAILED -> view.performHapticFeedback(HapticFeedbackConstants.REJECT)
            WordGameStatus.IN_PROGRESS -> Unit
        }
    }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(
                    horizontal = LogicaSpacing.gameplayHorizontal,
                    vertical = LogicaSpacing.screenVertical,
                ),
        verticalArrangement = Arrangement.spacedBy(LogicaSpacing.item),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(LogicaSpacing.item),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            DifficultyBadge(difficultyLabel(PuzzleType.WORD, puzzle.id.difficulty))
            SupportingText(
                stringResource(R.string.word_attempts_left, game.remainingAttempts, WordRules.MAXIMUM_ATTEMPTS),
            )
            // The saved word stays visible and intact at zero lives; only the actions stop working.
            ZeroLivesCard(economy, onRestoreLife)
            WordBoard(game)
            rejection?.let { reason ->
                Text(
                    stringResource(reason.messageResource()),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
                )
            }
            if (game.status != WordGameStatus.IN_PROGRESS) {
                WordTerminalCard(
                    puzzle = puzzle,
                    game = game,
                    completionPersistence = completionPersistence,
                    economy = economy,
                    onRetryCompletion = onRetryCompletion,
                    onRetryPuzzle = onRetryPuzzle,
                    onNewPuzzle = onNewPuzzle,
                    onGameHub = onGameHub,
                    isDaily = isDaily,
                )
            }
        }

        if (game.status == WordGameStatus.IN_PROGRESS) {
            WordKeyboard(
                knowledge = game.letterKnowledge,
                enabled = economy.isGameplayAllowed,
                onLetter = { letter ->
                    if (hapticsEnabled) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onDismissRejection()
                    onLetter(letter)
                },
                onBackspace = {
                    if (hapticsEnabled) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onDismissRejection()
                    onBackspace()
                },
                onSubmit = onSubmit,
            )
        }
    }
}

@Composable
private fun WordTerminalCard(
    puzzle: WordPuzzle,
    game: WordGameState,
    completionPersistence: CompletionPersistence,
    economy: PlayerEconomy,
    onRetryCompletion: () -> Unit,
    onRetryPuzzle: () -> Unit,
    onNewPuzzle: () -> Unit,
    onGameHub: () -> Unit,
    isDaily: Boolean,
) {
    val isSolved = game.status == WordGameStatus.SOLVED
    val palette = LocalLogicaPalette.current
    CompletionCard(
        icon = if (isSolved) Icons.Filled.CheckCircle else Icons.Filled.HighlightOff,
        title = stringResource(if (isSolved) R.string.word_solved else R.string.word_failed),
        containerColor = if (isSolved) palette.successContainer else MaterialTheme.colorScheme.errorContainer,
        contentColor = if (isSolved) palette.onSuccessContainer else MaterialTheme.colorScheme.onErrorContainer,
    ) {
        if (isSolved) {
            Text(
                stringResource(R.string.word_attempts_used, game.attempts.size),
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            Text(
                stringResource(R.string.word_answer_was, puzzle.answer.uppercase()),
                style = MaterialTheme.typography.titleMedium,
            )
        }

        when (completionPersistence) {
            CompletionPersistence.Error -> {
                Text(
                    stringResource(R.string.completion_save_error_body),
                    style = MaterialTheme.typography.bodyMedium,
                )
                TextButton(onClick = onRetryCompletion) { Text(stringResource(R.string.retry)) }
            }
            CompletionPersistence.Saving ->
                Text(
                    stringResource(R.string.saving_completion),
                    style = MaterialTheme.typography.bodyMedium,
                )
            // The wallet effect is only reported once the result is durably stored.
            CompletionPersistence.Saved ->
                EconomyResultFeedback(
                    isSolved = isSolved,
                    lives = economy.lives,
                    difficulty = puzzle.id.difficulty,
                )
            else -> Unit
        }

        CompletionActions {
            // A failed attempt leads with replaying the very same word; the Daily entry stays open.
            if (!isSolved && completionPersistence == CompletionPersistence.Saved) {
                Button(
                    onClick = onRetryPuzzle,
                    enabled = economy.isGameplayAllowed,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.retry_puzzle))
                }
                TextButton(onClick = onGameHub) { Text(stringResource(R.string.to_games)) }
            } else if (isDaily) {
                Button(onClick = onGameHub, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.to_games))
                }
            } else {
                Button(onClick = onNewPuzzle, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.new_puzzle))
                }
                TextButton(onClick = onGameHub) { Text(stringResource(R.string.to_games)) }
            }
        }
    }
}

private fun WordGuessRejection.messageResource(): Int =
    when (this) {
        WordGuessRejection.INCOMPLETE_INPUT -> R.string.word_rejection_incomplete
        WordGuessRejection.NOT_IN_ALLOWED_GUESSES -> R.string.word_rejection_unknown_word
        WordGuessRejection.NORMALIZATION_FAILED -> R.string.word_rejection_invalid_letters
        WordGuessRejection.GAME_FINISHED -> R.string.word_rejection_finished
    }
