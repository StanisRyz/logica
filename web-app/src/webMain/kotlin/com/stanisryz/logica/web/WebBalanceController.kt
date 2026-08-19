package com.stanisryz.logica.web

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.stanisryz.logica.puzzle.core.balance.BalanceCell
import com.stanisryz.logica.puzzle.core.balance.BalanceGameEngine
import com.stanisryz.logica.puzzle.core.balance.BalanceGameState
import com.stanisryz.logica.puzzle.core.balance.BalanceGameStatus
import com.stanisryz.logica.puzzle.core.balance.BalanceGeneratorV1
import com.stanisryz.logica.puzzle.core.balance.BalancePosition
import com.stanisryz.logica.puzzle.core.balance.BalancePuzzle
import com.stanisryz.logica.puzzle.core.catalog.BinaryCatalogLevelPack
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelDefinition
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelId
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelNumber
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelPack
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelPackResult
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelPackVersion
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

internal sealed interface WebBalanceState {
    data object DifficultySelection : WebBalanceState

    data class Loading(
        val difficulty: Difficulty,
        val levelNumber: CatalogLevelNumber? = null,
    ) : WebBalanceState

    data class Playing(
        val attempt: WebCatalogAttempt,
        val definition: CatalogLevelDefinition,
        val puzzle: BalancePuzzle,
        val game: BalanceGameState,
        val selectedValue: BalanceCell = BalanceCell.ONE,
        val isPencilMode: Boolean = false,
        val isHintLoading: Boolean = false,
    ) : WebBalanceState

    data class Error(
        val difficulty: Difficulty,
        val levelNumber: CatalogLevelNumber?,
        val detail: String,
        val progressionUnavailable: Boolean = false,
    ) : WebBalanceState
}

/** Minimal Web orchestration over the common frozen pack, generator, and game engine. */
internal class WebBalanceController(
    private val loadPack: suspend (Difficulty) -> Unit,
    private val progression: WebCatalogProgressAccess,
    private val levelPack: CatalogLevelPack = BinaryCatalogLevelPack(WebPuzzleData),
    private val generator: BalanceGeneratorV1 = BalanceGeneratorV1(),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private var operation: Job? = null
    private var engine: BalanceGameEngine? = null
    private val completion = WebCatalogCompletionController(progression)

    var state by mutableStateOf<WebBalanceState>(WebBalanceState.DifficultySelection)
        private set

    val completionState: WebCatalogCompletionState
        get() = completion.state

    fun selectDifficulty(difficulty: Difficulty) {
        operation?.cancel()
        state = WebBalanceState.Loading(difficulty)
        operation =
            scope.launch {
                var levelNumber: CatalogLevelNumber? = null
                try {
                    val attempt =
                        when (
                            val resolved =
                                progression.resolveCurrentLevel(
                                    PuzzleType.BALANCE,
                                    difficulty,
                                    CatalogLevelPackVersion.V1,
                                )
                        ) {
                            is WebCatalogLevelResolution.Resolved -> resolved.attempt
                            is WebCatalogLevelResolution.Unavailable -> {
                                state = WebBalanceState.Error(difficulty, null, resolved.detail, progressionUnavailable = true)
                                return@launch
                            }
                        }
                    levelNumber = attempt.levelId.levelNumber
                    state = WebBalanceState.Loading(difficulty, levelNumber)
                    loadPack(difficulty)
                    if (!progression.isCurrent(attempt)) return@launch
                    val definition = resolveLevel(attempt.levelId)
                    require(definition.generatorVersion == generator.version) {
                        "Balance level ${attempt.levelId.levelNumber.value} requires generator ${definition.generatorVersion.value}."
                    }
                    val puzzle = generator.generate(definition.seed, difficulty)
                    val nextEngine = BalanceGameEngine(puzzle)
                    engine = nextEngine
                    completion.startAttempt(attempt)
                    state = WebBalanceState.Playing(attempt, definition, puzzle, nextEngine.start())
                } catch (exception: CancellationException) {
                    throw exception
                } catch (exception: Exception) {
                    engine = null
                    completion.reset()
                    state =
                        WebBalanceState.Error(
                            difficulty,
                            levelNumber,
                            exception.message ?: "Balance level is unavailable.",
                        )
                }
            }
    }

    fun retryLoading() {
        val error = state as? WebBalanceState.Error ?: return
        if (error.progressionUnavailable) progression.retryContextBinding()
        selectDifficulty(error.difficulty)
    }

    fun selectValue(value: BalanceCell) {
        val playing = state as? WebBalanceState.Playing ?: return
        if (playing.game.status.isTerminal || playing.selectedValue == value) return
        state = playing.copy(selectedValue = value)
    }

    fun togglePencilMode() {
        val playing = state as? WebBalanceState.Playing ?: return
        if (playing.game.status.isTerminal) return
        state = playing.copy(isPencilMode = !playing.isPencilMode)
    }

    fun onCellTapped(position: BalancePosition) {
        val playing = state as? WebBalanceState.Playing ?: return
        if (playing.game.status.isTerminal) return
        val activeEngine = engine ?: return
        operation?.cancel()
        val updated =
            if (playing.isPencilMode) {
                activeEngine.togglePencilMark(playing.game, position, playing.selectedValue)
            } else {
                activeEngine.placeValue(playing.game, position, playing.selectedValue)
            }
        if (updated != playing.game) updateGame(playing, updated, isHintLoading = false)
    }

    fun requestHint() {
        val playing = state as? WebBalanceState.Playing ?: return
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
                if (current is WebBalanceState.Playing && current.game == requestedGame) {
                    updateGame(current, hinted, isHintLoading = false)
                }
            }
    }

    fun retry() {
        val playing = state as? WebBalanceState.Playing ?: return
        if (playing.game.status != BalanceGameStatus.FAILED) return
        val activeEngine = engine ?: return
        operation?.cancel()
        completion.startAttempt(playing.attempt)
        state =
            playing.copy(
                game = activeEngine.start(),
                isPencilMode = false,
                isHintLoading = false,
            )
    }

    fun nextLevel() {
        val playing = state as? WebBalanceState.Playing ?: return
        if (completion.state !is WebCatalogCompletionState.Saved) return
        selectDifficulty(playing.attempt.levelId.difficulty)
    }

    fun retrySave() {
        val playing = state as? WebBalanceState.Playing ?: return
        if (playing.game.status != BalanceGameStatus.SOLVED) return
        completion.saveSolved(playing.attempt)
    }

    fun showDifficultySelector() {
        operation?.cancel()
        engine = null
        completion.reset()
        state = WebBalanceState.DifficultySelection
    }

    fun dispose() {
        scope.cancel()
    }

    private fun updateGame(
        playing: WebBalanceState.Playing,
        updated: BalanceGameState,
        isHintLoading: Boolean,
    ) {
        state = playing.copy(game = updated, isHintLoading = isHintLoading)
        if (!playing.game.status.isTerminal && updated.status == BalanceGameStatus.SOLVED) {
            completion.saveSolved(playing.attempt)
        }
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
        ): WebBalanceController =
            WebBalanceController(
                loadPack = { difficulty ->
                    loader.loadCatalogLevelPack(
                        packVersion = CatalogLevelPackVersion.V1,
                        puzzleType = PuzzleType.BALANCE,
                        difficulty = difficulty,
                    )
                },
                progression = progression,
            )
    }
}
