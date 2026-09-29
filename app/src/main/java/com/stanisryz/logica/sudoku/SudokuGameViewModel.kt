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
import com.stanisryz.logica.economy.GameplayHints
import com.stanisryz.logica.economy.HintOffer
import com.stanisryz.logica.economy.PlayerEconomy
import com.stanisryz.logica.puzzle.core.model.PuzzleStars
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
        /** The third mistake is waiting on the one ad-paid second chance; nothing is recorded yet. */
        val continueOffered: Boolean = false,
        val canUndo: Boolean = false,
        /** A hint was requested with an empty hint stock; the screen offers to restock. */
        val hintsExhausted: Boolean = false,
    ) : SudokuGameUiState {
        val hasMeaningfulProgress: Boolean
            get() =
                // Leaving while the second chance is offered is leaving an unfinished level.
                continueOffered ||
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
    private val hints: GameplayHints = GameplayHints(economyRepository),
) : ViewModel() {
    private data class UndoFrame(
        val game: SudokuGameState,
        val selectedCell: SudokuPosition?,
    )

    private val mutableUiState = MutableStateFlow<SudokuGameUiState>(SudokuGameUiState.Loading)
    val uiState: StateFlow<SudokuGameUiState> = mutableUiState.asStateFlow()
    val economy: StateFlow<PlayerEconomy> =
        economyRepository.observe().stateIn(viewModelScope, SharingStarted.Eagerly, PlayerEconomy.LOADING)

    private var engine: SudokuGameEngine? = null
    private var attempt: GameAttempt? = null
    private var completionJob: Job? = null

    /** One second chance per attempt, used or declined. */
    private var continueUsed = false
    private val undoHistory = mutableListOf<UndoFrame>()
    private var hintJob: Job? = null

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

    fun eraseSelectedCell() {
        if (!economy.value.isGameplayAllowed) return
        val ready = mutableUiState.value as? SudokuGameUiState.Ready ?: return
        val position = ready.selectedCell ?: return
        val updated = engine?.eraseCell(ready.game, position) ?: return
        updateGame(ready, updated)
    }

    fun undo() {
        if (!economy.value.isGameplayAllowed) return
        val ready = mutableUiState.value as? SudokuGameUiState.Ready ?: return
        if (ready.game.status.isTerminal || undoHistory.isEmpty()) return
        val gameEngine = engine ?: return
        // Correct entries stay locked through undo, so a frame whose only change was a correct
        // placement restores nothing; skip such frames and undo the last reversible change instead.
        while (undoHistory.isNotEmpty()) {
            val frame = undoHistory.removeAt(undoHistory.lastIndex)
            val restored = gameEngine.restoreSnapshot(ready.game, frame.game)
            if (restored != ready.game) {
                mutableUiState.value =
                    ready.copy(
                        game = restored,
                        selectedCell = frame.selectedCell,
                        canUndo = undoHistory.isNotEmpty(),
                    )
                return
            }
        }
        mutableUiState.value = ready.copy(canUndo = false)
    }

    fun togglePencilMode() {
        if (!economy.value.isGameplayAllowed) return
        val ready = mutableUiState.value as? SudokuGameUiState.Ready ?: return
        if (ready.game.status.isTerminal) return
        mutableUiState.value = ready.copy(isPencilMode = !ready.isPencilMode)
    }

    fun requestHint() {
        if (!economy.value.isGameplayAllowed) return
        if (hintJob?.isActive == true) return
        val ready = mutableUiState.value as? SudokuGameUiState.Ready ?: return
        val updated = engine?.revealHint(ready.game, ready.selectedCell) ?: return
        if (updated == ready.game) return
        if (economy.value.hints <= 0) {
            mutableUiState.value = ready.copy(hintsExhausted = true)
            return
        }
        hintJob =
            viewModelScope.launch {
                // A hint costs one hint from the consumable stock before it is shown.
                val paid = hints.spend()
                val current = mutableUiState.value as? SudokuGameUiState.Ready ?: return@launch
                if (current.game != ready.game) return@launch
                if (!paid) {
                    mutableUiState.value = current.copy(hintsExhausted = true)
                    return@launch
                }
                updateGame(
                    current,
                    updated,
                    updated.currentHint?.position ?: current.selectedCell,
                    recordUndo = false,
                    clearUndo = true,
                )
            }
    }

    fun dismissHintsExhausted() {
        val ready = mutableUiState.value as? SudokuGameUiState.Ready ?: return
        mutableUiState.value = ready.copy(hintsExhausted = false)
    }

    /** Restocks from the exhausted-hints prompt without leaving the attempt. */
    fun buyHints(offer: HintOffer) {
        viewModelScope.launch {
            if (hints.buy(offer)) dismissHintsExhausted()
        }
    }

    /** A new attempt reuses the selected record and takes a new completion identity. */
    fun retry() {
        if (!economy.value.isGameplayAllowed) return
        val ready = mutableUiState.value as? SudokuGameUiState.Ready ?: return
        if (!ready.game.status.isTerminal || ready.completionPersistence != CompletionPersistence.Saved) return
        val gameEngine = engine ?: return
        val previous = attempt ?: return
        undoHistory.clear()
        attempt = previous.restarted(attemptFactory.nextAttemptId())
        continueUsed = false
        mutableUiState.value = SudokuGameUiState.Ready(ready.puzzle, gameEngine.start())
    }

    fun retryCompletion() {
        val ready = mutableUiState.value as? SudokuGameUiState.Ready ?: return
        if (ready.game.status.isTerminal && !ready.continueOffered) persistCompletion(ready.game)
    }

    private fun load() {
        undoHistory.clear()
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
        recordUndo: Boolean = true,
        clearUndo: Boolean = false,
    ) {
        if (updated == ready.game) return
        if (clearUndo) {
            undoHistory.clear()
        } else if (recordUndo && updated.status == SudokuGameStatus.IN_PROGRESS) {
            undoHistory += UndoFrame(ready.game, ready.selectedCell)
            if (undoHistory.size > MAX_UNDO_HISTORY) undoHistory.removeAt(0)
        }
        mutableUiState.value =
            ready.copy(
                game = updated,
                selectedCell = selectedCell,
                canUndo = undoHistory.isNotEmpty(),
            )
        if (updated.status.isTerminal) finishOrOffer(updated)
    }

    /** The third mistake first offers one ad-paid second chance; any other end is recorded at once. */
    private fun finishOrOffer(game: SudokuGameState) {
        val ready = mutableUiState.value as? SudokuGameUiState.Ready ?: return
        if (game.status == SudokuGameStatus.FAILED && !continueUsed) {
            continueUsed = true
            mutableUiState.value = ready.copy(continueOffered = true)
        } else {
            persistCompletion(game)
        }
    }

    /** The player turned the second chance down: the failure is recorded as usual. */
    fun declineContinue() {
        val ready = mutableUiState.value as? SudokuGameUiState.Ready ?: return
        if (!ready.continueOffered) return
        mutableUiState.value = ready.copy(continueOffered = false)
        persistCompletion(ready.game)
    }

    /** The rewarded ad was watched: the same board goes on with one mistake to spare. */
    fun continueAfterAd() {
        val ready = mutableUiState.value as? SudokuGameUiState.Ready ?: return
        val activeEngine = engine ?: return
        if (!ready.continueOffered) return
        mutableUiState.value = ready.copy(game = activeEngine.continueAfterFailure(ready.game), continueOffered = false)
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
                stars = if (game.status == SudokuGameStatus.SOLVED) PuzzleStars.forMistakes(game.mistakesUsed) else null,
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

private const val MAX_UNDO_HISTORY = 100

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
