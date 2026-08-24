package com.stanisryz.logica.ui.daily

import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.PuzzleType

/** How one Daily entry stands today; derived only from durable facts, never from live sessions. */
enum class DailyHubEntryState {
    AVAILABLE,
    RETRY,
    COMPLETED,
}

/** One policy entry of the current Daily challenge as the Game Hub shows it. */
data class DailyHubEntry(
    val puzzleType: PuzzleType,
    val difficulty: Difficulty,
    val state: DailyHubEntryState,
)

/**
 * Streak status separate from full completion. From Policy V5 on one solved entry already
 * qualifies the date, so [qualifiedToday] can be true while entries are still open.
 */
data class DailyHubStreak(
    val anySolvedQualifies: Boolean,
    val qualifiedToday: Boolean,
    val current: Int,
    val best: Int,
)

/** One spoiler-free per-puzzle result row of a completed Daily; hosts supply what they have. */
data class DailyHubResultRow(
    val puzzleType: PuzzleType,
    val solved: Boolean,
    val wordAttemptsUsed: Int? = null,
)

/**
 * The full-completion card payload. Hosts decide how much detail they can provide: Android
 * supplies result rows, hint totals, and its own share action, while Web currently offers only
 * the streaks. [onShare] stays host-owned; sharing itself never enters shared presentation.
 */
data class DailyHubCompletion(
    val currentStreak: Int,
    val bestStreak: Int,
    val hintsUsed: Int? = null,
    val resultRows: List<DailyHubResultRow>? = null,
    val onShare: (() -> Unit)? = null,
)

/**
 * Platform-neutral state of the Daily block inside the Game Hub. It carries no repository,
 * Player identity, Room entity, or browser type; hosts map their own domains onto it.
 */
sealed interface DailyHubUiState {
    data object Loading : DailyHubUiState

    /** A load or start failure; [detailLabel] is an already localized host string when present. */
    data class Error(
        val detailLabel: String? = null,
    ) : DailyHubUiState

    /**
     * Today's challenge. [dateLabel] arrives pre-formatted so shared presentation never touches
     * `java.time` or browser date APIs.
     */
    data class Content(
        val dateLabel: String,
        val entries: List<DailyHubEntry>,
        val streak: DailyHubStreak,
        val completion: DailyHubCompletion? = null,
    ) : DailyHubUiState {
        val totalCount: Int get() = entries.size
        val completedCount: Int get() = entries.count { it.state == DailyHubEntryState.COMPLETED }
    }
}
