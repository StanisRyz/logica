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
import com.stanisryz.logica.puzzle.core.game2048.Game2048Status
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
        val launch: WebGameLaunch,
    ) : Web2048State

    data class Playing(
        val source: WebGameplaySource,
        val game: Game2048State,
        val motionRevision: Long? = null,
        val motionTrace: Game2048MoveTrace? = null,
    ) : Web2048State

    data class Error(
        val difficulty: Difficulty,
        val levelNumber: CatalogLevelNumber?,
        val failure: WebLoadFailure,
        val progressionUnavailable: Boolean = false,
        val launch: WebGameLaunch,
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
    private val statistics: WebGameplayStatistics = DisabledWebGameplayStatistics,
    private val daily: WebDailyGameplayAccess = DisabledWebDailyGameplay,
    private val economy: WebGameplayEconomy = DisabledWebGameplayEconomy,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private var operation: Job? = null
    private var engine: Web2048GameEngine? = null
    private var statisticsAttempt: WebStatisticsAttempt? = null
    private var nextMotionRevision = 0L
    private val undoHistory = mutableListOf<Game2048State>()
    private val completion = WebCatalogCompletionController(progression)
    private val dailyCompletion = WebDailyCompletionController(daily)

    var state by mutableStateOf<Web2048State>(Web2048State.DifficultySelection)
        private set

    val completionState: WebCatalogCompletionState
        get() = completion.state

    val dailyCompletionState: WebDailyCompletionState
        get() = dailyCompletion.state

    val canUndo: Boolean
        get() =
            undoHistory.isNotEmpty() &&
                (state as? Web2048State.Playing)?.let {
                    it.game.status == Game2048Status.IN_PROGRESS && it.motionTrace == null
                } == true

    fun selectDifficulty(difficulty: Difficulty) {
        progression.publishBest2048()
        operation?.cancel()
        statisticsAttempt = null
        undoHistory.clear()
        completion.reset()
        dailyCompletion.reset()
        state =
            Web2048State.Loading(
                difficulty,
                launch = WebGameLaunch.Catalog(PuzzleType.GAME_2048, difficulty),
            )
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
                                state =
                                    Web2048State.Error(
                                        difficulty,
                                        null,
                                        WebLoadFailure.PROGRESS_UNAVAILABLE,
                                        progressionUnavailable = true,
                                        launch = WebGameLaunch.Catalog(PuzzleType.GAME_2048, difficulty),
                                    )
                                return@launch
                            }
                        }
                    levelNumber = attempt.levelId.levelNumber
                    state = Web2048State.Loading(difficulty, levelNumber, WebGameLaunch.Catalog(PuzzleType.GAME_2048, difficulty))
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
                    statisticsAttempt = statistics.startAttempt(PuzzleType.GAME_2048, difficulty)
                    state =
                        Web2048State.Playing(
                            WebGameplaySource.CatalogLevel(attempt, definition),
                            nextEngine.start(),
                        )
                } catch (exception: CancellationException) {
                    throw exception
                } catch (exception: Exception) {
                    engine = null
                    statisticsAttempt = null
                    completion.reset()
                    state =
                        Web2048State.Error(
                            difficulty,
                            levelNumber,
                            exception.toLoggedWebLoadFailure("2048 level"),
                            progressionUnavailable = false,
                            launch = WebGameLaunch.Catalog(PuzzleType.GAME_2048, difficulty),
                        )
                }
            }
    }

    fun retryLoading() {
        val error = state as? Web2048State.Error ?: return
        when (val launch = error.launch) {
            is WebGameLaunch.Catalog -> {
                if (error.progressionUnavailable) progression.retryContextBinding()
                selectDifficulty(launch.requestedDifficulty)
            }
            is WebGameLaunch.Daily -> startDaily(launch.attempt)
        }
    }

    /**
     * Starts Daily 2048 with explicitly different terminal semantics from Catalog: the V2 target
     * crossing is not a result here, and only the real final game state resolves the attempt.
     */
    fun startDaily(dailyAttempt: WebDailyAttempt) {
        progression.publishBest2048()
        operation?.cancel()
        statisticsAttempt = null
        undoHistory.clear()
        completion.reset()
        val launch = WebGameLaunch.Daily(dailyAttempt)
        dailyCompletion.startAttempt(dailyAttempt)
        state = Web2048State.Loading(dailyAttempt.entry.difficulty, launch = launch)
        operation =
            scope.launch {
                try {
                    val difficulty = dailyAttempt.entry.difficulty
                    val puzzleId =
                        Game2048PuzzleId(
                            seed = dailyAttempt.entry.seed,
                            difficulty = difficulty,
                            generatorVersion = dailyAttempt.entry.generatorVersion.toGame2048GeneratorVersion(),
                        )
                    val nextEngine = engineFactory(puzzleId)
                    engine = nextEngine
                    nextMotionRevision = 0L
                    statisticsAttempt = statistics.startAttempt(PuzzleType.GAME_2048, difficulty)
                    state =
                        Web2048State.Playing(
                            WebGameplaySource.DailyChallenge(dailyAttempt),
                            nextEngine.start(),
                        )
                } catch (exception: CancellationException) {
                    throw exception
                } catch (exception: Exception) {
                    engine = null
                    statisticsAttempt = null
                    state =
                        Web2048State.Error(
                            dailyAttempt.entry.difficulty,
                            null,
                            exception.toLoggedWebLoadFailure("2048 Daily"),
                            progressionUnavailable = false,
                            launch = launch,
                        )
                }
            }
    }

    fun move(direction: Game2048Direction) {
        val playing = state as? Web2048State.Playing ?: return
        if (playing.game.status.isTerminal || playing.motionTrace != null) return
        val transition = engine?.moveWithTrace(playing.game, direction) ?: return
        val trace = transition.trace ?: return
        val catalogGoalCrossing =
            playing.source is WebGameplaySource.CatalogLevel &&
                !playing.game.goalReached &&
                transition.state.goalReached
        if (transition.state.status.isTerminal || catalogGoalCrossing) {
            undoHistory.clear()
        } else {
            undoHistory += playing.game
            if (undoHistory.size > MAX_UNDO_HISTORY) undoHistory.removeAt(0)
        }
        nextMotionRevision += 1L
        state =
            playing.copy(
                game = transition.state,
                motionRevision = nextMotionRevision,
                motionTrace = trace,
            )
        // Every game's score is a candidate for the best one; it is published once the game ends.
        progression.recordBest2048(transition.state.score)
        if (transition.state.status.isTerminal) progression.publishBest2048()
        val firstGoalCrossing = !playing.game.goalReached && transition.state.goalReached
        when (val source = playing.source) {
            is WebGameplaySource.CatalogLevel -> {
                if (firstGoalCrossing) {
                    statisticsAttempt?.let {
                        statistics.recordTerminalResult(it, WebStatisticsTerminalOutcome.SOLVED)
                    }
                    completion.saveSolved(source.attempt)
                    // The first V2 target crossing is the Catalog result; it also feeds the wallet.
                    economy.recordTerminalResult(solved = true, gemsEarned = completion.gemsEarned)
                } else if (!playing.game.status.isTerminal && transition.state.status == Game2048Status.FAILED) {
                    statisticsAttempt?.let {
                        statistics.recordTerminalResult(it, WebStatisticsTerminalOutcome.FAILED)
                    }
                    // A pre-target game over is the normal Catalog failure and costs one life.
                    economy.recordTerminalResult(solved = false, gemsEarned = 0)
                }
            }
            is WebGameplaySource.DailyChallenge -> {
                // Daily 2048: a target crossing records nothing; only the real final game state
                // (game over) resolves exactly one Daily result and one Statistics result.
                if (!playing.game.status.isTerminal && transition.state.status.isTerminal) {
                    val outcome =
                        if (transition.state.status == Game2048Status.SOLVED) {
                            WebStatisticsTerminalOutcome.SOLVED
                        } else {
                            WebStatisticsTerminalOutcome.FAILED
                        }
                    statisticsAttempt?.let { statistics.recordTerminalResult(it, outcome) }
                    dailyCompletion.saveTerminal(source.attempt, outcome)
                    // A Daily result feeds the wallet like a Catalog one: a failure costs a life.
                    val solved = outcome == WebStatisticsTerminalOutcome.SOLVED
                    economy.recordTerminalResult(
                        solved = solved,
                        gemsEarned =
                            if (solved) {
                                WebEconomyProcessor.dailyGemsFor(
                                    PuzzleType.GAME_2048,
                                    source.difficulty,
                                    stars = null,
                                )
                            } else {
                                0
                            },
                    )
                }
            }
        }
    }

    fun undo() {
        val playing = state as? Web2048State.Playing ?: return
        if (playing.game.status.isTerminal || playing.motionTrace != null || undoHistory.isEmpty()) return
        val previous = undoHistory.removeAt(undoHistory.lastIndex)
        state = playing.copy(game = previous, motionRevision = null, motionTrace = null)
    }

    fun finishMotion(revision: Long) {
        val playing = state as? Web2048State.Playing ?: return
        if (playing.motionRevision == revision) {
            state = playing.copy(motionRevision = null, motionTrace = null)
        }
    }

    fun retry() {
        progression.publishBest2048()
        val playing = state as? Web2048State.Playing ?: return
        if (!playing.game.status.isTerminal || playing.motionTrace != null) return
        val source = playing.source
        val catalog = source as? WebGameplaySource.CatalogLevel
        // Catalog blocks a retry once the level is already cleared; a completed Daily entry is
        // never replayable either — only Daily FAILED remains a fresh real attempt.
        if (catalog != null) {
            if (playing.game.goalReached || completion.state != WebCatalogCompletionState.Idle) {
                return
            }
        } else if (dailyReplayBlocked(source)) {
            return
        }
        val activeEngine = engine ?: return
        operation?.cancel()
        undoHistory.clear()
        if (catalog != null) completion.startAttempt(catalog.attempt)
        (source as? WebGameplaySource.DailyChallenge)?.let { dailyCompletion.startAttempt(it.attempt) }
        statisticsAttempt = statistics.startAttempt(PuzzleType.GAME_2048, source.difficulty)
        state =
            playing.copy(
                game = activeEngine.retry(playing.game),
                motionRevision = null,
                motionTrace = null,
            )
    }

    fun nextLevel() {
        progression.publishBest2048()
        val playing = state as? Web2048State.Playing ?: return
        if (!playing.game.status.isTerminal) return
        val source = playing.source as? WebGameplaySource.CatalogLevel ?: return
        if (completion.state !is WebCatalogCompletionState.Saved) return
        selectDifficulty(source.attempt.levelId.difficulty)
    }

    fun retrySave() {
        val playing = state as? Web2048State.Playing ?: return
        val source = playing.source as? WebGameplaySource.CatalogLevel ?: return
        if (!playing.game.goalReached) return
        completion.saveSolved(source.attempt)
    }

    fun showDifficultySelector() {
        progression.publishBest2048()
        operation?.cancel()
        engine = null
        statisticsAttempt = null
        undoHistory.clear()
        completion.reset()
        dailyCompletion.reset()
        state = Web2048State.DifficultySelection
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
        progression.publishBest2048()
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
            statistics: WebGameplayStatistics = DisabledWebGameplayStatistics,
            daily: WebDailyGameplayAccess = DisabledWebDailyGameplay,
            economy: WebGameplayEconomy = DisabledWebGameplayEconomy,
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
                statistics = statistics,
                daily = daily,
                economy = economy,
            )
    }
}

private const val MAX_UNDO_HISTORY = 100
