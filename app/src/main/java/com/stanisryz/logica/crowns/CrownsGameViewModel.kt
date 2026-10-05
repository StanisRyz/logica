package com.stanisryz.logica.crowns

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
import com.stanisryz.logica.puzzle.core.crowns.CrownsGameEngine
import com.stanisryz.logica.puzzle.core.crowns.CrownsGameState
import com.stanisryz.logica.puzzle.core.crowns.CrownsGameStatus
import com.stanisryz.logica.puzzle.core.crowns.CrownsGeneratorV1
import com.stanisryz.logica.puzzle.core.crowns.CrownsPlayerCell
import com.stanisryz.logica.puzzle.core.crowns.CrownsPosition
import com.stanisryz.logica.puzzle.core.crowns.CrownsPuzzle
import com.stanisryz.logica.puzzle.core.crowns.hasMeaningfulProgress
import com.stanisryz.logica.puzzle.core.model.PuzzleStars
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
        /** A hint was requested with an empty hint stock; the screen offers to restock. */
        val hintsExhausted: Boolean = false,
        val completionPersistence: CompletionPersistence = CompletionPersistence.NotRequired,
        /** Gems the saved result actually credited, for the result card. */
        val gemsEarned: Int = 0,
        /** The third mistake is waiting on the one ad-paid second chance; nothing is recorded yet. */
        val continueOffered: Boolean = false,
    ) : CrownsGameUiState {
        val hasMeaningfulProgress: Boolean
            // Leaving while the second chance is offered is leaving an unfinished level.
            get() = continueOffered || game.hasMeaningfulProgress
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
    private val hints: GameplayHints = GameplayHints(economyRepository),
) : ViewModel() {
    private val mutableUiState = MutableStateFlow<CrownsGameUiState>(CrownsGameUiState.Loading)
    val uiState: StateFlow<CrownsGameUiState> = mutableUiState.asStateFlow()

    /** The live wallet: gameplay actions need a life, and the finished attempt reports its effect. */
    val economy: StateFlow<PlayerEconomy> =
        economyRepository.observe().stateIn(viewModelScope, SharingStarted.Eagerly, PlayerEconomy.LOADING)

    private var gameEngine: CrownsGameEngine? = null
    private var attempt: GameAttempt? = null
    private var hintJob: Job? = null
    private var completionJob: Job? = null

    /** One second chance per attempt, used or declined. */
    private var continueUsed = false

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
        continueUsed = false
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
        if (economy.value.hints <= 0) {
            mutableUiState.value = ready.copy(hintsExhausted = true)
            return
        }
        val requestedGame = ready.game
        mutableUiState.value = ready.copy(isHintLoading = true)

        hintJob =
            viewModelScope.launch {
                val hintedGame =
                    try {
                        withContext(workDispatcher) { engine.revealHint(requestedGame) }
                    } catch (exception: CancellationException) {
                        throw exception
                    } catch (_: Exception) {
                        requestedGame
                    }
                val current = mutableUiState.value as? CrownsGameUiState.Ready
                if (hintedGame == requestedGame) {
                    if (current?.game == requestedGame) mutableUiState.value = current.copy(isHintLoading = false)
                    return@launch
                }
                // Computed first; charged and shown as one step, and only on the board it was computed for.
                hints.chargeAndShow(
                    stillCurrent = { (mutableUiState.value as? CrownsGameUiState.Ready)?.game == requestedGame },
                ) { paid ->
                    val shown = mutableUiState.value as? CrownsGameUiState.Ready ?: return@chargeAndShow
                    mutableUiState.value =
                        if (paid) {
                            shown.copy(game = hintedGame, isHintLoading = false)
                        } else {
                            shown.copy(isHintLoading = false, hintsExhausted = true)
                        }
                    // A hint that opens the last cell finishes the attempt like a move does.
                    if (paid && hintedGame.status.isTerminal) finishOrOffer(hintedGame)
                }
            }
    }

    fun dismissHintsExhausted() {
        val ready = mutableUiState.value as? CrownsGameUiState.Ready ?: return
        mutableUiState.value = ready.copy(hintsExhausted = false)
    }

    /** Restocks from the exhausted-hints prompt without leaving the attempt. */
    fun buyHints(offer: HintOffer) {
        viewModelScope.launch {
            if (hints.buy(offer)) dismissHintsExhausted()
        }
    }

    fun retryCompletion() {
        val ready = mutableUiState.value as? CrownsGameUiState.Ready ?: return
        if (ready.game.status.isTerminal && !ready.continueOffered) persistCompletion(ready.game)
    }

    private fun updateGame(update: (CrownsGameEngine, CrownsGameState) -> CrownsGameState) {
        val engine = gameEngine ?: return
        // A hint being charged is shown first; a board change only cancels one still being computed.
        if (hints.isCharging) return
        hintJob?.cancel()
        val current = mutableUiState.value as? CrownsGameUiState.Ready ?: return
        val updatedGame = update(engine, current.game)
        if (updatedGame == current.game) {
            if (current.isHintLoading) mutableUiState.value = current.copy(isHintLoading = false)
            return
        }
        mutableUiState.value = current.copy(game = updatedGame, isHintLoading = false)
        if (updatedGame.status.isTerminal) finishOrOffer(updatedGame)
    }

    /** The third mistake first offers one ad-paid second chance; any other end is recorded at once. */
    private fun finishOrOffer(game: CrownsGameState) {
        val ready = mutableUiState.value as? CrownsGameUiState.Ready ?: return
        if (game.status == CrownsGameStatus.FAILED && !continueUsed) {
            continueUsed = true
            mutableUiState.value = ready.copy(continueOffered = true)
        } else {
            persistCompletion(game)
        }
    }

    /** The player turned the second chance down: the failure is recorded as usual. */
    fun declineContinue() {
        val ready = mutableUiState.value as? CrownsGameUiState.Ready ?: return
        if (!ready.continueOffered) return
        mutableUiState.value = ready.copy(continueOffered = false)
        persistCompletion(ready.game)
    }

    /** The rewarded ad was watched: the same board goes on with one mistake to spare. */
    fun continueAfterAd() {
        val ready = mutableUiState.value as? CrownsGameUiState.Ready ?: return
        val activeEngine = gameEngine ?: return
        if (!ready.continueOffered) return
        mutableUiState.value = ready.copy(game = activeEngine.continueAfterFailure(ready.game), continueOffered = false)
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
                stars = if (game.status == CrownsGameStatus.SOLVED) PuzzleStars.forMistakes(game.mistakesUsed) else null,
            )
        completionJob =
            viewModelScope.launch {
                try {
                    val saved = completionRepository.complete(completion)
                    updateCompletionPersistence(game, CompletionPersistence.Saved, saved.gemsEarned)
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
        gemsEarned: Int = 0,
    ) {
        val current = mutableUiState.value
        if (current is CrownsGameUiState.Ready && current.game == game) {
            mutableUiState.value = current.copy(completionPersistence = persistence, gemsEarned = gemsEarned)
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
