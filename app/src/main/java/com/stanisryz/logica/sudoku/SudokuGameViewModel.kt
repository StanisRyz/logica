package com.stanisryz.logica.sudoku

import android.content.res.AssetManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.stanisryz.logica.economy.EconomyRepository
import com.stanisryz.logica.economy.PlayerEconomy
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.GeneratorVersion
import com.stanisryz.logica.puzzle.core.model.PuzzleSeed
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import com.stanisryz.logica.puzzle.core.sudoku.BinarySudokuDataset
import com.stanisryz.logica.puzzle.core.sudoku.EncodedSudokuSession
import com.stanisryz.logica.puzzle.core.sudoku.SudokuDatasetError
import com.stanisryz.logica.puzzle.core.sudoku.SudokuDatasetResult
import com.stanisryz.logica.puzzle.core.sudoku.SudokuGameEngine
import com.stanisryz.logica.puzzle.core.sudoku.SudokuGameState
import com.stanisryz.logica.puzzle.core.sudoku.SudokuGameStatus
import com.stanisryz.logica.puzzle.core.sudoku.SudokuPosition
import com.stanisryz.logica.puzzle.core.sudoku.SudokuPuzzle
import com.stanisryz.logica.puzzle.core.sudoku.SudokuPuzzleId
import com.stanisryz.logica.puzzle.core.sudoku.SudokuSessionCodecV1
import com.stanisryz.logica.result.CompletionPersistence
import com.stanisryz.logica.result.GameCompletion
import com.stanisryz.logica.result.GameCompletionRepository
import com.stanisryz.logica.result.GameOutcome
import com.stanisryz.logica.session.GameSessionRepository
import com.stanisryz.logica.session.GameSessionScope
import com.stanisryz.logica.session.SavedGameSession
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
import java.util.UUID

internal sealed interface SudokuGameLaunch {
    data class New(
        val difficulty: Difficulty,
        val selectorSeed: PuzzleSeed,
        val providerVersion: GeneratorVersion = SudokuCatalogProvider.VERSION,
    ) : SudokuGameLaunch

    data class Restore(
        /** Optional exact identity supplied by a caller that already knows the selected record. */
        val expectedPuzzleId: SudokuPuzzleId? = null,
    ) : SudokuGameLaunch
}

internal sealed interface SudokuGameUiState {
    data object Loading : SudokuGameUiState

    data class Ready(
        val puzzle: SudokuPuzzle,
        val game: SudokuGameState,
        val selectedCell: SudokuPosition? = null,
        val isPencilMode: Boolean = false,
        val completionPersistence: CompletionPersistence = CompletionPersistence.NotRequired,
    ) : SudokuGameUiState

    data class Error(
        val reason: SudokuGameError,
    ) : SudokuGameUiState
}

internal enum class SudokuGameError {
    MISSING_SAVED_SESSION,
    INVALID_SAVED_SESSION,
    MISSING_DATASET,
    CORRUPT_DATASET,
    PUZZLE_NOT_FOUND,
}

/** The one production Sudoku implementation: dataset selection plus generic platform lifecycle. */
internal class SudokuGameViewModel(
    private val launch: SudokuGameLaunch,
    private val sessionRepository: GameSessionRepository,
    private val completionRepository: GameCompletionRepository,
    economyRepository: EconomyRepository,
    private val provider: SudokuCatalogProvider,
    private val workDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val sessionIdFactory: () -> String = { UUID.randomUUID().toString() },
) : ViewModel() {
    private val mutableUiState = MutableStateFlow<SudokuGameUiState>(SudokuGameUiState.Loading)
    val uiState: StateFlow<SudokuGameUiState> = mutableUiState.asStateFlow()
    val economy: StateFlow<PlayerEconomy> =
        economyRepository.observe().stateIn(viewModelScope, SharingStarted.Eagerly, PlayerEconomy())

    private var engine: SudokuGameEngine? = null
    private var activeSession: ActiveSession? = null
    private var completionJob: Job? = null

    init {
        load()
    }

    fun reload() {
        if (mutableUiState.value == SudokuGameUiState.Loading) return
        load()
    }

    fun selectCell(position: SudokuPosition) {
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

    /** A new attempt reuses the selected record but never the finished attempt's session ID. */
    fun retry() {
        if (!economy.value.isGameplayAllowed) return
        val ready = mutableUiState.value as? SudokuGameUiState.Ready ?: return
        if (!ready.game.status.isTerminal || ready.completionPersistence != CompletionPersistence.Saved) return
        val gameEngine = engine ?: return
        val previous = activeSession ?: return
        val session = previous.copy(sessionId = sessionIdFactory())
        val game = gameEngine.start()
        activeSession = session
        mutableUiState.value = SudokuGameUiState.Ready(ready.puzzle, game)
        sessionRepository.replaceActiveSession(session.saved(game))
    }

    fun retryCompletion() {
        val ready = mutableUiState.value as? SudokuGameUiState.Ready ?: return
        if (ready.game.status.isTerminal) persistCompletion(ready.game)
    }

    private fun load() {
        mutableUiState.value = SudokuGameUiState.Loading
        viewModelScope.launch {
            try {
                val loaded =
                    when (val requested = launch) {
                        is SudokuGameLaunch.New -> loadNew(requested)
                        is SudokuGameLaunch.Restore -> restore(requested)
                    }
                engine = loaded.engine
                activeSession = loaded.activeSession
                mutableUiState.value = SudokuGameUiState.Ready(loaded.puzzle, loaded.game)
                if (loaded.isNew) sessionRepository.replaceActiveSession(loaded.activeSession.saved(loaded.game))
                if (loaded.game.status.isTerminal) persistCompletion(loaded.game)
            } catch (exception: CancellationException) {
                throw exception
            } catch (error: LoadFailure) {
                mutableUiState.value = SudokuGameUiState.Error(error.reason)
            } catch (_: Exception) {
                mutableUiState.value = SudokuGameUiState.Error(SudokuGameError.INVALID_SAVED_SESSION)
            }
        }
    }

    private suspend fun loadNew(requested: SudokuGameLaunch.New): LoadedSession =
        withContext(workDispatcher) {
            val puzzle = provider.select(requested.difficulty, requested.selectorSeed, requested.providerVersion).requirePuzzle()
            require(puzzle.id.difficulty.toPlatformDifficulty() == requested.difficulty)
            val gameEngine = SudokuGameEngine(puzzle)
            val game = gameEngine.start()
            LoadedSession(
                puzzle,
                gameEngine,
                game,
                ActiveSession(sessionIdFactory(), requested.difficulty, requested.selectorSeed, requested.providerVersion, puzzle),
                isNew = true,
            )
        }

    private suspend fun restore(requested: SudokuGameLaunch.Restore): LoadedSession {
        val saved =
            sessionRepository.readActiveSession(PuzzleType.SUDOKU, GameSessionScope.CATALOG)
                ?: throw LoadFailure(SudokuGameError.MISSING_SAVED_SESSION)
        // A generic identity inconsistency may be recoverable by a newer build, so retain the save.
        if (!saved.hasSudokuCatalogIdentity(provider.version)) {
            throw LoadFailure(SudokuGameError.INVALID_SAVED_SESSION)
        }

        val encoded = EncodedSudokuSession(saved.sessionFormatVersion, saved.gameplayPayload)
        val embeddedId =
            try {
                SudokuSessionCodecV1.puzzleId(encoded)
            } catch (exception: Exception) {
                discardUnreadable(saved)
                throw LoadFailure(SudokuGameError.INVALID_SAVED_SESSION)
            }

        val puzzle =
            withContext(workDispatcher) {
                provider.select(saved.difficulty, saved.puzzleSeed, saved.generatorVersion).requirePuzzle()
            }
        // This is an identity disagreement, not corrupt player state. Keep it for diagnosis/recovery.
        if (
            embeddedId != puzzle.id ||
            requested.expectedPuzzleId?.let { it != puzzle.id } == true
        ) {
            throw LoadFailure(SudokuGameError.INVALID_SAVED_SESSION)
        }

        return try {
            withContext(workDispatcher) {
                val gameEngine = SudokuGameEngine(puzzle)
                val game = SudokuSessionCodecV1.decode(puzzle, encoded, gameEngine)
                require(game.hintsUsed == saved.hintsUsed)
                require(game.status.name == saved.status)
                LoadedSession(
                    puzzle,
                    gameEngine,
                    game,
                    ActiveSession(saved.sessionId, saved.difficulty, saved.puzzleSeed, saved.generatorVersion, puzzle),
                    isNew = false,
                )
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
            discardUnreadable(saved)
            throw LoadFailure(SudokuGameError.INVALID_SAVED_SESSION)
        }
    }

    private fun updateGame(
        ready: SudokuGameUiState.Ready,
        updated: SudokuGameState,
        selectedCell: SudokuPosition? = ready.selectedCell,
    ) {
        if (updated == ready.game) return
        mutableUiState.value = ready.copy(game = updated, selectedCell = selectedCell)
        persist(updated)
    }

    private fun persist(game: SudokuGameState) {
        val session = activeSession ?: return
        if (game.status.isTerminal) {
            // Keep the terminal attempt recoverable until the atomic completion transaction removes it.
            sessionRepository.updateActiveSession(session.saved(game))
            persistCompletion(game)
        } else {
            sessionRepository.updateActiveSession(session.saved(game))
        }
    }

    private fun persistCompletion(game: SudokuGameState) {
        if (completionJob?.isActive == true) return
        val session = activeSession ?: return
        val ready = mutableUiState.value as? SudokuGameUiState.Ready ?: return
        if (ready.completionPersistence == CompletionPersistence.Saved) return
        mutableUiState.value = ready.copy(completionPersistence = CompletionPersistence.Saving)
        completionJob =
            viewModelScope.launch {
                try {
                    completionRepository.complete(session.completion(game))
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

    private fun discardUnreadable(saved: SavedGameSession) {
        sessionRepository.deleteActiveSession(PuzzleType.SUDOKU, GameSessionScope.CATALOG, saved.sessionId)
    }

    private fun SudokuDatasetResult<SudokuPuzzle>.requirePuzzle(): SudokuPuzzle =
        when (this) {
            is SudokuDatasetResult.Success -> value
            is SudokuDatasetResult.Failure -> throw LoadFailure(error.toGameError())
        }

    private data class LoadedSession(
        val puzzle: SudokuPuzzle,
        val engine: SudokuGameEngine,
        val game: SudokuGameState,
        val activeSession: ActiveSession,
        val isNew: Boolean,
    )

    private data class ActiveSession(
        val sessionId: String,
        val difficulty: Difficulty,
        val selectorSeed: PuzzleSeed,
        val providerVersion: GeneratorVersion,
        val puzzle: SudokuPuzzle,
    ) {
        fun saved(game: SudokuGameState): SavedGameSession {
            require(game.puzzleId == puzzle.id)
            val encoded = SudokuSessionCodecV1.encode(game)
            return SavedGameSession(
                sessionId = sessionId,
                puzzleType = PuzzleType.SUDOKU,
                sessionScope = GameSessionScope.CATALOG,
                difficulty = difficulty,
                puzzleSeed = selectorSeed,
                generatorVersion = providerVersion,
                sessionFormatVersion = encoded.formatVersion,
                gameplayPayload = encoded.payload,
                moveHistoryPayload = "",
                hintsUsed = game.hintsUsed,
                status = game.status.name,
            )
        }

        fun completion(game: SudokuGameState): GameCompletion {
            require(game.status.isTerminal)
            return GameCompletion(
                resultId = sessionId,
                puzzleType = PuzzleType.SUDOKU,
                difficulty = difficulty,
                puzzleSeed = selectorSeed,
                generatorVersion = providerVersion,
                sessionScope = GameSessionScope.CATALOG,
                hintsUsed = game.hintsUsed,
                outcome = if (game.status == SudokuGameStatus.SOLVED) GameOutcome.SOLVED else GameOutcome.FAILED,
            )
        }
    }

    private class LoadFailure(
        val reason: SudokuGameError,
    ) : Exception()
}

private fun SavedGameSession.hasSudokuCatalogIdentity(providerVersion: GeneratorVersion): Boolean =
    puzzleType == PuzzleType.SUDOKU &&
        sessionScope == GameSessionScope.CATALOG &&
        dailyIdentity == null &&
        generatorVersion == providerVersion

private fun SudokuDatasetError.toGameError(): SudokuGameError =
    when (this) {
        SudokuDatasetError.MISSING_ASSET, SudokuDatasetError.EMPTY_BUCKET -> SudokuGameError.MISSING_DATASET
        SudokuDatasetError.CORRUPT_ASSET -> SudokuGameError.CORRUPT_DATASET
        SudokuDatasetError.PUZZLE_NOT_FOUND -> SudokuGameError.PUZZLE_NOT_FOUND
    }

internal class SudokuGameViewModelFactory(
    private val launch: SudokuGameLaunch,
    private val assetManager: AssetManager,
    private val sessionRepository: GameSessionRepository,
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
            sessionRepository,
            completionRepository,
            economyRepository,
            provider,
        ) as T
    }
}
