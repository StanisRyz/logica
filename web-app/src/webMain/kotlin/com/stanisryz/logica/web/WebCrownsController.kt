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
import com.stanisryz.logica.puzzle.core.crowns.CrownsGameEngine
import com.stanisryz.logica.puzzle.core.crowns.CrownsGameState
import com.stanisryz.logica.puzzle.core.crowns.CrownsGameStatus
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
        val levelNumber: CatalogLevelNumber? = null,
    ) : WebCrownsState

    data class Playing(
        val attempt: WebCatalogAttempt,
        val definition: CatalogLevelDefinition,
        val puzzle: CrownsPuzzle,
        val game: CrownsGameState,
        val selectedValue: CrownsPlayerCell = CrownsPlayerCell.CROWN,
        val isPencilMode: Boolean = false,
        val isHintLoading: Boolean = false,
    ) : WebCrownsState

    data class Error(
        val difficulty: Difficulty,
        val levelNumber: CatalogLevelNumber?,
        val detail: String,
        val progressionUnavailable: Boolean = false,
    ) : WebCrownsState
}

/** Lightweight Web adapter over authoritative frozen Crowns Catalog levels and the common engine. */
internal class WebCrownsController(
    private val loadPack: suspend (Difficulty) -> Unit,
    private val progression: WebCatalogProgressAccess,
    private val levelPack: CatalogLevelPack = BinaryCatalogLevelPack(WebPuzzleData),
    private val generator: CrownsGeneratorV1 = CrownsGeneratorV1(),
    private val statistics: WebGameplayStatistics = DisabledWebGameplayStatistics,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private var operation: Job? = null
    private var engine: CrownsGameEngine? = null
    private var statisticsAttempt: WebStatisticsAttempt? = null
    private val completion = WebCatalogCompletionController(progression)

    var state by mutableStateOf<WebCrownsState>(WebCrownsState.DifficultySelection)
        private set

    val completionState: WebCatalogCompletionState
        get() = completion.state

    fun selectDifficulty(difficulty: Difficulty) {
        operation?.cancel()
        statisticsAttempt = null
        state = WebCrownsState.Loading(difficulty)
        operation =
            scope.launch {
                var levelNumber: CatalogLevelNumber? = null
                try {
                    val attempt =
                        when (
                            val resolved =
                                progression.resolveCurrentLevel(
                                    PuzzleType.CROWNS,
                                    difficulty,
                                    CatalogLevelPackVersion.V1,
                                )
                        ) {
                            is WebCatalogLevelResolution.Resolved -> resolved.attempt
                            is WebCatalogLevelResolution.Unavailable -> {
                                state = WebCrownsState.Error(difficulty, null, resolved.detail, progressionUnavailable = true)
                                return@launch
                            }
                        }
                    levelNumber = attempt.levelId.levelNumber
                    state = WebCrownsState.Loading(difficulty, levelNumber)
                    loadPack(difficulty)
                    if (!progression.isCurrent(attempt)) return@launch
                    val definition = resolveLevel(attempt.levelId)
                    require(definition.generatorVersion == generator.version) {
                        "Crowns level ${attempt.levelId.levelNumber.value} requires generator ${definition.generatorVersion.value}."
                    }
                    val puzzle = generator.generate(definition.seed, difficulty)
                    val nextEngine = CrownsGameEngine(puzzle)
                    engine = nextEngine
                    completion.startAttempt(attempt)
                    statisticsAttempt = statistics.startAttempt(PuzzleType.CROWNS, difficulty)
                    state = WebCrownsState.Playing(attempt, definition, puzzle, nextEngine.start())
                } catch (exception: CancellationException) {
                    throw exception
                } catch (exception: Exception) {
                    engine = null
                    statisticsAttempt = null
                    completion.reset()
                    state = WebCrownsState.Error(difficulty, levelNumber, exception.message ?: "Crowns level is unavailable.")
                }
            }
    }

    fun retryLoading() {
        val error = state as? WebCrownsState.Error ?: return
        if (error.progressionUnavailable) progression.retryContextBinding()
        selectDifficulty(error.difficulty)
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
        if (updated != playing.game) updateGame(playing, updated, isHintLoading = false)
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
                    updateGame(current, hinted, isHintLoading = false)
                }
            }
    }

    fun retry() {
        val playing = state as? WebCrownsState.Playing ?: return
        if (playing.game.status != CrownsGameStatus.FAILED) return
        val activeEngine = engine ?: return
        operation?.cancel()
        completion.startAttempt(playing.attempt)
        statisticsAttempt = statistics.startAttempt(PuzzleType.CROWNS, playing.definition.difficulty)
        state =
            playing.copy(
                game = activeEngine.start(),
                isPencilMode = false,
                isHintLoading = false,
            )
    }

    fun nextLevel() {
        val playing = state as? WebCrownsState.Playing ?: return
        if (completion.state !is WebCatalogCompletionState.Saved) return
        selectDifficulty(playing.attempt.levelId.difficulty)
    }

    fun retrySave() {
        val playing = state as? WebCrownsState.Playing ?: return
        if (playing.game.status != CrownsGameStatus.SOLVED) return
        completion.saveSolved(playing.attempt)
    }

    fun showDifficultySelector() {
        operation?.cancel()
        engine = null
        statisticsAttempt = null
        completion.reset()
        state = WebCrownsState.DifficultySelection
    }

    fun dispose() {
        scope.cancel()
    }

    private fun updateGame(
        playing: WebCrownsState.Playing,
        updated: CrownsGameState,
        isHintLoading: Boolean,
    ) {
        state = playing.copy(game = updated, isHintLoading = isHintLoading)
        if (!playing.game.status.isTerminal && updated.status.isTerminal) {
            statisticsAttempt?.let {
                statistics.recordTerminalResult(
                    attempt = it,
                    outcome =
                        if (updated.status == CrownsGameStatus.SOLVED) {
                            WebStatisticsTerminalOutcome.SOLVED
                        } else {
                            WebStatisticsTerminalOutcome.FAILED
                        },
                    hintsUsed = updated.hintsUsed,
                )
            }
            if (updated.status == CrownsGameStatus.SOLVED) completion.saveSolved(playing.attempt)
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
            statistics: WebGameplayStatistics = DisabledWebGameplayStatistics,
        ): WebCrownsController =
            WebCrownsController(
                loadPack = { difficulty ->
                    loader.loadCatalogLevelPack(
                        packVersion = CatalogLevelPackVersion.V1,
                        puzzleType = PuzzleType.CROWNS,
                        difficulty = difficulty,
                    )
                },
                progression = progression,
                statistics = statistics,
            )
    }
}
