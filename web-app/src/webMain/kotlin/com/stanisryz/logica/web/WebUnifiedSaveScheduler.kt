package com.stanisryz.logica.web

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
 */
internal class WebUnifiedSaveScheduler(
    private val saveManager: WebSaveManager,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val isTokenCurrent: (WebPlayerContextToken) -> Boolean = { true },
    private val debounceMs: Long = DEBOUNCE_MS,
) : WebUnifiedSaveAccess {
    private var activeToken: WebPlayerContextToken? = null
    private var scheduled: Job? = null
    private var retryJob: Job? = null
    private var retryAttempts = 0
    private var dirty = false
    private val writeMutex = Mutex()
    private val mutableStatus = MutableStateFlow(WebUnifiedSaveStatus.IDLE)

    override val saveStatus: StateFlow<WebUnifiedSaveStatus> = mutableStatus.asStateFlow()
    override var unifiedSaveActive: Boolean = false
        private set

    /**
     * Migration entry point, runs once per bound Player context after the legacy per-domain
     * cloud merges: restore applies each unified section through its own domain merge
     * semantics on top of local+legacy state, then one canonical unified snapshot is written.
     * Unified ownership becomes ACTIVE only after that canonical write actually succeeds.
     */
    suspend fun restoreAndEstablish(token: WebPlayerContextToken): Boolean {
        activeToken = token
        unifiedSaveActive = false
        dirty = false
        retryAttempts = 0
        mutableStatus.value = WebUnifiedSaveStatus.IDLE
        val restored = runCatching { saveManager.restore() }.getOrDefault(false)
        if (!isCurrent(token)) return restored
        // Canonical unified snapshot attempt: the merged state becomes the future cloud
        // representation; legacy keys stay on the compatibility path until this succeeds.
        establish(token)
        return restored
    }

    override fun markDirty() {
        val token = activeToken ?: return
        // Token currency is the only gate: even while establishment has not succeeded yet
        // (or its retries ran out), a later durable mutation offers another save opportunity.
        if (!isTokenCurrent(token)) return
        dirty = true
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
            while (dirty && isTokenCurrent(token)) {
                dirty = false
                val saved = persistAttempt(token)
                if (!isTokenCurrent(token)) return
                if (!saved) return // A bounded retry continues; gameplay is never blocked.
            }
        }
    }

    /** One full-envelope unified write plus bounded transient-failure handling. */
    private suspend fun persistAttempt(token: WebPlayerContextToken): Boolean {
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
                if (!isTokenCurrent(token)) return@launch
                dirty = true
                drain(token)
            }
    }

    private suspend fun establish(token: WebPlayerContextToken) {
        persistAttempt(token)
    }

    override fun invalidateContext() {
        scheduled?.cancel()
        scheduled = null
        retryJob?.cancel()
        retryJob = null
        retryAttempts = 0
        dirty = false
        activeToken = null
        unifiedSaveActive = false
        mutableStatus.value = WebUnifiedSaveStatus.IDLE
    }

    private fun isCurrent(token: WebPlayerContextToken?): Boolean = token != null && isTokenCurrent(token)

    private companion object {
        /** Short enough that earned progress is not left unsaved, long enough to coalesce. */
        const val DEBOUNCE_MS = 500L

        /** Bounded transient retries: immediate attempt, then ~2s and ~8s, then ERROR. */
        val RETRY_DELAYS_MS = longArrayOf(2_000L, 8_000L)
    }
}
