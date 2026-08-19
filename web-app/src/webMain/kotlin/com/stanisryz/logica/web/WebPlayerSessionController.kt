package com.stanisryz.logica.web

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.stanisryz.logica.platform.CloudSaveGateway
import com.stanisryz.logica.platform.CloudSaveReadResult
import com.stanisryz.logica.platform.CloudSaveWriteResult
import com.stanisryz.logica.platform.PlayerAuthorizationResult
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

    data object Unsupported : WebPlayerSessionState

    data class Anonymous(
        val authorizationError: String? = null,
    ) : WebPlayerSessionState

    data class Authorized(
        val identity: PlayerIdentity,
        val syncStatus: WebCloudSyncStatus,
    ) : WebPlayerSessionState
}

/** Keeps account/auth/cloud policy separate from SDK bootstrap and gameplay controllers. */
internal class WebPlayerSessionController(
    private val playerIdentityGateway: PlayerIdentityGateway,
    private val cloudSaveGateway: CloudSaveGateway,
    private val progressRepository: WebCatalogProgressRepository,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private var started = false
    private var operation: Job? = null

    var state by mutableStateOf<WebPlayerSessionState>(WebPlayerSessionState.Loading)
        private set

    fun start() {
        if (started) return
        started = true
        operation =
            scope.launch {
                progressRepository.loadLocal()
                val identity = playerIdentityGateway.identity()
                when (identity.authorizationState) {
                    PlayerAuthorizationState.UNSUPPORTED -> state = WebPlayerSessionState.Unsupported
                    PlayerAuthorizationState.ANONYMOUS -> state = WebPlayerSessionState.Anonymous()
                    PlayerAuthorizationState.AUTHORIZED -> synchronize(identity)
                }
            }
    }

    /** The only path that may show the Yandex authorization dialog. */
    fun requestAuthorization() {
        if (state !is WebPlayerSessionState.Anonymous || operation?.isActive == true) return
        operation =
            scope.launch {
                when (val result = playerIdentityGateway.requestAuthorization()) {
                    is PlayerAuthorizationResult.Available -> {
                        if (result.identity.authorizationState == PlayerAuthorizationState.AUTHORIZED) {
                            synchronize(result.identity)
                        } else {
                            state = WebPlayerSessionState.Anonymous("Вход не был завершён.")
                        }
                    }
                    PlayerAuthorizationResult.Unsupported -> state = WebPlayerSessionState.Unsupported
                    is PlayerAuthorizationResult.Failed ->
                        state = WebPlayerSessionState.Anonymous("Не удалось войти. Попробуйте ещё раз.")
                }
            }
    }

    fun retrySynchronization() {
        val authorized = state as? WebPlayerSessionState.Authorized ?: return
        if (operation?.isActive == true) return
        operation = scope.launch { synchronize(authorized.identity) }
    }

    fun dispose() {
        scope.cancel()
    }

    private suspend fun synchronize(identity: PlayerIdentity) {
        state = WebPlayerSessionState.Authorized(identity, WebCloudSyncStatus.SYNCING)
        try {
            val cloud =
                when (val result = cloudSaveGateway.read()) {
                    is CloudSaveReadResult.Found ->
                        WebCatalogProgressCodec.decode(result.payload)
                            ?: return syncFailed(identity)
                    CloudSaveReadResult.Missing -> WebCatalogProgressSnapshot.EMPTY
                    CloudSaveReadResult.Unsupported,
                    is CloudSaveReadResult.Failed,
                    -> return syncFailed(identity)
                }

            when (val merge = progressRepository.mergeCloud(cloud)) {
                is WebCatalogMergeResult.PersistenceFailed -> syncFailed(identity)
                is WebCatalogMergeResult.Merged -> {
                    if (merge.cloudWriteRequired) {
                        when (cloudSaveGateway.write(WebCatalogProgressCodec.encode(merge.snapshot))) {
                            CloudSaveWriteResult.Saved -> Unit
                            CloudSaveWriteResult.Unsupported,
                            is CloudSaveWriteResult.Failed,
                            -> return syncFailed(identity)
                        }
                    }
                    state = WebPlayerSessionState.Authorized(identity, WebCloudSyncStatus.SYNCED)
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            syncFailed(identity)
        }
    }

    private fun syncFailed(identity: PlayerIdentity) {
        state = WebPlayerSessionState.Authorized(identity, WebCloudSyncStatus.ERROR)
    }
}
