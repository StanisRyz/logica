package com.stanisryz.logica.daily

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.stanisryz.logica.catalog.GameAttemptLaunch
import com.stanisryz.logica.puzzle.core.daily.DailyChallengeDefinition
import com.stanisryz.logica.puzzle.core.daily.DailyChallengePolicyResolver
import com.stanisryz.logica.puzzle.core.daily.DailyChallengePolicyV5
import com.stanisryz.logica.puzzle.core.daily.DailyPolicyVersion
import com.stanisryz.logica.puzzle.core.daily.DailyPuzzleEntry
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import com.stanisryz.logica.result.GameOutcome
import com.stanisryz.logica.result.GameResult
import com.stanisryz.logica.statistics.GameStatistics
import com.stanisryz.logica.statistics.StatisticsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate

/** A typed request to open one Daily puzzle on its own gameplay destination. */
internal data class DailyGameLaunch(
    val launch: GameAttemptLaunch.Daily,
) {
    val puzzleType: PuzzleType get() = launch.puzzleType
}

/**
 * How one Daily entry stands today. There is no in-progress state any more: an unfinished attempt is
 * transient, so an entry is either open (for a first or a repeat attempt) or durably solved.
 */
internal enum class DailyEntryState {
    AVAILABLE,

    /** Derived only: this entry already has at least one durable failed attempt. */
    RETRY,
    COMPLETED,
}

internal data class TodayEntryUiState(
    val puzzleType: PuzzleType,
    val difficulty: Difficulty,
    val state: DailyEntryState,
)

internal data class TodayCompletionUiState(
    val hintsUsed: Int?,
    val currentStreak: Int,
    val bestStreak: Int,
    val resultSummary: DailyResultSummary?,
)

/**
 * Streak status for the current date, separate from full Daily completion. From Policy V5 on, one
 * solved entry already qualifies the date, so this can be shown at 1/5 while the run is still
 * in progress; for the historical V1–V4 policies qualification still means the whole run.
 */
internal data class TodayStreakUiState(
    val anySolvedQualifies: Boolean,
    val qualifiedToday: Boolean,
    val current: Int,
    val best: Int,
)

internal sealed interface TodayUiState {
    data object Loading : TodayUiState

    data class Content(
        val definition: DailyChallengeDefinition,
        val runStatus: DailyRunStatus?,
        val entries: List<TodayEntryUiState>,
        val streak: TodayStreakUiState,
        val completion: TodayCompletionUiState?,
    ) : TodayUiState {
        val totalCount: Int get() = entries.size
        val completedCount: Int get() = entries.count { it.state == DailyEntryState.COMPLETED }
    }

    data class Error(
        val reason: TodayError,
    ) : TodayUiState
}

internal enum class TodayError {
    LOAD,
    START,
}

internal class TodayViewModel(
    private val dailyChallengeRepository: DailyChallengeRepository,
    private val statisticsRepository: StatisticsRepository,
    private val dailyResultRepository: DailyResultRepository,
    private val dateProvider: () -> LocalDate = LocalDate::now,
    private val definitionProvider: (LocalDate, DailyPolicyVersion) -> DailyChallengeDefinition =
        DailyChallengePolicyResolver::definitionFor,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow<TodayUiState>(TodayUiState.Loading)
    val uiState: StateFlow<TodayUiState> = mutableUiState.asStateFlow()

    private val mutableLaunches = MutableSharedFlow<DailyGameLaunch>(extraBufferCapacity = 1)
    val launches: SharedFlow<DailyGameLaunch> = mutableLaunches.asSharedFlow()

    private var refreshJob: Job? = null

    /**
     * There is no load on construction. The hub route refreshes whenever it becomes visible — on its
     * first frame and again on every return from gameplay — so a load started here would only be
     * cancelled and restarted a moment later.
     */
    fun refresh() {
        val challengeDate = dateProvider()
        val retainsSameDayContent =
            (mutableUiState.value as? TodayUiState.Content)?.definition?.challengeDate == challengeDate
        refreshJob?.cancel()
        refreshJob =
            viewModelScope.launch {
                // The Game tab can leave and return within the Home navigation scope. Keep its
                // same-day content visible while repositories refresh, but never carry it across
                // the date boundary (including after returning from the background overnight).
                if (!retainsSameDayContent) mutableUiState.value = TodayUiState.Loading
                try {
                    val run = dailyChallengeRepository.readRun(challengeDate)
                    // A persisted run keeps its own policy version forever; only brand-new runs use V5.
                    val definition =
                        definitionProvider(challengeDate, run?.policyVersion ?: DailyChallengePolicyV5.VERSION)
                    val results =
                        run
                            ?.let { dailyResultRepository.readResults(challengeDate, it.policyVersion) }
                            .orEmpty()
                    // Failed attempts stay durable, so an open entry can still be a retry rather than
                    // an untouched start.
                    val failedPuzzleTypes =
                        results
                            .filter { it.outcome == GameOutcome.FAILED }
                            .mapTo(mutableSetOf()) { it.puzzleType }
                    val entries =
                        definition.entries.map { entry -> entryState(definition, entry, run, failedPuzzleTypes) }
                    val snapshot = statisticsRepository.observe(challengeDate).first()
                    val streak = streakFor(definition, run, results, snapshot.statistics)
                    mutableUiState.value =
                        TodayUiState.Content(
                            definition = definition,
                            runStatus = run?.status,
                            entries = entries,
                            streak = streak,
                            completion =
                                run?.let {
                                    completionFor(definition, it, results, streak, snapshot.dailyHintsUsedByDate[challengeDate])
                                },
                        )
                } catch (exception: CancellationException) {
                    throw exception
                } catch (_: Exception) {
                    // A background refresh must not replace retained, usable same-day content
                    // with an error card merely because the refresh itself failed.
                    if (!retainsSameDayContent) mutableUiState.value = TodayUiState.Error(TodayError.LOAD)
                }
            }
    }

    /**
     * Streak status is derived from the same durable results the entries are, so a V5 date is
     * qualified the moment its first entry is solved, long before the run itself completes.
     */
    private fun streakFor(
        definition: DailyChallengeDefinition,
        run: SavedDailyRun?,
        results: List<GameResult>,
        statistics: GameStatistics,
    ): TodayStreakUiState {
        val anySolvedQualifies = DailyChallengePolicyResolver.qualifiesStreakOnAnySolvedEntry(definition.policyVersion)
        return TodayStreakUiState(
            anySolvedQualifies = anySolvedQualifies,
            qualifiedToday =
                if (anySolvedQualifies) {
                    results.any { it.outcome == GameOutcome.SOLVED }
                } else {
                    run?.status == DailyRunStatus.COMPLETED
                },
            current = statistics.currentDailyStreak,
            best = statistics.bestDailyStreak,
        )
    }

    /** The full-Daily card, including its Share action: still 5/5 only, never streak qualification. */
    private fun completionFor(
        definition: DailyChallengeDefinition,
        run: SavedDailyRun,
        results: List<GameResult>,
        streak: TodayStreakUiState,
        hintsUsed: Int?,
    ): TodayCompletionUiState? {
        if (run.status != DailyRunStatus.COMPLETED) return null
        return TodayCompletionUiState(
            hintsUsed = hintsUsed,
            currentStreak = streak.current,
            bestStreak = streak.best,
            resultSummary = DailyResultSummaryBuilder.build(definition, run, results, streak.current, streak.best),
        )
    }

    /**
     * Opens that day's puzzle as a fresh attempt. A repeat after a failure is just another attempt at
     * the same deterministic puzzle; a solved entry is never reopened.
     */
    fun start(puzzleType: PuzzleType) {
        val content = mutableUiState.value as? TodayUiState.Content ?: return
        val entry = content.definition.entryFor(puzzleType) ?: return
        if (content.entries.stateOf(puzzleType) !in setOf(DailyEntryState.AVAILABLE, DailyEntryState.RETRY)) return
        val definition = content.definition
        val needsRun = content.runStatus == null
        mutableUiState.value = TodayUiState.Loading
        viewModelScope.launch {
            try {
                if (needsRun) dailyChallengeRepository.createRun(definition)
                mutableLaunches.emit(definition.launchFor(entry))
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Exception) {
                mutableUiState.value = TodayUiState.Error(TodayError.START)
            }
        }
    }

    private suspend fun entryState(
        definition: DailyChallengeDefinition,
        entry: DailyPuzzleEntry,
        run: SavedDailyRun?,
        failedPuzzleTypes: Set<PuzzleType>,
    ): TodayEntryUiState {
        val lifecycle =
            dailyChallengeRepository
                .read(definition.challengeDate, entry.puzzleType)
                ?.takeIf { it.matches(definition, entry) }
        if (run != null) requireNotNull(lifecycle) { "The Daily run is missing the ${entry.puzzleType} entry." }
        val state =
            when {
                lifecycle?.status == DailyChallengeStatus.COMPLETED -> DailyEntryState.COMPLETED
                entry.puzzleType in failedPuzzleTypes -> DailyEntryState.RETRY
                else -> DailyEntryState.AVAILABLE
            }
        return TodayEntryUiState(entry.puzzleType, entry.difficulty, state)
    }

    private fun List<TodayEntryUiState>.stateOf(puzzleType: PuzzleType): DailyEntryState? =
        firstOrNull { it.puzzleType == puzzleType }?.state

    private fun DailyChallengeDefinition.entryFor(puzzleType: PuzzleType): DailyPuzzleEntry? =
        entries.firstOrNull { it.puzzleType == puzzleType }

    /** Every Daily game opens through the same attempt launch; only its identity differs. */
    private fun DailyChallengeDefinition.launchFor(entry: DailyPuzzleEntry): DailyGameLaunch {
        require(entry.puzzleType in DAILY_PUZZLE_TYPES) { "Daily does not support ${entry.puzzleType}." }
        return DailyGameLaunch(
            GameAttemptLaunch.Daily(
                puzzleType = entry.puzzleType,
                challengeDate = challengeDate,
                policyVersion = policyVersion,
                difficulty = entry.difficulty,
                seed = entry.seed,
                generatorVersion = entry.generatorVersion,
            ),
        )
    }

    private companion object {
        val DAILY_PUZZLE_TYPES =
            setOf(
                PuzzleType.BALANCE,
                PuzzleType.CROWNS,
                PuzzleType.WORD,
                PuzzleType.SUDOKU,
                PuzzleType.GAME_2048,
            )
    }
}

internal class TodayViewModelFactory(
    private val dailyChallengeRepository: DailyChallengeRepository,
    private val statisticsRepository: StatisticsRepository,
    private val dailyResultRepository: DailyResultRepository,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(TodayViewModel::class.java)) {
            "Unknown ViewModel class: ${modelClass.name}"
        }
        @Suppress("UNCHECKED_CAST")
        return TodayViewModel(
            dailyChallengeRepository,
            statisticsRepository,
            dailyResultRepository,
        ) as T
    }
}
