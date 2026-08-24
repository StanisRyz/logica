package com.stanisryz.logica.web

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.stanisryz.logica.puzzle.core.daily.DailyChallengeDefinition
import com.stanisryz.logica.puzzle.core.daily.DailyChallengePolicyResolver
import com.stanisryz.logica.puzzle.core.daily.DailyChallengePolicyV5
import com.stanisryz.logica.puzzle.core.daily.DailyDate
import com.stanisryz.logica.puzzle.core.daily.DailyPuzzleEntry
import com.stanisryz.logica.puzzle.core.daily.DailyStreakCalculator
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import com.stanisryz.logica.ui.daily.DailyHubCompletion
import com.stanisryz.logica.ui.daily.DailyHubEntry
import com.stanisryz.logica.ui.daily.DailyHubEntryState
import com.stanisryz.logica.ui.daily.DailyHubStreak
import com.stanisryz.logica.ui.daily.DailyHubUiState

/** One transient, runtime-only Web Daily attempt. It is never persisted in any form. */
internal data class WebDailyAttempt(
    val playerContextToken: WebPlayerContextToken,
    val definition: DailyChallengeDefinition,
    val puzzleType: PuzzleType,
) {
    val entry: DailyPuzzleEntry =
        requireNotNull(definition.entries.firstOrNull { it.puzzleType == puzzleType }) {
            "$puzzleType is not part of the Daily Policy ${definition.policyVersion.value} definition."
        }
}

internal sealed interface WebDailyStartResult {
    data class Started(
        val attempt: WebDailyAttempt,
    ) : WebDailyStartResult

    /** No Player-scoped Daily repository is currently bound. */
    data object Unavailable : WebDailyStartResult

    /** Local durability could not be established; gameplay must not start. */
    data object NotStarted : WebDailyStartResult
}

internal sealed interface WebDailyRecordResult {
    data object Recorded : WebDailyRecordResult

    /** The attempt belongs to an earlier Player context; nothing is written anywhere. */
    data object StaleContext : WebDailyRecordResult

    data object Unavailable : WebDailyRecordResult

    data class Rejected(
        val cause: Throwable,
    ) : WebDailyRecordResult

    data class PersistenceFailed(
        val cause: Throwable,
    ) : WebDailyRecordResult
}

/** Gameplay-facing Daily contract; controllers pass only their attempt and final facts. */
internal interface WebDailyGameplayAccess {
    fun recordTerminalResult(
        attempt: WebDailyAttempt,
        outcome: WebStatisticsTerminalOutcome,
        wordAttemptsUsed: Int? = null,
    ): WebDailyRecordResult
}

/** Test/default fallback that keeps gameplay independent when no Daily session is supplied. */
internal object DisabledWebDailyGameplay : WebDailyGameplayAccess {
    override fun recordTerminalResult(
        attempt: WebDailyAttempt,
        outcome: WebStatisticsTerminalOutcome,
        wordAttemptsUsed: Int?,
    ): WebDailyRecordResult = WebDailyRecordResult.Unavailable
}

/**
 * The one seam between Web game controllers and the Stage 45.8a Player-scoped Daily backend.
 * Controllers never touch bindings, tokens, storage keys, or cloud gateways; they receive a
 * validated token-bound attempt and report terminal facts back through this coordinator.
 *
 * Starts are local-first: `ensureRun` must land durably (or already exist) before gameplay may
 * begin; cloud synchronization is only requested afterwards and never blocks the start.
 */
internal class WebDailyGameplayCoordinator(
    private val playerSession: WebDailySessionAccess,
    private val dateProvider: WebDailyDateProvider = BrowserLocalWebDailyDateProvider,
) : WebDailyGameplayAccess {
    /** Surfaced by the Game Hub while a rejected start has not been retried yet. */
    var lastStartWasRejected by mutableStateOf(false)
        private set

    fun start(puzzleType: PuzzleType): WebDailyStartResult {
        val binding = playerSession.dailyBinding.value as? WebDailyBinding.Ready
        if (binding == null) return rejected(WebDailyStartResult.Unavailable)
        // The attempt keeps the challenge date on which it started even across midnight.
        val runState = binding.repository.stateFor(dateProvider.currentDate())
        val entry = runState.definition.entries.firstOrNull { it.puzzleType == puzzleType }
        if (entry == null || runState.entries[puzzleType] == WebDailyEntryState.COMPLETED) {
            return rejected(WebDailyStartResult.NotStarted)
        }
        return when (binding.repository.ensureRun(runState.definition)) {
            is WebDailyMutationResult.Updated, WebDailyMutationResult.Idempotent -> {
                lastStartWasRejected = false
                playerSession.requestDailyCloudSynchronization(binding)
                WebDailyStartResult.Started(
                    WebDailyAttempt(
                        playerContextToken = binding.token,
                        definition = runState.definition,
                        puzzleType = puzzleType,
                    ),
                )
            }
            is WebDailyMutationResult.Rejected, is WebDailyMutationResult.PersistenceFailed ->
                rejected(WebDailyStartResult.NotStarted)
        }
    }

    /**
     * Records the terminal lifecycle synchronously through the local-durable repository path and
     * only then asks for best-effort cloud synchronization, so ordinary Back/navigation can never
     * cancel an already-earned mutation. A stale Player context is a safe no-op.
     */
    override fun recordTerminalResult(
        attempt: WebDailyAttempt,
        outcome: WebStatisticsTerminalOutcome,
        wordAttemptsUsed: Int?,
    ): WebDailyRecordResult {
        val binding =
            playerSession.dailyBinding.value as? WebDailyBinding.Ready ?: return WebDailyRecordResult.Unavailable
        if (binding.token != attempt.playerContextToken) return WebDailyRecordResult.StaleContext
        val result =
            when (outcome) {
                WebStatisticsTerminalOutcome.SOLVED ->
                    binding.repository.recordSolved(
                        attempt.definition,
                        attempt.puzzleType,
                        wordAttemptsUsed.takeIf { attempt.puzzleType == PuzzleType.WORD },
                    )
                WebStatisticsTerminalOutcome.FAILED ->
                    binding.repository.recordFailed(attempt.definition, attempt.puzzleType)
            }
        return when (result) {
            is WebDailyMutationResult.Updated -> {
                playerSession.requestDailyCloudSynchronization(binding)
                WebDailyRecordResult.Recorded
            }
            WebDailyMutationResult.Idempotent -> WebDailyRecordResult.Recorded
            is WebDailyMutationResult.Rejected -> WebDailyRecordResult.Rejected(result.cause)
            is WebDailyMutationResult.PersistenceFailed -> WebDailyRecordResult.PersistenceFailed(result.cause)
        }
    }

    /** Clears a surfaced start rejection when the hub retries or renders fresh content. */
    fun clearStartRejection() {
        lastStartWasRejected = false
    }

    private fun rejected(result: WebDailyStartResult): WebDailyStartResult {
        lastStartWasRejected = true
        return result
    }
}

/** Host-owned deterministic date label; the shared presentation never formats dates itself. */
internal fun formatWebDailyDateLabel(date: DailyDate): String {
    val month =
        when (date.getMonthValue()) {
            1 -> "января"
            2 -> "февраля"
            3 -> "марта"
            4 -> "апреля"
            5 -> "мая"
            6 -> "июня"
            7 -> "июля"
            8 -> "августа"
            9 -> "сентября"
            10 -> "октября"
            11 -> "ноября"
            else -> "декабря"
        }
    return "${date.getDayOfMonth()} $month ${date.getYear()} г."
}

/**
 * Pure reactive mapping from the durable Daily snapshot to the shared hub model. Reading today's
 * state never mutates anything; the run is created only when gameplay actually starts.
 */
internal fun buildWebDailyHubUiState(
    snapshot: WebDailySnapshotV1,
    currentDate: DailyDate,
): DailyHubUiState {
    val record = snapshot.days[currentDate]
    val policyVersion = record?.policyVersion ?: DailyChallengePolicyV5.VERSION
    val definition = DailyChallengePolicyResolver.definitionFor(currentDate, policyVersion)
    val entries =
        definition.entries.map { entry ->
            val facts = record?.facts(entry.puzzleType) ?: WebDailyEntryFacts()
            val state =
                when {
                    facts.solved -> DailyHubEntryState.COMPLETED
                    facts.failedSeen -> DailyHubEntryState.RETRY
                    else -> DailyHubEntryState.AVAILABLE
                }
            DailyHubEntry(entry.puzzleType, entry.difficulty, state)
        }
    val relevant = snapshot.days.values.filterNot { it.date.isAfter(currentDate) }
    val qualifiedDates = relevant.filter(WebDailyDayRecord::qualifiedForStreak).mapTo(linkedSetOf()) { it.date }
    val streak = DailyStreakCalculator.calculate(currentDate, qualifiedDates)
    return DailyHubUiState.Content(
        dateLabel = formatWebDailyDateLabel(currentDate),
        entries = entries,
        streak =
            DailyHubStreak(
                anySolvedQualifies = DailyChallengePolicyResolver.qualifiesStreakOnAnySolvedEntry(policyVersion),
                qualifiedToday = record?.qualifiedForStreak == true,
                current = streak.current,
                best = streak.best,
            ),
        // Full completion only; V5 first-solve streak qualification is shown by the streak chip.
        completion = record?.fullyCompleted?.takeIf { it }?.let { DailyHubCompletion(streak.current, streak.best) },
    )
}


