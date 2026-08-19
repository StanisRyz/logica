package com.stanisryz.logica.web

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.stanisryz.logica.puzzle.core.catalog.BinaryCatalogLevelPack
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelDefinition
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelId
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelPack
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelPackResult
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelPackVersion
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelPacks
import com.stanisryz.logica.puzzle.core.game2048.Game2048Direction
import com.stanisryz.logica.puzzle.core.game2048.Game2048Engine
import com.stanisryz.logica.puzzle.core.game2048.Game2048MoveTrace
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
    ) : Web2048State

    data class Playing(
        val definition: CatalogLevelDefinition,
        val game: Game2048State,
        val motionRevision: Long? = null,
        val motionTrace: Game2048MoveTrace? = null,
        /** Session-only Catalog clear state; reaching it never ends an in-progress V2 board. */
        val levelCleared: Boolean = false,
    ) : Web2048State

    data class Error(
        val difficulty: Difficulty,
        val detail: String,
    ) : Web2048State
}

/** Lightweight Web adapter over frozen 2048 Level 1 and the common deterministic engine. */
internal class Web2048Controller(
    private val loadPack: suspend (Difficulty) -> Unit,
    private val levelPack: CatalogLevelPack = BinaryCatalogLevelPack(WebPuzzleData),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private var operation: Job? = null
    private var engine: Game2048Engine? = null
    private var nextMotionRevision = 0L

    var state by mutableStateOf<Web2048State>(Web2048State.DifficultySelection)
        private set

    fun selectDifficulty(difficulty: Difficulty) {
        operation?.cancel()
        state = Web2048State.Loading(difficulty)
        operation =
            scope.launch {
                try {
                    loadPack(difficulty)
                    val definition = resolveLevelOne(difficulty)
                    val puzzleId =
                        Game2048PuzzleId(
                            seed = definition.seed,
                            difficulty = definition.difficulty,
                            generatorVersion = definition.generatorVersion.toGame2048GeneratorVersion(),
                        )
                    val nextEngine = Game2048Engine(puzzleId)
                    engine = nextEngine
                    nextMotionRevision = 0L
                    state = Web2048State.Playing(definition, nextEngine.start())
                } catch (exception: CancellationException) {
                    throw exception
                } catch (exception: Exception) {
                    engine = null
                    state = Web2048State.Error(difficulty, exception.message ?: "2048 Level 1 is unavailable.")
                }
            }
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
                levelCleared = playing.levelCleared || transition.state.goalReached,
            )
    }

    fun finishMotion(revision: Long) {
        val playing = state as? Web2048State.Playing ?: return
        if (playing.motionRevision == revision) {
            state = playing.copy(motionRevision = null, motionTrace = null)
        }
    }

    fun retry() {
        val playing = state as? Web2048State.Playing ?: return
        if (!playing.game.status.isTerminal || playing.motionTrace != null) return
        val activeEngine = engine ?: return
        operation?.cancel()
        state =
            playing.copy(
                game = activeEngine.retry(playing.game),
                motionRevision = null,
                motionTrace = null,
                levelCleared = false,
            )
    }

    fun showDifficultySelector() {
        operation?.cancel()
        engine = null
        state = Web2048State.DifficultySelection
    }

    fun dispose() {
        scope.cancel()
    }

    private fun resolveLevelOne(difficulty: Difficulty): CatalogLevelDefinition {
        val levelId =
            CatalogLevelId(
                puzzleType = PuzzleType.GAME_2048,
                difficulty = difficulty,
                levelNumber = CatalogLevelPacks.FIRST_LEVEL,
                packVersion = CatalogLevelPackVersion.V1,
            )
        return when (val resolved = levelPack.resolve(levelId)) {
            is CatalogLevelPackResult.Success -> resolved.value
            is CatalogLevelPackResult.Failure -> error(resolved.detail)
        }
    }

    companion object {
        fun create(loader: BrowserPuzzleDataLoader): Web2048Controller =
            Web2048Controller(
                loadPack = { difficulty ->
                    loader.loadCatalogLevelPack(
                        packVersion = CatalogLevelPackVersion.V1,
                        puzzleType = PuzzleType.GAME_2048,
                        difficulty = difficulty,
                    )
                },
            )
    }
}
