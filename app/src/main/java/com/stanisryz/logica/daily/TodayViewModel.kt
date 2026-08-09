package com.stanisryz.logica.daily

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.stanisryz.logica.balance.BalanceGameContext
import com.stanisryz.logica.balance.BalanceGameLaunch
import com.stanisryz.logica.puzzle.core.daily.DailyChallengeDefinition
import com.stanisryz.logica.puzzle.core.daily.DailyChallengePolicyResolver
import com.stanisryz.logica.puzzle.core.daily.DailyChallengePolicyV1
import com.stanisryz.logica.puzzle.core.daily.DailyPolicyVersion
import com.stanisryz.logica.puzzle.core.daily.DailyPuzzleEntry
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import com.stanisryz.logica.session.DailyGameSessionIdentity
import com.stanisryz.logica.session.GameSessionRepository
import com.stanisryz.logica.session.GameSessionScope
import com.stanisryz.logica.session.SavedGameSession
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

internal sealed interface TodayUiState {
    data object Loading : TodayUiState

    sealed interface WithDefinition : TodayUiState {
        val definition: DailyChallengeDefinition
    }

    data class Available(
        override val definition: DailyChallengeDefinition,
    ) : WithDefinition

    data class InProgress(
        override val definition: DailyChallengeDefinition,
    ) : WithDefinition

    data class Completed(
        override val definition: DailyChallengeDefinition,
        val hintsUsed: Int?,
        val currentStreak: Int,
        val bestStreak: Int,
    ) : WithDefinition

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

    private val mutableLaunches = MutableSharedFlow<BalanceGameLaunch>(extraBufferCapacity = 1)
    val launches: SharedFlow<BalanceGameLaunch> = mutableLaunches.asSharedFlow()

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
                    val definition =
                        definitionProvider(
                            challengeDate,
                            run?.policyVersion ?: DailyChallengePolicyV1.VERSION,
                        )
                    require(definition.policyVersion == DailyChallengePolicyV1.VERSION) {
                        "The current Today UI supports Daily Policy V1 only."
                    }
                    val entry = definition.balanceEntry()
                    val lifecycle =
                        dailyChallengeRepository
                            .read(definition.challengeDate, entry.puzzleType)
                            ?.takeIf { it.matches(definition, entry) }
                    if (run != null) requireNotNull(lifecycle) { "The Daily run is missing its Balance entry." }
                    mutableUiState.value =
                        when (run?.status) {
                            DailyRunStatus.COMPLETED -> {
                                val snapshot = statisticsRepository.observe(definition.challengeDate).first()
                                TodayUiState.Completed(
                                    definition = definition,
                                    hintsUsed = snapshot.dailyHintsUsedByDate[definition.challengeDate],
                                    currentStreak = snapshot.statistics.currentDailyStreak,
                                    bestStreak = snapshot.statistics.bestDailyStreak,
                                )
                            }
                            DailyRunStatus.IN_PROGRESS ->
                                if (matchingDailySession(definition, entry) != null) {
                                    TodayUiState.InProgress(definition)
                                } else {
                                    TodayUiState.Available(definition)
                                }
                            null -> {
                                require(lifecycle == null) { "A Daily entry exists without its aggregate run." }
                                TodayUiState.Available(definition)
                            }
                        }
                } catch (exception: CancellationException) {
                    throw exception
                } catch (_: Exception) {
                    mutableUiState.value = TodayUiState.Error(TodayError.LOAD)
                }
            }
    }

    fun start() {
        val definition = (mutableUiState.value as? TodayUiState.Available)?.definition ?: return
        mutableUiState.value = TodayUiState.Loading
        viewModelScope.launch {
            try {
                val entry = definition.balanceEntry()
                dailyChallengeRepository.createRun(definition)
                mutableLaunches.emit(
                    BalanceGameLaunch.New(
                        difficulty = entry.difficulty,
                        seed = entry.seed,
                        generatorVersion = entry.generatorVersion,
                        context = definition.gameContext(),
                    ),
                )
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Exception) {
                mutableUiState.value = TodayUiState.Error(TodayError.START)
            }
        }
    }

    fun continueGame() {
        val definition = (mutableUiState.value as? TodayUiState.InProgress)?.definition ?: return
        val entry = definition.balanceEntry()
        mutableLaunches.tryEmit(
            BalanceGameLaunch.Restore(
                context = definition.gameContext(),
                expectedPuzzleId = entry.puzzleId,
            ),
        )
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

    private fun DailyChallengeDefinition.balanceEntry(): DailyPuzzleEntry = entries.single { it.puzzleType == PuzzleType.BALANCE }

    private fun DailyChallengeDefinition.gameContext(): BalanceGameContext.Daily = BalanceGameContext.Daily(challengeDate, policyVersion)
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
