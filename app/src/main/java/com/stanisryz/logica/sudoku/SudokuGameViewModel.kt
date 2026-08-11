package com.stanisryz.logica.sudoku

import android.content.res.AssetManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.stanisryz.logica.puzzle.core.sudoku.BinarySudokuDataset
import com.stanisryz.logica.puzzle.core.sudoku.EncodedSudokuSession
import com.stanisryz.logica.puzzle.core.sudoku.SudokuDatasetError
import com.stanisryz.logica.puzzle.core.sudoku.SudokuDatasetResult
import com.stanisryz.logica.puzzle.core.sudoku.SudokuDatasetVersion
import com.stanisryz.logica.puzzle.core.sudoku.SudokuDifficulty
import com.stanisryz.logica.puzzle.core.sudoku.SudokuGameEngine
import com.stanisryz.logica.puzzle.core.sudoku.SudokuGameState
import com.stanisryz.logica.puzzle.core.sudoku.SudokuPosition
import com.stanisryz.logica.puzzle.core.sudoku.SudokuPuzzle
import com.stanisryz.logica.puzzle.core.sudoku.SudokuPuzzleId
import com.stanisryz.logica.puzzle.core.sudoku.SudokuSessionCodecV1
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal sealed interface SudokuGameLaunch {
    data class Puzzle(
        val id: SudokuPuzzleId,
    ) : SudokuGameLaunch

    data class Select(
        val version: SudokuDatasetVersion,
        val difficulty: SudokuDifficulty,
        val selector: Long,
    ) : SudokuGameLaunch

    data class Restore(
        val session: EncodedSudokuSession,
    ) : SudokuGameLaunch
}

internal sealed interface SudokuGameUiState {
    data object Loading : SudokuGameUiState

    data class Ready(
        val puzzle: SudokuPuzzle,
        val game: SudokuGameState,
        val selectedCell: SudokuPosition? = null,
        val isPencilMode: Boolean = false,
    ) : SudokuGameUiState

    data class Error(
        val reason: SudokuGameError,
    ) : SudokuGameUiState
}

internal enum class SudokuGameError {
    MISSING_DATASET,
    CORRUPT_DATASET,
    PUZZLE_NOT_FOUND,
    INVALID_SESSION,
}

internal class SudokuGameViewModel(
    private val launch: SudokuGameLaunch,
    assetManager: AssetManager,
) : ViewModel() {
    private val dataset = BinarySudokuDataset(AndroidSudokuDatasetSource(assetManager))
    private val mutableUiState = MutableStateFlow<SudokuGameUiState>(SudokuGameUiState.Loading)
    val uiState: StateFlow<SudokuGameUiState> = mutableUiState.asStateFlow()
    private var engine: SudokuGameEngine? = null

    init {
        load()
    }

    fun reload() {
        if (mutableUiState.value == SudokuGameUiState.Loading) return
        load()
    }

    private fun load() {
        mutableUiState.value = SudokuGameUiState.Loading
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val puzzleResult =
                    when (val requested = launch) {
                        is SudokuGameLaunch.Puzzle -> dataset.getPuzzle(requested.id)
                        is SudokuGameLaunch.Select ->
                            dataset.selectPuzzle(requested.version, requested.difficulty, requested.selector)
                        is SudokuGameLaunch.Restore -> dataset.getPuzzle(SudokuSessionCodecV1.puzzleId(requested.session))
                    }
                when (puzzleResult) {
                    is SudokuDatasetResult.Failure -> {
                        mutableUiState.value = SudokuGameUiState.Error(puzzleResult.error.toGameError())
                    }
                    is SudokuDatasetResult.Success -> openPuzzle(puzzleResult.value)
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Exception) {
                mutableUiState.value = SudokuGameUiState.Error(SudokuGameError.INVALID_SESSION)
            }
        }
    }

    fun selectCell(position: SudokuPosition) {
        val ready = mutableUiState.value as? SudokuGameUiState.Ready ?: return
        if (ready.selectedCell == position) return
        mutableUiState.value = ready.copy(selectedCell = position)
    }

    fun inputDigit(digit: Int) {
        val ready = mutableUiState.value as? SudokuGameUiState.Ready ?: return
        val position = ready.selectedCell ?: return
        val gameEngine = engine ?: return
        val updated =
            if (ready.isPencilMode) {
                gameEngine.toggleCandidate(ready.game, position, digit)
            } else {
                gameEngine.placeValue(ready.game, position, digit)
            }
        if (updated != ready.game) mutableUiState.value = ready.copy(game = updated)
    }

    fun togglePencilMode() {
        val ready = mutableUiState.value as? SudokuGameUiState.Ready ?: return
        if (ready.game.status.isTerminal) return
        mutableUiState.value = ready.copy(isPencilMode = !ready.isPencilMode)
    }

    fun requestHint() {
        val ready = mutableUiState.value as? SudokuGameUiState.Ready ?: return
        val updated = engine?.requestHint(ready.game) ?: return
        if (updated != ready.game) {
            mutableUiState.value =
                ready.copy(
                    game = updated,
                    selectedCell = updated.currentHint?.position ?: ready.selectedCell,
                )
        }
    }

    fun retry() {
        val ready = mutableUiState.value as? SudokuGameUiState.Ready ?: return
        val updated = engine?.retry(ready.game) ?: return
        if (updated != ready.game) {
            mutableUiState.value = ready.copy(game = updated, selectedCell = null, isPencilMode = false)
        }
    }

    fun encodeSession(): EncodedSudokuSession? =
        (mutableUiState.value as? SudokuGameUiState.Ready)?.let { SudokuSessionCodecV1.encode(it.game) }

    private fun openPuzzle(puzzle: SudokuPuzzle) {
        val gameEngine = SudokuGameEngine(puzzle)
        val game =
            when (val requested = launch) {
                is SudokuGameLaunch.Restore -> SudokuSessionCodecV1.decode(puzzle, requested.session, gameEngine)
                is SudokuGameLaunch.Puzzle, is SudokuGameLaunch.Select -> gameEngine.start()
            }
        engine = gameEngine
        mutableUiState.value = SudokuGameUiState.Ready(puzzle, game)
    }

    private fun SudokuDatasetError.toGameError(): SudokuGameError =
        when (this) {
            SudokuDatasetError.MISSING_ASSET, SudokuDatasetError.EMPTY_BUCKET -> SudokuGameError.MISSING_DATASET
            SudokuDatasetError.CORRUPT_ASSET -> SudokuGameError.CORRUPT_DATASET
            SudokuDatasetError.PUZZLE_NOT_FOUND -> SudokuGameError.PUZZLE_NOT_FOUND
        }
}

internal class SudokuGameViewModelFactory(
    private val launch: SudokuGameLaunch,
    private val assetManager: AssetManager,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(SudokuGameViewModel::class.java)) {
            "Unknown ViewModel class: ${modelClass.name}"
        }
        @Suppress("UNCHECKED_CAST")
        return SudokuGameViewModel(launch, assetManager) as T
    }
}
