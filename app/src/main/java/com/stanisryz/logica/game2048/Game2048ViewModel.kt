package com.stanisryz.logica.game2048

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.stanisryz.logica.catalog.CatalogLevelUnavailableException
import com.stanisryz.logica.catalog.GameAttempt
import com.stanisryz.logica.catalog.GameAttemptFactory
import com.stanisryz.logica.catalog.GameAttemptLaunch
import com.stanisryz.logica.economy.EconomyRepository
import com.stanisryz.logica.economy.PlayerEconomy
import com.stanisryz.logica.puzzle.core.game2048.Game2048Direction
import com.stanisryz.logica.puzzle.core.game2048.Game2048Engine
import com.stanisryz.logica.puzzle.core.game2048.Game2048GeneratorVersion
import com.stanisryz.logica.puzzle.core.game2048.Game2048MoveTrace
import com.stanisryz.logica.puzzle.core.game2048.Game2048PuzzleId
import com.stanisryz.logica.puzzle.core.game2048.Game2048Ruleset
import com.stanisryz.logica.puzzle.core.game2048.Game2048State
import com.stanisryz.logica.puzzle.core.game2048.Game2048Status
import com.stanisryz.logica.puzzle.core.model.GeneratorVersion
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

internal sealed interface Game2048UiState {
    data object Loading : Game2048UiState

    data class Ready(
        val game: Game2048State,
        val motionEvent: Game2048MotionEvent? = null,
        val completionPersistence: CompletionPersistence = CompletionPersistence.NotRequired,
        /**
         * Whether this Catalog level has already been cleared by crossing its score target. The board
         * keeps running afterwards, so this is a persistent presentation state rather than an ending.
         */
        val levelCleared: Boolean = false,
    ) : Game2048UiState {
        /** A cleared level is already durable, so leaving costs the player nothing worth warning about. */
        val hasMeaningfulProgress: Boolean
            get() = !game.status.isTerminal && !levelCleared && game.nextSpawnIndex > INITIAL_SPAWN_COUNT
    }

    data class Error(
        val reason: Game2048GameError,
    ) : Game2048UiState
}

/** One transient delivery of a deterministic core trace; it is never persisted. */
internal data class Game2048MotionEvent(
    val revision: Long,
    val trace: Game2048MoveTrace,
)

internal enum class Game2048GameError {
    LEVEL_UNAVAILABLE,
    NO_LIVES,
    START,
}

/**
 * Production 2048 lifecycle around the deterministic engine.
 *
 * A Catalog level clears the moment its score target is crossed: the result, the gems, and the level
 * progression all land once, right then, while the board keeps running as freeplay. Running out of
 * moves after that is not a failure and costs no life; running out before the target is the normal
 * failure. Daily keeps its V5 rules, where only the final score decides the outcome.
 */
internal class Game2048ViewModel(
    private val launch: GameAttemptLaunch,
    private val attemptFactory: GameAttemptFactory,
    private val completionRepository: GameCompletionRepository,
    private val economyRepository: EconomyRepository,
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
    private var attempt: GameAttempt? = null
    private var completionJob: Job? = null
    private var nextMotionRevision = 0L

    init {
        viewModelScope.launch {
            try {
                if (!economyRepository.refresh().isGameplayAllowed) throw NoLivesException()
                val resolved = attemptFactory.create(launch, PuzzleType.GAME_2048)
                val puzzleId =
                    Game2048PuzzleId(
                        seed = resolved.seed,
                        difficulty = resolved.difficulty,
                        generatorVersion = resolved.generatorVersion.toGame2048Version(),
                    )
                val gameEngine = Game2048Engine(puzzleId)
                engine = gameEngine
                attempt = resolved
                mutableUiState.value = Game2048UiState.Ready(gameEngine.start())
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: NoLivesException) {
                mutableUiState.value = Game2048UiState.Error(Game2048GameError.NO_LIVES)
            } catch (_: CatalogLevelUnavailableException) {
                mutableUiState.value = Game2048UiState.Error(Game2048GameError.LEVEL_UNAVAILABLE)
            } catch (_: Exception) {
                mutableUiState.value = Game2048UiState.Error(Game2048GameError.START)
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
        onStateAdvanced(transition.state)
    }

    fun finishMotion(revision: Long) {
        val current = mutableUiState.value as? Game2048UiState.Ready ?: return
        if (current.motionEvent?.revision == revision) {
            mutableUiState.value = current.copy(motionEvent = null)
        }
    }

    /** Retry is for a genuine failure; a cleared level moves on instead of being replayed. */
    fun retry() {
        if (!economy.value.isGameplayAllowed) return
        val current = mutableUiState.value as? Game2048UiState.Ready ?: return
        if (!current.game.status.isTerminal || current.levelCleared) return
        if (current.completionPersistence != CompletionPersistence.Saved) return
        val gameEngine = engine ?: return
        val previous = attempt ?: return
        attempt = previous.restarted(attemptFactory.nextAttemptId())
        mutableUiState.value = Game2048UiState.Ready(gameEngine.retry(current.game))
    }

    fun retryCompletion() {
        val current = mutableUiState.value as? Game2048UiState.Ready ?: return
        // A cleared level is a solve however the freeplay board stands right now.
        if (current.levelCleared) {
            persistCompletion(current.game, GameOutcome.SOLVED)
        } else if (current.game.status.isTerminal) {
            persistCompletion(current.game)
        }
    }

    /**
     * The two moments a 2048 attempt can become durable. Crossing a Catalog level's score target is
     * the first threshold crossing only; the terminal evaluation is skipped once that has happened,
     * so an eventual game over neither pays again nor removes a life.
     */
    private fun onStateAdvanced(game: Game2048State) {
        val current = mutableUiState.value as? Game2048UiState.Ready ?: return
        val isCatalogLevel = attempt?.isCatalog == true
        if (isCatalogLevel && !current.levelCleared && game.goalReached) {
            mutableUiState.value = current.copy(levelCleared = true)
            persistCompletion(game, GameOutcome.SOLVED)
            return
        }
        if (current.levelCleared) return
        if (game.status.isTerminal) persistCompletion(game)
    }

    private fun persistCompletion(
        game: Game2048State,
        forcedOutcome: GameOutcome? = null,
    ) {
        if (completionJob?.isActive == true) return
        val current = attempt ?: return
        val ready = mutableUiState.value as? Game2048UiState.Ready ?: return
        if (ready.completionPersistence == CompletionPersistence.Saved) return
        mutableUiState.value = ready.copy(completionPersistence = CompletionPersistence.Saving)
        val outcome =
            forcedOutcome
                ?: if (game.status == Game2048Status.SOLVED) GameOutcome.SOLVED else GameOutcome.FAILED
        val completion = current.completion(outcome)
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
        game: Game2048State,
        persistence: CompletionPersistence,
    ) {
        val current = mutableUiState.value
        if (current is Game2048UiState.Ready && current.game == game) {
            mutableUiState.value = current.copy(completionPersistence = persistence)
        }
    }

    private fun GeneratorVersion.toGame2048Version(): Game2048GeneratorVersion {
        val version = Game2048GeneratorVersion(value)
        require(Game2048Ruleset.isSupported(version)) { "Unsupported 2048 rules version: $value." }
        return version
    }

    private class NoLivesException : Exception()
}

private const val INITIAL_SPAWN_COUNT = 2L

internal class Game2048ViewModelFactory(
    private val launch: GameAttemptLaunch,
    private val attemptFactory: GameAttemptFactory,
    private val completionRepository: GameCompletionRepository,
    private val economyRepository: EconomyRepository,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(Game2048ViewModel::class.java)) {
            "Unknown ViewModel class: ${modelClass.name}"
        }
        @Suppress("UNCHECKED_CAST")
        return Game2048ViewModel(launch, attemptFactory, completionRepository, economyRepository) as T
    }
}
