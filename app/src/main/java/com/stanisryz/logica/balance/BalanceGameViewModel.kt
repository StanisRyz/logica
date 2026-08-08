package com.stanisryz.logica.balance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.stanisryz.logica.puzzle.core.balance.BalanceGameEngine
import com.stanisryz.logica.puzzle.core.balance.BalanceGameState
import com.stanisryz.logica.puzzle.core.balance.BalanceGameStatus
import com.stanisryz.logica.puzzle.core.balance.BalanceGeneratorV1
import com.stanisryz.logica.puzzle.core.balance.BalancePosition
import com.stanisryz.logica.puzzle.core.balance.BalancePuzzle
import com.stanisryz.logica.puzzle.core.daily.DailyPolicyVersion
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.GeneratorVersion
import com.stanisryz.logica.puzzle.core.model.PuzzleId
import com.stanisryz.logica.puzzle.core.model.PuzzleSeed
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import com.stanisryz.logica.result.GameCompletion
import com.stanisryz.logica.result.GameCompletionRepository
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

internal sealed interface BalanceGameContext {
    val sessionScope: GameSessionScope

    data object Catalog : BalanceGameContext {
        override val sessionScope = GameSessionScope.CATALOG
    }

    data class Daily(
        val challengeDate: LocalDate,
        val policyVersion: DailyPolicyVersion,
    ) : BalanceGameContext {
        override val sessionScope = GameSessionScope.DAILY
    }
}

internal sealed interface BalanceGameLaunch {
    val context: BalanceGameContext

    data class New(
        val difficulty: Difficulty,
        val seed: PuzzleSeed,
        val generatorVersion: GeneratorVersion = GeneratorVersion(1),
        override val context: BalanceGameContext = BalanceGameContext.Catalog,
    ) : BalanceGameLaunch

    data class Restore(
        override val context: BalanceGameContext = BalanceGameContext.Catalog,
        val expectedPuzzleId: PuzzleId? = null,
    ) : BalanceGameLaunch
}

internal sealed interface BalanceGameUiState {
    data object Loading : BalanceGameUiState

    data class Ready(
        val puzzle: BalancePuzzle,
        val game: BalanceGameState,
        val isHintLoading: Boolean = false,
        val completionPersistence: CompletionPersistence = CompletionPersistence.NotRequired,
    ) : BalanceGameUiState

    data class Error(
        val reason: BalanceGameError,
    ) : BalanceGameUiState
}

internal enum class BalanceGameError {
    MISSING_SAVED_SESSION,
    INVALID_SAVED_SESSION,
    GENERATION,
}

internal sealed interface CompletionPersistence {
    data object NotRequired : CompletionPersistence

    data object Saving : CompletionPersistence

    data object Saved : CompletionPersistence

    data object Error : CompletionPersistence
}

internal class BalanceGameViewModel(
    private val launch: BalanceGameLaunch,
    private val sessionRepository: GameSessionRepository,
    private val completionRepository: GameCompletionRepository,
    private val generator: BalanceGeneratorV1 = BalanceGeneratorV1(),
    private val workDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val sessionIdFactory: () -> String = { UUID.randomUUID().toString() },
) : ViewModel() {
    private val mutableUiState = MutableStateFlow<BalanceGameUiState>(BalanceGameUiState.Loading)
    val uiState: StateFlow<BalanceGameUiState> = mutableUiState.asStateFlow()

    private var gameEngine: BalanceGameEngine? = null
    private var activeSession: ActiveSession? = null
    private var hintJob: Job? = null
    private var completionJob: Job? = null

    init {
        viewModelScope.launch {
            try {
                val session = loadSession()
                gameEngine = session.engine
                activeSession = session.activeSession
                mutableUiState.value = BalanceGameUiState.Ready(session.puzzle, session.game)
                if (session.isNew) {
                    sessionRepository.replaceActiveSession(session.activeSession.saved(session.game))
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: MissingSavedSessionException) {
                mutableUiState.value = BalanceGameUiState.Error(BalanceGameError.MISSING_SAVED_SESSION)
            } catch (_: InvalidSavedSessionException) {
                mutableUiState.value = BalanceGameUiState.Error(BalanceGameError.INVALID_SAVED_SESSION)
            } catch (_: Exception) {
                mutableUiState.value = BalanceGameUiState.Error(BalanceGameError.GENERATION)
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

                val current = mutableUiState.value
                if (current is BalanceGameUiState.Ready && current.game == requestedGame) {
                    mutableUiState.value = current.copy(game = hintedGame, isHintLoading = false)
                    if (hintedGame != requestedGame) persist(hintedGame)
                }
            }
    }

    fun retryCompletion() {
        val ready = mutableUiState.value as? BalanceGameUiState.Ready ?: return
        if (ready.game.status == BalanceGameStatus.SOLVED) persistCompletion(ready.game)
    }

    private suspend fun loadSession(): LoadedSession =
        when (val requestedLaunch = launch) {
            is BalanceGameLaunch.New ->
                withContext(workDispatcher) {
                    require(requestedLaunch.generatorVersion == generator.version)
                    val puzzle = generator.generate(requestedLaunch.seed, requestedLaunch.difficulty)
                    require(puzzle.id.generatorVersion == requestedLaunch.generatorVersion)
                    val engine = BalanceGameEngine(puzzle)
                    val game = engine.start()
                    LoadedSession(
                        puzzle = puzzle,
                        engine = engine,
                        game = game,
                        activeSession = ActiveSession(sessionIdFactory(), puzzle, requestedLaunch.context),
                        isNew = true,
                    )
                }
            is BalanceGameLaunch.Restore -> restoreSavedSession(requestedLaunch)
        }

    private suspend fun restoreSavedSession(requestedLaunch: BalanceGameLaunch.Restore): LoadedSession {
        val saved =
            sessionRepository.readActiveSession(PuzzleType.BALANCE, requestedLaunch.context.sessionScope)
                ?: throw MissingSavedSessionException()
        return try {
            withContext(workDispatcher) {
                require(saved.puzzleType == PuzzleType.BALANCE)
                require(saved.sessionScope == requestedLaunch.context.sessionScope)
                require(saved.generatorVersion == generator.version)
                require(saved.matches(requestedLaunch.context))
                val puzzle = generator.generate(saved.puzzleSeed, saved.difficulty)
                require(puzzle.id.generatorVersion == saved.generatorVersion)
                require(requestedLaunch.expectedPuzzleId == null || puzzle.id == requestedLaunch.expectedPuzzleId)
                val game =
                    BalanceSessionCodec.decode(
                        puzzle = puzzle,
                        sessionFormatVersion = saved.sessionFormatVersion,
                        gameplayPayload = saved.gameplayPayload,
                        moveHistoryPayload = saved.moveHistoryPayload,
                        hintsUsed = saved.hintsUsed,
                        status = saved.status,
                    )
                require(game.status == BalanceGameStatus.IN_PROGRESS)
                LoadedSession(
                    puzzle = puzzle,
                    engine = BalanceGameEngine(puzzle),
                    game = game,
                    activeSession = ActiveSession(saved.sessionId, puzzle, requestedLaunch.context),
                    isNew = false,
                )
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
            sessionRepository.deleteActiveSession(
                PuzzleType.BALANCE,
                requestedLaunch.context.sessionScope,
                saved.sessionId,
            )
            throw InvalidSavedSessionException()
        }
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
        persist(updatedGame)
    }

    private fun persist(game: BalanceGameState) {
        val session = activeSession ?: return
        if (game.status == BalanceGameStatus.SOLVED) {
            persistCompletion(game)
        } else {
            sessionRepository.updateActiveSession(session.saved(game))
        }
    }

    private fun persistCompletion(game: BalanceGameState) {
        if (completionJob?.isActive == true) return
        val session = activeSession ?: return
        val ready = mutableUiState.value as? BalanceGameUiState.Ready ?: return
        if (ready.completionPersistence == CompletionPersistence.Saved) return
        mutableUiState.value = ready.copy(completionPersistence = CompletionPersistence.Saving)
        completionJob =
            viewModelScope.launch {
                try {
                    completionRepository.complete(session.completion(game))
                    val current = mutableUiState.value
                    if (current is BalanceGameUiState.Ready && current.game == game) {
                        mutableUiState.value = current.copy(completionPersistence = CompletionPersistence.Saved)
                    }
                } catch (exception: CancellationException) {
                    throw exception
                } catch (_: Exception) {
                    val current = mutableUiState.value
                    if (current is BalanceGameUiState.Ready && current.game == game) {
                        mutableUiState.value = current.copy(completionPersistence = CompletionPersistence.Error)
                    }
                }
            }
    }

    private data class LoadedSession(
        val puzzle: BalancePuzzle,
        val engine: BalanceGameEngine,
        val game: BalanceGameState,
        val activeSession: ActiveSession,
        val isNew: Boolean,
    )

    private data class ActiveSession(
        val sessionId: String,
        val puzzle: BalancePuzzle,
        val context: BalanceGameContext,
    ) {
        fun saved(game: BalanceGameState): SavedGameSession {
            val encoded = BalanceSessionCodec.encode(game)
            return SavedGameSession(
                sessionId = sessionId,
                puzzleType = puzzle.id.type,
                sessionScope = context.sessionScope,
                difficulty = puzzle.id.difficulty,
                puzzleSeed = puzzle.id.seed,
                generatorVersion = puzzle.id.generatorVersion,
                dailyIdentity =
                    (context as? BalanceGameContext.Daily)?.let {
                        DailyGameSessionIdentity(it.challengeDate, it.policyVersion.value)
                    },
                sessionFormatVersion = BalanceSessionCodec.SESSION_FORMAT_VERSION,
                gameplayPayload = encoded.gameplayPayload,
                moveHistoryPayload = encoded.moveHistoryPayload,
                hintsUsed = encoded.hintsUsed,
                status = encoded.status,
            )
        }

        fun completion(game: BalanceGameState): GameCompletion {
            require(game.status == BalanceGameStatus.SOLVED)
            val dailyContext = context as? BalanceGameContext.Daily
            return GameCompletion(
                resultId = sessionId,
                puzzleType = puzzle.id.type,
                difficulty = puzzle.id.difficulty,
                puzzleSeed = puzzle.id.seed,
                generatorVersion = puzzle.id.generatorVersion,
                sessionScope = context.sessionScope,
                hintsUsed = game.hintsUsed,
                challengeDate = dailyContext?.challengeDate,
                dailyPolicyVersion = dailyContext?.policyVersion,
            )
        }
    }

    private class MissingSavedSessionException : Exception()

    private class InvalidSavedSessionException : Exception()

    private fun SavedGameSession.matches(context: BalanceGameContext): Boolean =
        when (context) {
            BalanceGameContext.Catalog -> dailyIdentity == null
            is BalanceGameContext.Daily ->
                dailyIdentity == DailyGameSessionIdentity(context.challengeDate, context.policyVersion.value)
        }
}

internal class BalanceGameViewModelFactory(
    private val launch: BalanceGameLaunch,
    private val sessionRepository: GameSessionRepository,
    private val completionRepository: GameCompletionRepository,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(BalanceGameViewModel::class.java)) {
            "Unknown ViewModel class: ${modelClass.name}"
        }

        @Suppress("UNCHECKED_CAST")
        return BalanceGameViewModel(launch, sessionRepository, completionRepository) as T
    }
}
