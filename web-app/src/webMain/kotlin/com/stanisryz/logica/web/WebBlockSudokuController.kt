package com.stanisryz.logica.web

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.stanisryz.logica.puzzle.core.blocksudoku.BlockSudokuEngine
import com.stanisryz.logica.puzzle.core.blocksudoku.BlockSudokuRules
import com.stanisryz.logica.puzzle.core.blocksudoku.BlockSudokuState
import com.stanisryz.logica.puzzle.core.blocksudoku.BlockSudokuStatus
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

internal sealed interface WebBlockSudokuState {
    data object DifficultySelection : WebBlockSudokuState

    data class Loading(
        val difficulty: Difficulty,
        val levelNumber: CatalogLevelNumber? = null,
        val launch: WebGameLaunch,
    ) : WebBlockSudokuState

    data class Playing(
        val source: WebGameplaySource,
        val game: BlockSudokuState,
    ) : WebBlockSudokuState {
        val hasMeaningfulProgress: Boolean get() = game.hasMeaningfulProgress
    }

    data class Error(
        val difficulty: Difficulty,
        val levelNumber: CatalogLevelNumber?,
        val failure: WebLoadFailure,
        val progressionUnavailable: Boolean = false,
        val launch: WebGameLaunch,
    ) : WebBlockSudokuState
}

/**
 * Web orchestration of Block Sudoku over its frozen seeds and Rules V1, plus its Daily entry
 * (Policy V7) on the Daily seed: the game is cleared at its target score and lost when no tray piece
 * fits, through the same progression, Daily, completion, statistics, and economy seams as the other
 * games. There are no hints or stars.
 */
internal class WebBlockSudokuController(
    private val loadPack: suspend (Difficulty) -> Unit,
    private val progression: WebCatalogProgressAccess,
    private val levelPack: CatalogLevelPack = BinaryCatalogLevelPack(WebPuzzleData),
    private val statistics: WebGameplayStatistics = DisabledWebGameplayStatistics,
    private val economy: WebGameplayEconomy = DisabledWebGameplayEconomy,
    private val daily: WebDailyGameplayAccess = DisabledWebDailyGameplay,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private var operation: Job? = null
    private var engine: BlockSudokuEngine? = null
    private var statisticsAttempt: WebStatisticsAttempt? = null
    private val completion = WebCatalogCompletionController(progression)
    private val dailyCompletion = WebDailyCompletionController(daily)

    var state by mutableStateOf<WebBlockSudokuState>(WebBlockSudokuState.DifficultySelection)
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
        val launch = WebGameLaunch.Catalog(PuzzleType.BLOCK_SUDOKU, difficulty)
        state = WebBlockSudokuState.Loading(difficulty, launch = launch)
        operation =
            scope.launch {
                var levelNumber: CatalogLevelNumber? = null
                try {
                    val attempt =
                        when (
                            val resolved =
                                progression.resolveCurrentLevel(PuzzleType.BLOCK_SUDOKU, difficulty, CatalogLevelPackVersion.V1)
                        ) {
                            is WebCatalogLevelResolution.Resolved -> resolved.attempt
                            is WebCatalogLevelResolution.Unavailable -> {
                                state =
                                    WebBlockSudokuState.Error(
                                        difficulty,
                                        null,
                                        WebLoadFailure.PROGRESS_UNAVAILABLE,
                                        progressionUnavailable = true,
                                        launch = launch,
                                    )
                                return@launch
                            }
                        }
                    levelNumber = attempt.levelId.levelNumber
                    state = WebBlockSudokuState.Loading(difficulty, levelNumber, launch)
                    loadPack(difficulty)
                    if (!progression.isCurrent(attempt)) return@launch
                    val definition = resolveLevel(attempt.levelId)
                    require(definition.generatorVersion == BlockSudokuRules.VERSION) {
                        "Block Sudoku level ${attempt.levelId.levelNumber.value} requires rules ${definition.generatorVersion.value}."
                    }
                    val nextEngine = BlockSudokuEngine(definition.seed, difficulty)
                    engine = nextEngine
                    completion.startAttempt(attempt)
                    statisticsAttempt = statistics.startAttempt(PuzzleType.BLOCK_SUDOKU, difficulty)
                    state = WebBlockSudokuState.Playing(WebGameplaySource.CatalogLevel(attempt, definition), nextEngine.start())
                } catch (exception: CancellationException) {
                    throw exception
                } catch (exception: Exception) {
                    engine = null
                    statisticsAttempt = null
                    completion.reset()
                    state =
                        WebBlockSudokuState.Error(
                            difficulty,
                            levelNumber,
                            exception.toLoggedWebLoadFailure("Block Sudoku level"),
                            launch = launch,
                        )
                }
            }
    }

    fun retryLoading() {
        val error = state as? WebBlockSudokuState.Error ?: return
        when (val launch = error.launch) {
            is WebGameLaunch.Catalog -> {
                if (error.progressionUnavailable) progression.retryContextBinding()
                selectDifficulty(error.difficulty)
            }
            is WebGameLaunch.Daily -> startDaily(launch.attempt)
        }
    }

    /** Starts the Daily Block Sudoku on the Daily seed, with no Catalog level involved. */
    fun startDaily(dailyAttempt: WebDailyAttempt) {
        operation?.cancel()
        statisticsAttempt = null
        completion.reset()
        val launch = WebGameLaunch.Daily(dailyAttempt)
        dailyCompletion.startAttempt(dailyAttempt)
        val entry = dailyAttempt.entry
        try {
            require(entry.generatorVersion == BlockSudokuRules.VERSION) {
                "Block Sudoku Daily requires rules ${entry.generatorVersion.value}."
            }
            val nextEngine = BlockSudokuEngine(entry.seed, entry.difficulty)
            engine = nextEngine
            statisticsAttempt = statistics.startAttempt(PuzzleType.BLOCK_SUDOKU, entry.difficulty)
            state = WebBlockSudokuState.Playing(WebGameplaySource.DailyChallenge(dailyAttempt), nextEngine.start())
        } catch (exception: Exception) {
            engine = null
            statisticsAttempt = null
            state =
                WebBlockSudokuState.Error(
                    entry.difficulty,
                    null,
                    exception.toLoggedWebLoadFailure("Block Sudoku Daily"),
                    launch = launch,
                )
        }
    }

    fun place(
        trayIndex: Int,
        row: Int,
        column: Int,
    ) {
        val playing = state as? WebBlockSudokuState.Playing ?: return
        val activeEngine = engine ?: return
        val updated = activeEngine.place(playing.game, trayIndex, row, column)
        if (updated == playing.game) return
        state = playing.copy(game = updated)
        if (!playing.game.status.isTerminal && updated.status.isTerminal) recordTerminal(playing, updated)
    }

    fun retry() {
        val playing = state as? WebBlockSudokuState.Playing ?: return
        if (playing.game.status != BlockSudokuStatus.FAILED) return
        val activeEngine = engine ?: return
        when (val source = playing.source) {
            is WebGameplaySource.CatalogLevel -> completion.startAttempt(source.attempt)
            is WebGameplaySource.DailyChallenge -> dailyCompletion.startAttempt(source.attempt)
        }
        statisticsAttempt = statistics.startAttempt(PuzzleType.BLOCK_SUDOKU, playing.source.difficulty)
        state = playing.copy(game = activeEngine.start())
    }

    fun nextLevel() {
        val playing = state as? WebBlockSudokuState.Playing ?: return
        val source = playing.source as? WebGameplaySource.CatalogLevel ?: return
        if (completion.state !is WebCatalogCompletionState.Saved) return
        selectDifficulty(source.attempt.levelId.difficulty)
    }

    fun retrySave() {
        val playing = state as? WebBlockSudokuState.Playing ?: return
        val source = playing.source as? WebGameplaySource.CatalogLevel ?: return
        if (playing.game.status != BlockSudokuStatus.SOLVED) return
        completion.saveSolved(source.attempt)
    }

    /** Repeats only the failed Daily local mutation; never Statistics, never a new gameplay attempt. */
    fun retryDailySave() {
        dailyCompletion.retrySave()
    }

    fun showDifficultySelector() {
        operation?.cancel()
        engine = null
        statisticsAttempt = null
        completion.reset()
        dailyCompletion.reset()
        state = WebBlockSudokuState.DifficultySelection
    }

    fun dispose() {
        scope.cancel()
    }

    private fun recordTerminal(
        playing: WebBlockSudokuState.Playing,
        updated: BlockSudokuState,
    ) {
        val solved = updated.status == BlockSudokuStatus.SOLVED
        statisticsAttempt?.let {
            statistics.recordTerminalResult(
                attempt = it,
                outcome = if (solved) WebStatisticsTerminalOutcome.SOLVED else WebStatisticsTerminalOutcome.FAILED,
            )
        }
        when (val source = playing.source) {
            is WebGameplaySource.CatalogLevel -> if (solved) completion.saveSolved(source.attempt)
            is WebGameplaySource.DailyChallenge ->
                dailyCompletion.saveTerminal(
                    source.attempt,
                    if (solved) WebStatisticsTerminalOutcome.SOLVED else WebStatisticsTerminalOutcome.FAILED,
                )
        }
        val gemsEarned =
            when {
                !solved -> 0
                playing.source is WebGameplaySource.CatalogLevel -> completion.gemsEarned
                else -> WebEconomyProcessor.dailyGemsFor(PuzzleType.BLOCK_SUDOKU, playing.source.difficulty, stars = null)
            }
        economy.recordTerminalResult(solved = solved, gemsEarned = gemsEarned)
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
            economy: WebGameplayEconomy = DisabledWebGameplayEconomy,
            daily: WebDailyGameplayAccess = DisabledWebDailyGameplay,
        ): WebBlockSudokuController =
            WebBlockSudokuController(
                loadPack = { difficulty ->
                    loader.loadCatalogLevelPack(
                        packVersion = CatalogLevelPackVersion.V1,
                        puzzleType = PuzzleType.BLOCK_SUDOKU,
                        difficulty = difficulty,
                    )
                },
                progression = progression,
                statistics = statistics,
                economy = economy,
                daily = daily,
            )
    }
}
