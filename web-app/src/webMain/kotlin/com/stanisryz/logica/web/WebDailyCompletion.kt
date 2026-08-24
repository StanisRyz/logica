package com.stanisryz.logica.web

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Web-only Daily terminal persistence state. It is gameplay/application state bound to exactly one
 * active Daily attempt — never shared Daily Hub state, whose entries stay durable-only.
 */
internal sealed interface WebDailyCompletionState {
    data object Idle : WebDailyCompletionState

    /** The Daily local-durable mutation actually succeeded; the terminal UI may claim it saved. */
    data class Saved(
        val outcome: WebStatisticsTerminalOutcome,
    ) : WebDailyCompletionState

    /**
     * The real gameplay result was reached, but local Daily durability could not be established.
     * Cloud synchronization failures never land here.
     */
    data class SaveError(
        val outcome: WebStatisticsTerminalOutcome,
        val detail: String,
    ) : WebDailyCompletionState
}

/** One immutable terminal fact set; save retry repeats only this, never Statistics. */
private data class WebDailyTerminalFacts(
    val outcome: WebStatisticsTerminalOutcome,
    val wordAttemptsUsed: Int?,
)

/**
 * The one reusable Daily completion persistence abstraction shared by all five Web controllers,
 * deliberately separate from `WebCatalogCompletionController`: Catalog and Daily stay different
 * domains with different durability semantics.
 *
 * The local Daily mutation itself is synchronous; no cancellable UI coroutine ever hides it.
 */
internal class WebDailyCompletionController(
    private val daily: WebDailyGameplayAccess,
) {
    private var attempt: WebDailyAttempt? = null
    private var pendingFacts: WebDailyTerminalFacts? = null

    var state by mutableStateOf<WebDailyCompletionState>(WebDailyCompletionState.Idle)
        private set

    /** Binds the state to one fresh Daily gameplay attempt and clears any previous terminal facts. */
    fun startAttempt(newAttempt: WebDailyAttempt) {
        attempt = newAttempt
        pendingFacts = null
        state = WebDailyCompletionState.Idle
    }

    /**
     * Records one terminal fact through the local-durable repository path and updates the state
     * from the actual result: only [WebDailyRecordResult.Recorded] produces [Saved].
     */
    fun saveTerminal(
        dailyAttempt: WebDailyAttempt,
        outcome: WebStatisticsTerminalOutcome,
        wordAttemptsUsed: Int? = null,
    ) {
        if (attempt != dailyAttempt) return
        if (state is WebDailyCompletionState.Saved) return
        pendingFacts = WebDailyTerminalFacts(outcome, wordAttemptsUsed)
        state =
            when (val result = daily.recordTerminalResult(dailyAttempt, outcome, wordAttemptsUsed)) {
                WebDailyRecordResult.Recorded -> WebDailyCompletionState.Saved(outcome)
                WebDailyRecordResult.StaleContext ->
                    WebDailyCompletionState.SaveError(outcome, "The Player context changed before the result could be saved.")
                WebDailyRecordResult.Unavailable ->
                    WebDailyCompletionState.SaveError(outcome, "No Player-scoped Daily storage is currently available.")
                is WebDailyRecordResult.Rejected ->
                    WebDailyCompletionState.SaveError(outcome, result.cause.message ?: "The Daily entry rejected the result.")
                is WebDailyRecordResult.PersistenceFailed ->
                    WebDailyCompletionState.SaveError(outcome, result.cause.message ?: "Browser storage rejected the update.")
            }
    }

    /** Repeats only the original Daily mutation; Statistics were recorded once at the real attempt. */
    fun retrySave() {
        val facts = pendingFacts ?: return
        if (state !is WebDailyCompletionState.SaveError) return
        val bound = attempt ?: return
        saveTerminal(bound, facts.outcome, facts.wordAttemptsUsed)
    }

    fun reset() {
        attempt = null
        pendingFacts = null
        state = WebDailyCompletionState.Idle
    }
}
