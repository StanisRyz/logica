package com.stanisryz.logica.word

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.stanisryz.logica.catalog.CatalogLevelUnavailableException
import com.stanisryz.logica.catalog.GameAttempt
import com.stanisryz.logica.catalog.GameAttemptFactory
import com.stanisryz.logica.catalog.GameAttemptLaunch
import com.stanisryz.logica.economy.EconomyRepository
import com.stanisryz.logica.economy.PlayerEconomy
import com.stanisryz.logica.puzzle.core.contract.PuzzleGenerator
import com.stanisryz.logica.puzzle.core.model.GeneratorVersion
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import com.stanisryz.logica.puzzle.core.word.WordAllowedGuesses
import com.stanisryz.logica.puzzle.core.word.WordGameEngine
import com.stanisryz.logica.puzzle.core.word.WordGameState
import com.stanisryz.logica.puzzle.core.word.WordGameStatus
import com.stanisryz.logica.puzzle.core.word.WordGeneratorV1
import com.stanisryz.logica.puzzle.core.word.WordGeneratorV2
import com.stanisryz.logica.puzzle.core.word.WordGuessRejection
import com.stanisryz.logica.puzzle.core.word.WordLexiconV1
import com.stanisryz.logica.puzzle.core.word.WordLexiconV2
import com.stanisryz.logica.puzzle.core.word.WordPuzzle
import com.stanisryz.logica.puzzle.core.word.WordSubmitResult
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
    ) : WordGameUiState {
        val hasMeaningfulProgress: Boolean
            get() =
                !game.isFinished &&
                    (game.attempts.isNotEmpty() || game.currentDraft.positions.any { it != null })
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
    private val runtimeResolver: (GeneratorVersion) -> WordRuntime = ::resolveWordRuntime,
    private val workDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow<WordGameUiState>(WordGameUiState.Loading)
    val uiState: StateFlow<WordGameUiState> = mutableUiState.asStateFlow()

    /** The live wallet: gameplay actions need a life, and the finished attempt reports its effect. */
    val economy: StateFlow<PlayerEconomy> =
        economyRepository.observe().stateIn(viewModelScope, SharingStarted.Eagerly, PlayerEconomy())

    private var gameEngine: WordGameEngine? = null
    private var attempt: GameAttempt? = null
    private var completionJob: Job? = null

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
        if (!economy.value.isGameplayAllowed) return
        updateGame { engine, game -> engine.setLetter(game, position, letter) }
    }

    fun clearLetter(position: Int) {
        if (!economy.value.isGameplayAllowed) return
        updateGame { engine, game -> engine.clearLetter(game, position) }
    }

    fun submit() {
        if (!economy.value.isGameplayAllowed) return
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
                attemptsUsed = game.attempts.size,
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
        game: WordGameState,
        persistence: CompletionPersistence,
    ) {
        val current = mutableUiState.value
        if (current is WordGameUiState.Ready && current.game == game) {
            mutableUiState.value = current.copy(completionPersistence = persistence)
        }
    }
}

internal data class WordRuntime(
    val generator: PuzzleGenerator<WordPuzzle>,
    val allowedGuesses: WordAllowedGuesses,
)

private fun resolveWordRuntime(generatorVersion: GeneratorVersion): WordRuntime =
    when (generatorVersion.value) {
        1 -> WordRuntime(WordGeneratorV1(), WordLexiconV1.allowedGuesses)
        2 -> WordRuntime(WordGeneratorV2(), WordLexiconV2.allowedGuesses)
        else -> error("Unsupported Word generator version ${generatorVersion.value}.")
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
