package com.stanisryz.logica.web

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.stanisryz.logica.puzzle.core.catalog.BinaryCatalogLevelPack
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelDefinition
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelId
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelPack
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelPackResult
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelPackVersion
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelPacks
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
    ) : WebSudokuState

    data class Playing(
        val definition: CatalogLevelDefinition,
        val puzzle: SudokuPuzzle,
        val game: SudokuGameState,
        val selectedCell: SudokuPosition? = null,
        val isPencilMode: Boolean = false,
    ) : WebSudokuState

    data class Error(
        val difficulty: Difficulty,
        val detail: String,
    ) : WebSudokuState
}

/** Lightweight Web adapter over frozen Sudoku Level 1, Dataset V1, and the common engine. */
internal class WebSudokuController(
    private val loadPack: suspend (Difficulty) -> Unit,
    private val loadDataset: suspend (SudokuDatasetVersion, SudokuDifficulty) -> Unit,
    private val levelPack: CatalogLevelPack = BinaryCatalogLevelPack(WebPuzzleData),
    dataset: SudokuDataset = BinarySudokuDataset(WebPuzzleData),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private val provider = SudokuCatalogProvider(dataset)
    private var operation: Job? = null
    private var engine: SudokuGameEngine? = null

    var state by mutableStateOf<WebSudokuState>(WebSudokuState.DifficultySelection)
        private set

    fun selectDifficulty(difficulty: Difficulty) {
        operation?.cancel()
        state = WebSudokuState.Loading(difficulty)
        operation =
            scope.launch {
                try {
                    loadPack(difficulty)
                    val definition = resolveLevelOne(difficulty)
                    require(definition.generatorVersion == provider.version) {
                        "Sudoku Level 1 requires provider ${definition.generatorVersion.value}."
                    }
                    loadDataset(SudokuDatasetVersion.V1, difficulty.toSudokuDifficulty())
                    val puzzle = provider.select(difficulty, definition.seed, definition.generatorVersion).requirePuzzle()
                    val nextEngine = SudokuGameEngine(puzzle)
                    engine = nextEngine
                    state = WebSudokuState.Playing(definition, puzzle, nextEngine.start())
                } catch (exception: CancellationException) {
                    throw exception
                } catch (exception: Exception) {
                    engine = null
                    state = WebSudokuState.Error(difficulty, exception.message ?: "Sudoku Level 1 is unavailable.")
                }
            }
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
        if (updated != playing.game) state = playing.copy(game = updated)
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
            state = playing.copy(game = updated, selectedCell = updated.currentHint?.position ?: playing.selectedCell)
        }
    }

    fun retry() {
        val playing = state as? WebSudokuState.Playing ?: return
        if (!playing.game.status.isTerminal) return
        val activeEngine = engine ?: return
        operation?.cancel()
        state = playing.copy(game = activeEngine.start(), selectedCell = null, isPencilMode = false)
    }

    fun showDifficultySelector() {
        operation?.cancel()
        engine = null
        state = WebSudokuState.DifficultySelection
    }

    fun dispose() {
        scope.cancel()
    }

    private fun resolveLevelOne(difficulty: Difficulty): CatalogLevelDefinition {
        val levelId =
            CatalogLevelId(
                puzzleType = PuzzleType.SUDOKU,
                difficulty = difficulty,
                levelNumber = CatalogLevelPacks.FIRST_LEVEL,
                packVersion = CatalogLevelPackVersion.V1,
            )
        return when (val resolved = levelPack.resolve(levelId)) {
            is CatalogLevelPackResult.Success -> resolved.value
            is CatalogLevelPackResult.Failure -> error(resolved.detail)
        }
    }

    private fun SudokuDatasetResult<SudokuPuzzle>.requirePuzzle(): SudokuPuzzle =
        when (this) {
            is SudokuDatasetResult.Success -> value
            is SudokuDatasetResult.Failure -> error(detail)
        }

    companion object {
        fun create(loader: BrowserPuzzleDataLoader): WebSudokuController =
            WebSudokuController(
                loadPack = { difficulty ->
                    loader.loadCatalogLevelPack(
                        packVersion = CatalogLevelPackVersion.V1,
                        puzzleType = PuzzleType.SUDOKU,
                        difficulty = difficulty,
                    )
                },
                loadDataset = loader::loadSudokuDataset,
            )
    }
}
