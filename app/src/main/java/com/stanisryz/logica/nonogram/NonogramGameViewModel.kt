package com.stanisryz.logica.nonogram

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
import com.stanisryz.logica.puzzle.core.nonogram.NonogramGameEngine
import com.stanisryz.logica.puzzle.core.nonogram.NonogramGameState
import com.stanisryz.logica.puzzle.core.nonogram.NonogramGameStatus
import com.stanisryz.logica.puzzle.core.nonogram.NonogramGeneratorV1
import com.stanisryz.logica.puzzle.core.nonogram.NonogramPosition
import com.stanisryz.logica.puzzle.core.nonogram.NonogramPuzzle
import com.stanisryz.logica.puzzle.core.nonogram.NonogramTool
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

internal sealed interface NonogramGameUiState {
    data object Loading : NonogramGameUiState

    data class Ready(
        val puzzle: NonogramPuzzle,
        val game: NonogramGameState,
        val initial: NonogramGameState,
        /** The tool the next tap applies; presentation state that is never persisted. */
        val selectedTool: NonogramTool = NonogramTool.FILL,
        /** A hint was requested with an empty hint stock; the screen offers to restock. */
        val hintsExhausted: Boolean = false,
        val completionPersistence: CompletionPersistence = CompletionPersistence.NotRequired,
        /** The third mistake is waiting on the one ad-paid second chance; nothing is recorded yet. */
        val continueOffered: Boolean = false,
    ) : NonogramGameUiState {
        /** Whether leaving now would throw away something the player actually did. */
        val hasMeaningfulProgress: Boolean
            get() = continueOffered || !game.status.isTerminal && game.hasMeaningfulProgress(initial)
    }

    data object Error : NonogramGameUiState
}

/**
 * One transient Nonogram attempt. The board lives here and nowhere else: leaving discards it, and
 * reopening the same level regenerates it from the frozen level definition.
 */
internal class NonogramGameViewModel(
    private val launch: GameAttemptLaunch,
    private val attemptFactory: GameAttemptFactory,
    private val completionRepository: GameCompletionRepository,
    economyRepository: EconomyRepository,
    private val generator: NonogramGeneratorV1 = NonogramGeneratorV1(),
    private val workDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val hints: GameplayHints = GameplayHints(economyRepository),
) : ViewModel() {
    private val mutableUiState = MutableStateFlow<NonogramGameUiState>(NonogramGameUiState.Loading)
    val uiState: StateFlow<NonogramGameUiState> = mutableUiState.asStateFlow()

    /** The live wallet: gameplay actions need a life, and the finished attempt reports its effect. */
    val economy: StateFlow<PlayerEconomy> =
        economyRepository.observe().stateIn(viewModelScope, SharingStarted.Eagerly, PlayerEconomy.LOADING)

    private var gameEngine: NonogramGameEngine? = null
    private var attempt: GameAttempt? = null
    private var completionJob: Job? = null

    /** One second chance per attempt, used or declined. */
    private var continueUsed = false
    private var hintJob: Job? = null

    init {
        viewModelScope.launch {
            try {
                val resolved = attemptFactory.create(launch, PuzzleType.NONOGRAM)
                val (puzzle, engine) =
                    withContext(workDispatcher) {
                        val puzzle = generator.generate(resolved.seed, resolved.difficulty)
                        require(puzzle.id.generatorVersion == resolved.generatorVersion)
                        puzzle to NonogramGameEngine(puzzle)
                    }
                gameEngine = engine
                attempt = resolved
                val initial = engine.start()
                mutableUiState.value = NonogramGameUiState.Ready(puzzle, initial, initial)
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: CatalogLevelUnavailableException) {
                mutableUiState.value = NonogramGameUiState.Error
            } catch (_: Exception) {
                mutableUiState.value = NonogramGameUiState.Error
            }
        }
    }

    fun selectTool(tool: NonogramTool) {
        if (!economy.value.isGameplayAllowed) return
        val ready = mutableUiState.value as? NonogramGameUiState.Ready ?: return
        if (ready.selectedTool != tool) mutableUiState.value = ready.copy(selectedTool = tool)
    }

    fun onCell(position: NonogramPosition) {
        if (!economy.value.isGameplayAllowed) return
        val ready = mutableUiState.value as? NonogramGameUiState.Ready ?: return
        val engine = gameEngine ?: return
        updateGame(ready, engine.mark(ready.game, position, ready.selectedTool))
    }

    fun requestHint() {
        if (!economy.value.isGameplayAllowed) return
        if (hintJob?.isActive == true) return
        val engine = gameEngine ?: return
        val ready = mutableUiState.value as? NonogramGameUiState.Ready ?: return
        if (economy.value.hints <= 0) {
            mutableUiState.value = ready.copy(hintsExhausted = true)
            return
        }
        val hinted = engine.revealHint(ready.game)
        if (hinted == ready.game) return
        hintJob =
            viewModelScope.launch {
                // A produced hint costs one hint from the consumable stock before it is shown.
                val paid = hints.spend()
                val current = mutableUiState.value as? NonogramGameUiState.Ready ?: return@launch
                if (current.game != ready.game) return@launch
                if (paid) updateGame(current, hinted) else mutableUiState.value = current.copy(hintsExhausted = true)
            }
    }

    fun dismissHintsExhausted() {
        val ready = mutableUiState.value as? NonogramGameUiState.Ready ?: return
        mutableUiState.value = ready.copy(hintsExhausted = false)
    }

    /** Restocks from the exhausted-hints prompt without leaving the attempt. */
    fun buyHints(offer: HintOffer) {
        viewModelScope.launch {
            if (hints.buy(offer)) dismissHintsExhausted()
        }
    }

    /** The same level again from its initial state, once the finished attempt's result is durable. */
    fun retry() {
        if (!economy.value.isGameplayAllowed) return
        val ready = mutableUiState.value as? NonogramGameUiState.Ready ?: return
        if (!ready.game.status.isTerminal || ready.completionPersistence != CompletionPersistence.Saved) return
        val engine = gameEngine ?: return
        val previous = attempt ?: return
        attempt = previous.restarted(attemptFactory.nextAttemptId())
        continueUsed = false
        val initial = engine.start()
        mutableUiState.value = NonogramGameUiState.Ready(ready.puzzle, initial, initial, selectedTool = ready.selectedTool)
    }

    fun retryCompletion() {
        val ready = mutableUiState.value as? NonogramGameUiState.Ready ?: return
        if (ready.game.status.isTerminal && !ready.continueOffered) persistCompletion(ready.game)
    }

    private fun updateGame(
        current: NonogramGameUiState.Ready,
        updated: NonogramGameState,
    ) {
        if (updated == current.game) return
        mutableUiState.value = current.copy(game = updated)
        if (updated.status.isTerminal) finishOrOffer(updated)
    }

    /** The third mistake first offers one ad-paid second chance; any other end is recorded at once. */
    private fun finishOrOffer(game: NonogramGameState) {
        val ready = mutableUiState.value as? NonogramGameUiState.Ready ?: return
        if (game.status == NonogramGameStatus.FAILED && !continueUsed) {
            continueUsed = true
            mutableUiState.value = ready.copy(continueOffered = true)
        } else {
            persistCompletion(game)
        }
    }

    /** The player turned the second chance down: the failure is recorded as usual. */
    fun declineContinue() {
        val ready = mutableUiState.value as? NonogramGameUiState.Ready ?: return
        if (!ready.continueOffered) return
        mutableUiState.value = ready.copy(continueOffered = false)
        persistCompletion(ready.game)
    }

    /** The rewarded ad was watched: the same board goes on with one mistake to spare. */
    fun continueAfterAd() {
        val ready = mutableUiState.value as? NonogramGameUiState.Ready ?: return
        val activeEngine = gameEngine ?: return
        if (!ready.continueOffered) return
        mutableUiState.value = ready.copy(game = activeEngine.continueAfterFailure(ready.game), continueOffered = false)
    }

    private fun persistCompletion(game: NonogramGameState) {
        if (completionJob?.isActive == true) return
        val current = attempt ?: return
        val ready = mutableUiState.value as? NonogramGameUiState.Ready ?: return
        if (ready.completionPersistence == CompletionPersistence.Saved) return
        mutableUiState.value = ready.copy(completionPersistence = CompletionPersistence.Saving)
        val solved = game.status == NonogramGameStatus.SOLVED
        val completion =
            current.completion(
                outcome = if (solved) GameOutcome.SOLVED else GameOutcome.FAILED,
                hintsUsed = game.hintsUsed,
                stars = if (solved) PuzzleStars.forMistakes(game.mistakesUsed) else null,
            )
        completionJob =
            viewModelScope.launch {
                val persistence =
                    try {
                        completionRepository.complete(completion)
                        CompletionPersistence.Saved
                    } catch (exception: CancellationException) {
                        throw exception
                    } catch (_: Exception) {
                        CompletionPersistence.Error
                    }
                val latest = mutableUiState.value
                if (latest is NonogramGameUiState.Ready && latest.game == game) {
                    mutableUiState.value = latest.copy(completionPersistence = persistence)
                }
            }
    }
}

internal class NonogramGameViewModelFactory(
    private val launch: GameAttemptLaunch,
    private val attemptFactory: GameAttemptFactory,
    private val completionRepository: GameCompletionRepository,
    private val economyRepository: EconomyRepository,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(NonogramGameViewModel::class.java)) {
            "Unknown ViewModel class: ${modelClass.name}"
        }

        @Suppress("UNCHECKED_CAST")
        return NonogramGameViewModel(launch, attemptFactory, completionRepository, economyRepository) as T
    }
}
