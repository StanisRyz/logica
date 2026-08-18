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
import com.stanisryz.logica.puzzle.core.crowns.CrownsGameEngine
import com.stanisryz.logica.puzzle.core.crowns.CrownsGameState
import com.stanisryz.logica.puzzle.core.crowns.CrownsGeneratorV1
import com.stanisryz.logica.puzzle.core.crowns.CrownsPlayerCell
import com.stanisryz.logica.puzzle.core.crowns.CrownsPosition
import com.stanisryz.logica.puzzle.core.crowns.CrownsPuzzle
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

internal sealed interface WebCrownsState {
    data object DifficultySelection : WebCrownsState

    data class Loading(
        val difficulty: Difficulty,
    ) : WebCrownsState

    data class Playing(
        val definition: CatalogLevelDefinition,
        val puzzle: CrownsPuzzle,
        val game: CrownsGameState,
        val selectedValue: CrownsPlayerCell = CrownsPlayerCell.CROWN,
        val isPencilMode: Boolean = false,
        val isHintLoading: Boolean = false,
    ) : WebCrownsState

    data class Error(
        val difficulty: Difficulty,
        val detail: String,
    ) : WebCrownsState
}

/** Lightweight Web adapter over frozen Crowns Level 1 and the common gameplay engine. */
internal class WebCrownsController(
    private val loadPack: suspend (Difficulty) -> Unit,
    private val levelPack: CatalogLevelPack = BinaryCatalogLevelPack(WebPuzzleData),
    private val generator: CrownsGeneratorV1 = CrownsGeneratorV1(),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private var operation: Job? = null
    private var engine: CrownsGameEngine? = null

    var state by mutableStateOf<WebCrownsState>(WebCrownsState.DifficultySelection)
        private set

    fun selectDifficulty(difficulty: Difficulty) {
        operation?.cancel()
        state = WebCrownsState.Loading(difficulty)
        operation =
            scope.launch {
                try {
                    loadPack(difficulty)
                    val definition = resolveLevelOne(difficulty)
                    require(definition.generatorVersion == generator.version) {
                        "Crowns Level 1 requires generator ${definition.generatorVersion.value}."
                    }
                    val puzzle = generator.generate(definition.seed, difficulty)
                    val nextEngine = CrownsGameEngine(puzzle)
                    engine = nextEngine
                    state = WebCrownsState.Playing(definition, puzzle, nextEngine.start())
                } catch (exception: CancellationException) {
                    throw exception
                } catch (exception: Exception) {
                    engine = null
                    state = WebCrownsState.Error(difficulty, exception.message ?: "Crowns Level 1 is unavailable.")
                }
            }
    }

    fun selectValue(value: CrownsPlayerCell) {
        if (value == CrownsPlayerCell.EMPTY) return
        val playing = state as? WebCrownsState.Playing ?: return
        if (playing.game.status.isTerminal || playing.selectedValue == value) return
        state = playing.copy(selectedValue = value)
    }

    fun togglePencilMode() {
        val playing = state as? WebCrownsState.Playing ?: return
        if (playing.game.status.isTerminal) return
        state = playing.copy(isPencilMode = !playing.isPencilMode)
    }

    fun onCellTapped(position: CrownsPosition) {
        val playing = state as? WebCrownsState.Playing ?: return
        if (playing.game.status.isTerminal) return
        val activeEngine = engine ?: return
        operation?.cancel()
        val updated =
            if (playing.isPencilMode) {
                activeEngine.togglePencilMark(playing.game, position, playing.selectedValue)
            } else {
                activeEngine.placeValue(playing.game, position, playing.selectedValue)
            }
        if (updated != playing.game) state = playing.copy(game = updated, isHintLoading = false)
    }

    fun requestHint() {
        val playing = state as? WebCrownsState.Playing ?: return
        if (playing.game.status.isTerminal || playing.isHintLoading || operation?.isActive == true) return
        val activeEngine = engine ?: return
        val requestedGame = playing.game
        state = playing.copy(isHintLoading = true)
        operation =
            scope.launch {
                val hinted =
                    try {
                        activeEngine.requestHint(requestedGame)
                    } catch (exception: CancellationException) {
                        throw exception
                    } catch (_: Exception) {
                        requestedGame
                    }
                val current = state
                if (current is WebCrownsState.Playing && current.game == requestedGame) {
                    state = current.copy(game = hinted, isHintLoading = false)
                }
            }
    }

    fun retry() {
        val playing = state as? WebCrownsState.Playing ?: return
        if (!playing.game.status.isTerminal) return
        val activeEngine = engine ?: return
        operation?.cancel()
        state =
            playing.copy(
                game = activeEngine.start(),
                isPencilMode = false,
                isHintLoading = false,
            )
    }

    fun showDifficultySelector() {
        operation?.cancel()
        engine = null
        state = WebCrownsState.DifficultySelection
    }

    fun dispose() {
        scope.cancel()
    }

    private fun resolveLevelOne(difficulty: Difficulty): CatalogLevelDefinition {
        val levelId =
            CatalogLevelId(
                puzzleType = PuzzleType.CROWNS,
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
        fun create(loader: BrowserPuzzleDataLoader): WebCrownsController =
            WebCrownsController(
                loadPack = { difficulty ->
                    loader.loadCatalogLevelPack(
                        packVersion = CatalogLevelPackVersion.V1,
                        puzzleType = PuzzleType.CROWNS,
                        difficulty = difficulty,
                    )
                },
            )
    }
}
