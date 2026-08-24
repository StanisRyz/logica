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
import com.stanisryz.logica.puzzle.core.model.GeneratorVersion
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import com.stanisryz.logica.puzzle.core.web.WebPuzzleData
import com.stanisryz.logica.puzzle.core.word.WordGameEngine
import com.stanisryz.logica.puzzle.core.word.WordGameState
import com.stanisryz.logica.puzzle.core.word.WordGameStatus
import com.stanisryz.logica.puzzle.core.word.WordGuessRejection
import com.stanisryz.logica.puzzle.core.word.WordPuzzle
import com.stanisryz.logica.puzzle.core.word.WordRuntime
import com.stanisryz.logica.puzzle.core.word.WordRuntimeResolver
import com.stanisryz.logica.puzzle.core.word.WordSubmitResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

internal sealed interface WebWordState {
    data object DifficultySelection : WebWordState

    data class Loading(
        val difficulty: Difficulty,
        val levelNumber: CatalogLevelNumber? = null,
        val launch: WebGameLaunch,
    ) : WebWordState

    data class Playing(
        val source: WebGameplaySource,
        val puzzle: WordPuzzle,
        val game: WordGameState,
        val rejection: WordGuessRejection? = null,
        val rejectionRevision: Int = 0,
        val acceptedAttemptRevision: Int = 0,
        val revealedAttemptRevision: Int = 0,
    ) : WebWordState {
        val isTerminalRevealReady: Boolean
            get() =
                game.isFinished &&
                    (acceptedAttemptRevision == 0 || revealedAttemptRevision >= acceptedAttemptRevision)
    }

    data class Error(
        val difficulty: Difficulty,
        val levelNumber: CatalogLevelNumber?,
        val detail: String,
        val progressionUnavailable: Boolean = false,
        val launch: WebGameLaunch,
    ) : WebWordState
}

/** Lightweight Web adapter over authoritative frozen Word Catalog levels and the common runtime. */
internal class WebWordController(
    private val loadPack: suspend (Difficulty) -> Unit,
    private val loadRuntimeResources: suspend (List<String>) -> Unit,
    private val progression: WebCatalogProgressAccess,
    private val levelPack: CatalogLevelPack = BinaryCatalogLevelPack(WebPuzzleData),
    private val runtimeResolver: (GeneratorVersion) -> WordRuntime = WordRuntimeResolver::resolve,
    private val statistics: WebGameplayStatistics = DisabledWebGameplayStatistics,
    private val daily: WebDailyGameplayAccess = DisabledWebDailyGameplay,
    private val economy: WebGameplayEconomy = DisabledWebGameplayEconomy,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private var operation: Job? = null
    private var engine: WordGameEngine? = null
    private var statisticsAttempt: WebStatisticsAttempt? = null
    private val completion = WebCatalogCompletionController(progression)
    private val dailyCompletion = WebDailyCompletionController(daily)

    var state by mutableStateOf<WebWordState>(WebWordState.DifficultySelection)
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
            WebWordState.Loading(
                difficulty,
                launch = WebGameLaunch.Catalog(PuzzleType.WORD, difficulty),
            )
        operation =
            scope.launch {
                var levelNumber: CatalogLevelNumber? = null
                try {
                    val attempt =
                        when (
                            val resolved =
                                progression.resolveCurrentLevel(
                                    PuzzleType.WORD,
                                    difficulty,
                                    CatalogLevelPackVersion.V1,
                                )
                        ) {
                            is WebCatalogLevelResolution.Resolved -> resolved.attempt
                            is WebCatalogLevelResolution.Unavailable -> {
                                state =
                                    WebWordState.Error(
                                        difficulty,
                                        null,
                                        resolved.detail,
                                        progressionUnavailable = true,
                                        launch = WebGameLaunch.Catalog(PuzzleType.WORD, difficulty),
                                    )
                                return@launch
                            }
                        }
                    levelNumber = attempt.levelId.levelNumber
                    state = WebWordState.Loading(difficulty, levelNumber, WebGameLaunch.Catalog(PuzzleType.WORD, difficulty))
                    loadPack(difficulty)
                    if (!progression.isCurrent(attempt)) return@launch
                    val definition = resolveLevel(attempt.levelId)
                    val runtime = runtimeResolver(definition.generatorVersion)
                    require(runtime.generator.version == definition.generatorVersion) {
                        "Word level ${attempt.levelId.levelNumber.value} requires generator ${definition.generatorVersion.value}."
                    }
                    loadRuntimeResources(runtime.requiredResourcePaths)
                    if (!progression.isCurrent(attempt)) return@launch
                    val puzzle = runtime.generator.generate(definition.seed, difficulty)
                    require(puzzle.id.generatorVersion == definition.generatorVersion)
                    runtime.allowedGuesses.size
                    val nextEngine = WordGameEngine(puzzle, runtime.allowedGuesses)
                    engine = nextEngine
                    completion.startAttempt(attempt)
                    statisticsAttempt = statistics.startAttempt(PuzzleType.WORD, difficulty)
                    state =
                        WebWordState.Playing(
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
                        WebWordState.Error(
                            difficulty,
                            levelNumber,
                            exception.message ?: "Word level is unavailable.",
                            progressionUnavailable = false,
                            launch = WebGameLaunch.Catalog(PuzzleType.WORD, difficulty),
                        )
                }
            }
    }

    fun retryLoading() {
        val error = state as? WebWordState.Error ?: return
        when (val launch = error.launch) {
            is WebGameLaunch.Catalog -> {
                if (error.progressionUnavailable) progression.retryContextBinding()
                selectDifficulty(launch.requestedDifficulty)
            }
            is WebGameLaunch.Daily -> startDaily(launch.attempt)
        }
    }

    /** Starts the deterministic Daily Word puzzle from its policy entry identity and runtime. */
    fun startDaily(dailyAttempt: WebDailyAttempt) {
        operation?.cancel()
        statisticsAttempt = null
        completion.reset()
        val launch = WebGameLaunch.Daily(dailyAttempt)
        dailyCompletion.startAttempt(dailyAttempt)
        state = WebWordState.Loading(dailyAttempt.entry.difficulty, launch = launch)
        operation =
            scope.launch {
                try {
                    val difficulty = dailyAttempt.entry.difficulty
                    val runtime = runtimeResolver(dailyAttempt.entry.generatorVersion)
                    require(runtime.generator.version == dailyAttempt.entry.generatorVersion) {
                        "Word Daily requires generator ${dailyAttempt.entry.generatorVersion.value}."
                    }
                    loadRuntimeResources(runtime.requiredResourcePaths)
                    val puzzle = runtime.generator.generate(dailyAttempt.entry.seed, difficulty)
                    require(puzzle.id.generatorVersion == dailyAttempt.entry.generatorVersion)
                    runtime.allowedGuesses.size
                    val nextEngine = WordGameEngine(puzzle, runtime.allowedGuesses)
                    engine = nextEngine
                    statisticsAttempt = statistics.startAttempt(PuzzleType.WORD, difficulty)
                    state =
                        WebWordState.Playing(
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
                        WebWordState.Error(
                            dailyAttempt.entry.difficulty,
                            null,
                            exception.message ?: "Word Daily is unavailable.",
                            progressionUnavailable = false,
                            launch = launch,
                        )
                }
            }
    }

    fun setLetter(
        position: Int,
        letter: Char,
    ) {
        updateGame { activeEngine, game -> activeEngine.setLetter(game, position, letter) }
    }

    fun clearLetter(position: Int) {
        updateGame { activeEngine, game -> activeEngine.clearLetter(game, position) }
    }

    fun submit() {
        val activeEngine = engine ?: return
        val playing = state as? WebWordState.Playing ?: return
        when (val result = activeEngine.submit(playing.game)) {
            is WordSubmitResult.Rejected ->
                state =
                    playing.copy(
                        rejection = result.rejection,
                        rejectionRevision = playing.rejectionRevision + 1,
                    )
            is WordSubmitResult.Accepted ->
                playing
                    .copy(
                        game = result.state,
                        rejection = null,
                        acceptedAttemptRevision = playing.acceptedAttemptRevision + 1,
                    ).also { updated ->
                        state = updated
                        if (!playing.game.isFinished && updated.game.isFinished) {
                            val solved = updated.game.status == WordGameStatus.SOLVED
                            val outcome =
                                if (solved) WebStatisticsTerminalOutcome.SOLVED else WebStatisticsTerminalOutcome.FAILED
                            // Terminal persistence happens here immediately — it never waits for
                            // the reveal animation, and the reveal is never shortened by it.
                            val wordAttemptsUsed = updated.game.attempts.size.takeIf { solved }
                            statisticsAttempt?.let {
                                statistics.recordTerminalResult(
                                    attempt = it,
                                    outcome = outcome,
                                    wordAttemptsUsed = wordAttemptsUsed,
                                )
                            }
                            when (val source = playing.source) {
                                is WebGameplaySource.CatalogLevel -> {
                                    // Daily never advances Catalog progression; Catalog completion stays here only.
                                    if (solved) completion.saveSolved(source.attempt)
                                    // Catalog terminals feed the wallet; Daily is intentionally absent here.
                                    economy.recordCatalogTerminalResult(
                                        PuzzleType.WORD,
                                        source.attempt.levelId.difficulty,
                                        solved = solved,
                                    )
                                }
                                is WebGameplaySource.DailyChallenge ->
                                    dailyCompletion.saveTerminal(source.attempt, outcome, wordAttemptsUsed)
                            }
                        }
                    }
        }
    }

    fun dismissRejection() {
        val playing = state as? WebWordState.Playing ?: return
        if (playing.rejection != null) state = playing.copy(rejection = null)
    }

    fun onAcceptedAttemptRevealed(revision: Int) {
        val playing = state as? WebWordState.Playing ?: return
        if (revision <= playing.revealedAttemptRevision || revision > playing.acceptedAttemptRevision) return
        state = playing.copy(revealedAttemptRevision = revision)
    }

    fun retry() {
        val playing = state as? WebWordState.Playing ?: return
        if (playing.game.status != WordGameStatus.FAILED) return
        if (dailyReplayBlocked(playing.source)) return
        val activeEngine = engine ?: return
        operation?.cancel()
        val source = playing.source
        if (source is WebGameplaySource.CatalogLevel) completion.startAttempt(source.attempt)
        (source as? WebGameplaySource.DailyChallenge)?.let { dailyCompletion.startAttempt(it.attempt) }
        statisticsAttempt = statistics.startAttempt(PuzzleType.WORD, playing.source.difficulty)
        state =
            playing.copy(
                game = activeEngine.start(),
                rejection = null,
                rejectionRevision = 0,
                acceptedAttemptRevision = 0,
                revealedAttemptRevision = 0,
            )
    }

    fun nextLevel() {
        val playing = state as? WebWordState.Playing ?: return
        val source = playing.source as? WebGameplaySource.CatalogLevel ?: return
        if (completion.state !is WebCatalogCompletionState.Saved) return
        selectDifficulty(source.attempt.levelId.difficulty)
    }

    fun retrySave() {
        val playing = state as? WebWordState.Playing ?: return
        val source = playing.source as? WebGameplaySource.CatalogLevel ?: return
        if (playing.game.status != WordGameStatus.SOLVED) return
        completion.saveSolved(source.attempt)
    }

    fun showDifficultySelector() {
        operation?.cancel()
        engine = null
        statisticsAttempt = null
        completion.reset()
        dailyCompletion.reset()
        state = WebWordState.DifficultySelection
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

    private fun updateGame(update: (WordGameEngine, WordGameState) -> WordGameState) {
        val activeEngine = engine ?: return
        val playing = state as? WebWordState.Playing ?: return
        if (playing.game.isFinished) return
        val updated = update(activeEngine, playing.game)
        if (updated == playing.game) {
            if (playing.rejection != null) state = playing.copy(rejection = null)
            return
        }
        state = playing.copy(game = updated, rejection = null)
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
        ): WebWordController =
            WebWordController(
                loadPack = { difficulty ->
                    loader.loadCatalogLevelPack(
                        packVersion = CatalogLevelPackVersion.V1,
                        puzzleType = PuzzleType.WORD,
                        difficulty = difficulty,
                    )
                },
                loadRuntimeResources = loader::loadWordResources,
                progression = progression,
                statistics = statistics,
                daily = daily,
                economy = economy,
            )
    }
}
