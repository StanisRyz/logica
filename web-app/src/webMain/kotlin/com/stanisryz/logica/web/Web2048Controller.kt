package com.stanisryz.logica.web

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.stanisryz.logica.puzzle.core.catalog.BinaryCatalogLevelPack
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelDefinition
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelId
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelNumber
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelPack
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelPackResult
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelPackVersion
import com.stanisryz.logica.puzzle.core.game2048.Game2048Direction
import com.stanisryz.logica.puzzle.core.game2048.Game2048Engine
import com.stanisryz.logica.puzzle.core.game2048.Game2048MoveTrace
import com.stanisryz.logica.puzzle.core.game2048.Game2048MoveTransition
import com.stanisryz.logica.puzzle.core.game2048.Game2048PuzzleId
import com.stanisryz.logica.puzzle.core.game2048.Game2048State
import com.stanisryz.logica.puzzle.core.game2048.toGame2048GeneratorVersion
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import com.stanisryz.logica.puzzle.core.web.WebPuzzleData
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

internal sealed interface Web2048State {
    data object DifficultySelection : Web2048State

    data class Loading(
        val difficulty: Difficulty,
        val levelNumber: CatalogLevelNumber? = null,
    ) : Web2048State

    data class Playing(
        val attempt: WebCatalogAttempt,
        val definition: CatalogLevelDefinition,
        val game: Game2048State,
        val motionRevision: Long? = null,
        val motionTrace: Game2048MoveTrace? = null,
    ) : Web2048State

    data class Error(
        val difficulty: Difficulty,
        val levelNumber: CatalogLevelNumber?,
        val detail: String,
        val progressionUnavailable: Boolean = false,
    ) : Web2048State
}

internal interface Web2048GameEngine {
    fun start(): Game2048State

    fun moveWithTrace(
        state: Game2048State,
        direction: Game2048Direction,
    ): Game2048MoveTransition

    fun retry(state: Game2048State): Game2048State
}

private class CoreWeb2048GameEngine(
    puzzleId: Game2048PuzzleId,
) : Web2048GameEngine {
    private val delegate = Game2048Engine(puzzleId)

    override fun start(): Game2048State = delegate.start()

    override fun moveWithTrace(
        state: Game2048State,
        direction: Game2048Direction,
    ): Game2048MoveTransition = delegate.moveWithTrace(state, direction)

    override fun retry(state: Game2048State): Game2048State = delegate.retry(state)
}

/** Lightweight Web adapter over authoritative frozen 2048 Catalog levels and the common engine. */
internal class Web2048Controller(
    private val loadPack: suspend (Difficulty) -> Unit,
    private val progression: WebCatalogProgressAccess,
    private val levelPack: CatalogLevelPack = BinaryCatalogLevelPack(WebPuzzleData),
    private val engineFactory: (Game2048PuzzleId) -> Web2048GameEngine = ::CoreWeb2048GameEngine,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private var operation: Job? = null
    private var engine: Web2048GameEngine? = null
    private var nextMotionRevision = 0L
    private val completion = WebCatalogCompletionController(progression, scope)

    var state by mutableStateOf<Web2048State>(Web2048State.DifficultySelection)
        private set

    val completionState: WebCatalogCompletionState
        get() = completion.state

    fun selectDifficulty(difficulty: Difficulty) {
        operation?.cancel()
        state = Web2048State.Loading(difficulty)
        operation =
            scope.launch {
                var levelNumber: CatalogLevelNumber? = null
                try {
                    val attempt =
                        when (
                            val resolved =
                                progression.resolveCurrentLevel(
                                    PuzzleType.GAME_2048,
                                    difficulty,
                                    CatalogLevelPackVersion.V1,
                                )
                        ) {
                            is WebCatalogLevelResolution.Resolved -> resolved.attempt
                            is WebCatalogLevelResolution.Unavailable -> {
                                state = Web2048State.Error(difficulty, null, resolved.detail, progressionUnavailable = true)
                                return@launch
                            }
                        }
                    levelNumber = attempt.levelId.levelNumber
                    state = Web2048State.Loading(difficulty, levelNumber)
                    loadPack(difficulty)
                    if (!progression.isCurrent(attempt)) return@launch
                    val definition = resolveLevel(attempt.levelId)
                    val puzzleId =
                        Game2048PuzzleId(
                            seed = definition.seed,
                            difficulty = definition.difficulty,
                            generatorVersion = definition.generatorVersion.toGame2048GeneratorVersion(),
                        )
                    val nextEngine = engineFactory(puzzleId)
                    engine = nextEngine
                    nextMotionRevision = 0L
                    completion.startAttempt(attempt)
                    state = Web2048State.Playing(attempt, definition, nextEngine.start())
                } catch (exception: CancellationException) {
                    throw exception
                } catch (exception: Exception) {
                    engine = null
                    completion.reset()
                    state = Web2048State.Error(difficulty, levelNumber, exception.message ?: "2048 level is unavailable.")
                }
            }
    }

    fun retryLoading() {
        val error = state as? Web2048State.Error ?: return
        if (error.progressionUnavailable) progression.retryContextBinding()
        selectDifficulty(error.difficulty)
    }

    fun move(direction: Game2048Direction) {
        val playing = state as? Web2048State.Playing ?: return
        if (playing.game.status.isTerminal || playing.motionTrace != null) return
        val transition = engine?.moveWithTrace(playing.game, direction) ?: return
        val trace = transition.trace ?: return
        nextMotionRevision += 1L
        state =
            playing.copy(
                game = transition.state,
                motionRevision = nextMotionRevision,
                motionTrace = trace,
            )
        if (!playing.game.goalReached && transition.state.goalReached) {
            completion.saveSolved(playing.attempt)
        }
    }

    fun finishMotion(revision: Long) {
        val playing = state as? Web2048State.Playing ?: return
        if (playing.motionRevision == revision) {
            state = playing.copy(motionRevision = null, motionTrace = null)
        }
    }

    fun retry() {
        val playing = state as? Web2048State.Playing ?: return
        if (
            !playing.game.status.isTerminal ||
            playing.game.goalReached ||
            playing.motionTrace != null ||
            completion.state != WebCatalogCompletionState.Idle
        ) {
            return
        }
        val activeEngine = engine ?: return
        operation?.cancel()
        completion.startAttempt(playing.attempt)
        state =
            playing.copy(
                game = activeEngine.retry(playing.game),
                motionRevision = null,
                motionTrace = null,
            )
    }

    fun nextLevel() {
        val playing = state as? Web2048State.Playing ?: return
        if (!playing.game.status.isTerminal || completion.state !is WebCatalogCompletionState.Saved) return
        selectDifficulty(playing.attempt.levelId.difficulty)
    }

    fun retrySave() {
        val playing = state as? Web2048State.Playing ?: return
        if (!playing.game.goalReached) return
        completion.saveSolved(playing.attempt)
    }

    fun showDifficultySelector() {
        operation?.cancel()
        engine = null
        completion.reset()
        state = Web2048State.DifficultySelection
    }

    fun dispose() {
        scope.cancel()
    }

    private fun resolveLevel(levelId: CatalogLevelId): CatalogLevelDefinition =
        when (val resolved = levelPack.resolve(levelId)) {
            is CatalogLevelPackResult.Success -> resolved.value
            is CatalogLevelPackResult.Failure -> error(resolved.detail)
        }

    companion object {
        fun create(
            loader: BrowserPuzzleDataLoader,
            progression: WebCatalogProgressAccess,
        ): Web2048Controller =
            Web2048Controller(
                loadPack = { difficulty ->
                    loader.loadCatalogLevelPack(
                        packVersion = CatalogLevelPackVersion.V1,
                        puzzleType = PuzzleType.GAME_2048,
                        difficulty = difficulty,
                    )
                },
                progression = progression,
            )
    }
}
