@file:OptIn(ExperimentalWasmJsInterop::class)

package com.stanisryz.logica.web

import com.stanisryz.logica.platform.EconomyConsumptionType
import com.stanisryz.logica.platform.EconomyEvent
import com.stanisryz.logica.platform.EconomyPolicy
import com.stanisryz.logica.platform.EconomyRewardType
import com.stanisryz.logica.platform.EconomyState
import com.stanisryz.logica.platform.PlayerIdentity
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.PuzzleGemReward
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Explicit result of applying an external (cloud/recovered) snapshot to a local repository.
 * External state never becomes observable unless its local durable write succeeded.
 */
internal sealed interface WebExternalRestoreResult {
    data object Applied : WebExternalRestoreResult

    data object NoChange : WebExternalRestoreResult

    data class PersistenceFailed(
        val cause: Throwable,
    ) : WebExternalRestoreResult

    data object Rejected : WebExternalRestoreResult
}

/** Adds [amount] gems to [gems], saturating at [EconomyPolicy.MAX_GEMS] instead of overflowing. */
internal fun saturatedGems(
    gems: Int,
    amount: Int,
): Int = (gems.toLong() + amount).coerceIn(0L, EconomyPolicy.MAX_GEMS.toLong()).toInt()

/**
 * Versioned Player-scoped economy save model. Intentionally simple and migration-ready: a new
 * schema version can be introduced without infrastructure because every read validates the
 * version explicitly.
 */
internal data class WebEconomySnapshot(
    val version: Int = CURRENT_VERSION,
    val gems: Int,
    val lives: Int,
    val nextLifeRestoreAtEpochMs: Long?,
    /**
     * Monotonic mutation revision from the Player-scoped [WebPlayerStateRevisions] timeline.
     * V1 payloads carry no revision and load as `0`; the field exists so unified cloud restore
     * can compare whole wallet snapshots instead of naively overwriting newer local state.
     */
    val revision: Long = 0L,
) {
    init {
        require(version == CURRENT_VERSION) { "Unsupported Web economy schema $version." }
        require(gems >= 0) { "Stored Web gems must never be negative." }
        require(lives in 0..EconomyPolicy.MAXIMUM_LIVES) { "Stored Web lives are outside the supported range." }
        require(revision >= 0L) { "Web economy revisions are monotonic and never negative." }
    }

    fun toState(): EconomyState = EconomyState(gems = gems, lives = lives, nextLifeRestoreAtEpochMs = nextLifeRestoreAtEpochMs)

    companion object {
        const val CURRENT_VERSION = 2

        val DEFAULT =
            WebEconomySnapshot(
                gems = EconomyPolicy.STARTING_GEMS,
                lives = EconomyPolicy.STARTING_LIVES,
                nextLifeRestoreAtEpochMs = null,
            )
    }
}

/** Deterministic compact binary format with an explicit schema version byte. */
internal object WebEconomyCodec {
    private val magic = byteArrayOf('L'.code.toByte(), 'G'.code.toByte(), 'E'.code.toByte(), 'C'.code.toByte())
    private const val V1_SIZE = 4 + 1 + 4 + 1 + 1 + 8
    private const val SIZE = V1_SIZE + 8

    fun encode(snapshot: WebEconomySnapshot): ByteArray {
        val result = ByteArray(SIZE)
        magic.copyInto(result)
        result[4] = snapshot.version.toByte()
        writeInt(result, 5, snapshot.gems)
        result[9] = snapshot.lives.toByte()
        val restore = snapshot.nextLifeRestoreAtEpochMs
        result[10] = if (restore == null) 0 else 1
        if (restore != null) {
            for (index in 0 until 8) {
                result[11 + index] = (restore ushr ((7 - index) * 8)).toByte()
            }
        } else {
            for (index in 0 until 8) {
                result[11 + index] = 0
            }
        }
        writeLong(result, V1_SIZE, snapshot.revision)
        return result
    }

    fun decode(payload: ByteArray): WebEconomySnapshot? =
        runCatching {
            // V1 payloads carry no revision and normalize to revision 0.
            require(payload.size == V1_SIZE || payload.size == SIZE)
            require(magic.indices.all { payload[it] == magic[it] })
            val version = payload[4].toInt() and 0xff
            require(version in 1..WebEconomySnapshot.CURRENT_VERSION)
            // Values outside the wallet's invariants (a tampered or foreign payload) are clamped to the
            // nearest bound instead of rejecting the whole snapshot.
            val gems = readInt(payload, 5).coerceIn(0, EconomyPolicy.MAX_GEMS)
            val lives = payload[9].toInt().coerceIn(0, EconomyPolicy.MAXIMUM_LIVES)
            val hasRestore = payload[10].toInt() != 0
            var restore = 0L
            if (hasRestore) {
                for (index in 0 until 8) {
                    restore = (restore shl 8) or (payload[11 + index].toLong() and 0xff)
                }
            }
            var revision = 0L
            if (payload.size == SIZE) {
                for (index in 0 until 8) {
                    revision = (revision shl 8) or (payload[V1_SIZE + index].toLong() and 0xff)
                }
            }
            WebEconomySnapshot(
                gems = gems,
                lives = lives,
                nextLifeRestoreAtEpochMs = if (hasRestore) restore else null,
                revision = revision,
            )
        }.getOrNull()

    private fun writeLong(
        destination: ByteArray,
        offset: Int,
        value: Long,
    ) {
        for (index in 0 until 8) {
            destination[offset + index] = (value ushr ((7 - index) * 8)).toByte()
        }
    }

    private fun writeInt(
        destination: ByteArray,
        offset: Int,
        value: Int,
    ) {
        destination[offset] = (value ushr 24).toByte()
        destination[offset + 1] = (value ushr 16).toByte()
        destination[offset + 2] = (value ushr 8).toByte()
        destination[offset + 3] = value.toByte()
    }

    private fun readInt(
        source: ByteArray,
        offset: Int,
    ): Int =
        ((source[offset].toInt() and 0xff) shl 24) or
            ((source[offset + 1].toInt() and 0xff) shl 16) or
            ((source[offset + 2].toInt() and 0xff) shl 8) or
            (source[offset + 3].toInt() and 0xff)
}

internal interface WebEconomyStore {
    fun load(): WebEconomySnapshot

    fun save(snapshot: WebEconomySnapshot)
}

/** Economy uses its own Player-scoped local key and never shares another domain's payload. */
internal class WebEconomyLocalStore(
    scope: WebCatalogProgressScope,
) : WebEconomyStore {
    internal val storageKey = "$LOCAL_STORAGE_KEY_PREFIX:${scope.keySuffix}"

    override fun load(): WebEconomySnapshot {
        val encoded = economyLocalStorageGet(storageKey) ?: return WebEconomySnapshot.DEFAULT
        val payload = WebBase64.decode(encoded) ?: return WebEconomySnapshot.DEFAULT
        return WebEconomyCodec.decode(payload) ?: WebEconomySnapshot.DEFAULT
    }

    override fun save(snapshot: WebEconomySnapshot) {
        economyLocalStorageSet(storageKey, WebBase64.encode(WebEconomyCodec.encode(snapshot)))
    }

    private companion object {
        const val LOCAL_STORAGE_KEY_PREFIX = "logica_economy_v1"
    }
}

private fun economyLocalStorageGet(key: String): String? = js("globalThis.localStorage.getItem(key)")

private fun economyLocalStorageSet(
    key: String,
    value: String,
) {
    js("globalThis.localStorage.setItem(key, value)")
}

internal fun interface WebEconomyRepositoryFactory {
    fun create(scope: WebCatalogProgressScope): WebPlayerEconomyRepository
}

internal sealed interface WebEconomyBinding {
    data object Loading : WebEconomyBinding

    data class Ready(
        val token: WebPlayerContextToken,
        val repository: WebPlayerEconomyRepository,
        val identity: PlayerIdentity?,
    ) : WebEconomyBinding

    data class Unavailable(
        val detail: String,
    ) : WebEconomyBinding
}

/** Session-facing economy surface used by gameplay and Profile; cloud sync joins later stages. */
internal interface WebEconomySessionAccess {
    val economyBinding: StateFlow<WebEconomyBinding>
}

/**
 * Gameplay economy seam, the same for Catalog and Daily: a solved attempt pays the gems the one rule
 * ([PuzzleGemReward]) gave it — worked out once by its caller, which also shows them — a failed one
 * costs a life, and leaving an attempt with real progress costs a life as well.
 */
internal interface WebGameplayEconomy {
    fun recordTerminalResult(
        solved: Boolean,
        gemsEarned: Int,
    )

    /** The player left an unfinished attempt that already had real progress. */
    fun recordAbandonedAttempt()
}

internal object DisabledWebGameplayEconomy : WebGameplayEconomy {
    override fun recordTerminalResult(
        solved: Boolean,
        gemsEarned: Int,
    ) = Unit

    override fun recordAbandonedAttempt() = Unit
}

/** Dynamically applies gameplay economy effects to the repository bound to the current Player. */
internal class WebGameplayEconomyCoordinator(
    private val playerSession: WebEconomySessionAccess,
) : WebGameplayEconomy {
    override fun recordTerminalResult(
        solved: Boolean,
        gemsEarned: Int,
    ) {
        val binding = playerSession.economyBinding.value as? WebEconomyBinding.Ready ?: return
        binding.repository.applyTerminalResult(solved, gemsEarned)
    }

    override fun recordAbandonedAttempt() {
        val binding = playerSession.economyBinding.value as? WebEconomyBinding.Ready ?: return
        binding.repository.applyAbandonedAttempt()
    }
}

/**
 * Gameplay inventory consumption seam. Only Sudoku hint usage is integrated in this stage; a
 * consumption attempt spends exactly one unit of the Player's own inventory and never touches
 * Daily lifecycle, lives, or Catalog progression.
 */
internal interface WebGameplayStore {
    /** Consumes one hint from the bound Player inventory; false when none is available. */
    fun tryConsumeHint(): Boolean
}

internal object DisabledWebGameplayStore : WebGameplayStore {
    override fun tryConsumeHint(): Boolean = false
}

internal class WebGameplayStoreCoordinator(
    private val playerSession: WebStoreSessionAccess,
) : WebGameplayStore {
    override fun tryConsumeHint(): Boolean {
        val binding = playerSession.storeBinding.value as? WebStoreBinding.Ready ?: return false
        return binding.repository.consumeInventory(STORE_INVENTORY_HINTS)
    }
}

/**
 * The pure economy processing pipeline: gameplay facts in, new state plus events out. UI never
 * mutates wallet values directly; every change flows through here.
 */
internal object WebEconomyProcessor {
    /**
     * A Daily entry's gems: the same rule as a Catalog level ([PuzzleGemReward], as on Android) with
     * no earlier best, so a Medium Daily pays none.
     */
    fun dailyGemsFor(
        puzzleType: PuzzleType,
        difficulty: Difficulty,
        stars: Int?,
    ): Int = PuzzleGemReward.forSolved(puzzleType, difficulty, stars, previousBestStars = null)

    fun onTerminalResult(
        state: EconomyState,
        solved: Boolean,
        gemsEarned: Int,
        nowEpochMs: Long,
    ): Pair<EconomyState, List<EconomyEvent>> =
        if (solved) {
            val reward = gemsEarned.coerceAtLeast(0)
            if (reward == 0) {
                state to listOf(EconomyEvent.GameCompleted)
            } else {
                EconomyState(saturatedGems(state.gems, reward), state.lives, state.nextLifeRestoreAtEpochMs) to
                    listOf(EconomyEvent.GameCompleted, EconomyEvent.RewardGranted(EconomyRewardType.GEMS, reward))
            }
        } else {
            onLifeLost(state, EconomyEvent.GameFailed, nowEpochMs)
        }

    /** Leaving an attempt with real progress costs a life, exactly like losing it. */
    fun onAbandonedAttempt(
        state: EconomyState,
        nowEpochMs: Long,
    ): Pair<EconomyState, List<EconomyEvent>> = onLifeLost(state, null, nowEpochMs)

    private fun onLifeLost(
        state: EconomyState,
        cause: EconomyEvent?,
        nowEpochMs: Long,
    ): Pair<EconomyState, List<EconomyEvent>> {
        val consumed = minOf(EconomyPolicy.FAILED_ATTEMPT_LIFE_COST, state.lives)
        val events =
            listOfNotNull(cause) +
                if (consumed > 0) listOf(EconomyEvent.ResourceConsumed(EconomyConsumptionType.LIFE, consumed)) else emptyList()
        return withLivesSpent(state, consumed, nowEpochMs) to events
    }

    /** Spends lives; an already running countdown keeps its remaining time instead of restarting. */
    fun withLivesSpent(
        state: EconomyState,
        amount: Int,
        nowEpochMs: Long,
    ): EconomyState {
        val lives = (state.lives - amount).coerceAtLeast(0)
        if (lives == state.lives) return state
        return EconomyState(
            state.gems,
            lives,
            state.nextLifeRestoreAtEpochMs ?: (nowEpochMs + EconomyPolicy.LIFE_RESTORE_INTERVAL_MS),
        )
    }

    /** Lives from any non-regeneration source; a full wallet clears the countdown. */
    fun restoreAnchorFor(
        lives: Int,
        currentAnchor: Long?,
    ): Long? = if (lives >= EconomyPolicy.MAXIMUM_LIVES) null else currentAnchor

    /**
     * Restores every life whose whole interval has elapsed and keeps the remaining partial interval
     * running toward the next one, exactly like the Android wallet. Elapsed time is never negative:
     * a backwards clock restores nothing, and a wait longer than one interval is repaired so a wrong
     * clock cannot freeze regeneration. A missing life without a countdown (older saves) starts one.
     */
    fun regenerated(
        state: EconomyState,
        nowEpochMs: Long,
    ): EconomyState {
        val interval = EconomyPolicy.LIFE_RESTORE_INTERVAL_MS
        if (state.lives >= EconomyPolicy.MAXIMUM_LIVES) {
            return if (state.nextLifeRestoreAtEpochMs == null) state else state.copy(nextLifeRestoreAtEpochMs = null)
        }
        val dueAt = state.nextLifeRestoreAtEpochMs ?: return state.copy(nextLifeRestoreAtEpochMs = nowEpochMs + interval)
        if (nowEpochMs < dueAt) {
            val latestSaneDueAt = nowEpochMs + interval
            return if (dueAt > latestSaneDueAt) state.copy(nextLifeRestoreAtEpochMs = latestSaneDueAt) else state
        }
        val completedIntervals = 1 + (nowEpochMs - dueAt) / interval
        val restored = minOf(completedIntervals, (EconomyPolicy.MAXIMUM_LIVES - state.lives).toLong()).toInt()
        val lives = state.lives + restored
        return if (lives >= EconomyPolicy.MAXIMUM_LIVES) {
            state.copy(lives = EconomyPolicy.MAXIMUM_LIVES, nextLifeRestoreAtEpochMs = null)
        } else {
            state.copy(lives = lives, nextLifeRestoreAtEpochMs = dueAt + restored * interval)
        }
    }
}

/**
 * Player-scoped wallet foundation. The repository owns persistence; all mutations go through
 * [WebEconomyProcessor] and land durably before the observable state is published. The shared
 * [WebPlayerStateRevisions] timeline stamps every mutation so unified cloud restore can compare
 * whole snapshots against cloud state without ever mixing wallet and inventory generations.
 */
internal class WebPlayerEconomyRepository(
    val scope: WebCatalogProgressScope,
    private val store: WebEconomyStore,
    private val revisions: WebPlayerStateRevisions = WebPlayerStateRevisions(),
    private val currentTimeMs: () -> Long = webClock::now,
) {
    private val mutableState =
        MutableStateFlow(EconomyState(EconomyPolicy.STARTING_GEMS, EconomyPolicy.STARTING_LIVES, null))
    val state: StateFlow<EconomyState> = mutableState.asStateFlow()

    /** Latest durable snapshot including its restore revision; export for the unified save. */
    var currentSnapshot: WebEconomySnapshot = WebEconomySnapshot.DEFAULT
        private set

    /** Invoked after every successful durable local mutation; never after a cloud restore. */
    var onDurableChange: (() -> Unit)? = null

    fun loadLocal() {
        val loaded = store.load()
        currentSnapshot = loaded
        revisions.raiseTo(loaded.revision)
        mutableState.value = loaded.toState()
    }

    /** A finished attempt, Catalog or Daily: its earned gems when solved, one life when failed. */
    fun applyTerminalResult(
        solved: Boolean,
        gemsEarned: Int,
    ): List<EconomyEvent> = mutate { state, now -> WebEconomyProcessor.onTerminalResult(state, solved, gemsEarned, now) }

    /** An unfinished attempt with real progress was left: one life. */
    fun applyAbandonedAttempt(): List<EconomyEvent> = mutate { state, now -> WebEconomyProcessor.onAbandonedAttempt(state, now) }

    /**
     * Persists whatever life regeneration is already due. Every mutation regenerates first as well,
     * so this only needs calling when the wallet is shown or before the zero-life gate is checked.
     */
    fun refresh() {
        mutate { state, _ -> state to emptyList() }
    }

    /** Wallet foundation: adds a positive amount of gems. */
    fun addGems(amount: Int): Boolean {
        if (amount <= 0) return false
        mutate { state, _ ->
            EconomyState(saturatedGems(state.gems, amount), state.lives, state.nextLifeRestoreAtEpochMs) to emptyList()
        }
        return true
    }

    /** Wallet foundation: spends gems only when the balance allows it; never negative. */
    fun spendGems(amount: Int): Boolean {
        if (amount <= 0) return false
        var spent = false
        mutate { state, _ ->
            if (state.gems >= amount) {
                spent = true
                EconomyState(state.gems - amount, state.lives, state.nextLifeRestoreAtEpochMs) to emptyList()
            } else {
                state to emptyList()
            }
        }
        return spent
    }

    /** Wallet foundation: consumes one life when available; never negative. */
    fun consumeLife(): Boolean {
        var consumed = false
        mutate { state, now ->
            if (state.lives > 0) {
                consumed = true
                WebEconomyProcessor.withLivesSpent(state, 1, now) to emptyList()
            } else {
                state to emptyList()
            }
        }
        return consumed
    }

    /** Store reward support: restores lives up to the policy maximum. */
    fun restoreLives(amount: Int): Boolean {
        if (amount <= 0) return false
        var granted = false
        mutate { state, _ ->
            val restored = minOf(amount, EconomyPolicy.MAXIMUM_LIVES - state.lives)
            if (restored > 0) {
                granted = true
                val lives = state.lives + restored
                EconomyState(
                    state.gems,
                    lives,
                    WebEconomyProcessor.restoreAnchorFor(lives, state.nextLifeRestoreAtEpochMs),
                ) to emptyList()
            } else {
                state to emptyList()
            }
        }
        return granted
    }

    /** Gems from a rewarded advertisement: one ordinary durable wallet mutation. */
    fun grantGems(amount: Int): Boolean {
        if (amount <= 0) return false
        mutate { state, _ -> state.copy(gems = saturatedGems(state.gems, amount)) to emptyList() }
        return true
    }

    /**
     * Emits the durable-change signal explicitly; used by coupled transaction paths that must
     * produce exactly one unified-save notification for the whole Economy+Store pair.
     */
    fun notifyDurableChange() {
        onDurableChange?.invoke()
    }

    /**
     * Restores an externally supplied durable snapshot (unified cloud save or transaction
     * recovery). Durable-first: the snapshot is persisted to Player-scoped local storage and
     * only a successful write updates the current snapshot, raises the revision timeline, and
     * publishes the wallet state. A failed persistence keeps the previous durable state.
     */
    fun applyExternal(snapshot: WebEconomySnapshot): WebExternalRestoreResult =
        runCatching {
            if (snapshot == currentSnapshot) return@runCatching WebExternalRestoreResult.NoChange as WebExternalRestoreResult
            runCatching { store.save(snapshot) }.getOrElse {
                return@runCatching WebExternalRestoreResult.PersistenceFailed(it) as WebExternalRestoreResult
            }
            currentSnapshot = snapshot
            revisions.raiseTo(snapshot.revision)
            mutableState.value = snapshot.toState()
            WebExternalRestoreResult.Applied as WebExternalRestoreResult
        }.getOrDefault(WebExternalRestoreResult.Rejected)

    private fun EconomyState.toSnapshot(): WebEconomySnapshot =
        WebEconomySnapshot(gems = gems, lives = lives, nextLifeRestoreAtEpochMs = nextLifeRestoreAtEpochMs)

    private inline fun mutate(update: (EconomyState, Long) -> Pair<EconomyState, List<EconomyEvent>>): List<EconomyEvent> {
        val previous = mutableState.value
        // Due regeneration lands first, so every change starts from the wallet the player should see.
        val now = currentTimeMs()
        val (updated, events) = update(WebEconomyProcessor.regenerated(previous, now), now)
        if (updated == previous) return events
        // Local durability precedes publication: a failed save leaves the wallet untouched.
        val stamped = updated.toSnapshot().copy(revision = revisions.next())
        runCatching {
            store.save(stamped)
        }.onFailure { return events }
        currentSnapshot = stamped
        mutableState.value = updated
        onDurableChange?.invoke()
        return events
    }
}
