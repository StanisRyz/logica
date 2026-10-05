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
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.PuzzleStars
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import com.stanisryz.logica.puzzle.core.nonogram.NonogramGameEngine
import com.stanisryz.logica.puzzle.core.nonogram.NonogramGameState
import com.stanisryz.logica.puzzle.core.nonogram.NonogramGameStatus
import com.stanisryz.logica.puzzle.core.nonogram.NonogramGeneratorV1
import com.stanisryz.logica.puzzle.core.nonogram.NonogramGeneratorV2
import com.stanisryz.logica.puzzle.core.nonogram.NonogramPosition
import com.stanisryz.logica.puzzle.core.nonogram.NonogramPuzzle
import com.stanisryz.logica.puzzle.core.nonogram.NonogramTool
import com.stanisryz.logica.puzzle.core.web.WebPuzzleData
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal sealed interface WebNonogramState {
    data object DifficultySelection : WebNonogramState

    data class Loading(
        val difficulty: Difficulty,
        val levelNumber: CatalogLevelNumber? = null,
        val launch: WebGameLaunch,
    ) : WebNonogramState

    data class Playing(
        val source: WebGameplaySource,
        val puzzle: NonogramPuzzle,
        val game: NonogramGameState,
        val initial: NonogramGameState,
        val selectedTool: NonogramTool = NonogramTool.FILL,
    ) : WebNonogramState {
        val hasMeaningfulProgress: Boolean get() = game.hasMeaningfulProgress(initial)
    }

    data class Error(
        val difficulty: Difficulty,
        val levelNumber: CatalogLevelNumber?,
        val failure: WebLoadFailure,
        val progressionUnavailable: Boolean = false,
        val launch: WebGameLaunch,
    ) : WebNonogramState
}

/**
 * Web orchestration of the Nonogram: Catalog levels over the frozen pack and Generator V1, and the
 * Daily entry (Policy V6) as a Generator V2 real picture — the same progression, Daily, statistics,
 * economy, and hint-inventory seams as the other games.
 */
internal class WebNonogramController(
    private val loadPack: suspend (Difficulty) -> Unit,
    private val progression: WebCatalogProgressAccess,
    private val levelPack: CatalogLevelPack = BinaryCatalogLevelPack(WebPuzzleData),
    private val generator: NonogramGeneratorV1 = NonogramGeneratorV1(),
    private val statistics: WebGameplayStatistics = DisabledWebGameplayStatistics,
    private val economy: WebGameplayEconomy = DisabledWebGameplayEconomy,
    private val store: WebGameplayStore = DisabledWebGameplayStore,
    private val daily: WebDailyGameplayAccess = DisabledWebDailyGameplay,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private var operation: Job? = null
    private val galleryPackLock = Mutex()
    private var engine: NonogramGameEngine? = null
    private var statisticsAttempt: WebStatisticsAttempt? = null
    private val completion = WebCatalogCompletionController(progression)
    private val dailyCompletion = WebDailyCompletionController(daily)
    private val pictureGenerator = NonogramGeneratorV2()

    val dailyCompletionState: WebDailyCompletionState
        get() = dailyCompletion.state

    var state by mutableStateOf<WebNonogramState>(WebNonogramState.DifficultySelection)
        private set

    val completionState: WebCatalogCompletionState
        get() = completion.state

    /** Set when a hint was requested with an empty hint inventory; the host shows where to get more. */
    var hintsExhaustedNotice by mutableStateOf(false)
        private set

    fun selectDifficulty(difficulty: Difficulty) = launchCatalog(difficulty, replayLevel = null)

    /** A cleared [level] again from the level map; it can raise its stars and pays no gems. */
    fun replayLevel(
        difficulty: Difficulty,
        level: Int,
    ) = launchCatalog(difficulty, replayLevel = level)

    private fun launchCatalog(
        difficulty: Difficulty,
        replayLevel: Int?,
    ) {
        resetSecondChance()
        operation?.cancel()
        statisticsAttempt = null
        completion.reset()
        dailyCompletion.reset()
        val launch = WebGameLaunch.Catalog(PuzzleType.NONOGRAM, difficulty)
        state = WebNonogramState.Loading(difficulty, launch = launch)
        operation =
            scope.launch {
                var levelNumber: CatalogLevelNumber? = null
                try {
                    val attempt =
                        when (
                            val resolved =
                                (
                                    replayLevel?.let { progression.resolveReplayLevel(PuzzleType.NONOGRAM, difficulty, it) }
                                        ?: progression.resolveCurrentLevel(PuzzleType.NONOGRAM, difficulty, CatalogLevelPackVersion.V1)
                                )
                        ) {
                            is WebCatalogLevelResolution.Resolved -> resolved.attempt
                            is WebCatalogLevelResolution.Unavailable -> {
                                state =
                                    WebNonogramState.Error(
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
                    state = WebNonogramState.Loading(difficulty, levelNumber, launch)
                    loadPack(difficulty)
                    if (!progression.isCurrent(attempt)) return@launch
                    val definition = resolveLevel(attempt.levelId)
                    require(definition.generatorVersion == generator.version) {
                        "Nonogram level ${attempt.levelId.levelNumber.value} requires generator ${definition.generatorVersion.value}."
                    }
                    val puzzle = generator.generate(definition.seed, difficulty)
                    val nextEngine = NonogramGameEngine(puzzle)
                    engine = nextEngine
                    completion.startAttempt(attempt)
                    statisticsAttempt = statistics.startAttempt(PuzzleType.NONOGRAM, difficulty)
                    val initial = nextEngine.start()
                    state = WebNonogramState.Playing(WebGameplaySource.CatalogLevel(attempt, definition), puzzle, initial, initial)
                } catch (exception: CancellationException) {
                    throw exception
                } catch (exception: Exception) {
                    engine = null
                    statisticsAttempt = null
                    completion.reset()
                    state =
                        WebNonogramState.Error(
                            difficulty,
                            levelNumber,
                            exception.toWebLoadFailure(),
                            launch = launch,
                        )
                }
            }
    }

    fun retryLoading() {
        val error = state as? WebNonogramState.Error ?: return
        when (val launch = error.launch) {
            is WebGameLaunch.Catalog -> {
                if (error.progressionUnavailable) progression.retryContextBinding()
                selectDifficulty(error.difficulty)
            }
            is WebGameLaunch.Daily -> startDaily(launch.attempt)
        }
    }

    /** Starts the Daily Nonogram: a real picture from Generator V2, with no Catalog level involved. */
    fun startDaily(dailyAttempt: WebDailyAttempt) {
        resetSecondChance()
        operation?.cancel()
        statisticsAttempt = null
        completion.reset()
        val launch = WebGameLaunch.Daily(dailyAttempt)
        dailyCompletion.startAttempt(dailyAttempt)
        state = WebNonogramState.Loading(dailyAttempt.entry.difficulty, launch = launch)
        try {
            require(dailyAttempt.entry.generatorVersion == pictureGenerator.version) {
                "Nonogram Daily requires generator ${dailyAttempt.entry.generatorVersion.value}."
            }
            val puzzle = pictureGenerator.generate(dailyAttempt.entry.seed, dailyAttempt.entry.difficulty)
            val nextEngine = NonogramGameEngine(puzzle)
            engine = nextEngine
            statisticsAttempt = statistics.startAttempt(PuzzleType.NONOGRAM, dailyAttempt.entry.difficulty)
            val initial = nextEngine.start()
            state = WebNonogramState.Playing(WebGameplaySource.DailyChallenge(dailyAttempt), puzzle, initial, initial)
        } catch (exception: Exception) {
            engine = null
            statisticsAttempt = null
            state =
                WebNonogramState.Error(
                    dailyAttempt.entry.difficulty,
                    null,
                    exception.toWebLoadFailure(),
                    launch = launch,
                )
        }
    }

    fun selectTool(tool: NonogramTool) {
        val playing = state as? WebNonogramState.Playing ?: return
        if (playing.game.status.isTerminal || playing.selectedTool == tool) return
        state = playing.copy(selectedTool = tool)
    }

    fun onCell(position: NonogramPosition) {
        val playing = state as? WebNonogramState.Playing ?: return
        val activeEngine = engine ?: return
        val updated = activeEngine.mark(playing.game, position, playing.selectedTool)
        if (updated != playing.game) updateGame(playing, updated)
    }

    fun requestHint() {
        val playing = state as? WebNonogramState.Playing ?: return
        if (playing.game.status.isTerminal) return
        val activeEngine = engine ?: return
        val hinted = activeEngine.revealHint(playing.game)
        if (hinted == playing.game) return
        // A produced hint consumes one hint from the Player's inventory; without one the board stays.
        if (!store.tryConsumeHint()) {
            hintsExhaustedNotice = true
            return
        }
        updateGame(playing, hinted)
    }

    fun dismissHintsExhaustedNotice() {
        hintsExhaustedNotice = false
    }

    fun retry() {
        resetSecondChance()
        val playing = state as? WebNonogramState.Playing ?: return
        if (playing.game.status != NonogramGameStatus.FAILED) return
        if (dailyReplayBlocked(playing.source)) return
        val activeEngine = engine ?: return
        when (val source = playing.source) {
            is WebGameplaySource.CatalogLevel -> completion.startAttempt(source.attempt)
            is WebGameplaySource.DailyChallenge -> dailyCompletion.startAttempt(source.attempt)
        }
        statisticsAttempt = statistics.startAttempt(PuzzleType.NONOGRAM, playing.source.difficulty)
        val initial = activeEngine.start()
        state = playing.copy(game = initial, initial = initial)
    }

    fun nextLevel() {
        val playing = state as? WebNonogramState.Playing ?: return
        val source = playing.source as? WebGameplaySource.CatalogLevel ?: return
        if (completion.state !is WebCatalogCompletionState.Saved) return
        selectDifficulty(source.attempt.levelId.difficulty)
    }

    fun retrySave() {
        val playing = state as? WebNonogramState.Playing ?: return
        val source = playing.source as? WebGameplaySource.CatalogLevel ?: return
        if (playing.game.status != NonogramGameStatus.SOLVED) return
        completion.saveSolved(source.attempt, PuzzleStars.forMistakes(playing.game.mistakesUsed))
    }

    /** Repeats only the failed Daily local mutation; never Statistics, never a new gameplay attempt. */
    fun retryDailySave() {
        dailyCompletion.retrySave()
    }

    /** A solved Daily entry is never replayable from the terminal screen. */
    private fun dailyReplayBlocked(source: WebGameplaySource): Boolean =
        source is WebGameplaySource.DailyChallenge &&
            (dailyCompletion.state as? WebDailyCompletionState.Saved)?.outcome == WebStatisticsTerminalOutcome.SOLVED

    /** One second chance per attempt: the failed board waiting on its ad, recorded only if declined. */
    private var secondChanceUsed = false
    private var secondChanceOffer by mutableStateOf<Pair<WebNonogramState.Playing, NonogramGameState>?>(null)

    val secondChanceOffered: Boolean
        get() = secondChanceOffer != null

    /** The player ended the level instead: the failure is recorded as usual. */
    fun declineSecondChance() {
        val (playing, failed) = secondChanceOffer ?: return
        secondChanceOffer = null
        recordTerminal(playing, failed)
    }

    /** The rewarded ad was watched: the same board goes on with one mistake to spare. */
    fun continueAfterAd() {
        secondChanceOffer ?: return
        secondChanceOffer = null
        val playing = state as? WebNonogramState.Playing ?: return
        val activeEngine = engine ?: return
        state = playing.copy(game = activeEngine.continueAfterFailure(playing.game))
    }

    private fun resetSecondChance() {
        secondChanceUsed = false
        secondChanceOffer = null
    }

    fun showDifficultySelector() {
        resetSecondChance()
        operation?.cancel()
        hintsExhaustedNotice = false
        engine = null
        statisticsAttempt = null
        completion.reset()
        dailyCompletion.reset()
        state = WebNonogramState.DifficultySelection
    }

    /**
     * The picture of an already cleared [level] for the gallery, rebuilt from its frozen level.
     * Null when its bucket cannot be loaded; the gallery simply leaves that tile blank.
     */
    suspend fun galleryPicture(
        difficulty: Difficulty,
        level: Int,
    ): NonogramPuzzle? =
        runCatching {
            // The first visible tiles all ask at once; one of them fetches the bucket.
            galleryPackLock.withLock { loadPack(difficulty) }
            val definition = resolveLevel(CatalogLevelId(PuzzleType.NONOGRAM, difficulty, CatalogLevelNumber(level)))
            generator.generate(definition.seed, difficulty)
        }.getOrNull()

    fun dispose() {
        scope.cancel()
    }

    private fun updateGame(
        playing: WebNonogramState.Playing,
        updated: NonogramGameState,
    ) {
        state = playing.copy(game = updated)
        if (playing.game.status.isTerminal || !updated.status.isTerminal) return
        // The third mistake first offers the one ad-paid second chance; nothing is recorded yet.
        if (updated.status == NonogramGameStatus.FAILED && !secondChanceUsed) {
            secondChanceUsed = true
            secondChanceOffer = playing to updated
            return
        }
        recordTerminal(playing, updated)
    }

    private fun recordTerminal(
        playing: WebNonogramState.Playing,
        updated: NonogramGameState,
    ) {
        val solved = updated.status == NonogramGameStatus.SOLVED
        statisticsAttempt?.let {
            statistics.recordTerminalResult(
                attempt = it,
                outcome = if (solved) WebStatisticsTerminalOutcome.SOLVED else WebStatisticsTerminalOutcome.FAILED,
                hintsUsed = updated.hintsUsed,
            )
        }
        when (val source = playing.source) {
            is WebGameplaySource.CatalogLevel -> {
                if (solved) completion.saveSolved(source.attempt, PuzzleStars.forMistakes(updated.mistakesUsed))
                // A solved replay only raises stars and pays nothing; a failed one still costs a life.
                if (!(solved && source.attempt.replay)) {
                    economy.recordTerminalResult(PuzzleType.NONOGRAM, source.difficulty, solved = solved)
                }
            }
            is WebGameplaySource.DailyChallenge -> {
                dailyCompletion.saveTerminal(
                    source.attempt,
                    if (solved) WebStatisticsTerminalOutcome.SOLVED else WebStatisticsTerminalOutcome.FAILED,
                )
                economy.recordTerminalResult(PuzzleType.NONOGRAM, source.difficulty, solved = solved)
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
            economy: WebGameplayEconomy = DisabledWebGameplayEconomy,
            store: WebGameplayStore = DisabledWebGameplayStore,
            daily: WebDailyGameplayAccess = DisabledWebDailyGameplay,
        ): WebNonogramController =
            WebNonogramController(
                loadPack = { difficulty ->
                    loader.loadCatalogLevelPack(
                        packVersion = CatalogLevelPackVersion.V1,
                        puzzleType = PuzzleType.NONOGRAM,
                        difficulty = difficulty,
                    )
                },
                progression = progression,
                statistics = statistics,
                economy = economy,
                store = store,
                daily = daily,
            )
    }
}
