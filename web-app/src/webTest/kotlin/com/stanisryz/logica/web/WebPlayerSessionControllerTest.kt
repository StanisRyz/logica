package com.stanisryz.logica.web

import com.stanisryz.logica.platform.CloudSaveAvailability
import com.stanisryz.logica.platform.CloudSaveGateway
import com.stanisryz.logica.platform.CloudSaveReadResult
import com.stanisryz.logica.platform.CloudSaveWriteResult
import com.stanisryz.logica.platform.PlayerAuthorizationResult
import com.stanisryz.logica.platform.PlayerAuthorizationState
import com.stanisryz.logica.platform.PlayerIdentity
import com.stanisryz.logica.platform.PlayerIdentityGateway
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelNumber
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelPackVersion
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals

class WebPlayerSessionControllerTest {
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun accountSwitchLoadsOnlyTheNewPlayersLocalAndCloudScope() =
        runTest {
            val balance =
                WebCatalogProgressBucket(
                    PuzzleType.BALANCE,
                    Difficulty.EASY,
                    CatalogLevelPackVersion.V1,
                )
            val playerA = "player/A"
            val playerB = "player/B"
            val scopeA = WebCatalogProgressScope.yandexPlayer(playerA)
            val scopeB = WebCatalogProgressScope.yandexPlayer(playerB)
            assertNotEquals(scopeA, scopeB)
            assertNotEquals(scopeA, WebCatalogProgressScope.STANDALONE)
            assertNotEquals(scopeB, WebCatalogProgressScope.STANDALONE)

            val identity = FakePlayerIdentityGateway(playerA)
            val cloud = FakeCloudSaveGateway(identity)
            cloud.snapshots[playerA] = snapshot(balance, 7)
            cloud.snapshots[playerB] = snapshot(balance, 7)
            val stores =
                mutableMapOf(
                    scopeA to FakeProgressStore(snapshot(balance, 12)),
                    scopeB to FakeProgressStore(snapshot(balance, 4)),
                )
            val events = FakePlayerContextEvents()
            val controller =
                WebPlayerSessionController(
                    playerIdentityGateway = identity,
                    cloudSaveGateway = cloud,
                    progressRepositoryFactory =
                        WebCatalogProgressRepositoryFactory { scope ->
                            WebCatalogProgressRepository(scope, stores.getValue(scope))
                        },
                    playerContextEvents = events,
                    scope = this,
                )

            controller.start()
            advanceUntilIdle()
            val progression = WebCatalogProgressCoordinator(controller)
            val playerAAttempt =
                assertIs<WebCatalogLevelResolution.Resolved>(
                    progression.resolveCurrentLevel(PuzzleType.BALANCE, Difficulty.EASY),
                ).attempt

            assertEquals(scopeA, controller.progressRepository?.scope)
            assertEquals(12, controller.progressRepository?.currentLevel(balance)?.value)
            assertEquals(12, WebCatalogProgressCodec.decode(cloud.snapshots.getValue(playerA))?.currentLevel(balance)?.value)

            events.fireOpened()
            assertIs<WebCatalogCompletionResult.ContextChanged>(progression.advanceSolved(playerAAttempt))
            assertEquals(
                12,
                stores
                    .getValue(scopeA)
                    .snapshot
                    .currentLevel(balance)
                    .value,
            )

            identity.playerId = playerB
            events.fireChanged()
            advanceUntilIdle()

            assertEquals(scopeB, controller.progressRepository?.scope)
            assertEquals(7, controller.progressRepository?.currentLevel(balance)?.value)
            assertEquals(
                12,
                stores
                    .getValue(scopeA)
                    .snapshot
                    .currentLevel(balance)
                    .value,
            )
            assertIs<WebCatalogCompletionResult.ContextChanged>(progression.advanceSolved(playerAAttempt))
            assertEquals(
                7,
                stores
                    .getValue(scopeB)
                    .snapshot
                    .currentLevel(balance)
                    .value,
            )
            val state = assertIs<WebPlayerSessionState.PlayerReady>(controller.state)
            assertEquals(WebCloudSyncStatus.SYNCED, state.syncStatus)
        }

    private fun snapshot(
        bucket: WebCatalogProgressBucket,
        level: Int,
    ): ByteArray =
        WebCatalogProgressCodec.encode(
            WebCatalogProgressSnapshot(levels = mapOf(bucket to CatalogLevelNumber(level))),
        )

    private class FakePlayerIdentityGateway(
        var playerId: String,
    ) : PlayerIdentityGateway {
        override suspend fun identity(): PlayerIdentity =
            PlayerIdentity(
                playerId = playerId,
                authorizationState = PlayerAuthorizationState.ANONYMOUS,
                provider = "yandex-games",
            )

        override suspend fun requestAuthorization(): PlayerAuthorizationResult = PlayerAuthorizationResult.Unsupported
    }

    private class FakeCloudSaveGateway(
        private val identity: FakePlayerIdentityGateway,
    ) : CloudSaveGateway {
        override val availability = CloudSaveAvailability.AVAILABLE
        val snapshots = mutableMapOf<String, ByteArray>()

        override suspend fun read(): CloudSaveReadResult =
            snapshots[identity.playerId]?.let(CloudSaveReadResult::Found) ?: CloudSaveReadResult.Missing

        override suspend fun write(payload: ByteArray): CloudSaveWriteResult {
            snapshots[identity.playerId] = payload
            return CloudSaveWriteResult.Saved
        }
    }

    private class FakePlayerContextEvents : WebPlayerContextEvents {
        private var openedListener: (() -> Unit)? = null
        private var listener: (() -> Unit)? = null

        override fun setAccountSelectionOpenedListener(listener: (() -> Unit)?) {
            openedListener = listener
        }

        override fun setPlayerContextChangedListener(listener: (() -> Unit)?) {
            this.listener = listener
        }

        fun fireChanged() {
            listener?.invoke()
        }

        fun fireOpened() {
            openedListener?.invoke()
        }
    }

    private class FakeProgressStore(
        var snapshot: WebCatalogProgressSnapshot,
    ) : WebCatalogProgressStore {
        constructor(payload: ByteArray) : this(checkNotNull(WebCatalogProgressCodec.decode(payload)))

        override fun load(): WebCatalogProgressSnapshot = snapshot

        override fun save(snapshot: WebCatalogProgressSnapshot) {
            this.snapshot = snapshot
        }
    }
}
