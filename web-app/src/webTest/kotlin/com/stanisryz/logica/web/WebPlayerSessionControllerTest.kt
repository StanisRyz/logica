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
import com.stanisryz.logica.puzzle.core.daily.DailyChallengePolicyV5
import com.stanisryz.logica.puzzle.core.daily.DailyDate
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

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
            val statisticsCloud = FakeCloudSaveGateway(identity)
            statisticsCloud.snapshots[playerA] = statisticsSnapshot(7L)
            statisticsCloud.snapshots[playerB] = statisticsSnapshot(7L)
            val dailyDate = DailyDate(2026, 8, 20)
            val dailyCloud = FakeCloudSaveGateway(identity)
            dailyCloud.snapshots[playerA] = dailySnapshot(dailyDate, solved = PuzzleType.WORD)
            dailyCloud.snapshots[playerB] = dailySnapshot(dailyDate, solved = PuzzleType.SUDOKU)
            val stores =
                mutableMapOf(
                    scopeA to FakeProgressStore(snapshot(balance, 12)),
                    scopeB to FakeProgressStore(snapshot(balance, 4)),
                )
            val statisticsStores =
                mutableMapOf(
                    scopeA to FakeStatisticsStore(checkNotNull(WebStatisticsCodec.decode(statisticsSnapshot(12L)))),
                    scopeB to FakeStatisticsStore(checkNotNull(WebStatisticsCodec.decode(statisticsSnapshot(4L)))),
                )
            val dailyStores =
                mutableMapOf(
                    scopeA to FakeDailyStore(dailySnapshotValue(dailyDate, failed = PuzzleType.BALANCE)),
                    scopeB to FakeDailyStore(WebDailySnapshotV1.EMPTY),
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
                    statisticsCloudSaveGateway = statisticsCloud,
                    statisticsRepositoryFactory =
                        WebStatisticsRepositoryFactory { scope ->
                            WebStatisticsRepository(scope, INSTALLATION_ID, statisticsStores.getValue(scope))
                        },
                    dailyCloudSaveGateway = dailyCloud,
                    dailyRepositoryFactory =
                        WebDailyRepositoryFactory { scope ->
                            WebDailyRepository(scope, dailyStores.getValue(scope)) { dailyDate }
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
            assertEquals(scopeA, controller.statisticsRepository?.scope)
            assertEquals(
                12L,
                controller.statisticsRepository
                    ?.aggregate()
                    ?.totals()
                    ?.played,
            )
            assertEquals(scopeA, controller.dailyRepository?.scope)
            val playerADaily =
                checkNotNull(
                    controller.dailyRepository
                        ?.snapshot
                        ?.value
                        ?.days
                        ?.get(dailyDate),
                )
            assertEquals(true, playerADaily.facts(PuzzleType.BALANCE).failedSeen)
            assertEquals(true, playerADaily.facts(PuzzleType.WORD).solved)
            assertEquals(
                12L,
                WebStatisticsCodec
                    .decode(statisticsCloud.snapshots.getValue(playerA))
                    ?.let(WebStatisticsAggregator::aggregate)
                    ?.totals()
                    ?.played,
            )

            events.fireOpened()
            assertIs<WebCatalogCompletionResult.ContextChanged>(progression.advanceSolved(playerAAttempt))
            assertNull(controller.statisticsRepository)
            assertIs<WebStatisticsBinding.Loading>(controller.statisticsBinding.value)
            assertNull(controller.dailyRepository)
            assertIs<WebDailyBinding.Loading>(controller.dailyBinding.value)
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
            assertEquals(scopeB, controller.statisticsRepository?.scope)
            assertEquals(scopeB, controller.dailyRepository?.scope)
            assertEquals(
                true,
                controller.dailyRepository
                    ?.snapshot
                    ?.value
                    ?.days
                    ?.get(dailyDate)
                    ?.facts(PuzzleType.SUDOKU)
                    ?.solved,
            )
            assertEquals(
                7L,
                controller.statisticsRepository
                    ?.aggregate()
                    ?.totals()
                    ?.played,
            )
            assertEquals(
                12,
                stores
                    .getValue(scopeA)
                    .snapshot
                    .currentLevel(balance)
                    .value,
            )
            assertIs<WebCatalogCompletionResult.ContextChanged>(progression.advanceSolved(playerAAttempt))
            assertEquals(12L, WebStatisticsAggregator.aggregate(statisticsStores.getValue(scopeA).snapshot).totals().played)
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

    private fun statisticsSnapshot(played: Long): ByteArray =
        WebStatisticsCodec.encode(
            WebStatisticsSnapshot(
                components =
                    mapOf(
                        INSTALLATION_ID to
                            WebStatisticsDeviceComponent(
                                buckets =
                                    mapOf(
                                        WebStatisticsBucket(PuzzleType.WORD, Difficulty.EASY) to
                                            WebStatisticsCounters(played = played),
                                    ),
                            ),
                    ),
            ),
        )

    private fun dailySnapshot(
        date: DailyDate,
        solved: PuzzleType,
    ): ByteArray = WebDailyCodec.encode(dailySnapshotValue(date, solved = solved))

    private fun dailySnapshotValue(
        date: DailyDate,
        failed: PuzzleType? = null,
        solved: PuzzleType? = null,
    ): WebDailySnapshotV1 {
        val record =
            WebDailyDayRecord(
                date = date,
                policyVersion = DailyChallengePolicyV5.VERSION,
                failedMask = failed?.let(WebDailyPuzzleOrder::bit) ?: 0,
                solvedMask = solved?.let(WebDailyPuzzleOrder::bit) ?: 0,
            )
        return WebDailySnapshotV1(days = mapOf(date to record))
    }

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

    private companion object {
        const val INSTALLATION_ID = "browser-installation-0001"
    }
}
