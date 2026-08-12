package com.stanisryz.logica.ui.screens

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stanisryz.logica.R
import com.stanisryz.logica.economy.EconomyRepository
import com.stanisryz.logica.economy.PlayerEconomy
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import com.stanisryz.logica.puzzle.core.word.WordDraft
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
import com.stanisryz.logica.ui.word.WORD_KEYBOARD_ROWS
import com.stanisryz.logica.ui.word.WORD_KEY_SPACING
import com.stanisryz.logica.ui.word.WordBoard
import com.stanisryz.logica.ui.word.WordKeyboard
import com.stanisryz.logica.word.WordGameContext
import com.stanisryz.logica.word.WordGameError
import com.stanisryz.logica.word.WordGameLaunch
import com.stanisryz.logica.word.WordGameUiState
import com.stanisryz.logica.word.WordGameViewModel
import com.stanisryz.logica.word.WordGameViewModelFactory
import kotlin.math.roundToInt

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
    onTerminalAction: (() -> Unit) -> Unit = { it() },
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
        onLetter = gameViewModel::setLetter,
        onClearLetter = gameViewModel::clearLetter,
        onSubmit = gameViewModel::submit,
        onDismissRejection = gameViewModel::dismissRejection,
        onRetryCompletion = gameViewModel::retryCompletion,
        onRetryPuzzle = { onTerminalAction(gameViewModel::retry) },
        onRestoreLife = onRestoreLife,
        hapticsEnabled = hapticsEnabled,
        onBack = onBack,
        onNewPuzzle = { difficulty -> onTerminalAction { onNewPuzzle(difficulty) } },
        onStartNew = { onTerminalAction(onStartNew) },
        onGameHub = { onTerminalAction(onGameHub) },
        isDaily = launch.context is WordGameContext.Daily,
        modifier = modifier,
    )
}

@Composable
private fun WordGameScreen(
    uiState: WordGameUiState,
    economy: PlayerEconomy,
    onLetter: (Int, Char) -> Unit,
    onClearLetter: (Int) -> Unit,
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
                rejectionRevision = uiState.rejectionRevision,
                acceptedAttemptRevision = uiState.acceptedAttemptRevision,
                completionPersistence = uiState.completionPersistence,
                economy = economy,
                onLetter = onLetter,
                onClearLetter = onClearLetter,
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
 * One screen, no scrolling: the metrics line, the board, and the keyboard are budgeted against the
 * height the screen actually has, so a seven-letter EXPERT game on a small portrait phone compacts
 * its cells and keys rather than moving any of them out of reach. Only the finished attempt, whose
 * completion card can be taller than the space the keyboard leaves behind, may scroll.
 */
@Composable
private fun WordReadyState(
    puzzle: WordPuzzle,
    game: WordGameState,
    rejection: WordGuessRejection?,
    rejectionRevision: Int,
    acceptedAttemptRevision: Int,
    completionPersistence: CompletionPersistence,
    economy: PlayerEconomy,
    onLetter: (Int, Char) -> Unit,
    onClearLetter: (Int) -> Unit,
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
    val shakeDistance = with(LocalDensity.current) { SHAKE_DISTANCE.toPx() }
    var selectedCellIndex by
        rememberSaveable(puzzle.id) {
            mutableIntStateOf(initialWordSelection(game))
        }
    val rejectionShake = remember { Animatable(0f) }
    var revealedAttemptRevision by
        rememberSaveable(puzzle.id) {
            mutableIntStateOf(0)
        }

    LaunchedEffect(rejectionRevision) {
        if (rejectionRevision == 0) return@LaunchedEffect
        if (rejection == WordGuessRejection.INCOMPLETE_INPUT) {
            game.currentDraft.firstEmptyIndex()?.let { selectedCellIndex = it }
        }
        if (hapticsEnabled) view.performHapticFeedback(HapticFeedbackConstants.REJECT)
        rejectionShake.snapTo(0f)
        listOf(-shakeDistance, shakeDistance, -shakeDistance * 0.6f, shakeDistance * 0.6f, 0f)
            .forEach { target -> rejectionShake.animateTo(target, tween(SHAKE_STEP_MILLIS)) }
    }
    LaunchedEffect(game.attempts.size, game.status) {
        if (game.status == WordGameStatus.IN_PROGRESS) {
            selectedCellIndex = initialWordSelection(game)
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

    val isPlaying = game.status == WordGameStatus.IN_PROGRESS
    val attemptsText =
        stringResource(R.string.word_attempts_left, game.remainingAttempts, WordRules.MAXIMUM_ATTEMPTS)
    /*
     * A rejected guess shakes the board and buzzes; it no longer inserts a line of text that moves
     * the whole board and keyboard down and back up again. The reason still reaches a screen reader,
     * announced through the metrics line that is on screen anyway, so nothing new is laid out.
     */
    val rejectionMessage = rejection?.let { stringResource(it.messageResource()) }

    BoxWithConstraints(
        modifier =
            modifier
                .fillMaxSize()
                .padding(
                    horizontal = LogicaSpacing.gameplayHorizontal,
                    vertical = LogicaSpacing.item,
                ),
    ) {
        val compact = maxHeight < COMPACT_SCREEN_HEIGHT
        val gap = if (compact) LogicaSpacing.text else LogicaSpacing.item
        val keyHeight = (maxHeight * KEY_HEIGHT_RATIO).coerceIn(MIN_KEY_HEIGHT, MAX_KEY_HEIGHT)
        /*
         * The board keeps the height the keyboard leaves it whether the game is running or finished,
         * so a terminal card that scrolls into view does not first resize the board underneath it.
         */
        val keyboardHeight = keyHeight * WORD_KEYBOARD_ROWS + WORD_KEY_SPACING * (WORD_KEYBOARD_ROWS - 1)
        val boardHeight = (maxHeight - keyboardHeight - HEADER_HEIGHT_BUDGET).coerceAtLeast(MIN_BOARD_HEIGHT)

        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(gap),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(LogicaSpacing.action, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DifficultyBadge(difficultyLabel(PuzzleType.WORD, puzzle.id.difficulty))
                SupportingText(
                    text = attemptsText,
                    modifier =
                        Modifier.semantics {
                            if (rejectionMessage != null) {
                                liveRegion = LiveRegionMode.Assertive
                                contentDescription = rejectionMessage
                            }
                        },
                )
            }
            // The saved word stays visible and intact at zero lives; only the actions stop working.
            ZeroLivesCard(economy, onRestoreLife)
            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .then(if (isPlaying) Modifier else Modifier.verticalScroll(rememberScrollState())),
                verticalArrangement = Arrangement.spacedBy(gap, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                WordBoard(
                    game = game,
                    selectedCellIndex = selectedCellIndex,
                    editableEnabled = economy.isGameplayAllowed,
                    onCellSelected = { selectedCellIndex = it },
                    acceptedAttemptRevision = acceptedAttemptRevision,
                    onAcceptedAttemptRevealed = { revealedAttemptRevision = it },
                    modifier =
                        Modifier
                            .heightIn(max = boardHeight)
                            .offset {
                                IntOffset(rejectionShake.value.roundToInt(), 0)
                            },
                )
                AnimatedVisibility(
                    visible =
                        !isPlaying &&
                            (acceptedAttemptRevision == 0 || revealedAttemptRevision >= acceptedAttemptRevision),
                    enter =
                        fadeIn(tween(TERMINAL_APPEAR_MILLIS)) +
                            scaleIn(
                                animationSpec = tween(TERMINAL_APPEAR_MILLIS),
                                initialScale = TERMINAL_INITIAL_SCALE,
                            ),
                ) {
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

            if (isPlaying) {
                WordKeyboard(
                    knowledge = game.letterKnowledge,
                    enabled = economy.isGameplayAllowed,
                    onLetter = { letter ->
                        if (hapticsEnabled) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        onDismissRejection()
                        val editedPosition = selectedCellIndex
                        onLetter(editedPosition, letter)
                        selectedCellIndex = nextWordSelection(game.currentDraft, editedPosition)
                    },
                    onBackspace = {
                        if (hapticsEnabled) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        onDismissRejection()
                        positionToClear(game.currentDraft, selectedCellIndex)?.let { position ->
                            onClearLetter(position)
                            selectedCellIndex = position
                        }
                    },
                    onSubmit = onSubmit,
                    keyHeight = keyHeight,
                )
            }
        }
    }
}

private fun initialWordSelection(game: WordGameState): Int = game.currentDraft.firstEmptyIndex() ?: game.wordLength - 1

private fun nextWordSelection(
    draft: WordDraft,
    editedPosition: Int,
): Int {
    ((editedPosition + 1) until draft.wordLength).firstOrNull { draft[it] == null }?.let { return it }
    (0 until editedPosition).firstOrNull { draft[it] == null }?.let { return it }
    return editedPosition
}

private fun positionToClear(
    draft: WordDraft,
    selectedPosition: Int,
): Int? =
    if (draft[selectedPosition] != null) {
        selectedPosition
    } else {
        (selectedPosition - 1 downTo 0).firstOrNull { draft[it] != null }
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
            when (completionPersistence) {
                CompletionPersistence.Saved -> {
                    // A failed attempt leads with replaying the same word; Daily stays open.
                    if (!isSolved) {
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
                CompletionPersistence.Saving -> {
                    TextButton(onClick = {}, enabled = false) { Text(stringResource(R.string.saving)) }
                }
                CompletionPersistence.Error,
                CompletionPersistence.NotRequired,
                -> Unit
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

private val SHAKE_DISTANCE = 8.dp
private const val SHAKE_STEP_MILLIS = 35

/** Below this the gameplay column tightens its gaps so board and keyboard both keep their size. */
private val COMPACT_SCREEN_HEIGHT = 620.dp

/** The keyboard is four rows, so this ratio keeps it around a third of a short screen. */
private const val KEY_HEIGHT_RATIO = 0.068f
private val MIN_KEY_HEIGHT = 36.dp
private val MAX_KEY_HEIGHT = 48.dp

/** What the difficulty/attempts line plus the gaps around it cost the board. */
private val HEADER_HEIGHT_BUDGET = 56.dp
private val MIN_BOARD_HEIGHT = 180.dp
private const val TERMINAL_APPEAR_MILLIS = 180
private const val TERMINAL_INITIAL_SCALE = 0.98f
