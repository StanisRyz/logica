package com.stanisryz.logica.game2048

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.stanisryz.logica.economy.EconomyRepository
import com.stanisryz.logica.economy.PlayerEconomy
import com.stanisryz.logica.puzzle.core.daily.DailyPolicyVersion
import com.stanisryz.logica.puzzle.core.game2048.EncodedGame2048Session
import com.stanisryz.logica.puzzle.core.game2048.Game2048Direction
import com.stanisryz.logica.puzzle.core.game2048.Game2048Engine
import com.stanisryz.logica.puzzle.core.game2048.Game2048GeneratorVersion
import com.stanisryz.logica.puzzle.core.game2048.Game2048MoveTrace
import com.stanisryz.logica.puzzle.core.game2048.Game2048PuzzleId
import com.stanisryz.logica.puzzle.core.game2048.Game2048Ruleset
import com.stanisryz.logica.puzzle.core.game2048.Game2048SessionCodecV1
import com.stanisryz.logica.puzzle.core.game2048.Game2048State
import com.stanisryz.logica.puzzle.core.game2048.Game2048Status
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.GeneratorVersion
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID

/** Where a 2048 attempt belongs. The context alone decides its session scope and Daily identity. */
internal sealed interface Game2048GameContext {
    val sessionScope: GameSessionScope

    data object Catalog : Game2048GameContext {
        override val sessionScope = GameSessionScope.CATALOG
    }

    data class Daily(
        val challengeDate: LocalDate,
        val policyVersion: DailyPolicyVersion,
    ) : Game2048GameContext {
        override val sessionScope = GameSessionScope.DAILY
    }
}

internal val Game2048GameContext.dailyIdentity: DailyGameSessionIdentity?
    get() =
        (this as? Game2048GameContext.Daily)?.let {
            DailyGameSessionIdentity(it.challengeDate, it.policyVersion.value)
        }

internal sealed interface Game2048Launch {
    val context: Game2048GameContext

    /** A brand-new attempt: selected difficulty, fresh seed, and the current rules version. */
    data class New(
        val difficulty: Difficulty,
        val seed: PuzzleSeed,
        val generatorVersion: GeneratorVersion = NEW_GAME_VERSION,
        override val context: Game2048GameContext = Game2048GameContext.Catalog,
    ) : Game2048Launch

    data class Restore(
        override val context: Game2048GameContext = Game2048GameContext.Catalog,
        val expectedPuzzleId: Game2048PuzzleId? = null,
    ) : Game2048Launch

    companion object {
        /** New games are V2 (score targets); persisted V1 attempts keep playing as V1. */
        val NEW_GAME_VERSION = GeneratorVersion(Game2048GeneratorVersion.V2.value)
    }
}

internal sealed interface Game2048UiState {
    data object Loading : Game2048UiState

    data class Ready(
        val game: Game2048State,
        val motionEvent: Game2048MotionEvent? = null,
        val completionPersistence: CompletionPersistence = CompletionPersistence.NotRequired,
    ) : Game2048UiState

    data class Error(
        val reason: Game2048GameError,
    ) : Game2048UiState
}

/** One transient delivery of a deterministic core trace; it is never written to a game session. */
internal data class Game2048MotionEvent(
    val revision: Long,
    val trace: Game2048MoveTrace,
)

internal enum class Game2048GameError {
    MISSING_SAVED_SESSION,
    INVALID_SAVED_SESSION,
    NO_LIVES,
}

/**
 * Production Catalog lifecycle around the deterministic engine. New games are created as V2; a
 * restored game plays under the rules version that was persisted with it.
 */
internal class Game2048ViewModel(
    private val launch: Game2048Launch,
    private val sessionRepository: GameSessionRepository,
    private val completionRepository: GameCompletionRepository,
    private val economyRepository: EconomyRepository,
    private val sessionIdFactory: () -> String = { UUID.randomUUID().toString() },
) : ViewModel() {
    private val mutableUiState = MutableStateFlow<Game2048UiState>(Game2048UiState.Loading)
    val uiState: StateFlow<Game2048UiState> = mutableUiState.asStateFlow()
    val economy: StateFlow<PlayerEconomy> =
        economyRepository
            .observe()
            .stateIn(
                viewModelScope,
                SharingStarted.Eagerly,
                PlayerEconomy(lives = 0, nextLifeAtEpochMillis = Long.MAX_VALUE),
            )

    private var engine: Game2048Engine? = null
    private var activeSession: ActiveSession? = null
    private var completionJob: Job? = null
    private var nextMotionRevision = 0L

    init {
        viewModelScope.launch {
            try {
                val loaded = loadSession()
                engine = loaded.engine
                activeSession = loaded.activeSession
                mutableUiState.value = Game2048UiState.Ready(loaded.game)
                if (loaded.isNew) {
                    sessionRepository.replaceActiveSession(loaded.activeSession.saved(loaded.game))
                }
                if (loaded.game.status.isTerminal) persistCompletion(loaded.game)
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: MissingSavedSessionException) {
                mutableUiState.value = Game2048UiState.Error(Game2048GameError.MISSING_SAVED_SESSION)
            } catch (_: NoLivesException) {
                mutableUiState.value = Game2048UiState.Error(Game2048GameError.NO_LIVES)
            } catch (_: Exception) {
                mutableUiState.value = Game2048UiState.Error(Game2048GameError.INVALID_SAVED_SESSION)
            }
        }
    }

    fun move(direction: Game2048Direction) {
        if (!economy.value.isGameplayAllowed) return
        val current = mutableUiState.value as? Game2048UiState.Ready ?: return
        if (current.motionEvent != null) return
        val transition = engine?.moveWithTrace(current.game, direction) ?: return
        val trace = transition.trace ?: return
        nextMotionRevision += 1L
        mutableUiState.value =
            current.copy(
                game = transition.state,
                motionEvent = Game2048MotionEvent(nextMotionRevision, trace),
            )
        persist(transition.state)
    }

    fun finishMotion(revision: Long) {
        val current = mutableUiState.value as? Game2048UiState.Ready ?: return
        if (current.motionEvent?.revision == revision) {
            mutableUiState.value = current.copy(motionEvent = null)
        }
    }

    fun retry() {
        if (!economy.value.isGameplayAllowed) return
        val current = mutableUiState.value as? Game2048UiState.Ready ?: return
        if (!current.game.status.isTerminal || current.completionPersistence != CompletionPersistence.Saved) return
        val gameEngine = engine ?: return
        val previous = activeSession ?: return
        val nextSession = previous.copy(sessionId = sessionIdFactory())
        val restarted = gameEngine.retry(current.game)
        activeSession = nextSession
        mutableUiState.value = Game2048UiState.Ready(restarted)
        sessionRepository.replaceActiveSession(nextSession.saved(restarted))
    }

    fun retryCompletion() {
        val current = mutableUiState.value as? Game2048UiState.Ready ?: return
        if (current.game.status.isTerminal) persistCompletion(current.game)
    }

    private suspend fun loadSession(): LoadedSession =
        when (val requested = launch) {
            is Game2048Launch.New -> {
                if (!economyRepository.refresh().isGameplayAllowed) throw NoLivesException()
                val puzzleId =
                    Game2048PuzzleId(
                        seed = requested.seed,
                        difficulty = requested.difficulty,
                        generatorVersion = requested.generatorVersion.toGame2048Version(),
                    )
                val gameEngine = Game2048Engine(puzzleId)
                val game = gameEngine.start()
                LoadedSession(
                    engine = gameEngine,
                    game = game,
                    activeSession = ActiveSession(sessionIdFactory(), puzzleId, requested.context),
                    isNew = true,
                )
            }
            is Game2048Launch.Restore -> restore(requested)
        }

    private suspend fun restore(requested: Game2048Launch.Restore): LoadedSession {
        val saved =
            sessionRepository.readActiveSession(PuzzleType.GAME_2048, requested.context.sessionScope)
                ?: throw MissingSavedSessionException()
        if (!saved.hasRequestedIdentity(requested)) throw InvalidSavedSessionException()
        return try {
            val expectedId = saved.toGame2048PuzzleId()
            val game =
                Game2048SessionCodecV1.decode(
                    EncodedGame2048Session(
                        formatVersion = saved.sessionFormatVersion,
                        payload = saved.gameplayPayload,
                    ),
                )
            require(game.puzzleId == expectedId)
            require(saved.moveHistoryPayload.isEmpty())
            require(saved.hintsUsed == 0)
            require(saved.status == game.status.name)
            LoadedSession(
                engine = Game2048Engine(expectedId),
                game = game,
                activeSession = ActiveSession(saved.sessionId, expectedId, requested.context),
                isNew = false,
            )
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
            sessionRepository.deleteActiveSession(
                PuzzleType.GAME_2048,
                requested.context.sessionScope,
                saved.sessionId,
            )
            throw InvalidSavedSessionException()
        }
    }

    /**
     * A saved attempt is restored under the rules version it was persisted with, so an existing V1
     * game keeps its target-tile contract and is never silently converted to V2. A save belonging to
     * the other scope or to another Daily definition is never restored in its place.
     */
    private fun SavedGameSession.hasRequestedIdentity(requested: Game2048Launch.Restore): Boolean =
        puzzleType == PuzzleType.GAME_2048 &&
            sessionScope == requested.context.sessionScope &&
            dailyIdentity == requested.context.dailyIdentity &&
            generatorVersion.value > 0 &&
            Game2048Ruleset.isSupported(Game2048GeneratorVersion(generatorVersion.value)) &&
            (requested.expectedPuzzleId == null || toGame2048PuzzleId() == requested.expectedPuzzleId)

    private fun SavedGameSession.toGame2048PuzzleId(): Game2048PuzzleId =
        Game2048PuzzleId(
            seed = puzzleSeed,
            difficulty = difficulty,
            generatorVersion = Game2048GeneratorVersion(generatorVersion.value),
        )

    private fun GeneratorVersion.toGame2048Version(): Game2048GeneratorVersion {
        val version = Game2048GeneratorVersion(value)
        require(Game2048Ruleset.isSupported(version)) { "Unsupported 2048 rules version: $value." }
        return version
    }

    private fun persist(game: Game2048State) {
        val session = activeSession ?: return
        sessionRepository.updateActiveSession(session.saved(game))
        if (game.status.isTerminal) persistCompletion(game)
    }

    private fun persistCompletion(game: Game2048State) {
        if (completionJob?.isActive == true) return
        val session = activeSession ?: return
        val current = mutableUiState.value as? Game2048UiState.Ready ?: return
        if (current.completionPersistence == CompletionPersistence.Saved) return
        mutableUiState.value = current.copy(completionPersistence = CompletionPersistence.Saving)
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
        game: Game2048State,
        persistence: CompletionPersistence,
    ) {
        val current = mutableUiState.value
        if (current is Game2048UiState.Ready && current.game == game) {
            mutableUiState.value = current.copy(completionPersistence = persistence)
        }
    }

    private data class LoadedSession(
        val engine: Game2048Engine,
        val game: Game2048State,
        val activeSession: ActiveSession,
        val isNew: Boolean,
    )

    private data class ActiveSession(
        val sessionId: String,
        val puzzleId: Game2048PuzzleId,
        val context: Game2048GameContext,
    ) {
        fun saved(game: Game2048State): SavedGameSession {
            require(game.puzzleId == puzzleId)
            val encoded = Game2048SessionCodecV1.encode(game)
            return SavedGameSession(
                sessionId = sessionId,
                puzzleType = PuzzleType.GAME_2048,
                sessionScope = context.sessionScope,
                difficulty = puzzleId.difficulty,
                puzzleSeed = puzzleId.seed,
                generatorVersion = GeneratorVersion(puzzleId.generatorVersion.value),
                dailyIdentity = context.dailyIdentity,
                sessionFormatVersion = encoded.formatVersion,
                gameplayPayload = encoded.payload,
                moveHistoryPayload = "",
                hintsUsed = 0,
                status = game.status.name,
            )
        }

        fun completion(game: Game2048State): GameCompletion {
            require(game.puzzleId == puzzleId && game.status.isTerminal)
            val dailyContext = context as? Game2048GameContext.Daily
            return GameCompletion(
                resultId = sessionId,
                puzzleType = PuzzleType.GAME_2048,
                difficulty = puzzleId.difficulty,
                puzzleSeed = puzzleId.seed,
                generatorVersion = GeneratorVersion(puzzleId.generatorVersion.value),
                sessionScope = context.sessionScope,
                hintsUsed = 0,
                outcome = if (game.status == Game2048Status.SOLVED) GameOutcome.SOLVED else GameOutcome.FAILED,
                challengeDate = dailyContext?.challengeDate,
                dailyPolicyVersion = dailyContext?.policyVersion,
            )
        }
    }

    private class MissingSavedSessionException : Exception()

    private class InvalidSavedSessionException : Exception()

    private class NoLivesException : Exception()
}

internal class Game2048ViewModelFactory(
    private val launch: Game2048Launch,
    private val sessionRepository: GameSessionRepository,
    private val completionRepository: GameCompletionRepository,
    private val economyRepository: EconomyRepository,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(Game2048ViewModel::class.java)) {
            "Unknown ViewModel class: ${modelClass.name}"
        }
        @Suppress("UNCHECKED_CAST")
        return Game2048ViewModel(launch, sessionRepository, completionRepository, economyRepository) as T
    }
}
