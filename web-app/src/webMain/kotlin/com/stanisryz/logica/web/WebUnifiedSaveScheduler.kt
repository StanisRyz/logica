package com.stanisryz.logica.web

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Application-level diagnostics for the unified save pipeline; never business state. */
internal enum class WebUnifiedSaveStatus {
    IDLE,
    DIRTY,
    SAVING,
    SYNCED,
    ERROR,
}

/**
 * The session-facing unified save surface: the Web Player session gates its legacy per-domain
 * cloud writes through [unifiedSaveActive] and reports durable local changes via [markDirty].
 */
internal interface WebUnifiedSaveAccess {
    /** True once a canonical unified snapshot exists for the currently bound Player context. */
    val unifiedSaveActive: Boolean

    val saveStatus: StateFlow<WebUnifiedSaveStatus>

    /** Reports a meaningful durable Player-state change; coalesced, never per-move. */
    fun markDirty()

    /**
     * Serialized immediate canonical save for high-value durable changes (e.g., paid
     * fulfillment). Bypasses debouncing but reuses the same writer mutex, Player-context
     * checks, payload-budget validation, and canonical Yandex path. False on failure.
     */
    suspend fun flushNow(): Boolean

    /** Drops all pending state because the Player context changed or is being rebound. */
    fun invalidateContext()
}

/**
 * Operational Stage 45.13 unified save pipeline: one debounced, serialized, token-bound cloud
 * write path over the existing [WebSaveManager]. Closely related durable changes (one terminal
 * event touching Catalog + Statistics + Economy) coalesce into a single full-envelope write;
 * a change arriving during an in-flight write marks the state dirty and the newest full
 * snapshot is written right after. Cloud failure never blocks gameplay: local repositories
 * stay authoritative and the next durable change retries the unified write.
 *
 * Every unified write — establishment, coalesced drains, retries, and [flushNow] — is allowed
 * only after restore gave a definite answer (RESTORED or EMPTY) for the current Player
 * context. A failed read, an undecodable envelope, or an unreadable/unknown section leaves the
 * context UNRESOLVED: nothing is written, the legacy compatibility path keeps working, and
 * restore itself is retried (bounded, or on the next durable change) before establishment.
 */
internal class WebUnifiedSaveScheduler(
    private val saveManager: WebSaveManager,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val isTokenCurrent: (WebPlayerContextToken) -> Boolean = { true },
    private val debounceMs: Long = DEBOUNCE_MS,
) : WebUnifiedSaveAccess {
    private var activeToken: WebPlayerContextToken? = null

    /** The context whose restore gave a definite answer; the only context that may write. */
    private var resolvedToken: WebPlayerContextToken? = null

    /** True when the current context has no unified storage at all (never read, never written). */
    private var storageUnavailable = false
    private var scheduled: Job? = null
    private var retryJob: Job? = null
    private var retryAttempts = 0
    private var restoreRetryJob: Job? = null
    private var restoreRetryAttempts = 0
    private var dirty = false
    private val writeMutex = Mutex()
    private val restoreMutex = Mutex()
    private val mutableStatus = MutableStateFlow(WebUnifiedSaveStatus.IDLE)

    override val saveStatus: StateFlow<WebUnifiedSaveStatus> = mutableStatus.asStateFlow()
    override var unifiedSaveActive: Boolean = false
        private set

    /**
     * Migration entry point, runs once per bound Player context after the legacy per-domain
     * cloud merges: restore applies each unified section through its own domain merge
     * semantics on top of local+legacy state, then one canonical unified snapshot is written.
     * Unified ownership becomes ACTIVE only after that canonical write actually succeeds, and
     * the write is attempted only once restore answered definitely for this context.
     */
    suspend fun restoreAndEstablish(token: WebPlayerContextToken): WebSaveRestoreOutcome {
        cancelPending()
        activeToken = token
        resolvedToken = null
        storageUnavailable = false
        unifiedSaveActive = false
        dirty = false
        retryAttempts = 0
        restoreRetryAttempts = 0
        mutableStatus.value = WebUnifiedSaveStatus.IDLE
        return attemptRestore(token)
    }

    /**
     * One serialized restore attempt for [token]. A definite answer establishes the canonical
     * snapshot; an unresolved one keeps writes blocked and schedules a bounded restore retry.
     */
    private suspend fun attemptRestore(token: WebPlayerContextToken): WebSaveRestoreOutcome {
        var resolvedNow = false
        val outcome =
            restoreMutex.withLock {
                if (!isContextCurrent(token)) return WebSaveRestoreOutcome.UNRESOLVED
                // Another attempt already resolved and established this context.
                if (resolvedToken == token) return WebSaveRestoreOutcome.RESTORED
                val outcome =
                    try {
                        saveManager.restore()
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Throwable) {
                        WebSaveRestoreOutcome.UNRESOLVED
                    }
                if (!isContextCurrent(token)) return outcome
                when (outcome) {
                    WebSaveRestoreOutcome.RESTORED, WebSaveRestoreOutcome.EMPTY -> {
                        resolvedToken = token
                        resolvedNow = true
                        restoreRetryAttempts = 0
                        // A retry still waiting (never this attempt's own job) has nothing left to do.
                        restoreRetryJob?.cancel()
                        restoreRetryJob = null
                    }
                    WebSaveRestoreOutcome.UNRESOLVED -> {
                        // Never overwrite a save we could not read: the legacy path stays in
                        // charge and restore is retried before any unified write.
                        unifiedSaveActive = false
                        mutableStatus.value = WebUnifiedSaveStatus.ERROR
                        scheduleRestoreRetry(token)
                    }
                    WebSaveRestoreOutcome.UNAVAILABLE -> {
                        storageUnavailable = true
                        unifiedSaveActive = false
                    }
                }
                outcome
            }
        if (resolvedNow) {
            // Canonical unified snapshot attempt: the merged state becomes the future cloud
            // representation; legacy keys stay on the compatibility path until this succeeds.
            establish(token)
        }
        return outcome
    }

    /** Bounded restore retries for an unresolved context (~2s, ~8s, ~30s), then a stop. */
    private fun scheduleRestoreRetry(token: WebPlayerContextToken) {
        if (restoreRetryAttempts >= RESTORE_RETRY_DELAYS_MS.size) return
        val delayMs = RESTORE_RETRY_DELAYS_MS[restoreRetryAttempts]
        restoreRetryAttempts += 1
        launchRestore(token, delayMs)
    }

    private fun launchRestore(
        token: WebPlayerContextToken,
        delayMs: Long,
    ) {
        restoreRetryJob?.cancel()
        restoreRetryJob =
            scope.launch {
                delay(delayMs)
                restoreRetryJob = null
                attemptRestore(token)
            }
    }

    override fun markDirty() {
        val token = activeToken ?: return
        // Even while establishment has not succeeded yet (or its retries ran out), a later
        // durable mutation offers another save — or, while unresolved, restore — opportunity.
        if (!isTokenCurrent(token)) return
        if (storageUnavailable) return
        dirty = true
        if (resolvedToken != token) {
            // Unresolved restore: a durable change never writes the envelope; it only asks for
            // another restore (deduplicated against a pending one), which then establishes.
            if (restoreRetryJob?.isActive != true) launchRestore(token, debounceMs)
            return
        }
        if (mutableStatus.value != WebUnifiedSaveStatus.SAVING) {
            mutableStatus.value = WebUnifiedSaveStatus.DIRTY
        }
        // Coalesce bursts: only the latest pending debounce survives; an in-flight drain is
        // never cancelled, it observes `dirty` and rewrites the newest full snapshot itself.
        scheduled?.cancel()
        scheduled =
            scope.launch {
                delay(debounceMs)
                drain(token)
            }
    }

    private suspend fun drain(token: WebPlayerContextToken) {
        scheduled = null
        writeMutex.withLock {
            while (dirty && canWrite(token)) {
                dirty = false
                val saved = persistAttempt(token)
                if (!isTokenCurrent(token)) return
                if (!saved) return // A bounded retry continues; gameplay is never blocked.
            }
        }
    }

    /** One full-envelope unified write plus bounded transient-failure handling. */
    private suspend fun persistAttempt(token: WebPlayerContextToken): Boolean {
        // The single write gate: no unified write without a definite restore for this context.
        if (!canWrite(token)) return false
        mutableStatus.value = WebUnifiedSaveStatus.SAVING
        val saved = runCatching { saveManager.persist() }.getOrDefault(false)
        if (!isTokenCurrent(token)) return saved
        if (saved) {
            unifiedSaveActive = true
            retryAttempts = 0
            mutableStatus.value = WebUnifiedSaveStatus.SYNCED
        } else {
            // Never claim ownership/SYNCED without a real successful canonical write.
            unifiedSaveActive = false
            mutableStatus.value = WebUnifiedSaveStatus.ERROR
            scheduleBoundedRetry(token)
        }
        return saved
    }

    /**
     * Conservative bounded retry for transient failures: two short-backoff attempts, then a
     * stop. No polling, no background churn; a later durable change offers another opportunity.
     */
    private fun scheduleBoundedRetry(token: WebPlayerContextToken) {
        if (retryAttempts >= RETRY_DELAYS_MS.size) return
        val delayMs = RETRY_DELAYS_MS[retryAttempts]
        retryAttempts += 1
        retryJob?.cancel()
        retryJob =
            scope.launch {
                delay(delayMs)
                if (!canWrite(token)) return@launch
                dirty = true
                drain(token)
            }
    }

    private suspend fun establish(token: WebPlayerContextToken) {
        writeMutex.withLock {
            dirty = false
            persistAttempt(token)
        }
    }

    override fun invalidateContext() {
        cancelPending()
        retryAttempts = 0
        restoreRetryAttempts = 0
        dirty = false
        activeToken = null
        resolvedToken = null
        storageUnavailable = false
        unifiedSaveActive = false
        mutableStatus.value = WebUnifiedSaveStatus.IDLE
    }

    private fun cancelPending() {
        scheduled?.cancel()
        scheduled = null
        retryJob?.cancel()
        retryJob = null
        restoreRetryJob?.cancel()
        restoreRetryJob = null
    }

    private fun isContextCurrent(token: WebPlayerContextToken): Boolean = token == activeToken && isTokenCurrent(token)

    private fun canWrite(token: WebPlayerContextToken): Boolean = resolvedToken == token && isContextCurrent(token)

    override suspend fun flushNow(): Boolean {
        val token = activeToken ?: return false
        // Unresolved restore: report failure so callers (paid fulfillment) keep their own
        // recovery path instead of believing the purchase reached the cloud.
        if (!canWrite(token)) return false
        writeMutex.withLock { return persistAttempt(token) }
    }

    private companion object {
        /** Short enough that earned progress is not left unsaved, long enough to coalesce. */
        const val DEBOUNCE_MS = 500L

        /** Bounded transient retries: immediate attempt, then ~2s and ~8s, then ERROR. */
        val RETRY_DELAYS_MS = longArrayOf(2_000L, 8_000L)

        /** Bounded restore retries for an unresolved context: ~2s, ~8s, ~30s, then ERROR. */
        val RESTORE_RETRY_DELAYS_MS = longArrayOf(2_000L, 8_000L, 30_000L)
    }
}
