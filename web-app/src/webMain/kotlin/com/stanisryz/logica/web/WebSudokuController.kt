package com.stanisryz.logica.web

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import com.stanisryz.logica.puzzle.core.sudoku.BinarySudokuDataset
import com.stanisryz.logica.puzzle.core.sudoku.SudokuCatalogProvider
import com.stanisryz.logica.puzzle.core.sudoku.SudokuCellStatus
import com.stanisryz.logica.puzzle.core.sudoku.SudokuDataset
import com.stanisryz.logica.puzzle.core.sudoku.SudokuDatasetResult
import com.stanisryz.logica.puzzle.core.sudoku.SudokuDatasetVersion
import com.stanisryz.logica.puzzle.core.sudoku.SudokuDifficulty
import com.stanisryz.logica.puzzle.core.sudoku.SudokuGameEngine
import com.stanisryz.logica.puzzle.core.sudoku.SudokuGameState
import com.stanisryz.logica.puzzle.core.sudoku.SudokuGameStatus
import com.stanisryz.logica.puzzle.core.sudoku.SudokuPosition
import com.stanisryz.logica.puzzle.core.sudoku.SudokuPuzzle
import com.stanisryz.logica.puzzle.core.sudoku.toSudokuDifficulty
import com.stanisryz.logica.puzzle.core.web.WebPuzzleData
import com.stanisryz.logica.ui.components.GameKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

internal sealed interface WebSudokuState {
    data object DifficultySelection : WebSudokuState

    data class Loading(
        val difficulty: Difficulty,
        val levelNumber: CatalogLevelNumber? = null,
        val launch: WebGameLaunch,
    ) : WebSudokuState

    data class Playing(
        val source: WebGameplaySource,
        val puzzle: SudokuPuzzle,
        val game: SudokuGameState,
        val selectedCell: SudokuPosition? = null,
        val isPencilMode: Boolean = false,
    ) : WebSudokuState

    data class Error(
        val difficulty: Difficulty,
        val levelNumber: CatalogLevelNumber?,
        val detail: String,
        val progressionUnavailable: Boolean = false,
        val launch: WebGameLaunch,
    ) : WebSudokuState
}

/** Lightweight Web adapter over authoritative frozen Sudoku Catalog levels and Dataset V1. */
internal class WebSudokuController(
    private val loadPack: suspend (Difficulty) -> Unit,
    private val loadDataset: suspend (SudokuDatasetVersion, SudokuDifficulty) -> Unit,
    private val progression: WebCatalogProgressAccess,
    private val levelPack: CatalogLevelPack = BinaryCatalogLevelPack(WebPuzzleData),
    dataset: SudokuDataset = BinarySudokuDataset(WebPuzzleData),
    private val statistics: WebGameplayStatistics = DisabledWebGameplayStatistics,
    private val daily: WebDailyGameplayAccess = DisabledWebDailyGameplay,
    private val economy: WebGameplayEconomy = DisabledWebGameplayEconomy,
    private val store: WebGameplayStore = DisabledWebGameplayStore,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private data class UndoFrame(
        val game: SudokuGameState,
        val selectedCell: SudokuPosition?,
    )

    private val provider = SudokuCatalogProvider(dataset)
    private var operation: Job? = null
    private var engine: SudokuGameEngine? = null
    private var statisticsAttempt: WebStatisticsAttempt? = null

    // Snapshot-state list so [canUndo] recomposes whenever the history itself changes.
    private val undoHistory = mutableStateListOf<UndoFrame>()
    private val completion = WebCatalogCompletionController(progression)
    private val dailyCompletion = WebDailyCompletionController(daily)

    var state by mutableStateOf<WebSudokuState>(WebSudokuState.DifficultySelection)
        private set

    val completionState: WebCatalogCompletionState
        get() = completion.state

    val dailyCompletionState: WebDailyCompletionState
        get() = dailyCompletion.state

    val canUndo: Boolean
        get() =
            undoHistory.isNotEmpty() &&
                (state as? WebSudokuState.Playing)?.game?.status == SudokuGameStatus.IN_PROGRESS

    fun selectDifficulty(difficulty: Difficulty) {
        operation?.cancel()
        statisticsAttempt = null
        undoHistory.clear()
        completion.reset()
        dailyCompletion.reset()
        state =
            WebSudokuState.Loading(
                difficulty,
                launch = WebGameLaunch.Catalog(PuzzleType.SUDOKU, difficulty),
            )
        operation =
            scope.launch {
                var levelNumber: CatalogLevelNumber? = null
                try {
                    val attempt =
                        when (
                            val resolved =
                                progression.resolveCurrentLevel(
                                    PuzzleType.SUDOKU,
                                    difficulty,
                                    CatalogLevelPackVersion.V1,
                                )
                        ) {
                            is WebCatalogLevelResolution.Resolved -> resolved.attempt
                            is WebCatalogLevelResolution.Unavailable -> {
                                state =
                                    WebSudokuState.Error(
                                        difficulty,
                                        null,
                                        resolved.detail,
                                        progressionUnavailable = true,
                                        launch = WebGameLaunch.Catalog(PuzzleType.SUDOKU, difficulty),
                                    )
                                return@launch
                            }
                        }
                    levelNumber = attempt.levelId.levelNumber
                    state = WebSudokuState.Loading(difficulty, levelNumber, WebGameLaunch.Catalog(PuzzleType.SUDOKU, difficulty))
                    loadPack(difficulty)
                    if (!progression.isCurrent(attempt)) return@launch
                    val definition = resolveLevel(attempt.levelId)
                    require(definition.generatorVersion == provider.version) {
                        "Sudoku level ${attempt.levelId.levelNumber.value} requires provider ${definition.generatorVersion.value}."
                    }
                    loadDataset(SudokuDatasetVersion.V1, difficulty.toSudokuDifficulty())
                    if (!progression.isCurrent(attempt)) return@launch
                    val puzzle = provider.select(difficulty, definition.seed, definition.generatorVersion).requirePuzzle()
                    val nextEngine = SudokuGameEngine(puzzle)
                    engine = nextEngine
                    completion.startAttempt(attempt)
                    statisticsAttempt = statistics.startAttempt(PuzzleType.SUDOKU, difficulty)
                    state =
                        WebSudokuState.Playing(
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
                        WebSudokuState.Error(
                            difficulty,
                            levelNumber,
                            exception.message ?: "Sudoku level is unavailable.",
                            progressionUnavailable = false,
                            launch = WebGameLaunch.Catalog(PuzzleType.SUDOKU, difficulty),
                        )
                }
            }
    }

    fun retryLoading() {
        val error = state as? WebSudokuState.Error ?: return
        when (val launch = error.launch) {
            is WebGameLaunch.Catalog -> {
                if (error.progressionUnavailable) progression.retryContextBinding()
                selectDifficulty(launch.requestedDifficulty)
            }
            is WebGameLaunch.Daily -> startDaily(launch.attempt)
        }
    }

    /**
     * Starts the deterministic Daily Sudoku puzzle: the frozen selector resolves the same Dataset
     * V1 record from the Daily identity alone — never a Catalog level — while mistakes, Pencil,
     * hints, and shared presentation behave exactly as in Catalog gameplay.
     */
    fun startDaily(dailyAttempt: WebDailyAttempt) {
        operation?.cancel()
        statisticsAttempt = null
        undoHistory.clear()
        completion.reset()
        val launch = WebGameLaunch.Daily(dailyAttempt)
        dailyCompletion.startAttempt(dailyAttempt)
        state = WebSudokuState.Loading(dailyAttempt.entry.difficulty, launch = launch)
        operation =
            scope.launch {
                try {
                    require(dailyAttempt.entry.generatorVersion == provider.version) {
                        "Sudoku Daily requires provider ${dailyAttempt.entry.generatorVersion.value}."
                    }
                    val difficulty = dailyAttempt.entry.difficulty
                    loadDataset(SudokuDatasetVersion.V1, difficulty.toSudokuDifficulty())
                    val puzzle =
                        provider
                            .select(difficulty, dailyAttempt.entry.seed, dailyAttempt.entry.generatorVersion)
                            .requirePuzzle()
                    val nextEngine = SudokuGameEngine(puzzle)
                    engine = nextEngine
                    statisticsAttempt = statistics.startAttempt(PuzzleType.SUDOKU, difficulty)
                    state =
                        WebSudokuState.Playing(
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
                        WebSudokuState.Error(
                            dailyAttempt.entry.difficulty,
                            null,
                            exception.message ?: "Sudoku Daily is unavailable.",
                            progressionUnavailable = false,
                            launch = launch,
                        )
                }
            }
    }

    fun selectCell(position: SudokuPosition) {
        val playing = state as? WebSudokuState.Playing ?: return
        if (playing.game.status.isTerminal || playing.selectedCell == position) return
        state = playing.copy(selectedCell = position)
    }

    fun inputDigit(digit: Int) {
        val playing = state as? WebSudokuState.Playing ?: return
        if (playing.game.status.isTerminal) return
        val position = playing.selectedCell ?: return
        val activeEngine = engine ?: return
        val cell = playing.game.cellAt(position)
        val updated =
            if (playing.isPencilMode) {
                if (cell.status != SudokuCellStatus.EMPTY) return
                activeEngine.toggleCandidate(playing.game, position, digit)
            } else {
                if (cell.status != SudokuCellStatus.EMPTY && cell.status != SudokuCellStatus.INCORRECT) return
                activeEngine.placeValue(playing.game, position, digit)
            }
        if (updated != playing.game) updateGame(playing, updated)
    }

    /**
     * Desktop keyboard: arrows move the selection (starting top-left), 1–9 enter a digit exactly
     * like the keypad, 0/Backspace/Delete erase, and the P key position toggles Pencil.
     */
    fun onHardwareKey(key: GameKey) {
        val playing = state as? WebSudokuState.Playing ?: return
        if (playing.game.status.isTerminal) return
        when (key) {
            is GameKey.Digit -> if (key.value == 0) eraseSelectedCell() else inputDigit(key.value)
            GameKey.Backspace, GameKey.Delete -> eraseSelectedCell()
            GameKey.Up -> moveSelection(playing, rowStep = -1, columnStep = 0)
            GameKey.Down -> moveSelection(playing, rowStep = 1, columnStep = 0)
            GameKey.Left -> moveSelection(playing, rowStep = 0, columnStep = -1)
            GameKey.Right -> moveSelection(playing, rowStep = 0, columnStep = 1)
            is GameKey.Letter -> if (key.char == PENCIL_KEY_LETTER) togglePencilMode()
            GameKey.Enter -> Unit
        }
    }

    private fun moveSelection(
        playing: WebSudokuState.Playing,
        rowStep: Int,
        columnStep: Int,
    ) {
        val current = playing.selectedCell
        val next =
            if (current == null) {
                SudokuPosition(0, 0)
            } else {
                SudokuPosition(
                    (current.row + rowStep).coerceIn(0, SUDOKU_LAST_INDEX),
                    (current.column + columnStep).coerceIn(0, SUDOKU_LAST_INDEX),
                )
            }
        selectCell(next)
    }

    fun eraseSelectedCell() {
        val playing = state as? WebSudokuState.Playing ?: return
        if (playing.game.status.isTerminal) return
        val position = playing.selectedCell ?: return
        val updated = engine?.eraseCell(playing.game, position) ?: return
        updateGame(playing, updated)
    }

    fun undo() {
        val playing = state as? WebSudokuState.Playing ?: return
        if (playing.game.status.isTerminal || undoHistory.isEmpty()) return
        val activeEngine = engine ?: return
        // Correct entries stay locked through undo, so a frame whose only change was a correct
        // placement restores nothing; skip such frames and undo the last reversible change instead.
        while (undoHistory.isNotEmpty()) {
            val frame = undoHistory.removeAt(undoHistory.lastIndex)
            val restored = activeEngine.restoreSnapshot(playing.game, frame.game)
            if (restored != playing.game) {
                state = playing.copy(game = restored, selectedCell = frame.selectedCell)
                return
            }
        }
    }

    fun togglePencilMode() {
        val playing = state as? WebSudokuState.Playing ?: return
        if (playing.game.status.isTerminal) return
        state = playing.copy(isPencilMode = !playing.isPencilMode)
    }

    /** Set when a hint was requested with an empty hint inventory; the host shows where to get more. */
    var hintsExhaustedNotice by mutableStateOf(false)
        private set

    fun dismissHintsExhaustedNotice() {
        hintsExhaustedNotice = false
    }

    fun requestHint() {
        val playing = state as? WebSudokuState.Playing ?: return
        if (playing.game.status.isTerminal) return
        val updated = engine?.revealHint(playing.game, playing.selectedCell) ?: return
        // A hint consumes one hint item from the Player's own inventory (Catalog and Daily alike);
        // without one the host explains where hints come from instead of silently ignoring the tap.
        if (updated == playing.game) return
        if (!store.tryConsumeHint()) {
            hintsExhaustedNotice = true
            return
        }
        updateGame(
            playing,
            updated,
            updated.currentHint?.position ?: playing.selectedCell,
            recordUndo = false,
            clearUndo = true,
        )
    }

    fun retry() {
        val playing = state as? WebSudokuState.Playing ?: return
        if (playing.game.status != SudokuGameStatus.FAILED) return
        if (dailyReplayBlocked(playing.source)) return
        val activeEngine = engine ?: return
        operation?.cancel()
        undoHistory.clear()
        val source = playing.source
        if (source is WebGameplaySource.CatalogLevel) completion.startAttempt(source.attempt)
        (source as? WebGameplaySource.DailyChallenge)?.let { dailyCompletion.startAttempt(it.attempt) }
        statisticsAttempt = statistics.startAttempt(PuzzleType.SUDOKU, playing.source.difficulty)
        state = playing.copy(game = activeEngine.start(), selectedCell = null, isPencilMode = false)
    }

    fun nextLevel() {
        val playing = state as? WebSudokuState.Playing ?: return
        val source = playing.source as? WebGameplaySource.CatalogLevel ?: return
        if (completion.state !is WebCatalogCompletionState.Saved) return
        selectDifficulty(source.attempt.levelId.difficulty)
    }

    fun retrySave() {
        val playing = state as? WebSudokuState.Playing ?: return
        val source = playing.source as? WebGameplaySource.CatalogLevel ?: return
        if (playing.game.status != SudokuGameStatus.SOLVED) return
        completion.saveSolved(source.attempt, PuzzleStars.forMistakes(playing.game.mistakesUsed))
    }

    fun showDifficultySelector() {
        operation?.cancel()
        engine = null
        statisticsAttempt = null
        undoHistory.clear()
        hintsExhaustedNotice = false
        completion.reset()
        dailyCompletion.reset()
        state = WebSudokuState.DifficultySelection
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
        playing: WebSudokuState.Playing,
        updated: SudokuGameState,
        selectedCell: SudokuPosition? = playing.selectedCell,
        recordUndo: Boolean = true,
        clearUndo: Boolean = false,
    ) {
        if (updated == playing.game) return
        if (clearUndo) {
            undoHistory.clear()
        } else if (recordUndo && updated.status == SudokuGameStatus.IN_PROGRESS) {
            undoHistory += UndoFrame(playing.game, playing.selectedCell)
            if (undoHistory.size > MAX_UNDO_HISTORY) undoHistory.removeAt(0)
        }
        state = playing.copy(game = updated, selectedCell = selectedCell)
        if (!playing.game.status.isTerminal && updated.status.isTerminal) {
            val outcome =
                if (updated.status == SudokuGameStatus.SOLVED) {
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
                    if (updated.status ==
                        SudokuGameStatus.SOLVED
                    ) {
                        completion.saveSolved(source.attempt, PuzzleStars.forMistakes(updated.mistakesUsed))
                    }
                    // Catalog terminals feed the wallet.
                    economy.recordTerminalResult(
                        PuzzleType.SUDOKU,
                        source.attempt.levelId.difficulty,
                        solved = updated.status == SudokuGameStatus.SOLVED,
                    )
                }
                is WebGameplaySource.DailyChallenge -> {
                    dailyCompletion.saveTerminal(source.attempt, outcome)
                    // A Daily result feeds the wallet like a Catalog one: a failure costs a life.
                    economy.recordTerminalResult(
                        PuzzleType.SUDOKU,
                        source.difficulty,
                        solved = outcome == WebStatisticsTerminalOutcome.SOLVED,
                    )
                }
            }
        }
    }

    private fun resolveLevel(levelId: CatalogLevelId): CatalogLevelDefinition =
        when (val resolved = levelPack.resolve(levelId)) {
            is CatalogLevelPackResult.Success -> resolved.value
            is CatalogLevelPackResult.Failure -> error(resolved.detail)
        }

    private fun SudokuDatasetResult<SudokuPuzzle>.requirePuzzle(): SudokuPuzzle =
        when (this) {
            is SudokuDatasetResult.Success -> value
            is SudokuDatasetResult.Failure -> error(detail)
        }

    companion object {
        fun create(
            loader: BrowserPuzzleDataLoader,
            progression: WebCatalogProgressAccess,
            statistics: WebGameplayStatistics = DisabledWebGameplayStatistics,
            daily: WebDailyGameplayAccess = DisabledWebDailyGameplay,
            economy: WebGameplayEconomy = DisabledWebGameplayEconomy,
            store: WebGameplayStore = DisabledWebGameplayStore,
        ): WebSudokuController =
            WebSudokuController(
                loadPack = { difficulty ->
                    loader.loadCatalogLevelPack(
                        packVersion = CatalogLevelPackVersion.V1,
                        puzzleType = PuzzleType.SUDOKU,
                        difficulty = difficulty,
                    )
                },
                loadDataset = loader::loadSudokuDataset,
                progression = progression,
                statistics = statistics,
                daily = daily,
                economy = economy,
                store = store,
            )
    }
}

private const val MAX_UNDO_HISTORY = 100
private const val SUDOKU_LAST_INDEX = 8

/** The letter on the physical P key in the Russian layout (see `webGameKeyOf`). */
private const val PENCIL_KEY_LETTER = 'з'
