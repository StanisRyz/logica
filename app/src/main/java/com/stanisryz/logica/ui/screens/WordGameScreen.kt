package com.stanisryz.logica.ui.screens

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stanisryz.logica.R
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.word.WordGameState
import com.stanisryz.logica.puzzle.core.word.WordGameStatus
import com.stanisryz.logica.puzzle.core.word.WordGuessRejection
import com.stanisryz.logica.puzzle.core.word.WordPuzzle
import com.stanisryz.logica.puzzle.core.word.WordRules
import com.stanisryz.logica.result.CompletionPersistence
import com.stanisryz.logica.result.GameCompletionRepository
import com.stanisryz.logica.session.GameSessionRepository
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
    hapticsEnabled: Boolean,
    onBack: () -> Unit,
    onNewPuzzle: (Difficulty) -> Unit,
    onStartNew: () -> Unit,
    onCatalog: () -> Unit,
    onToday: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val factory =
        remember(launch, sessionRepository, completionRepository) {
            WordGameViewModelFactory(launch, sessionRepository, completionRepository)
        }
    val gameViewModel: WordGameViewModel = viewModel(factory = factory)
    val uiState by gameViewModel.uiState.collectAsStateWithLifecycle()

    WordGameScreen(
        uiState = uiState,
        onLetter = gameViewModel::appendLetter,
        onBackspace = gameViewModel::removeLastLetter,
        onSubmit = gameViewModel::submit,
        onDismissRejection = gameViewModel::dismissRejection,
        onRetryCompletion = gameViewModel::retryCompletion,
        hapticsEnabled = hapticsEnabled,
        onBack = onBack,
        onNewPuzzle = onNewPuzzle,
        onStartNew = onStartNew,
        onCatalog = onCatalog,
        onToday = onToday,
        isDaily = launch.context is WordGameContext.Daily,
        modifier = modifier,
    )
}

@Composable
private fun WordGameScreen(
    uiState: WordGameUiState,
    onLetter: (Char) -> Unit,
    onBackspace: () -> Unit,
    onSubmit: () -> Unit,
    onDismissRejection: () -> Unit,
    onRetryCompletion: () -> Unit,
    hapticsEnabled: Boolean,
    onBack: () -> Unit,
    onNewPuzzle: (Difficulty) -> Unit,
    onStartNew: () -> Unit,
    onCatalog: () -> Unit,
    onToday: () -> Unit,
    isDaily: Boolean,
    modifier: Modifier,
) {
    when (uiState) {
        WordGameUiState.Loading ->
            Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.creating_puzzle), Modifier.padding(top = 16.dp))
                }
            }
        is WordGameUiState.Error ->
            WordErrorState(uiState.reason, if (isDaily) onToday else onStartNew, onBack, isDaily, modifier)
        is WordGameUiState.Ready ->
            WordReadyState(
                puzzle = uiState.puzzle,
                game = uiState.game,
                rejection = uiState.rejection,
                completionPersistence = uiState.completionPersistence,
                onLetter = onLetter,
                onBackspace = onBackspace,
                onSubmit = onSubmit,
                onDismissRejection = onDismissRejection,
                onRetryCompletion = onRetryCompletion,
                hapticsEnabled = hapticsEnabled,
                onNewPuzzle = { onNewPuzzle(uiState.puzzle.id.difficulty) },
                onCatalog = onCatalog,
                onToday = onToday,
                isDaily = isDaily,
                modifier = modifier,
            )
    }
}

@Composable
private fun WordErrorState(
    reason: WordGameError,
    onTryAnother: () -> Unit,
    onBack: () -> Unit,
    isDaily: Boolean,
    modifier: Modifier,
) {
    Column(modifier.fillMaxSize().padding(24.dp), Arrangement.Center, Alignment.CenterHorizontally) {
        Text(
            stringResource(
                when (reason) {
                    WordGameError.MISSING_SAVED_SESSION -> R.string.missing_saved_game
                    WordGameError.INVALID_SAVED_SESSION -> R.string.invalid_saved_game
                    WordGameError.GENERATION -> R.string.puzzle_generation_error
                },
            ),
            style = MaterialTheme.typography.titleMedium,
        )
        Button(onClick = onTryAnother, modifier = Modifier.padding(top = 16.dp)) {
            Text(stringResource(if (isDaily) R.string.to_today else R.string.try_another))
        }
        TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
    }
}

@Composable
private fun WordReadyState(
    puzzle: WordPuzzle,
    game: WordGameState,
    rejection: WordGuessRejection?,
    completionPersistence: CompletionPersistence,
    onLetter: (Char) -> Unit,
    onBackspace: () -> Unit,
    onSubmit: () -> Unit,
    onDismissRejection: () -> Unit,
    onRetryCompletion: () -> Unit,
    hapticsEnabled: Boolean,
    onNewPuzzle: () -> Unit,
    onCatalog: () -> Unit,
    onToday: () -> Unit,
    isDaily: Boolean,
    modifier: Modifier,
) {
    val view = LocalView.current

    LaunchedEffect(rejection) {
        if (rejection != null && hapticsEnabled) {
            view.performHapticFeedback(HapticFeedbackConstants.REJECT)
        }
    }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(R.string.difficulty_value, puzzle.id.difficulty.russianLabel()),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            stringResource(R.string.word_attempts_left, game.remainingAttempts, WordRules.MAXIMUM_ATTEMPTS),
            style = MaterialTheme.typography.bodyMedium,
        )

        WordBoard(game)

        rejection?.let { reason ->
            Text(
                stringResource(reason.messageResource()),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
            )
        }

        if (game.status == WordGameStatus.IN_PROGRESS) {
            WordKeyboard(
                knowledge = game.letterKnowledge,
                enabled = true,
                onLetter = { letter ->
                    onDismissRejection()
                    onLetter(letter)
                },
                onBackspace = {
                    onDismissRejection()
                    onBackspace()
                },
                onSubmit = onSubmit,
            )
        } else {
            WordTerminalCard(
                puzzle = puzzle,
                game = game,
                completionPersistence = completionPersistence,
                onRetryCompletion = onRetryCompletion,
                onNewPuzzle = onNewPuzzle,
                onCatalog = onCatalog,
                onToday = onToday,
                isDaily = isDaily,
            )
        }
    }
}

@Composable
private fun WordTerminalCard(
    puzzle: WordPuzzle,
    game: WordGameState,
    completionPersistence: CompletionPersistence,
    onRetryCompletion: () -> Unit,
    onNewPuzzle: () -> Unit,
    onCatalog: () -> Unit,
    onToday: () -> Unit,
    isDaily: Boolean,
) {
    val isSolved = game.status == WordGameStatus.SOLVED
    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(20.dp).semantics { liveRegion = LiveRegionMode.Polite },
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (isSolved) {
                Text(
                    stringResource(R.string.word_solved),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(stringResource(R.string.word_attempts_used, game.attempts.size))
            } else {
                Text(
                    stringResource(R.string.word_failed),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                Text(stringResource(R.string.word_answer_was, puzzle.answer.uppercase()))
            }

            when (completionPersistence) {
                CompletionPersistence.Error -> {
                    Text(stringResource(R.string.completion_save_error_body))
                    TextButton(onClick = onRetryCompletion) { Text(stringResource(R.string.retry)) }
                }
                CompletionPersistence.Saving -> Text(stringResource(R.string.saving_completion))
                else -> Unit
            }

            if (isDaily) {
                Button(onClick = onToday, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.to_today))
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onNewPuzzle) { Text(stringResource(R.string.new_game)) }
                    TextButton(onClick = onCatalog) { Text(stringResource(R.string.to_catalog)) }
                }
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
