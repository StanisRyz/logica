@file:OptIn(ExperimentalWasmJsInterop::class)

package com.stanisryz.logica.web

import com.stanisryz.logica.platform.EconomyConsumptionType
import com.stanisryz.logica.platform.EconomyEvent
import com.stanisryz.logica.platform.EconomyPolicy
import com.stanisryz.logica.platform.EconomyRewardType
import com.stanisryz.logica.platform.EconomyState
import com.stanisryz.logica.platform.PlayerIdentity
import com.stanisryz.logica.puzzle.core.model.Difficulty
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
            val gems = readInt(payload, 5)
            val lives = payload[9].toInt() and 0xff
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
 * Catalog-only gameplay economy seam. Daily challenges never consume lives and never grant
 * Catalog rewards, so no Daily path exists on this interface by design.
 */
internal interface WebGameplayEconomy {
    fun recordCatalogTerminalResult(
        puzzleType: PuzzleType,
        difficulty: Difficulty,
        solved: Boolean,
    )
}

internal object DisabledWebGameplayEconomy : WebGameplayEconomy {
    override fun recordCatalogTerminalResult(
        puzzleType: PuzzleType,
        difficulty: Difficulty,
        solved: Boolean,
    ) = Unit
}

/** Dynamically applies Catalog economy effects to the repository bound to the current Player. */
internal class WebGameplayEconomyCoordinator(
    private val playerSession: WebEconomySessionAccess,
) : WebGameplayEconomy {
    override fun recordCatalogTerminalResult(
        puzzleType: PuzzleType,
        difficulty: Difficulty,
        solved: Boolean,
    ) {
        val binding = playerSession.economyBinding.value as? WebEconomyBinding.Ready ?: return
        binding.repository.applyCatalogTerminalResult(puzzleType, difficulty, solved)
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
    /** Mirrors the established solved-gem rewards by difficulty (1/2/3/4 for Easy..Expert). */
    fun gemRewardFor(difficulty: Difficulty): Int =
        when (difficulty) {
            Difficulty.EASY -> 1
            Difficulty.MEDIUM -> 2
            Difficulty.HARD -> 3
            Difficulty.EXPERT -> 4
        }

    fun onCatalogTerminalResult(
        state: EconomyState,
        difficulty: Difficulty,
        solved: Boolean,
    ): Pair<EconomyState, List<EconomyEvent>> =
        if (solved) {
            val reward = gemRewardFor(difficulty)
            EconomyState(state.gems + reward, state.lives, state.nextLifeRestoreAtEpochMs) to
                listOf(EconomyEvent.GameCompleted, EconomyEvent.RewardGranted(EconomyRewardType.GEMS, reward))
        } else {
            val consumed = minOf(EconomyPolicy.FAILED_ATTEMPT_LIFE_COST, state.lives)
            EconomyState(state.gems, state.lives - consumed, null) to
                listOf(EconomyEvent.GameFailed, EconomyEvent.ResourceConsumed(EconomyConsumptionType.LIFE, consumed))
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

    /** Solved Catalog puzzle: grants the difficulty's gem reward through the processor. */
    fun applyCatalogTerminalResult(
        puzzleType: PuzzleType,
        difficulty: Difficulty,
        solved: Boolean,
    ): List<EconomyEvent> = mutate { state -> WebEconomyProcessor.onCatalogTerminalResult(state, difficulty, solved) }

    /** Wallet foundation: adds a positive amount of gems. */
    fun addGems(amount: Int): Boolean {
        if (amount <= 0) return false
        mutate { state ->
            EconomyState(state.gems + amount, state.lives, state.nextLifeRestoreAtEpochMs) to emptyList()
        }
        return true
    }

    /** Wallet foundation: spends gems only when the balance allows it; never negative. */
    fun spendGems(amount: Int): Boolean {
        if (amount <= 0) return false
        var spent = false
        mutate { state ->
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
        mutate { state ->
            if (state.lives > 0) {
                consumed = true
                EconomyState(state.gems, state.lives - 1, null) to emptyList()
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
        mutate { state ->
            val restored = minOf(amount, EconomyPolicy.MAXIMUM_LIVES - state.lives)
            if (restored > 0) {
                granted = true
                EconomyState(state.gems, state.lives + restored, state.nextLifeRestoreAtEpochMs) to emptyList()
            } else {
                state to emptyList()
            }
        }
        return granted
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

    private inline fun mutate(update: (EconomyState) -> Pair<EconomyState, List<EconomyEvent>>): List<EconomyEvent> {
        val previous = mutableState.value
        val (updated, events) = update(previous)
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
