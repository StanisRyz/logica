package com.stanisryz.logica.crowns

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.stanisryz.logica.catalog.CatalogLevelUnavailableException
import com.stanisryz.logica.catalog.GameAttempt
import com.stanisryz.logica.catalog.GameAttemptFactory
import com.stanisryz.logica.catalog.GameAttemptLaunch
import com.stanisryz.logica.economy.EconomyRepository
import com.stanisryz.logica.economy.PlayerEconomy
import com.stanisryz.logica.puzzle.core.crowns.CrownsGameEngine
import com.stanisryz.logica.puzzle.core.crowns.CrownsGameState
import com.stanisryz.logica.puzzle.core.crowns.CrownsGameStatus
import com.stanisryz.logica.puzzle.core.crowns.CrownsGeneratorV1
import com.stanisryz.logica.puzzle.core.crowns.CrownsPlayerCell
import com.stanisryz.logica.puzzle.core.crowns.CrownsPosition
import com.stanisryz.logica.puzzle.core.crowns.CrownsPuzzle
import com.stanisryz.logica.puzzle.core.model.PuzzleType
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

internal sealed interface CrownsGameUiState {
    data object Loading : CrownsGameUiState

    data class Ready(
        val puzzle: CrownsPuzzle,
        val game: CrownsGameState,
        /** The value the next tap places. Tool selection is presentation state and is never persisted. */
        val selectedValue: CrownsPlayerCell = CrownsPlayerCell.CROWN,
        val isPencilMode: Boolean = false,
        val isHintLoading: Boolean = false,
        val completionPersistence: CompletionPersistence = CompletionPersistence.NotRequired,
    ) : CrownsGameUiState {
        val hasMeaningfulProgress: Boolean
            get() =
                !game.status.isTerminal &&
                    (
                        game.cellStatuses.isNotEmpty() ||
                            game.pencilCrowns.isNotEmpty() ||
                            game.pencilMarks.isNotEmpty() ||
                            game.userMarks.isNotEmpty() ||
                            game.mistakesUsed > 0 ||
                            game.hintsUsed > 0
                    )
    }

    data class Error(
        val reason: CrownsGameError,
    ) : CrownsGameUiState
}

internal enum class CrownsGameError {
    LEVEL_UNAVAILABLE,
    GENERATION,
}

/** One transient Crowns attempt; its board is regenerated from the frozen level on every open. */
internal class CrownsGameViewModel(
    private val launch: GameAttemptLaunch,
    private val attemptFactory: GameAttemptFactory,
    private val completionRepository: GameCompletionRepository,
    economyRepository: EconomyRepository,
    private val generator: CrownsGeneratorV1 = CrownsGeneratorV1(),
    private val workDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow<CrownsGameUiState>(CrownsGameUiState.Loading)
    val uiState: StateFlow<CrownsGameUiState> = mutableUiState.asStateFlow()

    /** The live wallet: gameplay actions need a life, and the finished attempt reports its effect. */
    val economy: StateFlow<PlayerEconomy> =
        economyRepository.observe().stateIn(viewModelScope, SharingStarted.Eagerly, PlayerEconomy())

    private var gameEngine: CrownsGameEngine? = null
    private var attempt: GameAttempt? = null
    private var hintJob: Job? = null
    private var completionJob: Job? = null

    init {
        viewModelScope.launch {
            try {
                val resolved = attemptFactory.create(launch, PuzzleType.CROWNS)
                val loaded =
                    withContext(workDispatcher) {
                        val puzzle = generator.generate(resolved.seed, resolved.difficulty)
                        require(puzzle.id.generatorVersion == resolved.generatorVersion)
                        val engine = CrownsGameEngine(puzzle)
                        Triple(puzzle, engine, engine.start())
                    }
                gameEngine = loaded.second
                attempt = resolved
                mutableUiState.value = CrownsGameUiState.Ready(loaded.first, loaded.third)
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: CatalogLevelUnavailableException) {
                mutableUiState.value = CrownsGameUiState.Error(CrownsGameError.LEVEL_UNAVAILABLE)
            } catch (_: Exception) {
                mutableUiState.value = CrownsGameUiState.Error(CrownsGameError.GENERATION)
            }
        }
    }

    fun selectValue(value: CrownsPlayerCell) {
        require(value != CrownsPlayerCell.EMPTY) { "Only a concrete value can be selected." }
        if (!economy.value.isGameplayAllowed) return
        val ready = mutableUiState.value as? CrownsGameUiState.Ready ?: return
        if (ready.selectedValue == value) return
        mutableUiState.value = ready.copy(selectedValue = value)
    }

    fun togglePencilMode() {
        if (!economy.value.isGameplayAllowed) return
        val ready = mutableUiState.value as? CrownsGameUiState.Ready ?: return
        mutableUiState.value = ready.copy(isPencilMode = !ready.isPencilMode)
    }

    fun onCellTapped(position: CrownsPosition) {
        if (!economy.value.isGameplayAllowed) return
        val ready = mutableUiState.value as? CrownsGameUiState.Ready ?: return
        val value = ready.selectedValue
        if (ready.isPencilMode) {
            updateGame { engine, game -> engine.togglePencilMark(game, position, value) }
        } else {
            updateGame { engine, game -> engine.placeValue(game, position, value) }
        }
    }

    /** Starts the same level again from its initial state under a new completion identity. */
    fun retry() {
        if (!economy.value.isGameplayAllowed) return
        val ready = mutableUiState.value as? CrownsGameUiState.Ready ?: return
        if (!ready.game.status.isTerminal) return
        if (ready.completionPersistence != CompletionPersistence.Saved) return
        val engine = gameEngine ?: return
        val previous = attempt ?: return

        attempt = previous.restarted(attemptFactory.nextAttemptId())
        mutableUiState.value =
            CrownsGameUiState.Ready(
                puzzle = ready.puzzle,
                game = engine.start(),
                selectedValue = ready.selectedValue,
            )
    }

    fun requestHint() {
        if (!economy.value.isGameplayAllowed) return
        if (hintJob?.isActive == true) return
        val engine = gameEngine ?: return
        val ready = mutableUiState.value as? CrownsGameUiState.Ready ?: return
        val requestedGame = ready.game
        mutableUiState.value = ready.copy(isHintLoading = true)

        hintJob =
            viewModelScope.launch {
                val hintedGame =
                    try {
                        withContext(workDispatcher) { engine.requestHint(requestedGame) }
                    } catch (exception: CancellationException) {
                        throw exception
                    } catch (_: Exception) {
                        requestedGame
                    }
                val current = mutableUiState.value
                if (current is CrownsGameUiState.Ready && current.game == requestedGame) {
                    mutableUiState.value = current.copy(game = hintedGame, isHintLoading = false)
                }
            }
    }

    fun retryCompletion() {
        val ready = mutableUiState.value as? CrownsGameUiState.Ready ?: return
        if (ready.game.status.isTerminal) persistCompletion(ready.game)
    }

    private fun updateGame(update: (CrownsGameEngine, CrownsGameState) -> CrownsGameState) {
        val engine = gameEngine ?: return
        hintJob?.cancel()
        val current = mutableUiState.value as? CrownsGameUiState.Ready ?: return
        val updatedGame = update(engine, current.game)
        if (updatedGame == current.game) {
            if (current.isHintLoading) mutableUiState.value = current.copy(isHintLoading = false)
            return
        }
        mutableUiState.value = current.copy(game = updatedGame, isHintLoading = false)
        if (updatedGame.status.isTerminal) persistCompletion(updatedGame)
    }

    private fun persistCompletion(game: CrownsGameState) {
        if (completionJob?.isActive == true) return
        val current = attempt ?: return
        val ready = mutableUiState.value as? CrownsGameUiState.Ready ?: return
        if (ready.completionPersistence == CompletionPersistence.Saved) return
        mutableUiState.value = ready.copy(completionPersistence = CompletionPersistence.Saving)
        val completion =
            current.completion(
                outcome = if (game.status == CrownsGameStatus.SOLVED) GameOutcome.SOLVED else GameOutcome.FAILED,
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
        game: CrownsGameState,
        persistence: CompletionPersistence,
    ) {
        val current = mutableUiState.value
        if (current is CrownsGameUiState.Ready && current.game == game) {
            mutableUiState.value = current.copy(completionPersistence = persistence)
        }
    }
}

internal class CrownsGameViewModelFactory(
    private val launch: GameAttemptLaunch,
    private val attemptFactory: GameAttemptFactory,
    private val completionRepository: GameCompletionRepository,
    private val economyRepository: EconomyRepository,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(CrownsGameViewModel::class.java)) {
            "Unknown ViewModel class: ${modelClass.name}"
        }

        @Suppress("UNCHECKED_CAST")
        return CrownsGameViewModel(launch, attemptFactory, completionRepository, economyRepository) as T
    }
}
