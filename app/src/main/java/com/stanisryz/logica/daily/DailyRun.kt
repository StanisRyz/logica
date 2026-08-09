package com.stanisryz.logica.daily

import com.stanisryz.logica.puzzle.core.daily.DailyPolicyVersion
import java.time.Instant
import java.time.LocalDate

internal enum class DailyRunStatus {
    IN_PROGRESS,
    COMPLETED,
}

internal data class SavedDailyRun(
    val challengeDate: LocalDate,
    val policyVersion: DailyPolicyVersion,
    val status: DailyRunStatus,
    val createdAt: Instant,
    val updatedAt: Instant,
    val completedAt: Instant?,
) {
    init {
        require((status == DailyRunStatus.COMPLETED) == (completedAt != null)) {
            "A completed Daily run must have a completion timestamp, and an in-progress run must not."
        }
    }
}
