package com.stanisryz.logica.daily

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.stanisryz.logica.catalog.GameAttemptLaunch
import com.stanisryz.logica.economy.DailyArchivePayment
import com.stanisryz.logica.economy.DailyArchiveUnlockOutcome
import com.stanisryz.logica.economy.EconomyRepository
import com.stanisryz.logica.economy.EconomyRules
import com.stanisryz.logica.puzzle.core.daily.DailyArchive
import com.stanisryz.logica.puzzle.core.daily.DailyChallengeDefinition
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import com.stanisryz.logica.result.GameOutcome
import com.stanisryz.logica.runCatchingCancellable
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate

/** One past Daily day as the archive shows it. */
internal data class ArchiveDayUiState(
    val definition: DailyChallengeDefinition,
    val entries: List<TodayEntryUiState>,
    val unlocked: Boolean,
    /** The player started this day on its own date, so it opens for free. */
    val started: Boolean,
) {
    val date: LocalDate get() = definition.challengeDate
    val epochDay: Long get() = date.toEpochDay()
    val completedCount: Int get() = entries.count { it.state == DailyEntryState.COMPLETED }

    /** What opening it costs: nothing once open or for a day already started, else the gem price. */
    val unlockPrice: Int get() = if (unlocked || started) 0 else EconomyRules.DAILY_ARCHIVE_UNLOCK_GEMS
}

internal sealed interface DailyArchiveUiState {
    data object Loading : DailyArchiveUiState

    data class Content(
        val days: List<ArchiveDayUiState>,
    ) : DailyArchiveUiState

    data object Error : DailyArchiveUiState
}

/**
 * The Daily archive: the last [EconomyRules.DAILY_ARCHIVE_DAYS] days before today, or the one day
 * [onlyDay] when a day screen is open. A day keeps the policy of its persisted run, else gets the
 * newest one; it opens for good through a `daily_archive:<day>` ledger row, paid with gems, a
 * rewarded ad (through the shell), or for free when the player started it on its own date. Play is
 * an ordinary Daily attempt at that date; only the streak ignores it (see [DailyStreakQualification]).
 */
internal class DailyArchiveViewModel(
    private val dailyChallengeRepository: DailyChallengeRepository,
    private val dailyResultRepository: DailyResultRepository,
    private val economyRepository: EconomyRepository,
    private val onlyDay: Long? = null,
    private val dateProvider: () -> LocalDate = LocalDate::now,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow<DailyArchiveUiState>(DailyArchiveUiState.Loading)
    val uiState: StateFlow<DailyArchiveUiState> = mutableUiState.asStateFlow()

    private val mutableLaunches = MutableSharedFlow<DailyGameLaunch>(extraBufferCapacity = 1)
    val launches: SharedFlow<DailyGameLaunch> = mutableLaunches.asSharedFlow()

    private var refreshJob: Job? = null
    private var launchInFlight = false

    init {
        // A day opened by a rewarded ad lands in the ledger from the shell; show it open at once.
        viewModelScope.launch {
            economyRepository
                .observeDailyArchiveUnlocks()
                .distinctUntilChanged()
                .drop(1)
                .collect { refresh() }
        }
    }

    /** The hub-like screens refresh on every resume, so a return from gameplay shows the new state. */
    fun refresh() {
        launchInFlight = false
        refreshJob?.cancel()
        refreshJob =
            viewModelScope.launch {
                runCatchingCancellable { load() }
                    .onSuccess { mutableUiState.value = DailyArchiveUiState.Content(it) }
                    .onFailure {
                        if (mutableUiState.value !is DailyArchiveUiState.Content) mutableUiState.value = DailyArchiveUiState.Error
                    }
            }
    }

    private suspend fun load(): List<ArchiveDayUiState> {
        val today = dateProvider()
        val unlocks = economyRepository.observeDailyArchiveUnlocks().first()
        val dates =
            DailyArchive
                .dates(today, EconomyRules.DAILY_ARCHIVE_DAYS)
                .filter { onlyDay == null || it.toEpochDay() == onlyDay }
        return dates.map { date -> dayState(date, date.toEpochDay() in unlocks) }
    }

    private suspend fun dayState(
        date: LocalDate,
        unlocked: Boolean,
    ): ArchiveDayUiState {
        val run = dailyChallengeRepository.readRun(date)
        val definition = DailyArchive.definitionFor(date, run?.policyVersion)
        val results = run?.let { dailyResultRepository.readResults(date, it.policyVersion) }.orEmpty()
        val failed = results.filter { it.outcome == GameOutcome.FAILED }.mapTo(mutableSetOf()) { it.puzzleType }
        val entries =
            definition.entries.map { entry ->
                val completed =
                    run != null &&
                        dailyChallengeRepository
                            .read(date, entry.puzzleType)
                            ?.takeIf { it.matches(definition, entry) }
                            ?.status == DailyChallengeStatus.COMPLETED
                val state =
                    when {
                        completed -> DailyEntryState.COMPLETED
                        entry.puzzleType in failed -> DailyEntryState.RETRY
                        else -> DailyEntryState.AVAILABLE
                    }
                TodayEntryUiState(entry.puzzleType, entry.difficulty, state)
            }
        return ArchiveDayUiState(definition, entries, unlocked = unlocked, started = run != null)
    }

    /** Opens the day for gems; the ledger re-checks the balance and a repeat charges nothing. */
    fun unlockWithGems(epochDay: Long) {
        viewModelScope.launch {
            runCatchingCancellable { economyRepository.unlockDailyArchive(epochDay, DailyArchivePayment.GEMS) }
                .onSuccess { if (it == DailyArchiveUnlockOutcome.Unlocked) refresh() }
        }
    }

    /**
     * Starts a fresh attempt at one entry of an archive day. A day the player started on its own
     * date is opened for free right here, so its ledger row marks when archive play began.
     */
    fun start(
        epochDay: Long,
        puzzleType: PuzzleType,
    ) {
        if (launchInFlight) return
        val day = (mutableUiState.value as? DailyArchiveUiState.Content)?.days?.firstOrNull { it.epochDay == epochDay } ?: return
        val entry = day.definition.entries.firstOrNull { it.puzzleType == puzzleType } ?: return
        if (day.entries.firstOrNull { it.puzzleType == puzzleType }?.state == DailyEntryState.COMPLETED) return
        if (!day.unlocked && !day.started) return
        // A day that slid out of the window while the screen stayed open is no longer playable.
        if (!DailyArchive.contains(dateProvider(), day.date, EconomyRules.DAILY_ARCHIVE_DAYS)) {
            refresh()
            return
        }
        launchInFlight = true
        viewModelScope.launch {
            runCatchingCancellable {
                if (!day.unlocked) economyRepository.unlockDailyArchive(epochDay, DailyArchivePayment.FREE)
                if (!day.started) dailyChallengeRepository.createRun(day.definition)
                mutableLaunches.emit(
                    DailyGameLaunch(
                        GameAttemptLaunch.Daily(
                            puzzleType = entry.puzzleType,
                            challengeDate = day.date,
                            policyVersion = day.definition.policyVersion,
                            difficulty = entry.difficulty,
                            seed = entry.seed,
                            generatorVersion = entry.generatorVersion,
                            archive = true,
                        ),
                    ),
                )
            }.onFailure {
                launchInFlight = false
                mutableUiState.value = DailyArchiveUiState.Error
            }
        }
    }
}

internal class DailyArchiveViewModelFactory(
    private val dailyChallengeRepository: DailyChallengeRepository,
    private val dailyResultRepository: DailyResultRepository,
    private val economyRepository: EconomyRepository,
    private val onlyDay: Long?,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(DailyArchiveViewModel::class.java)) {
            "Unknown ViewModel class: ${modelClass.name}"
        }
        @Suppress("UNCHECKED_CAST")
        return DailyArchiveViewModel(dailyChallengeRepository, dailyResultRepository, economyRepository, onlyDay) as T
    }
}
