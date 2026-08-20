package com.stanisryz.logica.web

import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import kotlinx.coroutines.flow.StateFlow
import kotlin.jvm.JvmInline

@JvmInline
internal value class WebStatisticsAttemptIdentity(
    val value: Long,
) {
    init {
        require(value > 0L) { "A Web statistics attempt identity must be positive." }
    }
}

private enum class WebStatisticsAttemptStatus {
    ACTIVE,
    RECORDED,
}

/** Opaque, transient identity for one fresh engine start. It is never encoded or persisted. */
internal class WebStatisticsAttempt internal constructor(
    internal val identity: WebStatisticsAttemptIdentity,
    internal val playerContextToken: WebPlayerContextToken?,
    internal val repository: WebStatisticsRepository?,
    internal val puzzleType: PuzzleType,
    internal val difficulty: Difficulty,
) {
    private var status = WebStatisticsAttemptStatus.ACTIVE

    internal val isRecorded: Boolean
        get() = status == WebStatisticsAttemptStatus.RECORDED

    internal fun markRecorded() {
        check(status == WebStatisticsAttemptStatus.ACTIVE)
        status = WebStatisticsAttemptStatus.RECORDED
    }
}

internal sealed interface WebStatisticsAttemptRecordResult {
    data object Recorded : WebStatisticsAttemptRecordResult

    data object AlreadyRecorded : WebStatisticsAttemptRecordResult

    data object ContextChanged : WebStatisticsAttemptRecordResult

    data object Unavailable : WebStatisticsAttemptRecordResult

    data class Failed(
        val cause: Throwable,
    ) : WebStatisticsAttemptRecordResult
}

/** Gameplay-facing statistics contract; controllers pass only final gameplay facts. */
internal interface WebGameplayStatistics {
    fun startAttempt(
        puzzleType: PuzzleType,
        difficulty: Difficulty,
    ): WebStatisticsAttempt

    fun recordTerminalResult(
        attempt: WebStatisticsAttempt,
        outcome: WebStatisticsTerminalOutcome,
        hintsUsed: Int = 0,
        wordAttemptsUsed: Int? = null,
    ): WebStatisticsAttemptRecordResult
}

internal interface WebStatisticsSessionAccess {
    val statisticsBinding: StateFlow<WebStatisticsBinding>

    fun requestStatisticsCloudSynchronization(binding: WebStatisticsBinding.Ready)
}

/** Dynamically binds fresh attempts to the current Player and owns their exactly-once transition. */
internal class WebGameplayStatisticsCoordinator(
    private val playerSession: WebStatisticsSessionAccess,
) : WebGameplayStatistics {
    private var nextAttemptIdentity = 0L

    var lastRecordingFailure: Throwable? = null
        private set

    override fun startAttempt(
        puzzleType: PuzzleType,
        difficulty: Difficulty,
    ): WebStatisticsAttempt {
        check(nextAttemptIdentity < Long.MAX_VALUE) { "Web statistics attempt identities are exhausted." }
        val binding = playerSession.statisticsBinding.value as? WebStatisticsBinding.Ready
        return WebStatisticsAttempt(
            identity = WebStatisticsAttemptIdentity(++nextAttemptIdentity),
            playerContextToken = binding?.token,
            repository = binding?.repository,
            puzzleType = puzzleType,
            difficulty = difficulty,
        )
    }

    override fun recordTerminalResult(
        attempt: WebStatisticsAttempt,
        outcome: WebStatisticsTerminalOutcome,
        hintsUsed: Int,
        wordAttemptsUsed: Int?,
    ): WebStatisticsAttemptRecordResult {
        if (attempt.isRecorded) return WebStatisticsAttemptRecordResult.AlreadyRecorded
        val token = attempt.playerContextToken ?: return WebStatisticsAttemptRecordResult.Unavailable
        val repository = attempt.repository ?: return WebStatisticsAttemptRecordResult.Unavailable
        val binding =
            playerSession.statisticsBinding.value as? WebStatisticsBinding.Ready
                ?: return WebStatisticsAttemptRecordResult.ContextChanged
        if (binding.token != token || binding.repository !== repository) {
            return WebStatisticsAttemptRecordResult.ContextChanged
        }

        val terminalResult =
            runCatching {
                WebStatisticsTerminalResult(
                    puzzleType = attempt.puzzleType,
                    difficulty = attempt.difficulty,
                    outcome = outcome,
                    hintsUsed = hintsUsed,
                    wordAttemptsUsed = wordAttemptsUsed,
                )
            }.getOrElse {
                lastRecordingFailure = it
                return WebStatisticsAttemptRecordResult.Failed(it)
            }

        return when (val recorded = repository.recordTerminalResult(terminalResult)) {
            is WebStatisticsRecordResult.Recorded -> {
                attempt.markRecorded()
                lastRecordingFailure = null
                runCatching { playerSession.requestStatisticsCloudSynchronization(binding) }
                WebStatisticsAttemptRecordResult.Recorded
            }
            is WebStatisticsRecordResult.PersistenceFailed -> {
                lastRecordingFailure = recorded.cause
                WebStatisticsAttemptRecordResult.Failed(recorded.cause)
            }
            is WebStatisticsRecordResult.Rejected -> {
                lastRecordingFailure = recorded.cause
                WebStatisticsAttemptRecordResult.Failed(recorded.cause)
            }
        }
    }
}

/** Test/default fallback that keeps gameplay independent when no statistics session is supplied. */
internal object DisabledWebGameplayStatistics : WebGameplayStatistics {
    private var nextAttemptIdentity = 0L

    override fun startAttempt(
        puzzleType: PuzzleType,
        difficulty: Difficulty,
    ): WebStatisticsAttempt =
        WebStatisticsAttempt(
            identity = WebStatisticsAttemptIdentity(++nextAttemptIdentity),
            playerContextToken = null,
            repository = null,
            puzzleType = puzzleType,
            difficulty = difficulty,
        )

    override fun recordTerminalResult(
        attempt: WebStatisticsAttempt,
        outcome: WebStatisticsTerminalOutcome,
        hintsUsed: Int,
        wordAttemptsUsed: Int?,
    ): WebStatisticsAttemptRecordResult = WebStatisticsAttemptRecordResult.Unavailable
}
