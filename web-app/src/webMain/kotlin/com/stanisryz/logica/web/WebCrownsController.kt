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
        val launch: WebGameLaunch,
    ) : WebCrownsState

    data class Playing(
        val source: WebGameplaySource,
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
        val launch: WebGameLaunch,
    ) : WebCrownsState
}

/** Lightweight Web adapter over authoritative frozen Crowns Catalog levels and the common engine. */
internal class WebCrownsController(
    private val loadPack: suspend (Difficulty) -> Unit,
    private val progression: WebCatalogProgressAccess,
    private val levelPack: CatalogLevelPack = BinaryCatalogLevelPack(WebPuzzleData),
    private val generator: CrownsGeneratorV1 = CrownsGeneratorV1(),
    private val statistics: WebGameplayStatistics = DisabledWebGameplayStatistics,
    private val daily: WebDailyGameplayAccess = DisabledWebDailyGameplay,
    private val economy: WebGameplayEconomy = DisabledWebGameplayEconomy,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private var operation: Job? = null
    private var engine: CrownsGameEngine? = null
    private var statisticsAttempt: WebStatisticsAttempt? = null
    private val completion = WebCatalogCompletionController(progression)
    private val dailyCompletion = WebDailyCompletionController(daily)

    var state by mutableStateOf<WebCrownsState>(WebCrownsState.DifficultySelection)
        private set

    val completionState: WebCatalogCompletionState
        get() = completion.state

    val dailyCompletionState: WebDailyCompletionState
        get() = dailyCompletion.state

    fun selectDifficulty(difficulty: Difficulty) {
        operation?.cancel()
        statisticsAttempt = null
        completion.reset()
        dailyCompletion.reset()
        state =
            WebCrownsState.Loading(
                difficulty,
                launch = WebGameLaunch.Catalog(PuzzleType.CROWNS, difficulty),
            )
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
                                state =
                                    WebCrownsState.Error(
                                        difficulty,
                                        null,
                                        resolved.detail,
                                        progressionUnavailable = true,
                                        launch = WebGameLaunch.Catalog(PuzzleType.CROWNS, difficulty),
                                    )
                                return@launch
                            }
                        }
                    levelNumber = attempt.levelId.levelNumber
                    state = WebCrownsState.Loading(difficulty, levelNumber, WebGameLaunch.Catalog(PuzzleType.CROWNS, difficulty))
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
                    state =
                        WebCrownsState.Playing(
                            WebGameplaySource.CatalogLevel(attempt, definition),
                            puzzle,
                            nextEngine.start(),
                        )
                } catch (exception: CancellationException) {
                    throw exception
                } catch (exception: Exception) {
                    engine = null
                    statisticsAttempt = null
                    completion.reset()
                    state =
                        WebCrownsState.Error(
                            difficulty,
                            levelNumber,
                            exception.message ?: "Crowns level is unavailable.",
                            progressionUnavailable = false,
                            launch = WebGameLaunch.Catalog(PuzzleType.CROWNS, difficulty),
                        )
                }
            }
    }

    fun retryLoading() {
        val error = state as? WebCrownsState.Error ?: return
        when (val launch = error.launch) {
            is WebGameLaunch.Catalog -> {
                if (error.progressionUnavailable) progression.retryContextBinding()
                selectDifficulty(launch.requestedDifficulty)
            }
            is WebGameLaunch.Daily -> startDaily(launch.attempt)
        }
    }

    /** Starts the deterministic Daily Crowns puzzle straight from its policy entry identity. */
    fun startDaily(dailyAttempt: WebDailyAttempt) {
        operation?.cancel()
        statisticsAttempt = null
        completion.reset()
        val launch = WebGameLaunch.Daily(dailyAttempt)
        dailyCompletion.startAttempt(dailyAttempt)
        state = WebCrownsState.Loading(dailyAttempt.entry.difficulty, launch = launch)
        operation =
            scope.launch {
                try {
                    require(dailyAttempt.entry.generatorVersion == generator.version) {
                        "Crowns Daily requires generator ${dailyAttempt.entry.generatorVersion.value}."
                    }
                    val puzzle = generator.generate(dailyAttempt.entry.seed, dailyAttempt.entry.difficulty)
                    val nextEngine = CrownsGameEngine(puzzle)
                    engine = nextEngine
                    statisticsAttempt = statistics.startAttempt(PuzzleType.CROWNS, dailyAttempt.entry.difficulty)
                    state =
                        WebCrownsState.Playing(
                            WebGameplaySource.DailyChallenge(dailyAttempt),
                            puzzle,
                            nextEngine.start(),
                        )
                } catch (exception: CancellationException) {
                    throw exception
                } catch (exception: Exception) {
                    engine = null
                    statisticsAttempt = null
                    state =
                        WebCrownsState.Error(
                            dailyAttempt.entry.difficulty,
                            null,
                            exception.message ?: "Crowns Daily is unavailable.",
                            progressionUnavailable = false,
                            launch = launch,
                        )
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
        if (dailyReplayBlocked(playing.source)) return
        val activeEngine = engine ?: return
        operation?.cancel()
        val source = playing.source
        if (source is WebGameplaySource.CatalogLevel) completion.startAttempt(source.attempt)
        (source as? WebGameplaySource.DailyChallenge)?.let { dailyCompletion.startAttempt(it.attempt) }
        statisticsAttempt = statistics.startAttempt(PuzzleType.CROWNS, playing.source.difficulty)
        state =
            playing.copy(
                game = activeEngine.start(),
                isPencilMode = false,
                isHintLoading = false,
            )
    }

    fun nextLevel() {
        val playing = state as? WebCrownsState.Playing ?: return
        val source = playing.source as? WebGameplaySource.CatalogLevel ?: return
        if (completion.state !is WebCatalogCompletionState.Saved) return
        selectDifficulty(source.attempt.levelId.difficulty)
    }

    fun retrySave() {
        val playing = state as? WebCrownsState.Playing ?: return
        val source = playing.source as? WebGameplaySource.CatalogLevel ?: return
        if (playing.game.status != CrownsGameStatus.SOLVED) return
        completion.saveSolved(source.attempt)
    }

    fun showDifficultySelector() {
        operation?.cancel()
        engine = null
        statisticsAttempt = null
        completion.reset()
        dailyCompletion.reset()
        state = WebCrownsState.DifficultySelection
    }

    /** Repeats only the failed Daily local mutation; never Statistics, never a new gameplay attempt. */
    fun retryDailySave() {
        dailyCompletion.retrySave()
    }

    /** A completed Daily entry is never replayable from the terminal screen. */
    private fun dailyReplayBlocked(source: WebGameplaySource): Boolean =
        source is WebGameplaySource.DailyChallenge &&
            dailyCompletion.state is WebDailyCompletionState.Saved &&
            (dailyCompletion.state as WebDailyCompletionState.Saved).outcome == WebStatisticsTerminalOutcome.SOLVED

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
            val outcome =
                if (updated.status == CrownsGameStatus.SOLVED) {
                    WebStatisticsTerminalOutcome.SOLVED
                } else {
                    WebStatisticsTerminalOutcome.FAILED
                }
            statisticsAttempt?.let {
                statistics.recordTerminalResult(
                    attempt = it,
                    outcome = outcome,
                    hintsUsed = updated.hintsUsed,
                )
            }
            when (val source = playing.source) {
                is WebGameplaySource.CatalogLevel -> {
                    // Daily never advances Catalog progression; Catalog completion stays here only.
                    if (updated.status == CrownsGameStatus.SOLVED) completion.saveSolved(source.attempt)
                    // Catalog terminals feed the wallet; Daily is intentionally absent here.
                    economy.recordCatalogTerminalResult(
                        PuzzleType.CROWNS,
                        source.attempt.levelId.difficulty,
                        solved = updated.status == CrownsGameStatus.SOLVED,
                    )
                }
                is WebGameplaySource.DailyChallenge ->
                    dailyCompletion.saveTerminal(source.attempt, outcome)
            }
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
            daily: WebDailyGameplayAccess = DisabledWebDailyGameplay,
            economy: WebGameplayEconomy = DisabledWebGameplayEconomy,
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
                daily = daily,
                economy = economy,
            )
    }
}
