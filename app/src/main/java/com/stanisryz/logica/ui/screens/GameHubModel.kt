package com.stanisryz.logica.ui.screens

import com.stanisryz.logica.R
import com.stanisryz.logica.daily.DailyEntryState
/**
 * The one action a Daily card performs when it is tapped, or `null` for an entry that is done. The
 * card itself is the target, so a state maps to exactly one label. An unfinished Daily attempt is
 * transient, so there is no Continue.
 */
internal fun DailyEntryState.primaryActionResource(): Int? =
    when (this) {
        DailyEntryState.AVAILABLE -> R.string.start
        DailyEntryState.RETRY -> R.string.retry_puzzle
        DailyEntryState.COMPLETED -> null
    }

/** Whether tapping the card does anything. A finished entry never acts; starting needs a life. */
internal fun DailyEntryState.isActionable(gameplayAllowed: Boolean): Boolean =
    when (this) {
        DailyEntryState.COMPLETED -> false
        DailyEntryState.AVAILABLE, DailyEntryState.RETRY -> gameplayAllowed
    }
