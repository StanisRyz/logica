package com.stanisryz.logica.ui.sudoku

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.sudoku.SudokuCellStatus
import com.stanisryz.logica.puzzle.core.sudoku.SudokuGameState
import com.stanisryz.logica.puzzle.core.sudoku.SudokuGameStatus
import com.stanisryz.logica.puzzle.core.sudoku.SudokuPosition
import com.stanisryz.logica.puzzle.core.sudoku.SudokuPuzzle
import com.stanisryz.logica.puzzle.core.sudoku.toPlatformDifficulty
import com.stanisryz.logica.shared.ui.generated.resources.Res
import com.stanisryz.logica.shared.ui.generated.resources.difficulty_easy
import com.stanisryz.logica.shared.ui.generated.resources.difficulty_expert
import com.stanisryz.logica.shared.ui.generated.resources.difficulty_hard
import com.stanisryz.logica.shared.ui.generated.resources.difficulty_medium
import com.stanisryz.logica.ui.components.BoardInfoHeader
import com.stanisryz.logica.ui.components.CellGameSounds
import com.stanisryz.logica.ui.components.CenteredBoardLayout
import com.stanisryz.logica.ui.components.GameSound
import com.stanisryz.logica.ui.components.LocalGameSounds
import com.stanisryz.logica.ui.components.LocalRoomyGameplayControls
import com.stanisryz.logica.ui.components.isRoomyPortrait
import com.stanisryz.logica.ui.components.isWideGameplayLayout
import com.stanisryz.logica.ui.theme.LogicaSpacing
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** Pure one-screen Sudoku presentation shared by Android and Web hosts. */
@Composable
fun SudokuGameContent(
    puzzle: SudokuPuzzle,
    game: SudokuGameState,
    selectedCell: SudokuPosition?,
    isPencilMode: Boolean,
    levelNumber: Int?,
    gameplayEnabled: Boolean,
    inputEnabled: Boolean,
    canUndo: Boolean,
    onCellSelected: (SudokuPosition) -> Unit,
    onDigit: (Int) -> Unit,
    onTogglePencil: () -> Unit,
    onErase: () -> Unit,
    onUndo: () -> Unit,
    onHint: () -> Unit,
    contextBadgeLabel: String? = null,
    modifier: Modifier = Modifier,
    hintCount: Int? = null,
    hostStatusContent: @Composable ColumnScope.() -> Unit = {},
) {
    CellGameSounds(
        correctCells = game.cells.count { it.status == SudokuCellStatus.CORRECT },
        mistakesUsed = game.mistakesUsed,
        hintsUsed = game.hintsUsed,
        solved = game.status == SudokuGameStatus.SOLVED,
        failed = game.status == SudokuGameStatus.FAILED,
    )
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val compact = maxHeight < COMPACT_HEIGHT_THRESHOLD
        val wideLayout = isWideGameplayLayout(maxWidth, maxHeight)
        val verticalPadding = if (compact) COMPACT_VERTICAL_PADDING else NORMAL_VERTICAL_PADDING
        val sectionSpacing = if (compact) COMPACT_SECTION_SPACING else NORMAL_SECTION_SPACING
        val panelWidth = if (compact) COMPACT_WIDE_PANEL_WIDTH else WIDE_PANEL_WIDTH
        val selectedState = selectedCell?.let(game::cellAt)
        val eraseEnabled =
            gameplayEnabled &&
                selectedState != null &&
                (selectedState.status == SudokuCellStatus.INCORRECT || !selectedState.candidates.isEmpty)
        val undoEnabled = canUndo && gameplayEnabled
        // The keypad stays live for the whole attempt; a digit simply does nothing without a cell to
        // fill, instead of the whole pad greying out after every hint or tap on a clue.
        val keypadEnabled = gameplayEnabled && game.status == SudokuGameStatus.IN_PROGRESS
        val sounds = LocalGameSounds.current
        val guardedDigit: (Int) -> Unit = { digit ->
            if (inputEnabled) {
                onDigit(digit)
                // A pencil note has no verdict to sound, so it just taps.
                if (isPencilMode) sounds.play(GameSound.TAP)
            }
        }
        val confirmedCounts = IntArray(DIGIT_SLOTS)
        game.cells.forEach { cell ->
            if (cell.status == SudokuCellStatus.GIVEN || cell.status == SudokuCellStatus.CORRECT) confirmedCounts[cell.value]++
        }
        val remaining: (Int) -> Int = { digit -> (DIGIT_SLOTS - 1 - confirmedCounts[digit]).coerceAtLeast(0) }
        val difficultyLabel =
            stringResource(
                puzzle.id.difficulty
                    .toPlatformDifficulty()
                    .labelResource(),
            )

        if (wideLayout) {
            Row(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = LogicaSpacing.gameplayHorizontal, vertical = verticalPadding)
                        .animateContentSize(),
                horizontalArrangement = Arrangement.spacedBy(WIDE_SECTION_SPACING),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    contentAlignment = Alignment.Center,
                ) {
                    SudokuBoard(
                        game = game,
                        selectedCell = selectedCell,
                        enabled = gameplayEnabled,
                        onCellSelected = onCellSelected,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Column(
                    modifier = Modifier.width(panelWidth).fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(sectionSpacing),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    BoardInfoHeader(
                        difficultyLabel = difficultyLabel,
                        mistakesUsed = game.mistakesUsed,
                        maxMistakes = SudokuGameState.MAX_MISTAKES,
                        levelNumber = levelNumber,
                        contextLabel = contextBadgeLabel,
                        solvedCells = game.cells.count { it.status == SudokuCellStatus.CORRECT },
                        totalCells = game.cells.count { it.status != SudokuCellStatus.GIVEN },
                        showTitle = false,
                    )
                    hostStatusContent()
                    Spacer(Modifier.weight(1f))
                    SudokuToolBar(
                        isPencilMode = isPencilMode,
                        onToggle = onTogglePencil,
                        onErase = onErase,
                        eraseEnabled = eraseEnabled,
                        canUndo = undoEnabled,
                        onUndo = onUndo,
                        onHint = onHint,
                        hintEnabled = game.status == SudokuGameStatus.IN_PROGRESS && gameplayEnabled,
                        enabled = gameplayEnabled,
                        wrapTools = wideLayout && !compact,
                        hintCount = hintCount,
                    )
                    SudokuNumberPad(
                        enabled = keypadEnabled,
                        onDigit = guardedDigit,
                        remaining = remaining,
                        isPencilMode = isPencilMode,
                        columns = WIDE_DIGIT_COLUMNS,
                    )
                }
            }
        } else {
            val roomy = isRoomyPortrait(maxWidth, maxHeight)
            CompositionLocalProvider(LocalRoomyGameplayControls provides roomy) {
                CenteredBoardLayout(
                    spacing = sectionSpacing,
                    anchorControlsToBottom = roomy,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(horizontal = PORTRAIT_HORIZONTAL_PADDING, vertical = verticalPadding),
                    header = {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(sectionSpacing),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            BoardInfoHeader(
                                difficultyLabel = difficultyLabel,
                                mistakesUsed = game.mistakesUsed,
                                maxMistakes = SudokuGameState.MAX_MISTAKES,
                                levelNumber = levelNumber,
                                contextLabel = contextBadgeLabel,
                                solvedCells = game.cells.count { it.status == SudokuCellStatus.CORRECT },
                                totalCells = game.cells.count { it.status != SudokuCellStatus.GIVEN },
                                showTitle = false,
                            )
                            hostStatusContent()
                        }
                    },
                    board = {
                        SudokuBoard(
                            game = game,
                            selectedCell = selectedCell,
                            enabled = gameplayEnabled,
                            onCellSelected = onCellSelected,
                            modifier = Modifier.fillMaxSize(),
                        )
                    },
                    controls = {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(sectionSpacing),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            SudokuToolBar(
                                isPencilMode = isPencilMode,
                                onToggle = onTogglePencil,
                                onErase = onErase,
                                eraseEnabled = eraseEnabled,
                                canUndo = undoEnabled,
                                onUndo = onUndo,
                                onHint = onHint,
                                hintEnabled = game.status == SudokuGameStatus.IN_PROGRESS && gameplayEnabled,
                                enabled = gameplayEnabled,
                                hintCount = hintCount,
                            )
                            SudokuNumberPad(
                                enabled = keypadEnabled,
                                onDigit = guardedDigit,
                                remaining = remaining,
                                isPencilMode = isPencilMode,
                            )
                        }
                    },
                )
            }
        }
    }
}

private fun Difficulty.labelResource(): StringResource =
    when (this) {
        Difficulty.EASY -> Res.string.difficulty_easy
        Difficulty.MEDIUM -> Res.string.difficulty_medium
        Difficulty.HARD -> Res.string.difficulty_hard
        Difficulty.EXPERT -> Res.string.difficulty_expert
    }

private val COMPACT_HEIGHT_THRESHOLD = 700.dp
private val COMPACT_VERTICAL_PADDING = 6.dp
private val NORMAL_VERTICAL_PADDING = 10.dp
private val COMPACT_SECTION_SPACING = 6.dp
private val NORMAL_SECTION_SPACING = 14.dp
private val WIDE_SECTION_SPACING = 8.dp
private val PORTRAIT_HORIZONTAL_PADDING = 6.dp
private const val WIDE_DIGIT_COLUMNS = 3
private val WIDE_PANEL_WIDTH = 224.dp
private val COMPACT_WIDE_PANEL_WIDTH = 256.dp

/** Nine places per digit; index 0 is the empty value and stays unused. */
private const val DIGIT_SLOTS = 10
