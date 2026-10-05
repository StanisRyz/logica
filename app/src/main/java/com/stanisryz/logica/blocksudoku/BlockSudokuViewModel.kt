package com.stanisryz.logica.blocksudoku

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.stanisryz.logica.catalog.GameAttempt
import com.stanisryz.logica.catalog.GameAttemptFactory
import com.stanisryz.logica.catalog.GameAttemptLaunch
import com.stanisryz.logica.economy.EconomyRepository
import com.stanisryz.logica.economy.PlayerEconomy
import com.stanisryz.logica.puzzle.core.blocksudoku.BlockSudokuEngine
import com.stanisryz.logica.puzzle.core.blocksudoku.BlockSudokuRules
import com.stanisryz.logica.puzzle.core.blocksudoku.BlockSudokuState
import com.stanisryz.logica.puzzle.core.blocksudoku.BlockSudokuStatus
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import com.stanisryz.logica.result.CompletionPersistence
import com.stanisryz.logica.result.GameCompletionRepository
import com.stanisryz.logica.result.GameOutcome
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

internal sealed interface BlockSudokuUiState {
    data object Loading : BlockSudokuUiState

    data class Ready(
        val game: BlockSudokuState,
        val completionPersistence: CompletionPersistence = CompletionPersistence.NotRequired,
        /** Gems the saved result actually credited, for the result card. */
        val gemsEarned: Int = 0,
    ) : BlockSudokuUiState {
        /** Whether leaving now would throw away something the player actually did. */
        val hasMeaningfulProgress: Boolean
            get() = game.hasMeaningfulProgress
    }

    data object Error : BlockSudokuUiState
}

/**
 * One transient Block Sudoku attempt over the frozen level seed and Rules V1. The level is cleared
 * at its target score and lost when nothing in the tray fits; either end is one durable result
 * through the shared completion transaction. Leaving discards the board.
 */
internal class BlockSudokuViewModel(
    launch: GameAttemptLaunch,
    private val attemptFactory: GameAttemptFactory,
    private val completionRepository: GameCompletionRepository,
    economyRepository: EconomyRepository,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow<BlockSudokuUiState>(BlockSudokuUiState.Loading)
    val uiState: StateFlow<BlockSudokuUiState> = mutableUiState.asStateFlow()

    val economy: StateFlow<PlayerEconomy> =
        economyRepository.observe().stateIn(viewModelScope, SharingStarted.Eagerly, PlayerEconomy.LOADING)

    private var engine: BlockSudokuEngine? = null
    private var attempt: GameAttempt? = null
    private var completionJob: Job? = null

    init {
        viewModelScope.launch {
            mutableUiState.value =
                try {
                    val resolved = attemptFactory.create(launch, PuzzleType.BLOCK_SUDOKU)
                    require(resolved.generatorVersion == BlockSudokuRules.VERSION) { "Block Sudoku needs Rules V1." }
                    val nextEngine = BlockSudokuEngine(resolved.seed, resolved.difficulty)
                    engine = nextEngine
                    attempt = resolved
                    BlockSudokuUiState.Ready(nextEngine.start())
                } catch (exception: CancellationException) {
                    throw exception
                } catch (_: Exception) {
                    BlockSudokuUiState.Error
                }
        }
    }

    fun place(
        trayIndex: Int,
        row: Int,
        column: Int,
    ) {
        if (!economy.value.isGameplayAllowed) return
        val ready = mutableUiState.value as? BlockSudokuUiState.Ready ?: return
        val activeEngine = engine ?: return
        val updated = activeEngine.place(ready.game, trayIndex, row, column)
        if (updated == ready.game) return
        mutableUiState.value = ready.copy(game = updated)
        if (updated.status.isTerminal) persistCompletion(updated)
    }

    /** The same level again from its initial deal, once the finished attempt's result is durable. */
    fun retry() {
        if (!economy.value.isGameplayAllowed) return
        val ready = mutableUiState.value as? BlockSudokuUiState.Ready ?: return
        if (!ready.game.status.isTerminal || ready.completionPersistence != CompletionPersistence.Saved) return
        val activeEngine = engine ?: return
        val previous = attempt ?: return
        attempt = previous.restarted(attemptFactory.nextAttemptId())
        mutableUiState.value = BlockSudokuUiState.Ready(activeEngine.start())
    }

    fun retryCompletion() {
        val ready = mutableUiState.value as? BlockSudokuUiState.Ready ?: return
        if (ready.game.status.isTerminal) persistCompletion(ready.game)
    }

    private fun persistCompletion(game: BlockSudokuState) {
        if (completionJob?.isActive == true) return
        val current = attempt ?: return
        val ready = mutableUiState.value as? BlockSudokuUiState.Ready ?: return
        if (ready.completionPersistence == CompletionPersistence.Saved) return
        mutableUiState.value = ready.copy(completionPersistence = CompletionPersistence.Saving)
        val completion =
            current.completion(
                outcome = if (game.status == BlockSudokuStatus.SOLVED) GameOutcome.SOLVED else GameOutcome.FAILED,
                hintsUsed = 0,
            )
        completionJob =
            viewModelScope.launch {
                var gemsEarned = 0
                val persistence =
                    try {
                        gemsEarned = completionRepository.complete(completion).gemsEarned
                        CompletionPersistence.Saved
                    } catch (exception: CancellationException) {
                        throw exception
                    } catch (_: Exception) {
                        CompletionPersistence.Error
                    }
                val latest = mutableUiState.value
                if (latest is BlockSudokuUiState.Ready && latest.game == game) {
                    mutableUiState.value = latest.copy(completionPersistence = persistence, gemsEarned = gemsEarned)
                }
            }
    }
}

internal class BlockSudokuViewModelFactory(
    private val launch: GameAttemptLaunch,
    private val attemptFactory: GameAttemptFactory,
    private val completionRepository: GameCompletionRepository,
    private val economyRepository: EconomyRepository,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(BlockSudokuViewModel::class.java)) {
            "Unknown ViewModel class: ${modelClass.name}"
        }

        @Suppress("UNCHECKED_CAST")
        return BlockSudokuViewModel(launch, attemptFactory, completionRepository, economyRepository) as T
    }
}
