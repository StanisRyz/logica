package com.stanisryz.logica.sudoku

import android.content.res.AssetManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.stanisryz.logica.catalog.CatalogLevelUnavailableException
import com.stanisryz.logica.catalog.GameAttempt
import com.stanisryz.logica.catalog.GameAttemptFactory
import com.stanisryz.logica.catalog.GameAttemptLaunch
import com.stanisryz.logica.economy.EconomyRepository
import com.stanisryz.logica.economy.PlayerEconomy
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import com.stanisryz.logica.puzzle.core.sudoku.BinarySudokuDataset
import com.stanisryz.logica.puzzle.core.sudoku.SudokuCatalogProvider
import com.stanisryz.logica.puzzle.core.sudoku.SudokuCellStatus
import com.stanisryz.logica.puzzle.core.sudoku.SudokuDatasetError
import com.stanisryz.logica.puzzle.core.sudoku.SudokuDatasetResult
import com.stanisryz.logica.puzzle.core.sudoku.SudokuGameEngine
import com.stanisryz.logica.puzzle.core.sudoku.SudokuGameState
import com.stanisryz.logica.puzzle.core.sudoku.SudokuGameStatus
import com.stanisryz.logica.puzzle.core.sudoku.SudokuPosition
import com.stanisryz.logica.puzzle.core.sudoku.SudokuPuzzle
import com.stanisryz.logica.puzzle.core.sudoku.toPlatformDifficulty
import com.stanisryz.logica.result.CompletionPersistence
import com.stanisryz.logica.result.GameCompletionRepository
import com.stanisryz.logica.result.GameOutcome
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal sealed interface SudokuGameUiState {
    data object Loading : SudokuGameUiState

    data class Ready(
        val puzzle: SudokuPuzzle,
        val game: SudokuGameState,
        val selectedCell: SudokuPosition? = null,
        val isPencilMode: Boolean = false,
        val completionPersistence: CompletionPersistence = CompletionPersistence.NotRequired,
    ) : SudokuGameUiState {
        val hasMeaningfulProgress: Boolean
            get() =
                !game.status.isTerminal &&
                    (
                        game.cells.any { cell ->
                            cell.status == SudokuCellStatus.CORRECT ||
                                cell.status == SudokuCellStatus.INCORRECT ||
                                !cell.candidates.isEmpty
                        } ||
                            game.mistakesUsed > 0 ||
                            game.hintsUsed > 0
                    )
    }

    data class Error(
        val reason: SudokuGameError,
    ) : SudokuGameUiState
}

internal enum class SudokuGameError {
    LEVEL_UNAVAILABLE,
    MISSING_DATASET,
    CORRUPT_DATASET,
    PUZZLE_NOT_FOUND,
}

/**
 * One transient Sudoku attempt. The frozen level (or Daily identity) selects exactly one Dataset V1
 * record; the player's values, candidates, mistakes, and hints live only in this ViewModel.
 */
internal class SudokuGameViewModel(
    private val launch: GameAttemptLaunch,
    private val attemptFactory: GameAttemptFactory,
    private val completionRepository: GameCompletionRepository,
    economyRepository: EconomyRepository,
    private val provider: SudokuCatalogProvider,
    private val workDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow<SudokuGameUiState>(SudokuGameUiState.Loading)
    val uiState: StateFlow<SudokuGameUiState> = mutableUiState.asStateFlow()
    val economy: StateFlow<PlayerEconomy> =
        economyRepository.observe().stateIn(viewModelScope, SharingStarted.Eagerly, PlayerEconomy())

    private var engine: SudokuGameEngine? = null
    private var attempt: GameAttempt? = null
    private var completionJob: Job? = null

    init {
        load()
    }

    fun reload() {
        if (mutableUiState.value == SudokuGameUiState.Loading) return
        load()
    }

    fun selectCell(position: SudokuPosition) {
        if (!economy.value.isGameplayAllowed) return
        val ready = mutableUiState.value as? SudokuGameUiState.Ready ?: return
        if (ready.selectedCell != position) mutableUiState.value = ready.copy(selectedCell = position)
    }

    fun inputDigit(digit: Int) {
        if (!economy.value.isGameplayAllowed) return
        val ready = mutableUiState.value as? SudokuGameUiState.Ready ?: return
        val position = ready.selectedCell ?: return
        val gameEngine = engine ?: return
        val updated =
            if (ready.isPencilMode) {
                gameEngine.toggleCandidate(ready.game, position, digit)
            } else {
                gameEngine.placeValue(ready.game, position, digit)
            }
        updateGame(ready, updated)
    }

    fun togglePencilMode() {
        if (!economy.value.isGameplayAllowed) return
        val ready = mutableUiState.value as? SudokuGameUiState.Ready ?: return
        if (ready.game.status.isTerminal) return
        mutableUiState.value = ready.copy(isPencilMode = !ready.isPencilMode)
    }

    fun requestHint() {
        if (!economy.value.isGameplayAllowed) return
        val ready = mutableUiState.value as? SudokuGameUiState.Ready ?: return
        val updated = engine?.requestHint(ready.game) ?: return
        updateGame(ready, updated, updated.currentHint?.position ?: ready.selectedCell)
    }

    /** A new attempt reuses the selected record and takes a new completion identity. */
    fun retry() {
        if (!economy.value.isGameplayAllowed) return
        val ready = mutableUiState.value as? SudokuGameUiState.Ready ?: return
        if (!ready.game.status.isTerminal || ready.completionPersistence != CompletionPersistence.Saved) return
        val gameEngine = engine ?: return
        val previous = attempt ?: return
        attempt = previous.restarted(attemptFactory.nextAttemptId())
        mutableUiState.value = SudokuGameUiState.Ready(ready.puzzle, gameEngine.start())
    }

    fun retryCompletion() {
        val ready = mutableUiState.value as? SudokuGameUiState.Ready ?: return
        if (ready.game.status.isTerminal) persistCompletion(ready.game)
    }

    private fun load() {
        mutableUiState.value = SudokuGameUiState.Loading
        viewModelScope.launch {
            try {
                val resolved = attemptFactory.create(launch, PuzzleType.SUDOKU)
                val loaded =
                    withContext(workDispatcher) {
                        val puzzle =
                            provider
                                .select(resolved.difficulty, resolved.seed, resolved.generatorVersion)
                                .requirePuzzle()
                        require(puzzle.id.difficulty.toPlatformDifficulty() == resolved.difficulty)
                        val gameEngine = SudokuGameEngine(puzzle)
                        Triple(puzzle, gameEngine, gameEngine.start())
                    }
                engine = loaded.second
                attempt = resolved
                mutableUiState.value = SudokuGameUiState.Ready(loaded.first, loaded.third)
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: CatalogLevelUnavailableException) {
                mutableUiState.value = SudokuGameUiState.Error(SudokuGameError.LEVEL_UNAVAILABLE)
            } catch (error: LoadFailure) {
                mutableUiState.value = SudokuGameUiState.Error(error.reason)
            } catch (_: Exception) {
                mutableUiState.value = SudokuGameUiState.Error(SudokuGameError.CORRUPT_DATASET)
            }
        }
    }

    private fun updateGame(
        ready: SudokuGameUiState.Ready,
        updated: SudokuGameState,
        selectedCell: SudokuPosition? = ready.selectedCell,
    ) {
        if (updated == ready.game) return
        mutableUiState.value = ready.copy(game = updated, selectedCell = selectedCell)
        if (updated.status.isTerminal) persistCompletion(updated)
    }

    private fun persistCompletion(game: SudokuGameState) {
        if (completionJob?.isActive == true) return
        val current = attempt ?: return
        val ready = mutableUiState.value as? SudokuGameUiState.Ready ?: return
        if (ready.completionPersistence == CompletionPersistence.Saved) return
        mutableUiState.value = ready.copy(completionPersistence = CompletionPersistence.Saving)
        val completion =
            current.completion(
                outcome = if (game.status == SudokuGameStatus.SOLVED) GameOutcome.SOLVED else GameOutcome.FAILED,
                hintsUsed = game.hintsUsed,
            )
        completionJob =
            viewModelScope.launch {
                try {
                    completionRepository.complete(completion)
                    updateCompletionPersistence(game, CompletionPersistence.Saved)
                } catch (exception: CancellationException) {
                    throw exception
                } catch (_: Exception) {
                    updateCompletionPersistence(game, CompletionPersistence.Error)
                }
            }
    }

    private fun updateCompletionPersistence(
        game: SudokuGameState,
        persistence: CompletionPersistence,
    ) {
        val current = mutableUiState.value
        if (current is SudokuGameUiState.Ready && current.game == game) {
            mutableUiState.value = current.copy(completionPersistence = persistence)
        }
    }

    private fun SudokuDatasetResult<SudokuPuzzle>.requirePuzzle(): SudokuPuzzle =
        when (this) {
            is SudokuDatasetResult.Success -> value
            is SudokuDatasetResult.Failure -> throw LoadFailure(error.toGameError())
        }

    private class LoadFailure(
        val reason: SudokuGameError,
    ) : Exception()
}

private fun SudokuDatasetError.toGameError(): SudokuGameError =
    when (this) {
        SudokuDatasetError.MISSING_ASSET, SudokuDatasetError.EMPTY_BUCKET -> SudokuGameError.MISSING_DATASET
        SudokuDatasetError.CORRUPT_ASSET -> SudokuGameError.CORRUPT_DATASET
        SudokuDatasetError.PUZZLE_NOT_FOUND -> SudokuGameError.PUZZLE_NOT_FOUND
    }

internal class SudokuGameViewModelFactory(
    private val launch: GameAttemptLaunch,
    private val assetManager: AssetManager,
    private val attemptFactory: GameAttemptFactory,
    private val completionRepository: GameCompletionRepository,
    private val economyRepository: EconomyRepository,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(SudokuGameViewModel::class.java)) {
            "Unknown ViewModel class: ${modelClass.name}"
        }
        val provider = SudokuCatalogProvider(BinarySudokuDataset(AndroidSudokuDatasetSource(assetManager)))
        @Suppress("UNCHECKED_CAST")
        return SudokuGameViewModel(
            launch,
            attemptFactory,
            completionRepository,
            economyRepository,
            provider,
        ) as T
    }
}
