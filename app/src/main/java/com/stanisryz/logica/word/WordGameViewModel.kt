package com.stanisryz.logica.word

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
import com.stanisryz.logica.puzzle.core.model.GeneratorVersion
import com.stanisryz.logica.puzzle.core.model.PuzzleStars
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import com.stanisryz.logica.puzzle.core.word.WordGameEngine
import com.stanisryz.logica.puzzle.core.word.WordGameState
import com.stanisryz.logica.puzzle.core.word.WordGameStatus
import com.stanisryz.logica.puzzle.core.word.WordGuessRejection
import com.stanisryz.logica.puzzle.core.word.WordPuzzle
import com.stanisryz.logica.puzzle.core.word.WordRuntime
import com.stanisryz.logica.puzzle.core.word.WordRuntimeResolver
import com.stanisryz.logica.puzzle.core.word.WordSubmitResult
import com.stanisryz.logica.puzzle.core.word.hasMeaningfulProgress
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

internal sealed interface WordGameUiState {
    data object Loading : WordGameUiState

    data class Ready(
        val puzzle: WordPuzzle,
        val game: WordGameState,
        val rejection: WordGuessRejection? = null,
        val rejectionRevision: Int = 0,
        val acceptedAttemptRevision: Int = 0,
        val completionPersistence: CompletionPersistence = CompletionPersistence.NotRequired,
        /** Gems the saved result actually credited, for the result card. */
        val gemsEarned: Int = 0,
        /** A hint was asked for with an empty stock; the screen offers to restock. */
        val hintsExhausted: Boolean = false,
    ) : WordGameUiState {
        val hasMeaningfulProgress: Boolean
            get() = game.hasMeaningfulProgress
    }

    data class Error(
        val reason: WordGameError,
    ) : WordGameUiState
}

internal enum class WordGameError {
    LEVEL_UNAVAILABLE,
    GENERATION,
}

/** One transient Word attempt on the level's frozen answer; nothing about it is persisted. */
internal class WordGameViewModel(
    private val launch: GameAttemptLaunch,
    private val attemptFactory: GameAttemptFactory,
    private val completionRepository: GameCompletionRepository,
    economyRepository: EconomyRepository,
    private val runtimeResolver: (GeneratorVersion) -> WordRuntime = WordRuntimeResolver::resolve,
    private val workDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val hints: GameplayHints = GameplayHints(economyRepository),
) : ViewModel() {
    private val mutableUiState = MutableStateFlow<WordGameUiState>(WordGameUiState.Loading)
    val uiState: StateFlow<WordGameUiState> = mutableUiState.asStateFlow()

    /** The live wallet: gameplay actions need a life, and the finished attempt reports its effect. */
    val economy: StateFlow<PlayerEconomy> =
        economyRepository.observe().stateIn(viewModelScope, SharingStarted.Eagerly, PlayerEconomy.LOADING)

    private var gameEngine: WordGameEngine? = null
    private var attempt: GameAttempt? = null
    private var completionJob: Job? = null
    private var hintJob: Job? = null

    init {
        viewModelScope.launch {
            try {
                val resolved = attemptFactory.create(launch, PuzzleType.WORD)
                val loaded =
                    withContext(workDispatcher) {
                        val runtime = runtimeResolver(resolved.generatorVersion)
                        val puzzle = runtime.generator.generate(resolved.seed, resolved.difficulty)
                        require(puzzle.id.generatorVersion == resolved.generatorVersion)
                        // Load the bundled guess pool here so the first submit never parses it on the main thread.
                        runtime.allowedGuesses.size
                        val engine = WordGameEngine(puzzle, runtime.allowedGuesses)
                        Triple(puzzle, engine, engine.start())
                    }
                gameEngine = loaded.second
                attempt = resolved
                mutableUiState.value = WordGameUiState.Ready(loaded.first, loaded.third)
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: CatalogLevelUnavailableException) {
                mutableUiState.value = WordGameUiState.Error(WordGameError.LEVEL_UNAVAILABLE)
            } catch (_: Exception) {
                mutableUiState.value = WordGameUiState.Error(WordGameError.GENERATION)
            }
        }
    }

    fun setLetter(
        position: Int,
        letter: Char,
    ) {
        if (!economy.value.isGameplayAllowed || hints.isCharging) return
        updateGame { engine, game -> engine.setLetter(game, position, letter) }
    }

    fun clearLetter(position: Int) {
        if (!economy.value.isGameplayAllowed || hints.isCharging) return
        updateGame { engine, game -> engine.clearLetter(game, position) }
    }

    /**
     * Opens one answer letter for one hint from the shared stock. The letter is computed first, then
     * charged and shown in one step, and only on the board it was computed for; an empty stock opens
     * nothing and offers to restock. A hint spends no guess.
     */
    fun requestHint() {
        if (!economy.value.isGameplayAllowed) return
        if (hintJob?.isActive == true) return
        val engine = gameEngine ?: return
        val ready = mutableUiState.value as? WordGameUiState.Ready ?: return
        val hinted = engine.revealHint(ready.game)
        if (hinted == ready.game) return
        hintJob =
            viewModelScope.launch {
                hints.chargeAndShow(
                    stillCurrent = { (mutableUiState.value as? WordGameUiState.Ready)?.game == ready.game },
                ) { paid ->
                    val current = mutableUiState.value as? WordGameUiState.Ready ?: return@chargeAndShow
                    mutableUiState.value =
                        if (paid) current.copy(game = hinted, rejection = null) else current.copy(hintsExhausted = true)
                }
            }
    }

    fun dismissHintsExhausted() {
        val ready = mutableUiState.value as? WordGameUiState.Ready ?: return
        mutableUiState.value = ready.copy(hintsExhausted = false)
    }

    /** Restocks from the exhausted-hints prompt without leaving the attempt. */
    fun buyHints(offer: HintOffer) {
        viewModelScope.launch {
            if (hints.buy(offer)) dismissHintsExhausted()
        }
    }

    fun submit() {
        if (!economy.value.isGameplayAllowed || hints.isCharging) return
        val engine = gameEngine ?: return
        val current = mutableUiState.value as? WordGameUiState.Ready ?: return
        when (val result = engine.submit(current.game)) {
            is WordSubmitResult.Rejected -> {
                // A rejected guess consumes no attempt and stays editable.
                mutableUiState.value =
                    current.copy(
                        rejection = result.rejection,
                        rejectionRevision = current.rejectionRevision + 1,
                    )
            }
            is WordSubmitResult.Accepted -> {
                mutableUiState.value =
                    current.copy(
                        game = result.state,
                        rejection = null,
                        acceptedAttemptRevision = current.acceptedAttemptRevision + 1,
                    )
                if (result.state.isFinished) persistCompletion(result.state)
            }
        }
    }

    fun dismissRejection() {
        val current = mutableUiState.value as? WordGameUiState.Ready ?: return
        if (current.rejection != null) mutableUiState.value = current.copy(rejection = null)
    }

    fun retryCompletion() {
        val ready = mutableUiState.value as? WordGameUiState.Ready ?: return
        if (ready.game.isFinished) persistCompletion(ready.game)
    }

    /** Starts the same level again: the same word, empty input, all six attempts, new identity. */
    fun retry() {
        if (!economy.value.isGameplayAllowed) return
        val ready = mutableUiState.value as? WordGameUiState.Ready ?: return
        if (!ready.game.isFinished) return
        if (ready.completionPersistence != CompletionPersistence.Saved) return
        val engine = gameEngine ?: return
        val previous = attempt ?: return

        attempt = previous.restarted(attemptFactory.nextAttemptId())
        mutableUiState.value = WordGameUiState.Ready(ready.puzzle, engine.start())
    }

    private fun updateGame(update: (WordGameEngine, WordGameState) -> WordGameState) {
        val engine = gameEngine ?: return
        val current = mutableUiState.value as? WordGameUiState.Ready ?: return
        val updatedGame = update(engine, current.game)
        if (updatedGame == current.game) {
            if (current.rejection != null) mutableUiState.value = current.copy(rejection = null)
            return
        }
        mutableUiState.value = current.copy(game = updatedGame, rejection = null)
    }

    private fun persistCompletion(game: WordGameState) {
        if (completionJob?.isActive == true) return
        val current = attempt ?: return
        val ready = mutableUiState.value as? WordGameUiState.Ready ?: return
        if (ready.completionPersistence == CompletionPersistence.Saved) return
        mutableUiState.value = ready.copy(completionPersistence = CompletionPersistence.Saving)
        val completion =
            current.completion(
                outcome = if (game.status == WordGameStatus.SOLVED) GameOutcome.SOLVED else GameOutcome.FAILED,
                hintsUsed = game.hintsUsed,
                attemptsUsed = game.attempts.size,
                stars = if (game.status == WordGameStatus.SOLVED) PuzzleStars.forWordAttempts(game.attempts.size) else null,
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
        game: WordGameState,
        persistence: CompletionPersistence,
        gemsEarned: Int = 0,
    ) {
        val current = mutableUiState.value
        if (current is WordGameUiState.Ready && current.game == game) {
            mutableUiState.value = current.copy(completionPersistence = persistence, gemsEarned = gemsEarned)
        }
    }
}

internal class WordGameViewModelFactory(
    private val launch: GameAttemptLaunch,
    private val attemptFactory: GameAttemptFactory,
    private val completionRepository: GameCompletionRepository,
    private val economyRepository: EconomyRepository,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(WordGameViewModel::class.java)) {
            "Unknown ViewModel class: ${modelClass.name}"
        }
        @Suppress("UNCHECKED_CAST")
        return WordGameViewModel(launch, attemptFactory, completionRepository, economyRepository) as T
    }
}
