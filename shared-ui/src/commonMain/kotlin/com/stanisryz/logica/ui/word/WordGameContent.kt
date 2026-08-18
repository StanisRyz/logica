package com.stanisryz.logica.ui.word

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.word.WordDraft
import com.stanisryz.logica.puzzle.core.word.WordGameState
import com.stanisryz.logica.puzzle.core.word.WordGameStatus
import com.stanisryz.logica.puzzle.core.word.WordGuessRejection
import com.stanisryz.logica.puzzle.core.word.WordPuzzle
import com.stanisryz.logica.shared.ui.generated.resources.Res
import com.stanisryz.logica.shared.ui.generated.resources.difficulty_easy
import com.stanisryz.logica.shared.ui.generated.resources.difficulty_expert
import com.stanisryz.logica.shared.ui.generated.resources.difficulty_hard
import com.stanisryz.logica.shared.ui.generated.resources.difficulty_medium
import com.stanisryz.logica.shared.ui.generated.resources.word_rejection_finished
import com.stanisryz.logica.shared.ui.generated.resources.word_rejection_incomplete
import com.stanisryz.logica.shared.ui.generated.resources.word_rejection_invalid_letters
import com.stanisryz.logica.shared.ui.generated.resources.word_rejection_unknown_word
import com.stanisryz.logica.ui.components.GameHeaderBadges
import com.stanisryz.logica.ui.theme.LogicaSpacing
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

/**
 * Pure shared Word gameplay presentation. Hosts own engines, persistence, economy, haptics,
 * navigation, terminal policy, and platform integrations.
 */
@Composable
fun WordGameContent(
    puzzle: WordPuzzle,
    game: WordGameState,
    levelNumber: Int?,
    rejection: WordGuessRejection?,
    rejectionRevision: Int,
    acceptedAttemptRevision: Int,
    gameplayEnabled: Boolean,
    onLetter: (Int, Char) -> Unit,
    onClearLetter: (Int) -> Unit,
    onSubmit: () -> Unit,
    onDismissRejection: () -> Unit,
    modifier: Modifier = Modifier,
    onInputInteraction: () -> Unit = {},
    onRejectionPresented: (WordGuessRejection) -> Unit = {},
    onAcceptedAttemptRevealed: (Int) -> Unit = {},
    hostStatusContent: @Composable ColumnScope.() -> Unit = {},
    terminalContent: @Composable ColumnScope.() -> Unit = {},
) {
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
    val currentOnRejectionPresented by rememberUpdatedState(onRejectionPresented)
    val currentOnAcceptedAttemptRevealed by rememberUpdatedState(onAcceptedAttemptRevealed)

    LaunchedEffect(rejectionRevision) {
        if (rejectionRevision == 0 || rejection == null) return@LaunchedEffect
        if (rejection == WordGuessRejection.INCOMPLETE_INPUT) {
            game.currentDraft.firstEmptyIndex()?.let { selectedCellIndex = it }
        }
        currentOnRejectionPresented(rejection)
        rejectionShake.snapTo(0f)
        listOf(-shakeDistance, shakeDistance, -shakeDistance * 0.6f, shakeDistance * 0.6f, 0f)
            .forEach { target -> rejectionShake.animateTo(target, tween(SHAKE_STEP_MILLIS)) }
    }
    LaunchedEffect(game.attempts.size, game.status) {
        if (game.status == WordGameStatus.IN_PROGRESS) {
            selectedCellIndex = initialWordSelection(game)
        }
    }

    val isPlaying = game.status == WordGameStatus.IN_PROGRESS
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
                GameHeaderBadges(stringResource(puzzle.id.difficulty.labelResource()), levelNumber)
                if (rejectionMessage != null) {
                    Box(
                        modifier =
                            Modifier.semantics {
                                liveRegion = LiveRegionMode.Assertive
                                contentDescription = rejectionMessage
                            },
                    )
                }
            }
            hostStatusContent()
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
                    editableEnabled = gameplayEnabled && isPlaying,
                    onCellSelected = { selectedCellIndex = it },
                    acceptedAttemptRevision = acceptedAttemptRevision,
                    onAcceptedAttemptRevealed = { revision ->
                        revealedAttemptRevision = revision
                        currentOnAcceptedAttemptRevealed(revision)
                    },
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
                    Column(content = terminalContent)
                }
            }

            if (isPlaying) {
                WordKeyboard(
                    knowledge = game.letterKnowledge,
                    enabled = gameplayEnabled,
                    onLetter = { letter ->
                        onInputInteraction()
                        onDismissRejection()
                        val editedPosition = selectedCellIndex
                        onLetter(editedPosition, letter)
                        selectedCellIndex = nextWordSelection(game.currentDraft, editedPosition)
                    },
                    onBackspace = {
                        onInputInteraction()
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

private fun WordGuessRejection.messageResource(): StringResource =
    when (this) {
        WordGuessRejection.INCOMPLETE_INPUT -> Res.string.word_rejection_incomplete
        WordGuessRejection.NOT_IN_ALLOWED_GUESSES -> Res.string.word_rejection_unknown_word
        WordGuessRejection.NORMALIZATION_FAILED -> Res.string.word_rejection_invalid_letters
        WordGuessRejection.GAME_FINISHED -> Res.string.word_rejection_finished
    }

private fun Difficulty.labelResource(): StringResource =
    when (this) {
        Difficulty.EASY -> Res.string.difficulty_easy
        Difficulty.MEDIUM -> Res.string.difficulty_medium
        Difficulty.HARD -> Res.string.difficulty_hard
        Difficulty.EXPERT -> Res.string.difficulty_expert
    }

private val SHAKE_DISTANCE = 8.dp
private const val SHAKE_STEP_MILLIS = 35
private val COMPACT_SCREEN_HEIGHT = 620.dp
private const val KEY_HEIGHT_RATIO = 0.068f
private val MIN_KEY_HEIGHT = 36.dp
private val MAX_KEY_HEIGHT = 48.dp
private val HEADER_HEIGHT_BUDGET = 56.dp
private val MIN_BOARD_HEIGHT = 180.dp
private const val TERMINAL_APPEAR_MILLIS = 180
private const val TERMINAL_INITIAL_SCALE = 0.98f
