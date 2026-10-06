package com.stanisryz.logica.web

import com.stanisryz.logica.platform.CloudSaveWriteResult
import com.stanisryz.logica.platform.PlayerAuthorizationResult
import com.stanisryz.logica.platform.PlayerAuthorizationState
import com.stanisryz.logica.platform.PlayerIdentity
import com.stanisryz.logica.platform.PlayerIdentityGateway
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelNumber
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelPackVersion
import com.stanisryz.logica.puzzle.core.daily.DailyDate
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The Yandex cloud receives one key only, the unified save: `player.setData` may replace all of a
 * Player's data with the object it is given, so a write of a legacy key could erase it. The bridge
 * here does exactly that on every write.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WebSingleCloudKeyTest {
    private val balance = WebCatalogProgressBucket(PuzzleType.BALANCE, Difficulty.EASY, CatalogLevelPackVersion.V1)
    private val dailyDate = DailyDate(2026, 10, 6)

    /** `setData` replaces every key of the Player with the object it is given. */
    private class ReplacingBridge : WebPlayerDataBridge {
        var data: Map<String, String> = emptyMap()
        val writtenKeys = mutableListOf<String>()
        var failingReads: Set<String> = emptySet()

        override suspend fun readPlayerData(key: String): String? {
            if (key in failingReads) error("player.getData failed")
            return data[key]
        }

        override suspend fun writePlayerData(
            key: String,
            value: String,
            flush: Boolean,
        ) {
            writtenKeys += key
            data = mapOf(key to value)
        }
    }

    @Test
    fun legacyKeysAreReadOnlyAndNeverReachTheSdk() =
        runTest {
            val bridge = ReplacingBridge()
            val gateways = YandexCloudGateways(bridge)
            listOf(gateways.catalog, gateways.statistics, gateways.daily).forEach { legacy ->
                assertEquals(CloudSaveWriteResult.Unsupported, legacy.write(byteArrayOf(1, 2, 3)))
            }
            assertEquals(CloudSaveWriteResult.Saved, gateways.unified.write(byteArrayOf(4)))
            assertEquals(listOf(YandexCloudSaveGateway.UNIFIED_STATE_KEY), bridge.writtenKeys)
        }

    // Device B's first unified read fails while its legacy synchronization runs. Writing a legacy
    // key then would have replaced device A's unified save; with one written key it survives, and
    // B's retried restore brings A's progress.
    @Test
    fun aFailedUnifiedReadOnAnotherDeviceNeverLosesTheFirstDevicesProgress() =
        runTest {
            val bridge = ReplacingBridge()
            val identity = FakePlayerIdentityGateway("player/A")

            val deviceA = device(bridge, identity, catalogLevel = 12, played = 0L, installationId = "device-a-installation-01")
            deviceA.start()
            advanceUntilIdle()
            assertTrue(deviceA.scheduler.unifiedSaveActive)
            assertEquals(12, unifiedCatalogLevel(bridge))

            bridge.failingReads = setOf(YandexCloudSaveGateway.UNIFIED_STATE_KEY)
            val deviceB = device(bridge, identity, catalogLevel = 1, played = 3L, installationId = "device-b-installation-01")
            deviceB.start()
            runCurrent()
            deviceB.scheduler.markDirty()
            deviceB.scheduler.flushNow()
            advanceTimeBy(1_000)
            runCurrent()
            // The unified save of device A is still there, untouched.
            assertEquals(12, unifiedCatalogLevel(bridge))

            bridge.failingReads = emptySet()
            advanceUntilIdle()
            assertEquals(
                12,
                deviceB.controller.progressRepository
                    ?.currentLevel(balance)
                    ?.value,
            )
            assertEquals(12, unifiedCatalogLevel(bridge))
            assertTrue(deviceB.scheduler.unifiedSaveActive)
            assertTrue(
                bridge.writtenKeys.isNotEmpty() && bridge.writtenKeys.all { it == YandexCloudSaveGateway.UNIFIED_STATE_KEY },
                "written keys: ${bridge.writtenKeys}",
            )
        }

    private class Device(
        val controller: WebPlayerSessionController,
        val scheduler: WebUnifiedSaveScheduler,
    ) {
        fun start() = controller.start()
    }

    private fun TestScope.device(
        bridge: ReplacingBridge,
        identity: FakePlayerIdentityGateway,
        catalogLevel: Int,
        played: Long,
        installationId: String,
    ): Device {
        val gateways = YandexCloudGateways(bridge)
        val controller =
            WebPlayerSessionController(
                playerIdentityGateway = identity,
                cloudSaveGateway = gateways.catalog,
                progressRepositoryFactory = { scope ->
                    WebCatalogProgressRepository(
                        scope,
                        FakeProgressStore(WebCatalogProgressSnapshot(levels = mapOf(balance to CatalogLevelNumber(catalogLevel)))),
                    )
                },
                statisticsCloudSaveGateway = gateways.statistics,
                statisticsRepositoryFactory = { scope ->
                    WebStatisticsRepository(scope, installationId, FakeStatisticsStore(statistics(installationId, played)))
                },
                dailyCloudSaveGateway = gateways.daily,
                dailyRepositoryFactory = { scope -> WebDailyRepository(scope, FakeDailyStore(WebDailySnapshotV1.EMPTY)) { dailyDate } },
                playerContextEvents = FakePlayerContextEvents(),
                economyRepositoryFactory = { scope -> WebPlayerEconomyRepository(scope, FakeEconomyStore()) },
                storeRepositoryFactory = { scope -> WebPlayerStoreRepository(scope, FakePlayerItemStore()) },
                scope = this,
            )
        val scheduler =
            WebUnifiedSaveScheduler(
                WebSaveManager(WebSaveSections(controller).all(), YandexCloudSaveRepository(gateways.unified)),
                this,
            )
        controller.unifiedSaveAccess = scheduler
        controller.postBindAction = { token -> scheduler.restoreAndEstablish(token) }
        return Device(controller, scheduler)
    }

    private fun unifiedCatalogLevel(bridge: ReplacingBridge): Int? {
        val encoded = bridge.data[YandexCloudSaveGateway.UNIFIED_STATE_KEY] ?: return null
        val save = WebSaveCodec.decode(WebBase64.decode(encoded) ?: return null) ?: return null
        val catalog = WebCatalogProgressCodec.decode(save.section(WebSaveSectionIds.CATALOG) ?: return null) ?: return null
        return catalog.currentLevel(balance).value
    }

    private fun statistics(
        installationId: String,
        played: Long,
    ): WebStatisticsSnapshot =
        if (played == 0L) {
            WebStatisticsSnapshot.EMPTY
        } else {
            WebStatisticsSnapshot(
                components =
                    mapOf(
                        installationId to
                            WebStatisticsDeviceComponent(
                                buckets =
                                    mapOf(
                                        WebStatisticsBucket(PuzzleType.BALANCE, Difficulty.EASY) to WebStatisticsCounters(played = played),
                                    ),
                            ),
                    ),
            )
        }

    private class FakePlayerIdentityGateway(
        val playerId: String,
    ) : PlayerIdentityGateway {
        override suspend fun identity(): PlayerIdentity =
            PlayerIdentity(playerId = playerId, authorizationState = PlayerAuthorizationState.ANONYMOUS, provider = "yandex-games")

        override suspend fun requestAuthorization(): PlayerAuthorizationResult = PlayerAuthorizationResult.Unsupported
    }

    private class FakePlayerContextEvents : WebPlayerContextEvents {
        override fun setAccountSelectionOpenedListener(listener: (() -> Unit)?) = Unit

        override fun setPlayerContextChangedListener(listener: (() -> Unit)?) = Unit
    }

    private class FakeProgressStore(
        var snapshot: WebCatalogProgressSnapshot,
    ) : WebCatalogProgressStore {
        override fun load(): WebCatalogProgressSnapshot = snapshot

        override fun save(snapshot: WebCatalogProgressSnapshot) {
            this.snapshot = snapshot
        }
    }

    private class FakeStatisticsStore(
        var snapshot: WebStatisticsSnapshot,
    ) : WebStatisticsStore {
        override fun load(): WebStatisticsSnapshot = snapshot

        override fun save(snapshot: WebStatisticsSnapshot) {
            this.snapshot = snapshot
        }
    }

    private class FakeDailyStore(
        var snapshot: WebDailySnapshotV1,
    ) : WebDailyStore {
        override fun load(): WebDailySnapshotV1 = snapshot

        override fun save(snapshot: WebDailySnapshotV1) {
            this.snapshot = snapshot
        }
    }

    private class FakeEconomyStore : WebEconomyStore {
        private var snapshot = WebEconomySnapshot.DEFAULT

        override fun load(): WebEconomySnapshot = snapshot

        override fun save(snapshot: WebEconomySnapshot) {
            this.snapshot = snapshot
        }
    }

    private class FakePlayerItemStore(
        private var snapshot: WebStoreSnapshot = WebStoreSnapshot.DEFAULT,
    ) : WebStoreStore {
        override fun load(): WebStoreSnapshot = snapshot

        override fun save(snapshot: WebStoreSnapshot) {
            this.snapshot = snapshot
        }
    }
}
