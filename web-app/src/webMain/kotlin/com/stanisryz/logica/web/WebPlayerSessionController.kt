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

internal enum class WebDailyCloudSyncStatus {
    LOCAL_ONLY,
    SYNCING,
    SYNCED,
    ERROR,
}

internal sealed interface WebDailyBinding {
    data object Loading : WebDailyBinding

    data class Ready(
        val token: WebPlayerContextToken,
        val repository: WebDailyRepository,
        val identity: PlayerIdentity?,
        val syncStatus: WebDailyCloudSyncStatus,
    ) : WebDailyBinding

    data class Unavailable(
        val detail: String,
    ) : WebDailyBinding
}

/** Token-bound session seam for the Stage 45.8b gameplay adapter. */
internal interface WebDailySessionAccess {
    val dailyBinding: StateFlow<WebDailyBinding>

    fun requestDailyCloudSynchronization(binding: WebDailyBinding.Ready)
}

/** Binds one freshly loaded local repository to one current Yandex Player before any cloud merge. */
internal class WebPlayerSessionController(
    private val playerIdentityGateway: PlayerIdentityGateway,
    private val cloudSaveGateway: CloudSaveGateway,
    private val progressRepositoryFactory: WebCatalogProgressRepositoryFactory,
    private val statisticsCloudSaveGateway: CloudSaveGateway,
    private val statisticsRepositoryFactory: WebStatisticsRepositoryFactory,
    private val dailyCloudSaveGateway: CloudSaveGateway,
    private val dailyRepositoryFactory: WebDailyRepositoryFactory,
    private val playerContextEvents: WebPlayerContextEvents,
    /** Invoked after a Player context is fully bound (identity resolved, all domains loaded). */
    var postBindAction: suspend (WebPlayerContextToken) -> Unit = { _ -> },
    private val economyRepositoryFactory: WebEconomyRepositoryFactory =
        WebEconomyRepositoryFactory { scope -> WebPlayerEconomyRepository(scope, WebEconomyLocalStore(scope)) },
    private val storeRepositoryFactory: WebStoreRepositoryFactory =
        WebStoreRepositoryFactory { scope -> WebPlayerStoreRepository(scope, WebStoreLocalStore(scope)) },
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : WebStatisticsSessionAccess,
    WebDailySessionAccess,
    WebEconomySessionAccess,
    WebStoreSessionAccess {
    private var started = false
    private var contextRevision = 0L
    private var accountSelectionOpen = false
    private var operation: Job? = null
    private val cloudWriteMutex = Mutex()
    private val mutableProgressBinding = MutableStateFlow<WebCatalogProgressBinding>(WebCatalogProgressBinding.Loading)
    private val mutableStatisticsBinding = MutableStateFlow<WebStatisticsBinding>(WebStatisticsBinding.Loading)
    private val mutableDailyBinding = MutableStateFlow<WebDailyBinding>(WebDailyBinding.Loading)
    private val mutableEconomyBinding = MutableStateFlow<WebEconomyBinding>(WebEconomyBinding.Loading)
    private val mutableStoreBinding = MutableStateFlow<WebStoreBinding>(WebStoreBinding.Loading)
    private var dailyCloudSnapshot: WebDailySnapshotV1? = null

    val progressBinding: StateFlow<WebCatalogProgressBinding> = mutableProgressBinding.asStateFlow()
    override val statisticsBinding: StateFlow<WebStatisticsBinding> = mutableStatisticsBinding.asStateFlow()
    override val dailyBinding: StateFlow<WebDailyBinding> = mutableDailyBinding.asStateFlow()
    override val economyBinding: StateFlow<WebEconomyBinding> = mutableEconomyBinding.asStateFlow()
    override val storeBinding: StateFlow<WebStoreBinding> = mutableStoreBinding.asStateFlow()

    var state by mutableStateOf<WebPlayerSessionState>(WebPlayerSessionState.Loading)
        private set

    var progressRepository: WebCatalogProgressRepository? = null
        private set

    var statisticsRepository: WebStatisticsRepository? = null
        private set

    var dailyRepository: WebDailyRepository? = null
        private set

    var economyRepository: WebPlayerEconomyRepository? = null
        private set

    var storeRepository: WebPlayerStoreRepository? = null
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

    override fun requestStatisticsCloudSynchronization(binding: WebStatisticsBinding.Ready) {
        if (!isCurrentStatistics(binding)) return
        val identity = binding.identity ?: return
        scope.launch {
            cloudWriteMutex.withLock {
                if (!isCurrentStatistics(binding)) return@withLock
                publishStatisticsReady(
                    binding.token.value,
                    identity,
                    binding.repository,
                    WebStatisticsCloudSyncStatus.SYNCING,
                )
                val result =
                    runCatching {
                        statisticsCloudSaveGateway.write(
                            WebStatisticsCodec.encode(binding.repository.snapshot.value),
                        )
                    }.getOrElse { CloudSaveWriteResult.Failed(it) }
                if (!isCurrentStatistics(binding)) return@withLock
                publishStatisticsReady(
                    binding.token.value,
                    identity,
                    binding.repository,
                    if (result == CloudSaveWriteResult.Saved) {
                        WebStatisticsCloudSyncStatus.SYNCED
                    } else {
                        WebStatisticsCloudSyncStatus.ERROR
                    },
                )
            }
        }
    }

    override fun requestDailyCloudSynchronization(binding: WebDailyBinding.Ready) {
        if (!isCurrentDaily(binding)) return
        val identity = binding.identity ?: return
        scope.launch {
            cloudWriteMutex.withLock {
                if (!isCurrentDaily(binding)) return@withLock
                val snapshot = binding.repository.snapshot.value
                if (snapshot == dailyCloudSnapshot) return@withLock
                publishDailyReady(
                    binding.token.value,
                    identity,
                    binding.repository,
                    WebDailyCloudSyncStatus.SYNCING,
                )
                val result =
                    runCatching {
                        dailyCloudSaveGateway.write(WebDailyCodec.encode(snapshot))
                    }.getOrElse { CloudSaveWriteResult.Failed(it) }
                if (!isCurrentDaily(binding)) return@withLock
                if (result == CloudSaveWriteResult.Saved) dailyCloudSnapshot = snapshot
                publishDailyReady(
                    binding.token.value,
                    identity,
                    binding.repository,
                    if (result == CloudSaveWriteResult.Saved) {
                        WebDailyCloudSyncStatus.SYNCED
                    } else {
                        WebDailyCloudSyncStatus.ERROR
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
        dailyRepository = null
        economyRepository = null
        storeRepository = null
        dailyCloudSnapshot = null
        mutableProgressBinding.value = WebCatalogProgressBinding.Loading
        mutableStatisticsBinding.value = WebStatisticsBinding.Loading
        mutableDailyBinding.value = WebDailyBinding.Loading
        mutableEconomyBinding.value = WebEconomyBinding.Loading
        mutableStoreBinding.value = WebStoreBinding.Loading
        state = WebPlayerSessionState.Loading
        operation = scope.launch { resolveAndSynchronize(revision) }
    }

    private fun suspendForAccountSelection() {
        accountSelectionOpen = true
        operation?.cancel()
        progressRepository = null
        statisticsRepository = null
        dailyRepository = null
        economyRepository = null
        storeRepository = null
        dailyCloudSnapshot = null
        mutableProgressBinding.value = WebCatalogProgressBinding.Loading
        mutableStatisticsBinding.value = WebStatisticsBinding.Loading
        mutableDailyBinding.value = WebDailyBinding.Loading
        mutableEconomyBinding.value = WebEconomyBinding.Loading
        mutableStoreBinding.value = WebStoreBinding.Loading
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
            val scopedDaily = bindDailyLocal(revision, playerScope, identity)
            bindEconomyLocal(revision, playerScope, identity)
            bindStoreLocal(revision, playerScope, identity)
            synchronize(revision, identity, repository)
            if (scopedStatistics != null && isCurrent(revision)) {
                synchronizeStatisticsSafely(revision, identity, scopedStatistics)
            }
            if (scopedDaily != null && isCurrent(revision)) {
                synchronizeDailySafely(revision, identity, scopedDaily)
            }
            // Unified cloud save restore: identity resolved, all domains local-loaded.
            if (isCurrent(revision)) {
                postBindAction(WebPlayerContextToken(revision))
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            unavailable(revision, "The current Yandex Player progression context is unavailable.")
        }
    }

    private suspend fun bindStandalone(revision: Long) {
        val standaloneScope = WebCatalogProgressScope.STANDALONE
        val repository = progressRepositoryFactory.create(standaloneScope)
        repository.loadLocal()
        if (!isCurrent(revision)) return
        progressRepository = repository
        bindStatisticsLocal(revision, standaloneScope, identity = null)
        bindDailyLocal(revision, standaloneScope, identity = null)
        bindEconomyLocal(revision, standaloneScope, identity = null)
        bindStoreLocal(revision, standaloneScope, identity = null)
        state = WebPlayerSessionState.LocalOnly
        mutableProgressBinding.value =
            WebCatalogProgressBinding.Ready(
                token = WebPlayerContextToken(revision),
                repository = repository,
                identity = null,
            )
        postBindAction(WebPlayerContextToken(revision))
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
                    val writeResult =
                        cloudWriteMutex.withLock {
                            if (!isCurrent(revision)) return@withLock null
                            cloudSaveGateway.write(WebCatalogProgressCodec.encode(merge.snapshot))
                        } ?: return
                    when (writeResult) {
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

    private fun bindDailyLocal(
        revision: Long,
        playerScope: WebCatalogProgressScope,
        identity: PlayerIdentity?,
    ): WebDailyRepository? {
        val repository =
            runCatching {
                dailyRepositoryFactory.create(playerScope).also { it.loadLocal() }
            }.getOrElse {
                if (isCurrent(revision)) {
                    dailyRepository = null
                    mutableDailyBinding.value =
                        WebDailyBinding.Unavailable("The current Web Daily context is unavailable.")
                }
                return null
            }
        if (!isCurrent(revision)) return null
        dailyRepository = repository
        mutableDailyBinding.value =
            WebDailyBinding.Ready(
                token = WebPlayerContextToken(revision),
                repository = repository,
                identity = identity,
                syncStatus =
                    if (identity == null) {
                        WebDailyCloudSyncStatus.LOCAL_ONLY
                    } else {
                        WebDailyCloudSyncStatus.SYNCING
                    },
            )
        return repository
    }

    /** Economy is local-only for now; future cloud synchronization joins this session like Daily. */
    private fun bindEconomyLocal(
        revision: Long,
        playerScope: WebCatalogProgressScope,
        identity: PlayerIdentity?,
    ): WebPlayerEconomyRepository? {
        val repository =
            runCatching {
                economyRepositoryFactory.create(playerScope).also { it.loadLocal() }
            }.getOrElse {
                if (isCurrent(revision)) {
                    economyRepository = null
                    mutableEconomyBinding.value =
                        WebEconomyBinding.Unavailable("The current Web economy context is unavailable.")
                }
                return null
            }
        if (!isCurrent(revision)) return null
        economyRepository = repository
        mutableEconomyBinding.value =
            WebEconomyBinding.Ready(
                token = WebPlayerContextToken(revision),
                repository = repository,
                identity = identity,
            )
        return repository
    }

    /** Store is local-only for now; future cloud synchronization joins this session like Economy. */
    private fun bindStoreLocal(
        revision: Long,
        playerScope: WebCatalogProgressScope,
        identity: PlayerIdentity?,
    ): WebPlayerStoreRepository? {
        val repository =
            runCatching {
                storeRepositoryFactory.create(playerScope).also { it.loadLocal() }
            }.getOrElse {
                if (isCurrent(revision)) {
                    storeRepository = null
                    mutableStoreBinding.value =
                        WebStoreBinding.Unavailable("The current Web store context is unavailable.")
                }
                return null
            }
        if (!isCurrent(revision)) return null
        storeRepository = repository
        mutableStoreBinding.value =
            WebStoreBinding.Ready(
                token = WebPlayerContextToken(revision),
                repository = repository,
                identity = identity,
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
                    val writeResult =
                        cloudWriteMutex.withLock {
                            if (!isCurrentStatistics(revision, repository)) return@withLock null
                            statisticsCloudSaveGateway.write(
                                WebStatisticsCodec.encode(repository.snapshot.value),
                            )
                        } ?: return
                    when (writeResult) {
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

    private suspend fun synchronizeDailySafely(
        revision: Long,
        identity: PlayerIdentity,
        repository: WebDailyRepository,
    ) {
        try {
            synchronizeDaily(revision, identity, repository)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            dailySyncFailed(revision, identity, repository)
        }
    }

    private suspend fun synchronizeDaily(
        revision: Long,
        identity: PlayerIdentity,
        repository: WebDailyRepository,
    ) {
        if (!isCurrentDaily(revision, repository)) return
        val cloud =
            when (val result = dailyCloudSaveGateway.read()) {
                is CloudSaveReadResult.Found ->
                    WebDailyCodec.decode(result.payload)
                        ?: return dailySyncFailed(revision, identity, repository)
                CloudSaveReadResult.Missing -> WebDailySnapshotV1.EMPTY
                CloudSaveReadResult.Unsupported,
                is CloudSaveReadResult.Failed,
                -> return dailySyncFailed(revision, identity, repository)
            }
        if (!isCurrentDaily(revision, repository)) return

        when (val merge = repository.mergeCloud(cloud)) {
            is WebDailyMergeResult.PolicyConflict,
            is WebDailyMergeResult.PersistenceFailed,
            -> dailySyncFailed(revision, identity, repository)
            is WebDailyMergeResult.Merged -> {
                if (merge.cloudWriteRequired) {
                    val writeResult =
                        cloudWriteMutex.withLock {
                            if (!isCurrentDaily(revision, repository)) return@withLock null
                            dailyCloudSaveGateway.write(WebDailyCodec.encode(merge.snapshot))
                        } ?: return
                    when (writeResult) {
                        CloudSaveWriteResult.Saved -> Unit
                        CloudSaveWriteResult.Unsupported,
                        is CloudSaveWriteResult.Failed,
                        -> return dailySyncFailed(revision, identity, repository)
                    }
                    if (!isCurrentDaily(revision, repository)) return
                }
                if (!isCurrentDaily(revision, repository)) return
                dailyCloudSnapshot = merge.snapshot
                publishDailyReady(
                    revision,
                    identity,
                    repository,
                    WebDailyCloudSyncStatus.SYNCED,
                )
            }
        }
    }

    private fun dailySyncFailed(
        revision: Long,
        identity: PlayerIdentity,
        repository: WebDailyRepository,
    ) {
        publishDailyReady(
            revision,
            identity,
            repository,
            WebDailyCloudSyncStatus.ERROR,
        )
    }

    private fun publishDailyReady(
        revision: Long,
        identity: PlayerIdentity,
        repository: WebDailyRepository,
        syncStatus: WebDailyCloudSyncStatus,
    ) {
        if (!isCurrentDaily(revision, repository)) return
        mutableDailyBinding.value =
            WebDailyBinding.Ready(
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
        dailyRepository = null
        economyRepository = null
        storeRepository = null
        dailyCloudSnapshot = null
        state = WebPlayerSessionState.LocalOnly
        mutableProgressBinding.value = WebCatalogProgressBinding.Unavailable(detail)
        mutableStatisticsBinding.value = WebStatisticsBinding.Unavailable(detail)
        mutableDailyBinding.value = WebDailyBinding.Unavailable(detail)
        mutableEconomyBinding.value = WebEconomyBinding.Unavailable(detail)
        mutableStoreBinding.value = WebStoreBinding.Unavailable(detail)
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

    private fun isCurrentStatistics(binding: WebStatisticsBinding.Ready): Boolean =
        !accountSelectionOpen &&
            binding.token.value == contextRevision &&
            statisticsRepository === binding.repository &&
            (mutableStatisticsBinding.value as? WebStatisticsBinding.Ready)?.let {
                it.token == binding.token && it.repository === binding.repository
            } == true

    private fun isCurrentDaily(
        revision: Long,
        repository: WebDailyRepository,
    ): Boolean = isCurrent(revision) && dailyRepository === repository

    private fun isCurrentDaily(binding: WebDailyBinding.Ready): Boolean =
        !accountSelectionOpen &&
            binding.token.value == contextRevision &&
            dailyRepository === binding.repository &&
            (mutableDailyBinding.value as? WebDailyBinding.Ready)?.let {
                it.token == binding.token && it.repository === binding.repository
            } == true
}
