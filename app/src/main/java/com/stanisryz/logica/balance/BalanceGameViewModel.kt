package com.stanisryz.logica.balance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.stanisryz.logica.puzzle.core.balance.BalanceGameEngine
import com.stanisryz.logica.puzzle.core.balance.BalanceGameState
import com.stanisryz.logica.puzzle.core.balance.BalanceGeneratorV1
import com.stanisryz.logica.puzzle.core.balance.BalancePosition
import com.stanisryz.logica.puzzle.core.balance.BalancePuzzle
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.PuzzleSeed
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface BalanceGameUiState {
    data object Loading : BalanceGameUiState

    data class Ready(
        val puzzle: BalancePuzzle,
        val game: BalanceGameState,
        val isHintLoading: Boolean = false,
    ) : BalanceGameUiState

    data class Error(
        val message: String,
    ) : BalanceGameUiState
}

class BalanceGameViewModel(
    difficulty: Difficulty,
    seed: PuzzleSeed,
    private val generator: BalanceGeneratorV1 = BalanceGeneratorV1(),
    private val workDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow<BalanceGameUiState>(BalanceGameUiState.Loading)
    val uiState: StateFlow<BalanceGameUiState> = mutableUiState.asStateFlow()

    private var gameEngine: BalanceGameEngine? = null
    private var hintJob: Job? = null

    init {
        viewModelScope.launch {
            try {
                val session =
                    withContext(workDispatcher) {
                        val puzzle = generator.generate(seed, difficulty)
                        val engine = BalanceGameEngine(puzzle)
                        GeneratedSession(puzzle, engine, engine.start())
                    }
                gameEngine = session.engine
                mutableUiState.value = BalanceGameUiState.Ready(session.puzzle, session.game)
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Exception) {
                mutableUiState.value = BalanceGameUiState.Error("Не удалось создать головоломку.")
            }
        }
    }

    fun onCellTapped(position: BalancePosition) {
        updateGame { engine, game -> engine.cycleValue(game, position) }
    }

    fun undo() {
        updateGame { engine, game -> engine.undo(game) }
    }

    fun reset() {
        updateGame { engine, game -> engine.reset(game) }
    }

    fun requestHint() {
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

                mutableUiState.update { current ->
                    if (current is BalanceGameUiState.Ready && current.game == requestedGame) {
                        current.copy(game = hintedGame, isHintLoading = false)
                    } else {
                        current
                    }
                }
            }
    }

    private fun updateGame(update: (BalanceGameEngine, BalanceGameState) -> BalanceGameState) {
        val engine = gameEngine ?: return
        hintJob?.cancel()
        mutableUiState.update { current ->
            if (current is BalanceGameUiState.Ready) {
                current.copy(
                    game = update(engine, current.game),
                    isHintLoading = false,
                )
            } else {
                current
            }
        }
    }

    private data class GeneratedSession(
        val puzzle: BalancePuzzle,
        val engine: BalanceGameEngine,
        val game: BalanceGameState,
    )
}

class BalanceGameViewModelFactory(
    private val difficulty: Difficulty,
    private val seed: PuzzleSeed,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(BalanceGameViewModel::class.java)) {
            "Unknown ViewModel class: ${modelClass.name}"
        }

        @Suppress("UNCHECKED_CAST")
        return BalanceGameViewModel(difficulty, seed) as T
    }
}
