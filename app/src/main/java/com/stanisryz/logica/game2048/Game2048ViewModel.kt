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
import com.stanisryz.logica.puzzle.core.game2048.Game2048MoveTrace
import com.stanisryz.logica.puzzle.core.game2048.Game2048PuzzleId
import com.stanisryz.logica.puzzle.core.game2048.Game2048State
import com.stanisryz.logica.puzzle.core.game2048.Game2048Status
import com.stanisryz.logica.puzzle.core.game2048.hasMeaningfulProgress
import com.stanisryz.logica.puzzle.core.game2048.toGame2048GeneratorVersion
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
        /** Gems the saved result actually credited, for the result card. */
        val gemsEarned: Int = 0,
        /**
         * Whether this Catalog level has already been cleared by crossing its score target. The board
         * keeps running afterwards, so this is a persistent presentation state rather than an ending.
         */
        val levelCleared: Boolean = false,
        val canUndo: Boolean = false,
        /** The player ended the game with «Finish» after reaching the target: it is over like a game over. */
        val finishedByPlayer: Boolean = false,
        /** A dead end before the target waits on the one ad-paid undo of the losing move. */
        val undoOffered: Boolean = false,
    ) : Game2048UiState {
        /**
         * A reached target stays guarded until its completion transaction is actually durable, and an
         * open undo offer counts as an unfinished level.
         */
        val hasMeaningfulProgress: Boolean
            get() =
                undoOffered ||
                    !finishedByPlayer &&
                    game.hasMeaningfulProgress(levelCleared, completionSaved = completionPersistence == CompletionPersistence.Saved)

        /** Over for the player: no move is left, or they finished after the target. */
        val isOver: Boolean
            get() = game.status.isTerminal || finishedByPlayer
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
    private val bestScore: Game2048BestScore? = null,
) : ViewModel() {
    private val undoHistory = mutableListOf<Game2048State>()
    private val mutableUiState = MutableStateFlow<Game2048UiState>(Game2048UiState.Loading)
    val uiState: StateFlow<Game2048UiState> = mutableUiState.asStateFlow()
    val economy: StateFlow<PlayerEconomy> =
        economyRepository
            .observe()
            .stateIn(
                viewModelScope,
                SharingStarted.Eagerly,
                PlayerEconomy.LOADING,
            )

    private var engine: Game2048Engine? = null
    private var attempt: GameAttempt? = null
    private var completionJob: Job? = null
    private var nextMotionRevision = 0L

    /** One undo offer per attempt, and the board before the losing move while it is open. */
    private var undoOfferUsed = false
    private var stateBeforeLoss: Game2048State? = null

    init {
        viewModelScope.launch {
            try {
                if (!economyRepository.refresh().isGameplayAllowed) throw NoLivesException()
                val resolved = attemptFactory.create(launch, PuzzleType.GAME_2048)
                val puzzleId =
                    Game2048PuzzleId(
                        seed = resolved.seed,
                        difficulty = resolved.difficulty,
                        generatorVersion = resolved.generatorVersion.toGame2048GeneratorVersion(),
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
        if (current.motionEvent != null || current.finishedByPlayer) return
        val transition = engine?.moveWithTrace(current.game, direction) ?: return
        val trace = transition.trace ?: return
        val catalogGoalCrossing = attempt?.isCatalog == true && !current.game.goalReached && transition.state.goalReached
        // A dead end before the target first offers to take the losing move back for an ad, once per
        // attempt; nothing is recorded until the player answers, and the undo history stays for after.
        val offersUndo = transition.state.status == Game2048Status.FAILED && !current.levelCleared && !undoOfferUsed
        if (offersUndo) {
            undoOfferUsed = true
            stateBeforeLoss = current.game
        } else if (transition.state.status.isTerminal || catalogGoalCrossing) {
            undoHistory.clear()
        } else {
            undoHistory += current.game
            if (undoHistory.size > MAX_UNDO_HISTORY) undoHistory.removeAt(0)
        }
        nextMotionRevision += 1L
        mutableUiState.value =
            current.copy(
                game = transition.state,
                motionEvent = Game2048MotionEvent(nextMotionRevision, trace),
                canUndo = !offersUndo && undoHistory.isNotEmpty(),
                undoOffered = offersUndo,
            )
        // Every game's score is a candidate for the best one, whether the level was cleared or not.
        bestScore?.offer(transition.state.score)
        if (offersUndo) return
        onStateAdvanced(transition.state)
    }

    fun undo() {
        if (!economy.value.isGameplayAllowed) return
        val current = mutableUiState.value as? Game2048UiState.Ready ?: return
        if (current.isOver || current.motionEvent != null || undoHistory.isEmpty()) return
        val previous = undoHistory.removeAt(undoHistory.lastIndex)
        mutableUiState.value =
            current.copy(
                game = previous,
                motionEvent = null,
                canUndo = undoHistory.isNotEmpty(),
            )
    }

    /**
     * «Finish» after the target: the game ends with its current score. A Daily entry records its one
     * SOLVED result now, exactly as a game over after the target would; a Catalog level was already
     * cleared at the crossing, so this only ends its freeplay and shows the result card. Ending is
     * not leaving the game, so it never brings an ad.
     */
    fun finishGame() {
        val current = mutableUiState.value as? Game2048UiState.Ready ?: return
        if (!current.game.goalReached || current.isOver || current.motionEvent != null) return
        undoHistory.clear()
        mutableUiState.value = current.copy(finishedByPlayer = true, canUndo = false)
        bestScore?.offer(current.game.score)
        if (attempt?.isCatalog != true) persistCompletion(current.game, GameOutcome.SOLVED)
    }

    /** The player ended the level instead of watching the ad: the failure is recorded and costs a life. */
    fun declineUndoOffer() {
        val current = mutableUiState.value as? Game2048UiState.Ready ?: return
        if (!current.undoOffered) return
        stateBeforeLoss = null
        undoHistory.clear()
        mutableUiState.value = current.copy(undoOffered = false, canUndo = false)
        onStateAdvanced(current.game)
    }

    /**
     * The rewarded ad was watched: the board goes back to just before the losing move and play goes
     * on, with no economy effect. The spawn index goes back too, so the same move would bring the
     * same frozen spawn again.
     */
    fun undoLosingMoveAfterAd() {
        val current = mutableUiState.value as? Game2048UiState.Ready ?: return
        val previous = stateBeforeLoss ?: return
        if (!current.undoOffered) return
        stateBeforeLoss = null
        mutableUiState.value =
            current.copy(game = previous, motionEvent = null, undoOffered = false, canUndo = undoHistory.isNotEmpty())
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
        undoHistory.clear()
        undoOfferUsed = false
        stateBeforeLoss = null
        attempt = previous.restarted(attemptFactory.nextAttemptId())
        mutableUiState.value = Game2048UiState.Ready(gameEngine.retry(current.game))
    }

    fun retryCompletion() {
        val current = mutableUiState.value as? Game2048UiState.Ready ?: return
        // A cleared level, or a game finished after its target, is a solve however the board stands.
        if (current.levelCleared || current.finishedByPlayer) {
            persistCompletion(current.game, GameOutcome.SOLVED)
        } else if (current.game.status.isTerminal && !current.undoOffered) {
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
                    val saved = completionRepository.complete(completion)
                    updateCompletionPersistence(current.resultId, CompletionPersistence.Saved, saved.gemsEarned)
                } catch (exception: CancellationException) {
                    throw exception
                } catch (_: Exception) {
                    updateCompletionPersistence(current.resultId, CompletionPersistence.Error)
                }
            }
    }

    private fun updateCompletionPersistence(
        resultId: String,
        persistence: CompletionPersistence,
        gemsEarned: Int = 0,
    ) {
        val current = mutableUiState.value
        if (current is Game2048UiState.Ready && attempt?.resultId == resultId) {
            mutableUiState.value = current.copy(completionPersistence = persistence, gemsEarned = gemsEarned)
        }
    }

    private class NoLivesException : Exception()
}

private const val MAX_UNDO_HISTORY = 100

internal class Game2048ViewModelFactory(
    private val launch: GameAttemptLaunch,
    private val attemptFactory: GameAttemptFactory,
    private val completionRepository: GameCompletionRepository,
    private val economyRepository: EconomyRepository,
    private val bestScore: Game2048BestScore? = null,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(Game2048ViewModel::class.java)) {
            "Unknown ViewModel class: ${modelClass.name}"
        }
        @Suppress("UNCHECKED_CAST")
        return Game2048ViewModel(launch, attemptFactory, completionRepository, economyRepository, bestScore) as T
    }
}
