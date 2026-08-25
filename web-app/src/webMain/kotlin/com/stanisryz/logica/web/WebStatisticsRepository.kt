@file:OptIn(ExperimentalWasmJsInterop::class)

package com.stanisryz.logica.web

import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import com.stanisryz.logica.puzzle.core.word.WordRules
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.js.ExperimentalWasmJsInterop

internal interface WebInstallationIdStore {
    fun load(): String?

    fun save(installationId: String)
}

/** One browser installation ID, independent of every standalone or Yandex Player scope. */
internal class WebInstallationIdProvider(
    private val store: WebInstallationIdStore = BrowserInstallationIdStore,
    private val generate: () -> String = ::randomInstallationId,
) {
    private var cached: String? = null

    fun getOrCreate(): String {
        cached?.let { return it }
        val stored = store.load()?.takeIf(WebInstallationId::isValid)
        if (stored != null) {
            cached = stored
            return stored
        }

        val created = generate()
        require(WebInstallationId.isValid(created)) { "Generated an invalid Web installation ID." }
        store.save(created)
        cached = created
        return created
    }
}

private object BrowserInstallationIdStore : WebInstallationIdStore {
    private const val STORAGE_KEY = "logica_installation_id_v1"

    override fun load(): String? = statisticsLocalStorageGet(STORAGE_KEY)

    override fun save(installationId: String) {
        statisticsLocalStorageSet(STORAGE_KEY, installationId)
    }
}

internal interface WebStatisticsStore {
    fun load(): WebStatisticsSnapshot

    fun save(snapshot: WebStatisticsSnapshot)
}

/** Statistics use their own Player-scoped local key and never share the Catalog payload. */
internal class WebStatisticsLocalStore(
    scope: WebCatalogProgressScope,
) : WebStatisticsStore {
    internal val storageKey = "$LOCAL_STORAGE_KEY_PREFIX:${scope.keySuffix}"

    override fun load(): WebStatisticsSnapshot {
        val encoded = statisticsLocalStorageGet(storageKey) ?: return WebStatisticsSnapshot.EMPTY
        val payload = requireNotNull(WebBase64.decode(encoded)) { "Stored Web statistics are not valid Base64." }
        return requireNotNull(WebStatisticsCodec.decode(payload)) { "Stored Web statistics are invalid or over budget." }
    }

    override fun save(snapshot: WebStatisticsSnapshot) {
        statisticsLocalStorageSet(storageKey, WebBase64.encode(WebStatisticsCodec.encode(snapshot)))
    }

    private companion object {
        const val LOCAL_STORAGE_KEY_PREFIX = "logica_statistics_v1"
    }
}

internal fun interface WebStatisticsRepositoryFactory {
    fun create(scope: WebCatalogProgressScope): WebStatisticsRepository
}

internal sealed interface WebStatisticsMergeResult {
    data class Merged(
        val snapshot: WebStatisticsSnapshot,
        val cloudWriteRequired: Boolean,
    ) : WebStatisticsMergeResult

    data object Invalid : WebStatisticsMergeResult

    data class PersistenceFailed(
        val cause: Throwable,
    ) : WebStatisticsMergeResult
}

internal enum class WebStatisticsTerminalOutcome {
    SOLVED,
    FAILED,
}

/** Final gameplay-only facts accepted by Web statistics; no transient board state crosses this boundary. */
internal data class WebStatisticsTerminalResult(
    val puzzleType: PuzzleType,
    val difficulty: Difficulty,
    val outcome: WebStatisticsTerminalOutcome,
    val hintsUsed: Int,
    val wordAttemptsUsed: Int? = null,
) {
    init {
        require(hintsUsed >= 0) { "Final Web hint usage must not be negative." }
        when {
            puzzleType == PuzzleType.WORD &&
                outcome == WebStatisticsTerminalOutcome.SOLVED ->
                require(wordAttemptsUsed in 1..WordRules.MAXIMUM_ATTEMPTS) {
                    "A solved Web Word result requires attempts used in the supported range."
                }
            else -> require(wordAttemptsUsed == null) { "Attempts used are stored only for solved Web Word results." }
        }
    }
}

internal sealed interface WebStatisticsRecordResult {
    data class Recorded(
        val snapshot: WebStatisticsSnapshot,
    ) : WebStatisticsRecordResult

    data class Rejected(
        val cause: Throwable,
    ) : WebStatisticsRecordResult

    data class PersistenceFailed(
        val cause: Throwable,
    ) : WebStatisticsRecordResult
}

/** Current-scope durable statistics, updated only in this installation's monotonic component. */
internal class WebStatisticsRepository(
    val scope: WebCatalogProgressScope,
    val installationId: String,
    private val localStore: WebStatisticsStore,
) {
    init {
        require(WebInstallationId.isValid(installationId))
    }

    private val mutableSnapshot = MutableStateFlow(WebStatisticsSnapshot.EMPTY)
    val snapshot: StateFlow<WebStatisticsSnapshot> = mutableSnapshot.asStateFlow()

    /** Invoked after every successful durable local mutation; never after a cloud merge. */
    var onDurableChange: (() -> Unit)? = null

    fun loadLocal(): WebStatisticsSnapshot {
        val loaded = localStore.load()
        val initialized =
            if (installationId in loaded.components) {
                loaded
            } else {
                loaded.copy(
                    components = loaded.components + (installationId to WebStatisticsDeviceComponent()),
                )
            }
        if (initialized != loaded) localStore.save(initialized)
        mutableSnapshot.value = initialized
        return initialized
    }

    fun aggregate(): WebStatisticsAggregate = WebStatisticsAggregator.aggregate(mutableSnapshot.value)

    fun recordTerminalResult(result: WebStatisticsTerminalResult): WebStatisticsRecordResult {
        val updated =
            runCatching {
                val current = mutableSnapshot.value
                val component = requireNotNull(current.components[installationId]) {
                    "The current Web installation component has not been initialized."
                }
                val bucket = WebStatisticsBucket(result.puzzleType, result.difficulty)
                val counters = component.buckets[bucket] ?: WebStatisticsCounters()
                val wordAttemptIncrement = result.wordAttemptsUsed?.let { mapOf(it to 1L) } ?: emptyMap()
                val increment =
                    WebStatisticsCounters(
                        played = 1L,
                        solved = if (result.outcome == WebStatisticsTerminalOutcome.SOLVED) 1L else 0L,
                        failed = if (result.outcome == WebStatisticsTerminalOutcome.FAILED) 1L else 0L,
                        hints = result.hintsUsed.toLong(),
                        wordSolvedAttempts = wordAttemptIncrement,
                    )
                val updatedComponent =
                    WebStatisticsDeviceComponent(
                        component.buckets + (bucket to counters.addChecked(increment)),
                    )
                current.copy(components = current.components + (installationId to updatedComponent))
            }.getOrElse { return WebStatisticsRecordResult.Rejected(it) }

        runCatching {
            WebStatisticsCodec.encode(updated)
            localStore.save(updated)
        }.exceptionOrNull()?.let { return WebStatisticsRecordResult.PersistenceFailed(it) }

        mutableSnapshot.value = updated
        onDurableChange?.invoke()
        return WebStatisticsRecordResult.Recorded(updated)
    }

    fun mergeCloud(cloud: WebStatisticsSnapshot): WebStatisticsMergeResult {
        val local = mutableSnapshot.value
        val merged =
            runCatching { WebStatisticsMerger.merge(local, cloud) }
                .getOrElse { return WebStatisticsMergeResult.Invalid }
        if (merged != local) {
            runCatching { localStore.save(merged) }
                .exceptionOrNull()
                ?.let { return WebStatisticsMergeResult.PersistenceFailed(it) }
            mutableSnapshot.value = merged
        }
        return WebStatisticsMergeResult.Merged(
            snapshot = merged,
            cloudWriteRequired = merged != cloud,
        )
    }
}

private fun randomInstallationId(): String =
    js(
        "Array.from(globalThis.crypto.getRandomValues(new Uint8Array(16)), " +
            "function(value) { return value.toString(16).padStart(2, '0'); }).join('')",
    )

private fun statisticsLocalStorageGet(key: String): String? = js("globalThis.localStorage.getItem(key)")

private fun statisticsLocalStorageSet(
    key: String,
    value: String,
) {
    js("globalThis.localStorage.setItem(key, value)")
}
