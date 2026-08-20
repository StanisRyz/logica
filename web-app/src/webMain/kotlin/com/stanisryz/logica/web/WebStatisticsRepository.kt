@file:OptIn(ExperimentalWasmJsInterop::class)

package com.stanisryz.logica.web

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
        val payload = WebBase64.decode(encoded) ?: return WebStatisticsSnapshot.EMPTY
        return WebStatisticsCodec.decode(payload) ?: WebStatisticsSnapshot.EMPTY
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

/** Current-scope durable statistics. Gameplay recording is intentionally deferred to Stage 45.7b. */
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
