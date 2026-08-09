package com.stanisryz.logica.daily

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.stanisryz.logica.balance.BalanceGameContext
import com.stanisryz.logica.balance.BalanceGameLaunch
import com.stanisryz.logica.crowns.CrownsGameContext
import com.stanisryz.logica.crowns.CrownsGameLaunch
import com.stanisryz.logica.puzzle.core.daily.DailyChallengeDefinition
import com.stanisryz.logica.puzzle.core.daily.DailyChallengePolicyResolver
import com.stanisryz.logica.puzzle.core.daily.DailyChallengePolicyV3
import com.stanisryz.logica.puzzle.core.daily.DailyPolicyVersion
import com.stanisryz.logica.puzzle.core.daily.DailyPuzzleEntry
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import com.stanisryz.logica.session.DailyGameSessionIdentity
import com.stanisryz.logica.session.GameSessionRepository
import com.stanisryz.logica.session.GameSessionScope
import com.stanisryz.logica.session.SavedGameSession
import com.stanisryz.logica.statistics.StatisticsRepository
import com.stanisryz.logica.word.WordGameContext
import com.stanisryz.logica.word.WordGameLaunch
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
internal sealed interface DailyGameLaunch {
    data class Balance(
        val launch: BalanceGameLaunch,
    ) : DailyGameLaunch

    data class Crowns(
        val launch: CrownsGameLaunch,
    ) : DailyGameLaunch

    data class Word(
        val launch: WordGameLaunch,
    ) : DailyGameLaunch
}

internal enum class DailyEntryState {
    AVAILABLE,
    IN_PROGRESS,
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
)

internal sealed interface TodayUiState {
    data object Loading : TodayUiState

    data class Content(
        val definition: DailyChallengeDefinition,
        val runStatus: DailyRunStatus?,
        val entries: List<TodayEntryUiState>,
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
    private val gameSessionRepository: GameSessionRepository,
    private val statisticsRepository: StatisticsRepository,
    private val dateProvider: () -> LocalDate = LocalDate::now,
    private val definitionProvider: (LocalDate, DailyPolicyVersion) -> DailyChallengeDefinition =
        DailyChallengePolicyResolver::definitionFor,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow<TodayUiState>(TodayUiState.Loading)
    val uiState: StateFlow<TodayUiState> = mutableUiState.asStateFlow()

    private val mutableLaunches = MutableSharedFlow<DailyGameLaunch>(extraBufferCapacity = 1)
    val launches: SharedFlow<DailyGameLaunch> = mutableLaunches.asSharedFlow()

    private var refreshJob: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        refreshJob?.cancel()
        refreshJob =
            viewModelScope.launch {
                mutableUiState.value = TodayUiState.Loading
                try {
                    val challengeDate = dateProvider()
                    val run = dailyChallengeRepository.readRun(challengeDate)
                    // A persisted run keeps its own policy version forever; only brand-new runs use V3.
                    val definition =
                        definitionProvider(challengeDate, run?.policyVersion ?: DailyChallengePolicyV3.VERSION)
                    val entries = definition.entries.map { entry -> entryState(definition, entry, run) }
                    mutableUiState.value =
                        TodayUiState.Content(
                            definition = definition,
                            runStatus = run?.status,
                            entries = entries,
                            completion =
                                if (run?.status == DailyRunStatus.COMPLETED) {
                                    val snapshot = statisticsRepository.observe(challengeDate).first()
                                    TodayCompletionUiState(
                                        hintsUsed = snapshot.dailyHintsUsedByDate[challengeDate],
                                        currentStreak = snapshot.statistics.currentDailyStreak,
                                        bestStreak = snapshot.statistics.bestDailyStreak,
                                    )
                                } else {
                                    null
                                },
                        )
                } catch (exception: CancellationException) {
                    throw exception
                } catch (_: Exception) {
                    mutableUiState.value = TodayUiState.Error(TodayError.LOAD)
                }
            }
    }

    fun start(puzzleType: PuzzleType) {
        val content = mutableUiState.value as? TodayUiState.Content ?: return
        val entry = content.definition.entryFor(puzzleType) ?: return
        if (content.entries.stateOf(puzzleType) != DailyEntryState.AVAILABLE) return
        val definition = content.definition
        val needsRun = content.runStatus == null
        mutableUiState.value = TodayUiState.Loading
        viewModelScope.launch {
            try {
                if (needsRun) dailyChallengeRepository.createRun(definition)
                mutableLaunches.emit(definition.newLaunch(entry))
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Exception) {
                mutableUiState.value = TodayUiState.Error(TodayError.START)
            }
        }
    }

    fun continueGame(puzzleType: PuzzleType) {
        val content = mutableUiState.value as? TodayUiState.Content ?: return
        val entry = content.definition.entryFor(puzzleType) ?: return
        if (content.entries.stateOf(puzzleType) != DailyEntryState.IN_PROGRESS) return
        mutableLaunches.tryEmit(content.definition.restoreLaunch(entry))
    }

    private suspend fun entryState(
        definition: DailyChallengeDefinition,
        entry: DailyPuzzleEntry,
        run: SavedDailyRun?,
    ): TodayEntryUiState {
        val lifecycle =
            dailyChallengeRepository
                .read(definition.challengeDate, entry.puzzleType)
                ?.takeIf { it.matches(definition, entry) }
        if (run != null) requireNotNull(lifecycle) { "The Daily run is missing the ${entry.puzzleType} entry." }
        // All policy entries are materialized when the run is created, so `daily_challenges.status`
        // alone cannot tell "not started" from "started": an active session decides that.
        val state =
            when {
                lifecycle?.status == DailyChallengeStatus.COMPLETED -> DailyEntryState.COMPLETED
                matchingDailySession(definition, entry) != null -> DailyEntryState.IN_PROGRESS
                else -> DailyEntryState.AVAILABLE
            }
        return TodayEntryUiState(entry.puzzleType, entry.difficulty, state)
    }

    private suspend fun matchingDailySession(
        definition: DailyChallengeDefinition,
        entry: DailyPuzzleEntry,
    ): SavedGameSession? =
        gameSessionRepository
            .readActiveSession(entry.puzzleType, GameSessionScope.DAILY)
            ?.takeIf { session ->
                session.dailyIdentity ==
                    DailyGameSessionIdentity(definition.challengeDate, definition.policyVersion.value) &&
                    session.puzzleType == entry.puzzleType &&
                    session.difficulty == entry.difficulty &&
                    session.puzzleSeed == entry.seed &&
                    session.generatorVersion == entry.generatorVersion
            }

    private fun List<TodayEntryUiState>.stateOf(puzzleType: PuzzleType): DailyEntryState? =
        firstOrNull { it.puzzleType == puzzleType }?.state

    private fun DailyChallengeDefinition.entryFor(puzzleType: PuzzleType): DailyPuzzleEntry? =
        entries.firstOrNull { it.puzzleType == puzzleType }

    private fun DailyChallengeDefinition.newLaunch(entry: DailyPuzzleEntry): DailyGameLaunch =
        when (entry.puzzleType) {
            PuzzleType.BALANCE ->
                DailyGameLaunch.Balance(
                    BalanceGameLaunch.New(
                        difficulty = entry.difficulty,
                        seed = entry.seed,
                        generatorVersion = entry.generatorVersion,
                        context = BalanceGameContext.Daily(challengeDate, policyVersion),
                    ),
                )
            PuzzleType.CROWNS ->
                DailyGameLaunch.Crowns(
                    CrownsGameLaunch.New(
                        difficulty = entry.difficulty,
                        seed = entry.seed,
                        generatorVersion = entry.generatorVersion,
                        context = CrownsGameContext.Daily(challengeDate, policyVersion),
                    ),
                )
            PuzzleType.WORD ->
                DailyGameLaunch.Word(
                    WordGameLaunch.New(
                        difficulty = entry.difficulty,
                        seed = entry.seed,
                        generatorVersion = entry.generatorVersion,
                        context = WordGameContext.Daily(challengeDate, policyVersion),
                    ),
                )
            else -> error("Daily does not support ${entry.puzzleType} yet.")
        }

    private fun DailyChallengeDefinition.restoreLaunch(entry: DailyPuzzleEntry): DailyGameLaunch =
        when (entry.puzzleType) {
            PuzzleType.BALANCE ->
                DailyGameLaunch.Balance(
                    BalanceGameLaunch.Restore(
                        context = BalanceGameContext.Daily(challengeDate, policyVersion),
                        expectedPuzzleId = entry.puzzleId,
                    ),
                )
            PuzzleType.CROWNS ->
                DailyGameLaunch.Crowns(
                    CrownsGameLaunch.Restore(
                        context = CrownsGameContext.Daily(challengeDate, policyVersion),
                        expectedPuzzleId = entry.puzzleId,
                    ),
                )
            PuzzleType.WORD ->
                DailyGameLaunch.Word(
                    WordGameLaunch.Restore(
                        context = WordGameContext.Daily(challengeDate, policyVersion),
                        expectedPuzzleId = entry.puzzleId,
                    ),
                )
            else -> error("Daily does not support ${entry.puzzleType} yet.")
        }
}

internal class TodayViewModelFactory(
    private val dailyChallengeRepository: DailyChallengeRepository,
    private val gameSessionRepository: GameSessionRepository,
    private val statisticsRepository: StatisticsRepository,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(TodayViewModel::class.java)) {
            "Unknown ViewModel class: ${modelClass.name}"
        }
        @Suppress("UNCHECKED_CAST")
        return TodayViewModel(dailyChallengeRepository, gameSessionRepository, statisticsRepository) as T
    }
}
