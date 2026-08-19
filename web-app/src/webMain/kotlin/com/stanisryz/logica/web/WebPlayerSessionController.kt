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
import kotlinx.coroutines.launch

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

/** Binds one freshly loaded local repository to one current Yandex Player before any cloud merge. */
internal class WebPlayerSessionController(
    private val playerIdentityGateway: PlayerIdentityGateway,
    private val cloudSaveGateway: CloudSaveGateway,
    private val progressRepositoryFactory: WebCatalogProgressRepositoryFactory,
    private val playerContextEvents: WebPlayerContextEvents,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private var started = false
    private var contextRevision = 0L
    private var operation: Job? = null

    var state by mutableStateOf<WebPlayerSessionState>(WebPlayerSessionState.Loading)
        private set

    var progressRepository: WebCatalogProgressRepository? = null
        private set

    init {
        playerContextEvents.setPlayerContextChangedListener {
            if (started) bindCurrentContext()
        }
    }

    fun start() {
        if (started) return
        started = true
        bindCurrentContext()
    }

    fun dispose() {
        playerContextEvents.setPlayerContextChangedListener(null)
        scope.cancel()
    }

    private fun bindCurrentContext() {
        operation?.cancel()
        val revision = ++contextRevision
        progressRepository = null
        state = WebPlayerSessionState.Loading
        operation = scope.launch { resolveAndSynchronize(revision) }
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
                state = WebPlayerSessionState.LocalOnly
                return
            }

            val repository =
                progressRepositoryFactory.create(WebCatalogProgressScope.yandexPlayer(playerId))
            repository.loadLocal()
            if (!isCurrent(revision)) return
            progressRepository = repository
            synchronize(revision, identity, repository)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            if (isCurrent(revision)) state = WebPlayerSessionState.LocalOnly
        }
    }

    private fun bindStandalone(revision: Long) {
        val repository = progressRepositoryFactory.create(WebCatalogProgressScope.STANDALONE)
        repository.loadLocal()
        if (!isCurrent(revision)) return
        progressRepository = repository
        state = WebPlayerSessionState.LocalOnly
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
                        ?: return syncFailed(revision, identity)
                CloudSaveReadResult.Missing -> WebCatalogProgressSnapshot.EMPTY
                CloudSaveReadResult.Unsupported,
                is CloudSaveReadResult.Failed,
                -> return syncFailed(revision, identity)
            }
        if (!isCurrent(revision)) return

        when (val merge = repository.mergeCloud(cloud)) {
            is WebCatalogMergeResult.PersistenceFailed -> syncFailed(revision, identity)
            is WebCatalogMergeResult.Merged -> {
                if (merge.cloudWriteRequired) {
                    when (cloudSaveGateway.write(WebCatalogProgressCodec.encode(merge.snapshot))) {
                        CloudSaveWriteResult.Saved -> Unit
                        CloudSaveWriteResult.Unsupported,
                        is CloudSaveWriteResult.Failed,
                        -> return syncFailed(revision, identity)
                    }
                    if (!isCurrent(revision)) return
                }
                state = WebPlayerSessionState.PlayerReady(identity, WebCloudSyncStatus.SYNCED)
            }
        }
    }

    private fun syncFailed(
        revision: Long,
        identity: PlayerIdentity,
    ) {
        if (isCurrent(revision)) {
            state = WebPlayerSessionState.PlayerReady(identity, WebCloudSyncStatus.ERROR)
        }
    }

    private fun isCurrent(revision: Long): Boolean = revision == contextRevision
}
