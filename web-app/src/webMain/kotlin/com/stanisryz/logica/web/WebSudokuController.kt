package com.stanisryz.logica.web

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.stanisryz.logica.puzzle.core.catalog.BinaryCatalogLevelPack
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelDefinition
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelId
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelNumber
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelPack
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelPackResult
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelPackVersion
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import com.stanisryz.logica.puzzle.core.sudoku.BinarySudokuDataset
import com.stanisryz.logica.puzzle.core.sudoku.SudokuCatalogProvider
import com.stanisryz.logica.puzzle.core.sudoku.SudokuCellStatus
import com.stanisryz.logica.puzzle.core.sudoku.SudokuDataset
import com.stanisryz.logica.puzzle.core.sudoku.SudokuDatasetResult
import com.stanisryz.logica.puzzle.core.sudoku.SudokuDatasetVersion
import com.stanisryz.logica.puzzle.core.sudoku.SudokuDifficulty
import com.stanisryz.logica.puzzle.core.sudoku.SudokuGameEngine
import com.stanisryz.logica.puzzle.core.sudoku.SudokuGameState
import com.stanisryz.logica.puzzle.core.sudoku.SudokuGameStatus
import com.stanisryz.logica.puzzle.core.sudoku.SudokuPosition
import com.stanisryz.logica.puzzle.core.sudoku.SudokuPuzzle
import com.stanisryz.logica.puzzle.core.sudoku.toSudokuDifficulty
import com.stanisryz.logica.puzzle.core.web.WebPuzzleData
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

internal sealed interface WebSudokuState {
    data object DifficultySelection : WebSudokuState

    data class Loading(
        val difficulty: Difficulty,
        val levelNumber: CatalogLevelNumber? = null,
    ) : WebSudokuState

    data class Playing(
        val attempt: WebCatalogAttempt,
        val definition: CatalogLevelDefinition,
        val puzzle: SudokuPuzzle,
        val game: SudokuGameState,
        val selectedCell: SudokuPosition? = null,
        val isPencilMode: Boolean = false,
    ) : WebSudokuState

    data class Error(
        val difficulty: Difficulty,
        val levelNumber: CatalogLevelNumber?,
        val detail: String,
        val progressionUnavailable: Boolean = false,
    ) : WebSudokuState
}

/** Lightweight Web adapter over authoritative frozen Sudoku Catalog levels and Dataset V1. */
internal class WebSudokuController(
    private val loadPack: suspend (Difficulty) -> Unit,
    private val loadDataset: suspend (SudokuDatasetVersion, SudokuDifficulty) -> Unit,
    private val progression: WebCatalogProgressAccess,
    private val levelPack: CatalogLevelPack = BinaryCatalogLevelPack(WebPuzzleData),
    dataset: SudokuDataset = BinarySudokuDataset(WebPuzzleData),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private val provider = SudokuCatalogProvider(dataset)
    private var operation: Job? = null
    private var engine: SudokuGameEngine? = null
    private val completion = WebCatalogCompletionController(progression)

    var state by mutableStateOf<WebSudokuState>(WebSudokuState.DifficultySelection)
        private set

    val completionState: WebCatalogCompletionState
        get() = completion.state

    fun selectDifficulty(difficulty: Difficulty) {
        operation?.cancel()
        state = WebSudokuState.Loading(difficulty)
        operation =
            scope.launch {
                var levelNumber: CatalogLevelNumber? = null
                try {
                    val attempt =
                        when (
                            val resolved =
                                progression.resolveCurrentLevel(
                                    PuzzleType.SUDOKU,
                                    difficulty,
                                    CatalogLevelPackVersion.V1,
                                )
                        ) {
                            is WebCatalogLevelResolution.Resolved -> resolved.attempt
                            is WebCatalogLevelResolution.Unavailable -> {
                                state = WebSudokuState.Error(difficulty, null, resolved.detail, progressionUnavailable = true)
                                return@launch
                            }
                        }
                    levelNumber = attempt.levelId.levelNumber
                    state = WebSudokuState.Loading(difficulty, levelNumber)
                    loadPack(difficulty)
                    if (!progression.isCurrent(attempt)) return@launch
                    val definition = resolveLevel(attempt.levelId)
                    require(definition.generatorVersion == provider.version) {
                        "Sudoku level ${attempt.levelId.levelNumber.value} requires provider ${definition.generatorVersion.value}."
                    }
                    loadDataset(SudokuDatasetVersion.V1, difficulty.toSudokuDifficulty())
                    if (!progression.isCurrent(attempt)) return@launch
                    val puzzle = provider.select(difficulty, definition.seed, definition.generatorVersion).requirePuzzle()
                    val nextEngine = SudokuGameEngine(puzzle)
                    engine = nextEngine
                    completion.startAttempt(attempt)
                    state = WebSudokuState.Playing(attempt, definition, puzzle, nextEngine.start())
                } catch (exception: CancellationException) {
                    throw exception
                } catch (exception: Exception) {
                    engine = null
                    completion.reset()
                    state = WebSudokuState.Error(difficulty, levelNumber, exception.message ?: "Sudoku level is unavailable.")
                }
            }
    }

    fun retryLoading() {
        val error = state as? WebSudokuState.Error ?: return
        if (error.progressionUnavailable) progression.retryContextBinding()
        selectDifficulty(error.difficulty)
    }

    fun selectCell(position: SudokuPosition) {
        val playing = state as? WebSudokuState.Playing ?: return
        if (playing.game.status.isTerminal || playing.selectedCell == position) return
        state = playing.copy(selectedCell = position)
    }

    fun inputDigit(digit: Int) {
        val playing = state as? WebSudokuState.Playing ?: return
        if (playing.game.status.isTerminal) return
        val position = playing.selectedCell ?: return
        val activeEngine = engine ?: return
        val cell = playing.game.cellAt(position)
        val updated =
            if (playing.isPencilMode) {
                if (cell.status != SudokuCellStatus.EMPTY) return
                activeEngine.toggleCandidate(playing.game, position, digit)
            } else {
                if (cell.status != SudokuCellStatus.EMPTY && cell.status != SudokuCellStatus.INCORRECT) return
                activeEngine.placeValue(playing.game, position, digit)
            }
        if (updated != playing.game) updateGame(playing, updated)
    }

    fun togglePencilMode() {
        val playing = state as? WebSudokuState.Playing ?: return
        if (playing.game.status.isTerminal) return
        state = playing.copy(isPencilMode = !playing.isPencilMode)
    }

    fun requestHint() {
        val playing = state as? WebSudokuState.Playing ?: return
        if (playing.game.status.isTerminal) return
        val updated = engine?.requestHint(playing.game) ?: return
        if (updated != playing.game) {
            updateGame(playing, updated, updated.currentHint?.position ?: playing.selectedCell)
        }
    }

    fun retry() {
        val playing = state as? WebSudokuState.Playing ?: return
        if (playing.game.status != SudokuGameStatus.FAILED) return
        val activeEngine = engine ?: return
        operation?.cancel()
        completion.startAttempt(playing.attempt)
        state = playing.copy(game = activeEngine.start(), selectedCell = null, isPencilMode = false)
    }

    fun nextLevel() {
        val playing = state as? WebSudokuState.Playing ?: return
        if (completion.state !is WebCatalogCompletionState.Saved) return
        selectDifficulty(playing.attempt.levelId.difficulty)
    }

    fun retrySave() {
        val playing = state as? WebSudokuState.Playing ?: return
        if (playing.game.status != SudokuGameStatus.SOLVED) return
        completion.saveSolved(playing.attempt)
    }

    fun showDifficultySelector() {
        operation?.cancel()
        engine = null
        completion.reset()
        state = WebSudokuState.DifficultySelection
    }

    fun dispose() {
        scope.cancel()
    }

    private fun updateGame(
        playing: WebSudokuState.Playing,
        updated: SudokuGameState,
        selectedCell: SudokuPosition? = playing.selectedCell,
    ) {
        state = playing.copy(game = updated, selectedCell = selectedCell)
        if (!playing.game.status.isTerminal && updated.status == SudokuGameStatus.SOLVED) {
            completion.saveSolved(playing.attempt)
        }
    }

    private fun resolveLevel(levelId: CatalogLevelId): CatalogLevelDefinition =
        when (val resolved = levelPack.resolve(levelId)) {
            is CatalogLevelPackResult.Success -> resolved.value
            is CatalogLevelPackResult.Failure -> error(resolved.detail)
        }

    private fun SudokuDatasetResult<SudokuPuzzle>.requirePuzzle(): SudokuPuzzle =
        when (this) {
            is SudokuDatasetResult.Success -> value
            is SudokuDatasetResult.Failure -> error(detail)
        }

    companion object {
        fun create(
            loader: BrowserPuzzleDataLoader,
            progression: WebCatalogProgressAccess,
        ): WebSudokuController =
            WebSudokuController(
                loadPack = { difficulty ->
                    loader.loadCatalogLevelPack(
                        packVersion = CatalogLevelPackVersion.V1,
                        puzzleType = PuzzleType.SUDOKU,
                        difficulty = difficulty,
                    )
                },
                loadDataset = loader::loadSudokuDataset,
                progression = progression,
            )
    }
}
