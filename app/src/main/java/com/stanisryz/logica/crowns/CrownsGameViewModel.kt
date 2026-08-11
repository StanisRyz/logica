package com.stanisryz.logica.crowns

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.stanisryz.logica.economy.EconomyRepository
import com.stanisryz.logica.economy.PlayerEconomy
import com.stanisryz.logica.puzzle.core.crowns.CrownsGameEngine
import com.stanisryz.logica.puzzle.core.crowns.CrownsGameState
import com.stanisryz.logica.puzzle.core.crowns.CrownsGameStatus
import com.stanisryz.logica.puzzle.core.crowns.CrownsGeneratorV1
import com.stanisryz.logica.puzzle.core.crowns.CrownsPlayerCell
import com.stanisryz.logica.puzzle.core.crowns.CrownsPosition
import com.stanisryz.logica.puzzle.core.crowns.CrownsPuzzle
import com.stanisryz.logica.puzzle.core.daily.DailyPolicyVersion
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.GeneratorVersion
import com.stanisryz.logica.puzzle.core.model.PuzzleId
import com.stanisryz.logica.puzzle.core.model.PuzzleSeed
import com.stanisryz.logica.puzzle.core.model.PuzzleType
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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.UUID

internal sealed interface CrownsGameContext {
    val sessionScope: GameSessionScope

    data object Catalog : CrownsGameContext {
        override val sessionScope = GameSessionScope.CATALOG
    }

    data class Daily(
        val challengeDate: LocalDate,
        val policyVersion: DailyPolicyVersion,
    ) : CrownsGameContext {
        override val sessionScope = GameSessionScope.DAILY
    }
}

internal sealed interface CrownsGameLaunch {
    val context: CrownsGameContext

    data class New(
        val difficulty: Difficulty,
        val seed: PuzzleSeed,
        val generatorVersion: GeneratorVersion = GeneratorVersion(1),
        override val context: CrownsGameContext = CrownsGameContext.Catalog,
    ) : CrownsGameLaunch

    data class Restore(
        override val context: CrownsGameContext = CrownsGameContext.Catalog,
        val expectedPuzzleId: PuzzleId? = null,
    ) : CrownsGameLaunch
}

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
    ) : CrownsGameUiState

    data class Error(
        val reason: CrownsGameError,
    ) : CrownsGameUiState
}

internal enum class CrownsGameError {
    MISSING_SAVED_SESSION,
    INVALID_SAVED_SESSION,
    GENERATION,
}

internal class CrownsGameViewModel(
    private val launch: CrownsGameLaunch,
    private val sessionRepository: GameSessionRepository,
    private val completionRepository: GameCompletionRepository,
    economyRepository: EconomyRepository,
    private val generator: CrownsGeneratorV1 = CrownsGeneratorV1(),
    private val workDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val sessionIdFactory: () -> String = { UUID.randomUUID().toString() },
) : ViewModel() {
    private val mutableUiState = MutableStateFlow<CrownsGameUiState>(CrownsGameUiState.Loading)
    val uiState: StateFlow<CrownsGameUiState> = mutableUiState.asStateFlow()

    /** The live wallet: gameplay actions need a life, and the finished attempt reports its effect. */
    val economy: StateFlow<PlayerEconomy> =
        economyRepository.observe().stateIn(viewModelScope, SharingStarted.Eagerly, PlayerEconomy())

    private var gameEngine: CrownsGameEngine? = null
    private var activeSession: ActiveSession? = null
    private var hintJob: Job? = null
    private var completionJob: Job? = null

    init {
        viewModelScope.launch {
            try {
                val session = loadSession()
                gameEngine = session.engine
                activeSession = session.activeSession
                mutableUiState.value = CrownsGameUiState.Ready(session.puzzle, session.game)
                if (session.isNew) sessionRepository.replaceActiveSession(session.activeSession.saved(session.game))
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: MissingSavedSessionException) {
                mutableUiState.value = CrownsGameUiState.Error(CrownsGameError.MISSING_SAVED_SESSION)
            } catch (_: InvalidSavedSessionException) {
                mutableUiState.value = CrownsGameUiState.Error(CrownsGameError.INVALID_SAVED_SESSION)
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

    /**
     * Starts a fresh attempt at the very same puzzle: a new session ID, a clean board, no pencil
     * marks, and no mistakes. It waits for the finished attempt's result to be durably stored, so one
     * attempt always maps to exactly one session and at most one terminal result.
     */
    fun retry() {
        if (!economy.value.isGameplayAllowed) return
        val ready = mutableUiState.value as? CrownsGameUiState.Ready ?: return
        if (!ready.game.status.isTerminal) return
        if (ready.completionPersistence != CompletionPersistence.Saved) return
        val engine = gameEngine ?: return
        val previous = activeSession ?: return

        val session = previous.copy(sessionId = sessionIdFactory())
        val game = engine.start()
        activeSession = session
        mutableUiState.value =
            CrownsGameUiState.Ready(
                puzzle = ready.puzzle,
                game = game,
                selectedValue = ready.selectedValue,
            )
        sessionRepository.replaceActiveSession(session.saved(game))
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
                    if (hintedGame != requestedGame) persist(hintedGame)
                }
            }
    }

    fun retryCompletion() {
        val ready = mutableUiState.value as? CrownsGameUiState.Ready ?: return
        if (ready.game.status.isTerminal) persistCompletion(ready.game)
    }

    private suspend fun loadSession(): LoadedSession =
        when (val requestedLaunch = launch) {
            is CrownsGameLaunch.New ->
                withContext(workDispatcher) {
                    require(requestedLaunch.generatorVersion == generator.version)
                    val puzzle = generator.generate(requestedLaunch.seed, requestedLaunch.difficulty)
                    require(puzzle.id.generatorVersion == requestedLaunch.generatorVersion)
                    val engine = CrownsGameEngine(puzzle)
                    val game = engine.start()
                    LoadedSession(
                        puzzle,
                        engine,
                        game,
                        ActiveSession(sessionIdFactory(), puzzle, requestedLaunch.context),
                        isNew = true,
                    )
                }
            is CrownsGameLaunch.Restore -> restoreSavedSession(requestedLaunch)
        }

    private suspend fun restoreSavedSession(requestedLaunch: CrownsGameLaunch.Restore): LoadedSession {
        val saved =
            sessionRepository.readActiveSession(PuzzleType.CROWNS, requestedLaunch.context.sessionScope)
                ?: throw MissingSavedSessionException()
        // A save that belongs to another Daily definition is a persistence inconsistency rather than
        // damaged gameplay, so it is reported without discarding the stored progress.
        if (!saved.hasRequestedIdentity(requestedLaunch)) throw InvalidSavedSessionException()
        return try {
            withContext(workDispatcher) {
                require(saved.generatorVersion == generator.version)
                val puzzle = generator.generate(saved.puzzleSeed, saved.difficulty)
                require(puzzle.id.generatorVersion == saved.generatorVersion)
                val engine = CrownsGameEngine(puzzle)
                val game =
                    CrownsSessionCodec.decode(
                        puzzle = puzzle,
                        sessionFormatVersion = saved.sessionFormatVersion,
                        gameplayPayload = saved.gameplayPayload,
                        hintsUsed = saved.hintsUsed,
                        status = saved.status,
                        engine = engine,
                    )
                require(game.status == CrownsGameStatus.IN_PROGRESS)
                LoadedSession(
                    puzzle,
                    engine,
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
                PuzzleType.CROWNS,
                requestedLaunch.context.sessionScope,
                saved.sessionId,
            )
            throw InvalidSavedSessionException()
        }
    }

    private fun SavedGameSession.hasRequestedIdentity(requestedLaunch: CrownsGameLaunch.Restore): Boolean =
        puzzleType == PuzzleType.CROWNS &&
            sessionScope == requestedLaunch.context.sessionScope &&
            matches(requestedLaunch.context) &&
            (
                requestedLaunch.expectedPuzzleId == null ||
                    PuzzleId(puzzleType, difficulty, puzzleSeed, generatorVersion) == requestedLaunch.expectedPuzzleId
            )

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
        persist(updatedGame)
    }

    private fun persist(game: CrownsGameState) {
        val session = activeSession ?: return
        if (game.status.isTerminal) {
            persistCompletion(game)
        } else {
            sessionRepository.updateActiveSession(session.saved(game))
        }
    }

    private fun persistCompletion(game: CrownsGameState) {
        if (completionJob?.isActive == true) return
        val session = activeSession ?: return
        val ready = mutableUiState.value as? CrownsGameUiState.Ready ?: return
        if (ready.completionPersistence == CompletionPersistence.Saved) return
        mutableUiState.value = ready.copy(completionPersistence = CompletionPersistence.Saving)
        completionJob =
            viewModelScope.launch {
                try {
                    completionRepository.complete(session.completion(game))
                    val current = mutableUiState.value
                    if (current is CrownsGameUiState.Ready && current.game == game) {
                        mutableUiState.value = current.copy(completionPersistence = CompletionPersistence.Saved)
                    }
                } catch (exception: CancellationException) {
                    throw exception
                } catch (_: Exception) {
                    val current = mutableUiState.value
                    if (current is CrownsGameUiState.Ready && current.game == game) {
                        mutableUiState.value = current.copy(completionPersistence = CompletionPersistence.Error)
                    }
                }
            }
    }

    private data class LoadedSession(
        val puzzle: CrownsPuzzle,
        val engine: CrownsGameEngine,
        val game: CrownsGameState,
        val activeSession: ActiveSession,
        val isNew: Boolean,
    )

    private data class ActiveSession(
        val sessionId: String,
        val puzzle: CrownsPuzzle,
        val context: CrownsGameContext,
    ) {
        fun saved(game: CrownsGameState): SavedGameSession {
            val encoded = CrownsSessionCodec.encode(puzzle, game)
            return SavedGameSession(
                sessionId = sessionId,
                puzzleType = PuzzleType.CROWNS,
                sessionScope = context.sessionScope,
                difficulty = puzzle.id.difficulty,
                puzzleSeed = puzzle.id.seed,
                generatorVersion = puzzle.id.generatorVersion,
                dailyIdentity =
                    (context as? CrownsGameContext.Daily)?.let {
                        DailyGameSessionIdentity(it.challengeDate, it.policyVersion.value)
                    },
                sessionFormatVersion = CrownsSessionCodec.SESSION_FORMAT_VERSION,
                gameplayPayload = encoded.gameplayPayload,
                moveHistoryPayload = encoded.moveHistoryPayload,
                hintsUsed = encoded.hintsUsed,
                status = encoded.status,
            )
        }

        fun completion(game: CrownsGameState): GameCompletion {
            require(game.status.isTerminal) { "Only a terminal Crowns attempt produces a result." }
            val dailyContext = context as? CrownsGameContext.Daily
            return GameCompletion(
                resultId = sessionId,
                puzzleType = PuzzleType.CROWNS,
                difficulty = puzzle.id.difficulty,
                puzzleSeed = puzzle.id.seed,
                generatorVersion = puzzle.id.generatorVersion,
                sessionScope = context.sessionScope,
                hintsUsed = game.hintsUsed,
                outcome =
                    if (game.status == CrownsGameStatus.SOLVED) GameOutcome.SOLVED else GameOutcome.FAILED,
                challengeDate = dailyContext?.challengeDate,
                dailyPolicyVersion = dailyContext?.policyVersion,
            )
        }
    }

    private class MissingSavedSessionException : Exception()

    private class InvalidSavedSessionException : Exception()

    private fun SavedGameSession.matches(context: CrownsGameContext): Boolean =
        when (context) {
            CrownsGameContext.Catalog -> dailyIdentity == null
            is CrownsGameContext.Daily ->
                dailyIdentity == DailyGameSessionIdentity(context.challengeDate, context.policyVersion.value)
        }
}

internal class CrownsGameViewModelFactory(
    private val launch: CrownsGameLaunch,
    private val sessionRepository: GameSessionRepository,
    private val completionRepository: GameCompletionRepository,
    private val economyRepository: EconomyRepository,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(CrownsGameViewModel::class.java)) {
            "Unknown ViewModel class: ${modelClass.name}"
        }
        @Suppress("UNCHECKED_CAST")
        return CrownsGameViewModel(launch, sessionRepository, completionRepository, economyRepository) as T
    }
}
