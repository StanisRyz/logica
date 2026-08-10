package com.stanisryz.logica.word

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.stanisryz.logica.puzzle.core.contract.PuzzleGenerator
import com.stanisryz.logica.puzzle.core.daily.DailyPolicyVersion
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.GeneratorVersion
import com.stanisryz.logica.puzzle.core.model.PuzzleId
import com.stanisryz.logica.puzzle.core.model.PuzzleSeed
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
import com.stanisryz.logica.result.GameCompletion
import com.stanisryz.logica.result.GameCompletionRepository
import com.stanisryz.logica.result.GameOutcome
import com.stanisryz.logica.session.DailyGameSessionIdentity
import com.stanisryz.logica.session.GameSessionRepository
import com.stanisryz.logica.session.GameSessionScope
import com.stanisryz.logica.session.SavedGameSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.UUID

internal sealed interface WordGameContext {
    val sessionScope: GameSessionScope

    data object Catalog : WordGameContext {
        override val sessionScope = GameSessionScope.CATALOG
    }

    data class Daily(
        val challengeDate: LocalDate,
        val policyVersion: DailyPolicyVersion,
    ) : WordGameContext {
        override val sessionScope = GameSessionScope.DAILY
    }
}

internal sealed interface WordGameLaunch {
    val context: WordGameContext

    data class New(
        val difficulty: Difficulty,
        val seed: PuzzleSeed,
        val generatorVersion: GeneratorVersion = GeneratorVersion(2),
        override val context: WordGameContext = WordGameContext.Catalog,
    ) : WordGameLaunch

    data class Restore(
        override val context: WordGameContext = WordGameContext.Catalog,
        val expectedPuzzleId: PuzzleId? = null,
    ) : WordGameLaunch
}

internal sealed interface WordGameUiState {
    data object Loading : WordGameUiState

    data class Ready(
        val puzzle: WordPuzzle,
        val game: WordGameState,
        val rejection: WordGuessRejection? = null,
        val completionPersistence: CompletionPersistence = CompletionPersistence.NotRequired,
    ) : WordGameUiState

    data class Error(
        val reason: WordGameError,
    ) : WordGameUiState
}

internal enum class WordGameError {
    MISSING_SAVED_SESSION,
    INVALID_SAVED_SESSION,
    GENERATION,
}

internal class WordGameViewModel(
    private val launch: WordGameLaunch,
    private val sessionRepository: GameSessionRepository,
    private val completionRepository: GameCompletionRepository,
    private val runtimeResolver: (GeneratorVersion) -> WordRuntime = ::resolveWordRuntime,
    private val workDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val sessionIdFactory: () -> String = { UUID.randomUUID().toString() },
) : ViewModel() {
    private val mutableUiState = MutableStateFlow<WordGameUiState>(WordGameUiState.Loading)
    val uiState: StateFlow<WordGameUiState> = mutableUiState.asStateFlow()

    private var gameEngine: WordGameEngine? = null
    private var activeSession: ActiveSession? = null
    private var completionJob: Job? = null

    init {
        viewModelScope.launch {
            try {
                val session = loadSession()
                gameEngine = session.engine
                activeSession = session.activeSession
                mutableUiState.value = WordGameUiState.Ready(session.puzzle, session.game)
                if (session.isNew) sessionRepository.replaceActiveSession(session.activeSession.saved(session.game))
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: MissingSavedSessionException) {
                mutableUiState.value = WordGameUiState.Error(WordGameError.MISSING_SAVED_SESSION)
            } catch (_: InvalidSavedSessionException) {
                mutableUiState.value = WordGameUiState.Error(WordGameError.INVALID_SAVED_SESSION)
            } catch (_: Exception) {
                mutableUiState.value = WordGameUiState.Error(WordGameError.GENERATION)
            }
        }
    }

    fun appendLetter(letter: Char) {
        updateGame { engine, game -> engine.appendLetter(game, letter) }
    }

    fun removeLastLetter() {
        updateGame { engine, game -> engine.removeLastLetter(game) }
    }

    fun submit() {
        val engine = gameEngine ?: return
        val current = mutableUiState.value as? WordGameUiState.Ready ?: return
        when (val result = engine.submit(current.game)) {
            is WordSubmitResult.Rejected -> {
                // A rejected guess consumes no attempt and stays editable.
                mutableUiState.value = current.copy(rejection = result.rejection)
            }
            is WordSubmitResult.Accepted -> {
                mutableUiState.value = current.copy(game = result.state, rejection = null)
                persist(result.state)
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

    /**
     * Starts a fresh attempt at the very same word: a new session ID, empty input, and all six
     * attempts again. The puzzle is the one already generated from the saved identity, never a new
     * one, and the retry waits for the finished attempt's result to be durably stored.
     */
    fun retry() {
        val ready = mutableUiState.value as? WordGameUiState.Ready ?: return
        if (!ready.game.isFinished) return
        if (ready.completionPersistence != CompletionPersistence.Saved) return
        val engine = gameEngine ?: return
        val previous = activeSession ?: return

        val session = previous.copy(sessionId = sessionIdFactory())
        val game = engine.start()
        activeSession = session
        mutableUiState.value = WordGameUiState.Ready(ready.puzzle, game)
        sessionRepository.replaceActiveSession(session.saved(game))
    }

    private suspend fun loadSession(): LoadedSession =
        when (val requestedLaunch = launch) {
            is WordGameLaunch.New ->
                withContext(workDispatcher) {
                    val runtime = runtimeResolver(requestedLaunch.generatorVersion)
                    val puzzle = runtime.generator.generate(requestedLaunch.seed, requestedLaunch.difficulty)
                    require(puzzle.id.generatorVersion == requestedLaunch.generatorVersion)
                    // Load the bundled guess pool here so the first submit never parses it on the main thread.
                    runtime.allowedGuesses.size
                    val engine = WordGameEngine(puzzle, runtime.allowedGuesses)
                    LoadedSession(
                        puzzle,
                        engine,
                        engine.start(),
                        ActiveSession(sessionIdFactory(), puzzle, requestedLaunch.context),
                        isNew = true,
                    )
                }
            is WordGameLaunch.Restore -> restoreSavedSession(requestedLaunch)
        }

    private suspend fun restoreSavedSession(requestedLaunch: WordGameLaunch.Restore): LoadedSession {
        val saved =
            sessionRepository.readActiveSession(PuzzleType.WORD, requestedLaunch.context.sessionScope)
                ?: throw MissingSavedSessionException()
        // A save that belongs to another Daily definition is a persistence inconsistency rather than
        // damaged gameplay, so it is reported without discarding the stored progress.
        if (!saved.hasRequestedIdentity(requestedLaunch)) throw InvalidSavedSessionException()
        return try {
            withContext(workDispatcher) {
                val runtime = runtimeResolver(saved.generatorVersion)
                val puzzle = runtime.generator.generate(saved.puzzleSeed, saved.difficulty)
                require(puzzle.id.generatorVersion == saved.generatorVersion)
                val game =
                    WordSessionCodec.decode(
                        puzzle = puzzle,
                        allowedGuesses = runtime.allowedGuesses,
                        sessionFormatVersion = saved.sessionFormatVersion,
                        gameplayPayload = saved.gameplayPayload,
                        moveHistoryPayload = saved.moveHistoryPayload,
                        hintsUsed = saved.hintsUsed,
                        status = saved.status,
                    )
                LoadedSession(
                    puzzle,
                    WordGameEngine(puzzle, runtime.allowedGuesses),
                    game,
                    ActiveSession(saved.sessionId, puzzle, requestedLaunch.context),
                    isNew = false,
                )
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
            // The identity is the requested one but its gameplay cannot be restored: drop only this session.
            sessionRepository.deleteActiveSession(
                PuzzleType.WORD,
                requestedLaunch.context.sessionScope,
                saved.sessionId,
            )
            throw InvalidSavedSessionException()
        }
    }

    private fun SavedGameSession.hasRequestedIdentity(requestedLaunch: WordGameLaunch.Restore): Boolean =
        puzzleType == PuzzleType.WORD &&
            sessionScope == requestedLaunch.context.sessionScope &&
            matches(requestedLaunch.context) &&
            (
                requestedLaunch.expectedPuzzleId == null ||
                    PuzzleId(puzzleType, difficulty, puzzleSeed, generatorVersion) == requestedLaunch.expectedPuzzleId
            )

    private fun updateGame(update: (WordGameEngine, WordGameState) -> WordGameState) {
        val engine = gameEngine ?: return
        val current = mutableUiState.value as? WordGameUiState.Ready ?: return
        val updatedGame = update(engine, current.game)
        if (updatedGame == current.game) {
            if (current.rejection != null) mutableUiState.value = current.copy(rejection = null)
            return
        }
        mutableUiState.value = current.copy(game = updatedGame, rejection = null)
        persist(updatedGame)
    }

    private fun persist(game: WordGameState) {
        val session = activeSession ?: return
        if (game.isFinished) {
            persistCompletion(game)
        } else {
            sessionRepository.updateActiveSession(session.saved(game))
        }
    }

    private fun persistCompletion(game: WordGameState) {
        if (completionJob?.isActive == true) return
        val session = activeSession ?: return
        val ready = mutableUiState.value as? WordGameUiState.Ready ?: return
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
        game: WordGameState,
        persistence: CompletionPersistence,
    ) {
        val current = mutableUiState.value
        if (current is WordGameUiState.Ready && current.game == game) {
            mutableUiState.value = current.copy(completionPersistence = persistence)
        }
    }

    private data class LoadedSession(
        val puzzle: WordPuzzle,
        val engine: WordGameEngine,
        val game: WordGameState,
        val activeSession: ActiveSession,
        val isNew: Boolean,
    )

    private data class ActiveSession(
        val sessionId: String,
        val puzzle: WordPuzzle,
        val context: WordGameContext,
    ) {
        fun saved(game: WordGameState): SavedGameSession {
            val encoded = WordSessionCodec.encode(puzzle, game)
            return SavedGameSession(
                sessionId = sessionId,
                puzzleType = PuzzleType.WORD,
                sessionScope = context.sessionScope,
                difficulty = puzzle.id.difficulty,
                puzzleSeed = puzzle.id.seed,
                generatorVersion = puzzle.id.generatorVersion,
                dailyIdentity =
                    (context as? WordGameContext.Daily)?.let {
                        DailyGameSessionIdentity(it.challengeDate, it.policyVersion.value)
                    },
                sessionFormatVersion = WordSessionCodec.SESSION_FORMAT_VERSION,
                gameplayPayload = encoded.gameplayPayload,
                moveHistoryPayload = encoded.moveHistoryPayload,
                hintsUsed = encoded.hintsUsed,
                status = encoded.status,
            )
        }

        fun completion(game: WordGameState): GameCompletion {
            require(game.isFinished) { "Only a terminal Word game produces a result." }
            val dailyContext = context as? WordGameContext.Daily
            return GameCompletion(
                resultId = sessionId,
                puzzleType = PuzzleType.WORD,
                difficulty = puzzle.id.difficulty,
                puzzleSeed = puzzle.id.seed,
                generatorVersion = puzzle.id.generatorVersion,
                sessionScope = context.sessionScope,
                hintsUsed = 0,
                outcome =
                    when (game.status) {
                        WordGameStatus.SOLVED -> GameOutcome.SOLVED
                        else -> GameOutcome.FAILED
                    },
                attemptsUsed = game.attempts.size,
                challengeDate = dailyContext?.challengeDate,
                dailyPolicyVersion = dailyContext?.policyVersion,
            )
        }
    }

    private class MissingSavedSessionException : Exception()

    private class InvalidSavedSessionException : Exception()

    private fun SavedGameSession.matches(context: WordGameContext): Boolean =
        when (context) {
            WordGameContext.Catalog -> dailyIdentity == null
            is WordGameContext.Daily ->
                dailyIdentity == DailyGameSessionIdentity(context.challengeDate, context.policyVersion.value)
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
    private val launch: WordGameLaunch,
    private val sessionRepository: GameSessionRepository,
    private val completionRepository: GameCompletionRepository,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(WordGameViewModel::class.java)) {
            "Unknown ViewModel class: ${modelClass.name}"
        }
        @Suppress("UNCHECKED_CAST")
        return WordGameViewModel(launch, sessionRepository, completionRepository) as T
    }
}
