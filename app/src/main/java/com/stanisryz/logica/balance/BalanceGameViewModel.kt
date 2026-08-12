package com.stanisryz.logica.balance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.stanisryz.logica.catalog.CatalogLevelUnavailableException
import com.stanisryz.logica.catalog.GameAttempt
import com.stanisryz.logica.catalog.GameAttemptFactory
import com.stanisryz.logica.catalog.GameAttemptLaunch
import com.stanisryz.logica.economy.EconomyRepository
import com.stanisryz.logica.economy.PlayerEconomy
import com.stanisryz.logica.puzzle.core.balance.BalanceCell
import com.stanisryz.logica.puzzle.core.balance.BalanceCellStatus
import com.stanisryz.logica.puzzle.core.balance.BalanceGameEngine
import com.stanisryz.logica.puzzle.core.balance.BalanceGameState
import com.stanisryz.logica.puzzle.core.balance.BalanceGameStatus
import com.stanisryz.logica.puzzle.core.balance.BalanceGeneratorV1
import com.stanisryz.logica.puzzle.core.balance.BalancePosition
import com.stanisryz.logica.puzzle.core.balance.BalancePuzzle
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

internal sealed interface BalanceGameUiState {
    data object Loading : BalanceGameUiState

    data class Ready(
        val puzzle: BalancePuzzle,
        val game: BalanceGameState,
        /** The value the next tap places. Tool selection is presentation state and is never persisted. */
        val selectedValue: BalanceCell = BalanceCell.ONE,
        val isPencilMode: Boolean = false,
        val isHintLoading: Boolean = false,
        val completionPersistence: CompletionPersistence = CompletionPersistence.NotRequired,
    ) : BalanceGameUiState {
        /** Whether leaving now would throw away something the player actually did. */
        val hasMeaningfulProgress: Boolean
            get() =
                !game.status.isTerminal &&
                    (
                        game.cellStatuses.values.any { it != BalanceCellStatus.FIXED } ||
                            game.pencilMarks.isNotEmpty() ||
                            game.mistakesUsed > 0 ||
                            game.hintsUsed > 0
                    )
    }

    data class Error(
        val reason: BalanceGameError,
    ) : BalanceGameUiState
}

internal enum class BalanceGameError {
    LEVEL_UNAVAILABLE,
    GENERATION,
}

/**
 * One transient Balance attempt. The board lives here and nowhere else: leaving discards it, and
 * reopening the same level regenerates it from the frozen level definition.
 */
internal class BalanceGameViewModel(
    private val launch: GameAttemptLaunch,
    private val attemptFactory: GameAttemptFactory,
    private val completionRepository: GameCompletionRepository,
    economyRepository: EconomyRepository,
    private val generator: BalanceGeneratorV1 = BalanceGeneratorV1(),
    private val workDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow<BalanceGameUiState>(BalanceGameUiState.Loading)
    val uiState: StateFlow<BalanceGameUiState> = mutableUiState.asStateFlow()

    /** The live wallet: gameplay actions need a life, and the finished attempt reports its effect. */
    val economy: StateFlow<PlayerEconomy> =
        economyRepository.observe().stateIn(viewModelScope, SharingStarted.Eagerly, PlayerEconomy())

    private var gameEngine: BalanceGameEngine? = null
    private var attempt: GameAttempt? = null
    private var hintJob: Job? = null
    private var completionJob: Job? = null

    init {
        viewModelScope.launch {
            try {
                val resolved = attemptFactory.create(launch, PuzzleType.BALANCE)
                val loaded =
                    withContext(workDispatcher) {
                        val puzzle = generator.generate(resolved.seed, resolved.difficulty)
                        require(puzzle.id.generatorVersion == resolved.generatorVersion)
                        val engine = BalanceGameEngine(puzzle)
                        Triple(puzzle, engine, engine.start())
                    }
                gameEngine = loaded.second
                attempt = resolved
                mutableUiState.value = BalanceGameUiState.Ready(loaded.first, loaded.third)
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: CatalogLevelUnavailableException) {
                mutableUiState.value = BalanceGameUiState.Error(BalanceGameError.LEVEL_UNAVAILABLE)
            } catch (_: Exception) {
                mutableUiState.value = BalanceGameUiState.Error(BalanceGameError.GENERATION)
            }
        }
    }

    fun selectValue(value: BalanceCell) {
        if (!economy.value.isGameplayAllowed) return
        val ready = mutableUiState.value as? BalanceGameUiState.Ready ?: return
        if (ready.selectedValue == value) return
        mutableUiState.value = ready.copy(selectedValue = value)
    }

    fun togglePencilMode() {
        if (!economy.value.isGameplayAllowed) return
        val ready = mutableUiState.value as? BalanceGameUiState.Ready ?: return
        mutableUiState.value = ready.copy(isPencilMode = !ready.isPencilMode)
    }

    fun onCellTapped(position: BalancePosition) {
        if (!economy.value.isGameplayAllowed) return
        val ready = mutableUiState.value as? BalanceGameUiState.Ready ?: return
        val value = ready.selectedValue
        if (ready.isPencilMode) {
            updateGame { engine, game -> engine.togglePencilMark(game, position, value) }
        } else {
            updateGame { engine, game -> engine.placeValue(game, position, value) }
        }
    }

    /**
     * Starts the same level again from its initial deterministic state under a new attempt identity,
     * so the finished attempt's reward or penalty can never be applied twice. It waits for that
     * result to be durable first.
     */
    fun retry() {
        if (!economy.value.isGameplayAllowed) return
        val ready = mutableUiState.value as? BalanceGameUiState.Ready ?: return
        if (!ready.game.status.isTerminal) return
        if (ready.completionPersistence != CompletionPersistence.Saved) return
        val engine = gameEngine ?: return
        val previous = attempt ?: return

        attempt = previous.restarted(attemptFactory.nextAttemptId())
        mutableUiState.value =
            BalanceGameUiState.Ready(
                puzzle = ready.puzzle,
                game = engine.start(),
                selectedValue = ready.selectedValue,
            )
    }

    fun requestHint() {
        if (!economy.value.isGameplayAllowed) return
        if (hintJob?.isActive == true) return
        val engine = gameEngine ?: return
        val ready = mutableUiState.value as? BalanceGameUiState.Ready ?: return
        val requestedGame = ready.game
        mutableUiState.value = ready.copy(isHintLoading = true)

        hintJob =
            viewModelScope.launch {
                val hintedGame =
                    try {
                        withContext(workDispatcher) {
                            engine.requestHint(requestedGame)
                        }
                    } catch (exception: CancellationException) {
                        throw exception
                    } catch (_: Exception) {
                        requestedGame
                    }

                val current = mutableUiState.value
                if (current is BalanceGameUiState.Ready && current.game == requestedGame) {
                    mutableUiState.value = current.copy(game = hintedGame, isHintLoading = false)
                }
            }
    }

    fun retryCompletion() {
        val ready = mutableUiState.value as? BalanceGameUiState.Ready ?: return
        if (ready.game.status.isTerminal) persistCompletion(ready.game)
    }

    private fun updateGame(update: (BalanceGameEngine, BalanceGameState) -> BalanceGameState) {
        val engine = gameEngine ?: return
        hintJob?.cancel()
        val current = mutableUiState.value as? BalanceGameUiState.Ready ?: return
        val updatedGame = update(engine, current.game)
        if (updatedGame == current.game) {
            if (current.isHintLoading) mutableUiState.value = current.copy(isHintLoading = false)
            return
        }
        mutableUiState.value = current.copy(game = updatedGame, isHintLoading = false)
        if (updatedGame.status.isTerminal) persistCompletion(updatedGame)
    }

    private fun persistCompletion(game: BalanceGameState) {
        if (completionJob?.isActive == true) return
        val current = attempt ?: return
        val ready = mutableUiState.value as? BalanceGameUiState.Ready ?: return
        if (ready.completionPersistence == CompletionPersistence.Saved) return
        mutableUiState.value = ready.copy(completionPersistence = CompletionPersistence.Saving)
        val completion =
            current.completion(
                outcome = if (game.status == BalanceGameStatus.SOLVED) GameOutcome.SOLVED else GameOutcome.FAILED,
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
        game: BalanceGameState,
        persistence: CompletionPersistence,
    ) {
        val current = mutableUiState.value
        if (current is BalanceGameUiState.Ready && current.game == game) {
            mutableUiState.value = current.copy(completionPersistence = persistence)
        }
    }
}

internal class BalanceGameViewModelFactory(
    private val launch: GameAttemptLaunch,
    private val attemptFactory: GameAttemptFactory,
    private val completionRepository: GameCompletionRepository,
    private val economyRepository: EconomyRepository,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(BalanceGameViewModel::class.java)) {
            "Unknown ViewModel class: ${modelClass.name}"
        }

        @Suppress("UNCHECKED_CAST")
        return BalanceGameViewModel(launch, attemptFactory, completionRepository, economyRepository) as T
    }
}
