package com.stanisryz.logica.ui.word

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.word.RussianWordNormalizer
import com.stanisryz.logica.puzzle.core.word.WordDraft
import com.stanisryz.logica.puzzle.core.word.WordGameState
import com.stanisryz.logica.puzzle.core.word.WordGameStatus
import com.stanisryz.logica.puzzle.core.word.WordGuessRejection
import com.stanisryz.logica.puzzle.core.word.WordPuzzle
import com.stanisryz.logica.puzzle.core.word.WordRules
import com.stanisryz.logica.shared.ui.generated.resources.Res
import com.stanisryz.logica.shared.ui.generated.resources.difficulty_easy
import com.stanisryz.logica.shared.ui.generated.resources.difficulty_expert
import com.stanisryz.logica.shared.ui.generated.resources.difficulty_hard
import com.stanisryz.logica.shared.ui.generated.resources.difficulty_medium
import com.stanisryz.logica.shared.ui.generated.resources.word_rejection_finished
import com.stanisryz.logica.shared.ui.generated.resources.word_rejection_incomplete
import com.stanisryz.logica.shared.ui.generated.resources.word_rejection_invalid_letters
import com.stanisryz.logica.shared.ui.generated.resources.word_rejection_unknown_word
import com.stanisryz.logica.ui.components.BoardTitle
import com.stanisryz.logica.ui.components.GameKey
import com.stanisryz.logica.ui.components.GameSound
import com.stanisryz.logica.ui.components.LocalGameSounds
import com.stanisryz.logica.ui.components.isWideGameplayLayout
import com.stanisryz.logica.ui.theme.LogicaSpacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
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
    contextBadgeLabel: String? = null,
    modifier: Modifier = Modifier,
    onInputInteraction: () -> Unit = {},
    onRejectionPresented: (WordGuessRejection) -> Unit = {},
    onAcceptedAttemptRevealed: (Int) -> Unit = {},
    hardwareKeys: Flow<GameKey>? = null,
    hostStatusContent: @Composable ColumnScope.() -> Unit = {},
    terminalContent: @Composable ColumnScope.() -> Unit = {},
) {
    val shakeDistance = with(LocalDensity.current) { SHAKE_DISTANCE.toPx() }
    var selectedCellIndex by
        rememberSaveable(puzzle.id) {
            mutableIntStateOf(initialWordSelection(game))
        }
    val rejectionShake = remember { Animatable(0f) }
    // The visible rejection note: shown on every rejection, hidden again after a moment or on input.
    var shownRejection by remember { mutableStateOf<WordGuessRejection?>(null) }
    var rejectionNoteVisible by remember { mutableStateOf(false) }
    var revealedAttemptRevision by
        rememberSaveable(puzzle.id) {
            mutableIntStateOf(0)
        }
    val currentOnRejectionPresented by rememberUpdatedState(onRejectionPresented)
    val currentOnAcceptedAttemptRevealed by rememberUpdatedState(onAcceptedAttemptRevealed)

    val sounds = LocalGameSounds.current
    // Each revealed guess sounds once: a win, a loss, or a plain step on.
    LaunchedEffect(revealedAttemptRevision) {
        if (revealedAttemptRevision == 0) return@LaunchedEffect
        sounds.play(
            when (game.status) {
                WordGameStatus.SOLVED -> GameSound.WIN
                WordGameStatus.FAILED -> GameSound.FAIL
                else -> GameSound.CORRECT
            },
        )
    }
    LaunchedEffect(rejectionRevision) {
        if (rejectionRevision == 0 || rejection == null) return@LaunchedEffect
        sounds.play(GameSound.MISTAKE)
        if (rejection == WordGuessRejection.INCOMPLETE_INPUT) {
            game.currentDraft.firstEmptyIndex()?.let { selectedCellIndex = it }
        }
        currentOnRejectionPresented(rejection)
        shownRejection = rejection
        rejectionNoteVisible = true
        rejectionShake.snapTo(0f)
        listOf(-shakeDistance, shakeDistance, -shakeDistance * 0.6f, shakeDistance * 0.6f, 0f)
            .forEach { target -> rejectionShake.animateTo(target, tween(SHAKE_STEP_MILLIS)) }
        delay(REJECTION_NOTE_MILLIS)
        rejectionNoteVisible = false
    }
    val rejectionNoteText = shownRejection?.let { stringResource(it.messageResource()) }
    val rejectionNoteShown = rejectionNoteVisible && rejection != null
    // Keep the note away from the row being typed: over the lower rows while the upper ones are in use.
    val rejectionNoteAlignment =
        if (game.attempts.size < WordRules.MAXIMUM_ATTEMPTS / 2) Alignment.BottomCenter else Alignment.TopCenter
    LaunchedEffect(game.attempts.size, game.status) {
        if (game.status == WordGameStatus.IN_PROGRESS) {
            selectedCellIndex = initialWordSelection(game)
        }
    }

    // A hardware keyboard edits the same draft and selection as the on-screen keys.
    val currentGame by rememberUpdatedState(game)
    val currentGameplayEnabled by rememberUpdatedState(gameplayEnabled)
    val currentOnLetter by rememberUpdatedState(onLetter)
    val currentOnClearLetter by rememberUpdatedState(onClearLetter)
    val currentOnSubmit by rememberUpdatedState(onSubmit)
    val currentOnDismissRejection by rememberUpdatedState(onDismissRejection)
    val currentOnInputInteraction by rememberUpdatedState(onInputInteraction)
    LaunchedEffect(hardwareKeys) {
        hardwareKeys?.collect { key ->
            val draft = currentGame.currentDraft
            if (!currentGameplayEnabled || currentGame.status != WordGameStatus.IN_PROGRESS) return@collect
            when (key) {
                is GameKey.Letter -> {
                    val letter = key.char.lowercaseChar()
                    if (!RussianWordNormalizer.isSupportedLetter(letter)) return@collect
                    currentOnInputInteraction()
                    currentOnDismissRejection()
                    val editedPosition = selectedCellIndex
                    currentOnLetter(editedPosition, letter)
                    sounds.play(GameSound.TAP)
                    selectedCellIndex = nextWordSelection(draft, editedPosition)
                }
                GameKey.Backspace, GameKey.Delete -> {
                    currentOnInputInteraction()
                    currentOnDismissRejection()
                    positionToClear(draft, selectedCellIndex)?.let { position ->
                        currentOnClearLetter(position)
                        selectedCellIndex = position
                    }
                }
                GameKey.Enter -> currentOnSubmit()
                GameKey.Left -> selectedCellIndex = (selectedCellIndex - 1).coerceAtLeast(0)
                GameKey.Right -> selectedCellIndex = (selectedCellIndex + 1).coerceAtMost(draft.wordLength - 1)
                else -> Unit
            }
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
        // Side by side only when the window is too low for the board over the keyboard (a phone in
        // landscape); a tall wide window (a desktop) keeps the full keyboard under the board.
        val wideLayout = isPlaying && isWideGameplayLayout(maxWidth, maxHeight) && maxHeight < STACKED_MIN_HEIGHT
        val keyHeight = (maxHeight * KEY_HEIGHT_RATIO).coerceIn(MIN_KEY_HEIGHT, MAX_KEY_HEIGHT)
        val keyboardHeight = keyHeight * WORD_KEYBOARD_ROWS + WORD_KEY_SPACING * (WORD_KEYBOARD_ROWS - 1)
        val boardHeight = (maxHeight - keyboardHeight - HEADER_HEIGHT_BUDGET).coerceAtLeast(MIN_BOARD_HEIGHT)
        val landscapeKeySpacing = if (maxHeight < COMPACT_LANDSCAPE_HEIGHT) COMPACT_LANDSCAPE_KEY_SPACING else WORD_KEY_SPACING
        val landscapeKeyHeight =
            ((maxHeight - landscapeKeySpacing * (WORD_LANDSCAPE_KEYBOARD_ROWS - 1)) / WORD_LANDSCAPE_KEYBOARD_ROWS)
                .coerceIn(MIN_LANDSCAPE_KEY_HEIGHT, MAX_KEY_HEIGHT)
        val landscapePanelWidth = minOf(WORD_LANDSCAPE_PANEL_MAX_WIDTH, maxWidth * WORD_LANDSCAPE_PANEL_FRACTION)

        if (wideLayout) {
            Row(
                modifier = Modifier.fillMaxSize().animateContentSize(),
                horizontalArrangement = Arrangement.spacedBy(WIDE_LAYOUT_SPACING),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(gap),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    WordGameHeader(
                        puzzle = puzzle,
                        levelNumber = levelNumber,
                        contextBadgeLabel = contextBadgeLabel,
                        rejectionMessage = rejectionMessage,
                    )
                    hostStatusContent()
                    Box(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        WordBoard(
                            game = game,
                            selectedCellIndex = selectedCellIndex,
                            editableEnabled = gameplayEnabled,
                            onCellSelected = { selectedCellIndex = it },
                            acceptedAttemptRevision = acceptedAttemptRevision,
                            onAcceptedAttemptRevealed = { revision ->
                                revealedAttemptRevision = revision
                                currentOnAcceptedAttemptRevealed(revision)
                            },
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .offset { IntOffset(rejectionShake.value.roundToInt(), 0) },
                        )
                        WordRejectionNote(rejectionNoteText, rejectionNoteShown, Modifier.align(rejectionNoteAlignment))
                    }
                }
                WordKeyboard(
                    knowledge = game.letterKnowledge,
                    enabled = gameplayEnabled,
                    onLetter = { letter ->
                        onInputInteraction()
                        onDismissRejection()
                        val editedPosition = selectedCellIndex
                        onLetter(editedPosition, letter)
                        sounds.play(GameSound.TAP)
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
                    modifier = Modifier.width(landscapePanelWidth),
                    keyHeight = landscapeKeyHeight,
                    landscapeCompact = true,
                    keySpacing = landscapeKeySpacing,
                )
            }
        } else {
            Column(
                modifier =
                    Modifier
                        .align(Alignment.TopCenter)
                        .widthIn(max = STACKED_MAX_WIDTH)
                        .fillMaxHeight()
                        .animateContentSize(),
                verticalArrangement = Arrangement.spacedBy(gap),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                WordGameHeader(
                    puzzle = puzzle,
                    levelNumber = levelNumber,
                    contextBadgeLabel = contextBadgeLabel,
                    rejectionMessage = rejectionMessage,
                )
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
                    // The note floats over the top of the board, so showing it never moves the layout.
                    Box(Modifier.heightIn(max = boardHeight)) {
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
                            modifier = Modifier.offset { IntOffset(rejectionShake.value.roundToInt(), 0) },
                        )
                        WordRejectionNote(rejectionNoteText, rejectionNoteShown, Modifier.align(rejectionNoteAlignment))
                    }
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
                            sounds.play(GameSound.TAP)
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
}

@Composable
private fun WordGameHeader(
    puzzle: WordPuzzle,
    levelNumber: Int?,
    contextBadgeLabel: String?,
    rejectionMessage: String?,
) {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        BoardTitle(
            difficultyLabel = stringResource(puzzle.id.difficulty.labelResource()),
            levelNumber = levelNumber,
            contextLabel = contextBadgeLabel,
            compact = true,
        )
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
}

/** A short dark note over the board; the header's live region already announces the same text. */
@Composable
private fun WordRejectionNote(
    text: String?,
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible && text != null,
        modifier = modifier.padding(vertical = LogicaSpacing.item).widthIn(max = REJECTION_NOTE_MAX_WIDTH),
        enter = fadeIn(tween(REJECTION_NOTE_FADE_MILLIS)),
        exit = fadeOut(tween(REJECTION_NOTE_FADE_MILLIS)),
    ) {
        Surface(
            modifier = Modifier.clearAndSetSemantics {},
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.inverseSurface,
            contentColor = MaterialTheme.colorScheme.inverseOnSurface,
            shadowElevation = REJECTION_NOTE_ELEVATION,
        ) {
            Text(
                text = text.orEmpty(),
                modifier = Modifier.padding(horizontal = LogicaSpacing.cardPadding, vertical = LogicaSpacing.boardPadding),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
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
private const val REJECTION_NOTE_MILLIS = 2_500L
private const val REJECTION_NOTE_FADE_MILLIS = 150
private val REJECTION_NOTE_ELEVATION = 4.dp
private val REJECTION_NOTE_MAX_WIDTH = 300.dp
private const val SHAKE_STEP_MILLIS = 35
private val COMPACT_SCREEN_HEIGHT = 620.dp
private val STACKED_MIN_HEIGHT = 520.dp
private val STACKED_MAX_WIDTH = 600.dp
private const val KEY_HEIGHT_RATIO = 0.068f
private val MIN_KEY_HEIGHT = 36.dp
private val MIN_LANDSCAPE_KEY_HEIGHT = 32.dp
private val MAX_KEY_HEIGHT = 48.dp
private val HEADER_HEIGHT_BUDGET = 64.dp
private val MIN_BOARD_HEIGHT = 180.dp
private val WORD_LANDSCAPE_PANEL_MAX_WIDTH = 280.dp
private val COMPACT_LANDSCAPE_HEIGHT = 360.dp
private val COMPACT_LANDSCAPE_KEY_SPACING = 2.dp
private val WIDE_LAYOUT_SPACING = 12.dp
private const val WORD_LANDSCAPE_PANEL_FRACTION = 0.52f
private const val TERMINAL_APPEAR_MILLIS = 180
private const val TERMINAL_INITIAL_SCALE = 0.98f
