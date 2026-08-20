package com.stanisryz.logica.web

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.stanisryz.logica.platform.CloudSaveGateway
import com.stanisryz.logica.platform.CloudSaveReadResult
import com.stanisryz.logica.platform.CloudSaveWriteResult
import com.stanisryz.logica.platform.PlayerAuthorizationState
import com.stanisryz.logica.platform.PlayerIdentity
import com.stanisryz.logica.platform.PlayerIdentityGateway
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal enum class WebCloudSyncStatus {
    SYNCING,
    SYNCED,
    ERROR,
}

internal sealed interface WebPlayerSessionState {
    data object Loading : WebPlayerSessionState

    data object LocalOnly : WebPlayerSessionState

    data class PlayerReady(
        val identity: PlayerIdentity,
        val syncStatus: WebCloudSyncStatus,
    ) : WebPlayerSessionState
}

internal sealed interface WebCatalogProgressBinding {
    data object Loading : WebCatalogProgressBinding

    data class Ready(
        val token: WebPlayerContextToken,
        val repository: WebCatalogProgressRepository,
        val identity: PlayerIdentity?,
    ) : WebCatalogProgressBinding

    data class Unavailable(
        val detail: String,
    ) : WebCatalogProgressBinding
}

internal enum class WebStatisticsCloudSyncStatus {
    LOCAL_ONLY,
    SYNCING,
    SYNCED,
    ERROR,
}

internal sealed interface WebStatisticsBinding {
    data object Loading : WebStatisticsBinding

    data class Ready(
        val token: WebPlayerContextToken,
        val repository: WebStatisticsRepository,
        val identity: PlayerIdentity?,
        val syncStatus: WebStatisticsCloudSyncStatus,
    ) : WebStatisticsBinding

    data class Unavailable(
        val detail: String,
    ) : WebStatisticsBinding
}

/** Binds one freshly loaded local repository to one current Yandex Player before any cloud merge. */
internal class WebPlayerSessionController(
    private val playerIdentityGateway: PlayerIdentityGateway,
    private val cloudSaveGateway: CloudSaveGateway,
    private val progressRepositoryFactory: WebCatalogProgressRepositoryFactory,
    private val statisticsCloudSaveGateway: CloudSaveGateway,
    private val statisticsRepositoryFactory: WebStatisticsRepositoryFactory,
    private val playerContextEvents: WebPlayerContextEvents,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private var started = false
    private var contextRevision = 0L
    private var accountSelectionOpen = false
    private var operation: Job? = null
    private val cloudWriteMutex = Mutex()
    private val mutableProgressBinding = MutableStateFlow<WebCatalogProgressBinding>(WebCatalogProgressBinding.Loading)
    private val mutableStatisticsBinding = MutableStateFlow<WebStatisticsBinding>(WebStatisticsBinding.Loading)

    val progressBinding: StateFlow<WebCatalogProgressBinding> = mutableProgressBinding.asStateFlow()
    val statisticsBinding: StateFlow<WebStatisticsBinding> = mutableStatisticsBinding.asStateFlow()

    var state by mutableStateOf<WebPlayerSessionState>(WebPlayerSessionState.Loading)
        private set

    var progressRepository: WebCatalogProgressRepository? = null
        private set

    var statisticsRepository: WebStatisticsRepository? = null
        private set

    var accountChangeRevision by mutableStateOf(0L)
        private set

    init {
        playerContextEvents.setAccountSelectionOpenedListener {
            if (started) suspendForAccountSelection()
        }
        playerContextEvents.setPlayerContextChangedListener {
            if (started) {
                accountSelectionOpen = false
                accountChangeRevision += 1L
                bindCurrentContext()
            }
        }
    }

    fun start() {
        if (started) return
        started = true
        bindCurrentContext()
    }

    fun dispose() {
        playerContextEvents.setAccountSelectionOpenedListener(null)
        playerContextEvents.setPlayerContextChangedListener(null)
        scope.cancel()
    }

    fun retryCurrentContext() {
        if (started && !accountSelectionOpen) bindCurrentContext()
    }

    internal fun requestCloudSynchronization(binding: WebCatalogProgressBinding.Ready) {
        if (!isCurrent(binding)) return
        val identity = binding.identity ?: return
        scope.launch {
            cloudWriteMutex.withLock {
                if (!isCurrent(binding)) return@withLock
                state = WebPlayerSessionState.PlayerReady(identity, WebCloudSyncStatus.SYNCING)
                val result = cloudSaveGateway.write(WebCatalogProgressCodec.encode(binding.repository.snapshot.value))
                if (!isCurrent(binding)) return@withLock
                state =
                    WebPlayerSessionState.PlayerReady(
                        identity,
                        if (result == CloudSaveWriteResult.Saved) {
                            WebCloudSyncStatus.SYNCED
                        } else {
                            WebCloudSyncStatus.ERROR
                        },
                    )
            }
        }
    }

    private fun bindCurrentContext() {
        operation?.cancel()
        val revision = ++contextRevision
        progressRepository = null
        statisticsRepository = null
        mutableProgressBinding.value = WebCatalogProgressBinding.Loading
        mutableStatisticsBinding.value = WebStatisticsBinding.Loading
        state = WebPlayerSessionState.Loading
        operation = scope.launch { resolveAndSynchronize(revision) }
    }

    private fun suspendForAccountSelection() {
        accountSelectionOpen = true
        operation?.cancel()
        progressRepository = null
        statisticsRepository = null
        mutableProgressBinding.value = WebCatalogProgressBinding.Loading
        mutableStatisticsBinding.value = WebStatisticsBinding.Loading
        state = WebPlayerSessionState.Loading
    }

    private suspend fun resolveAndSynchronize(revision: Long) {
        try {
            val identity = playerIdentityGateway.identity()
            if (!isCurrent(revision)) return

            if (identity.authorizationState == PlayerAuthorizationState.UNSUPPORTED) {
                bindStandalone(revision)
                return
            }

            val playerId = identity.playerId
            if (playerId.isNullOrBlank()) {
                unavailable(revision, "The current Yandex Player has no stable progress identity.")
                return
            }

            val playerScope = WebCatalogProgressScope.yandexPlayer(playerId)
            val repository = progressRepositoryFactory.create(playerScope)
            repository.loadLocal()
            if (!isCurrent(revision)) return
            progressRepository = repository
            val scopedStatistics = bindStatisticsLocal(revision, playerScope, identity)
            synchronize(revision, identity, repository)
            if (scopedStatistics != null && isCurrent(revision)) {
                synchronizeStatisticsSafely(revision, identity, scopedStatistics)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            unavailable(revision, "The current Yandex Player progression context is unavailable.")
        }
    }

    private fun bindStandalone(revision: Long) {
        val standaloneScope = WebCatalogProgressScope.STANDALONE
        val repository = progressRepositoryFactory.create(standaloneScope)
        repository.loadLocal()
        if (!isCurrent(revision)) return
        progressRepository = repository
        bindStatisticsLocal(revision, standaloneScope, identity = null)
        state = WebPlayerSessionState.LocalOnly
        mutableProgressBinding.value =
            WebCatalogProgressBinding.Ready(
                token = WebPlayerContextToken(revision),
                repository = repository,
                identity = null,
            )
    }

    private suspend fun synchronize(
        revision: Long,
        identity: PlayerIdentity,
        repository: WebCatalogProgressRepository,
    ) {
        if (!isCurrent(revision)) return
        state = WebPlayerSessionState.PlayerReady(identity, WebCloudSyncStatus.SYNCING)
        val cloud =
            when (val result = cloudSaveGateway.read()) {
                is CloudSaveReadResult.Found ->
                    WebCatalogProgressCodec.decode(result.payload)
                        ?: return syncFailed(revision, identity, repository)
                CloudSaveReadResult.Missing -> WebCatalogProgressSnapshot.EMPTY
                CloudSaveReadResult.Unsupported,
                is CloudSaveReadResult.Failed,
                -> return syncFailed(revision, identity, repository)
            }
        if (!isCurrent(revision)) return

        when (val merge = repository.mergeCloud(cloud)) {
            is WebCatalogMergeResult.PersistenceFailed -> syncFailed(revision, identity, repository)
            is WebCatalogMergeResult.Merged -> {
                if (merge.cloudWriteRequired) {
                    when (cloudSaveGateway.write(WebCatalogProgressCodec.encode(merge.snapshot))) {
                        CloudSaveWriteResult.Saved -> Unit
                        CloudSaveWriteResult.Unsupported,
                        is CloudSaveWriteResult.Failed,
                        -> return syncFailed(revision, identity, repository)
                    }
                    if (!isCurrent(revision)) return
                }
                state = WebPlayerSessionState.PlayerReady(identity, WebCloudSyncStatus.SYNCED)
                publishReady(revision, identity, repository)
            }
        }
    }

    private fun bindStatisticsLocal(
        revision: Long,
        playerScope: WebCatalogProgressScope,
        identity: PlayerIdentity?,
    ): WebStatisticsRepository? {
        val repository =
            runCatching {
                statisticsRepositoryFactory.create(playerScope).also { it.loadLocal() }
            }.getOrElse {
                if (isCurrent(revision)) {
                    statisticsRepository = null
                    mutableStatisticsBinding.value =
                        WebStatisticsBinding.Unavailable("The current Web statistics context is unavailable.")
                }
                return null
            }
        if (!isCurrent(revision)) return null
        statisticsRepository = repository
        mutableStatisticsBinding.value =
            WebStatisticsBinding.Ready(
                token = WebPlayerContextToken(revision),
                repository = repository,
                identity = identity,
                syncStatus =
                    if (identity == null) {
                        WebStatisticsCloudSyncStatus.LOCAL_ONLY
                    } else {
                        WebStatisticsCloudSyncStatus.SYNCING
                    },
            )
        return repository
    }

    private suspend fun synchronizeStatisticsSafely(
        revision: Long,
        identity: PlayerIdentity,
        repository: WebStatisticsRepository,
    ) {
        try {
            synchronizeStatistics(revision, identity, repository)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            statisticsSyncFailed(revision, identity, repository)
        }
    }

    private suspend fun synchronizeStatistics(
        revision: Long,
        identity: PlayerIdentity,
        repository: WebStatisticsRepository,
    ) {
        if (!isCurrentStatistics(revision, repository)) return
        val cloud =
            when (val result = statisticsCloudSaveGateway.read()) {
                is CloudSaveReadResult.Found ->
                    WebStatisticsCodec.decode(result.payload)
                        ?: return statisticsSyncFailed(revision, identity, repository)
                CloudSaveReadResult.Missing -> WebStatisticsSnapshot.EMPTY
                CloudSaveReadResult.Unsupported,
                is CloudSaveReadResult.Failed,
                -> return statisticsSyncFailed(revision, identity, repository)
            }
        if (!isCurrentStatistics(revision, repository)) return

        when (val merge = repository.mergeCloud(cloud)) {
            WebStatisticsMergeResult.Invalid,
            is WebStatisticsMergeResult.PersistenceFailed,
            -> statisticsSyncFailed(revision, identity, repository)
            is WebStatisticsMergeResult.Merged -> {
                if (merge.cloudWriteRequired) {
                    if (!isCurrentStatistics(revision, repository)) return
                    when (statisticsCloudSaveGateway.write(WebStatisticsCodec.encode(merge.snapshot))) {
                        CloudSaveWriteResult.Saved -> Unit
                        CloudSaveWriteResult.Unsupported,
                        is CloudSaveWriteResult.Failed,
                        -> return statisticsSyncFailed(revision, identity, repository)
                    }
                    if (!isCurrentStatistics(revision, repository)) return
                }
                publishStatisticsReady(
                    revision,
                    identity,
                    repository,
                    WebStatisticsCloudSyncStatus.SYNCED,
                )
            }
        }
    }

    private fun statisticsSyncFailed(
        revision: Long,
        identity: PlayerIdentity,
        repository: WebStatisticsRepository,
    ) {
        publishStatisticsReady(
            revision,
            identity,
            repository,
            WebStatisticsCloudSyncStatus.ERROR,
        )
    }

    private fun publishStatisticsReady(
        revision: Long,
        identity: PlayerIdentity,
        repository: WebStatisticsRepository,
        syncStatus: WebStatisticsCloudSyncStatus,
    ) {
        if (!isCurrentStatistics(revision, repository)) return
        mutableStatisticsBinding.value =
            WebStatisticsBinding.Ready(
                token = WebPlayerContextToken(revision),
                repository = repository,
                identity = identity,
                syncStatus = syncStatus,
            )
    }

    private fun syncFailed(
        revision: Long,
        identity: PlayerIdentity,
        repository: WebCatalogProgressRepository,
    ) {
        if (isCurrent(revision)) {
            state = WebPlayerSessionState.PlayerReady(identity, WebCloudSyncStatus.ERROR)
            publishReady(revision, identity, repository)
        }
    }

    private fun publishReady(
        revision: Long,
        identity: PlayerIdentity,
        repository: WebCatalogProgressRepository,
    ) {
        if (!isCurrent(revision)) return
        mutableProgressBinding.value =
            WebCatalogProgressBinding.Ready(
                token = WebPlayerContextToken(revision),
                repository = repository,
                identity = identity,
            )
    }

    private fun unavailable(
        revision: Long,
        detail: String,
    ) {
        if (!isCurrent(revision)) return
        progressRepository = null
        statisticsRepository = null
        state = WebPlayerSessionState.LocalOnly
        mutableProgressBinding.value = WebCatalogProgressBinding.Unavailable(detail)
        mutableStatisticsBinding.value = WebStatisticsBinding.Unavailable(detail)
    }

    private fun isCurrent(revision: Long): Boolean = revision == contextRevision && !accountSelectionOpen

    private fun isCurrent(binding: WebCatalogProgressBinding.Ready): Boolean =
        !accountSelectionOpen &&
            binding.token.value == contextRevision &&
            mutableProgressBinding.value === binding

    private fun isCurrentStatistics(
        revision: Long,
        repository: WebStatisticsRepository,
    ): Boolean = isCurrent(revision) && statisticsRepository === repository
}
