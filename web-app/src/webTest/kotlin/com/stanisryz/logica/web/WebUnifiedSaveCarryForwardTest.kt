package com.stanisryz.logica.web

import com.stanisryz.logica.platform.CloudSaveAvailability
import com.stanisryz.logica.platform.CloudSaveGateway
import com.stanisryz.logica.platform.CloudSaveReadResult
import com.stanisryz.logica.platform.CloudSaveWriteResult
import com.stanisryz.logica.platform.PlayerAuthorizationResult
import com.stanisryz.logica.platform.PlayerAuthorizationState
import com.stanisryz.logica.platform.PlayerIdentity
import com.stanisryz.logica.platform.PlayerIdentityGateway
import com.stanisryz.logica.platform.SaveData
import com.stanisryz.logica.platform.SaveLoadResult
import com.stanisryz.logica.platform.SaveRepository
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelNumber
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelPackVersion
import com.stanisryz.logica.puzzle.core.daily.DailyChallengePolicyV5
import com.stanisryz.logica.puzzle.core.daily.DailyChallengePolicyV6
import com.stanisryz.logica.puzzle.core.daily.DailyDate
import com.stanisryz.logica.puzzle.core.daily.DailyPolicyVersion
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Stage 1a: a cloud section whose merge did not land durably keeps the context unresolved, and
 * a section the domain does not export right now is carried forward instead of being erased.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WebUnifiedSaveCarryForwardTest {
    private val balance = WebCatalogProgressBucket(PuzzleType.BALANCE, Difficulty.EASY, CatalogLevelPackVersion.V1)
    private val dailyDate = DailyDate(2026, 8, 20)

    @Test
    fun aFailedLocalMergeOfAnySectionKeepsTheCloudUntilARetrySucceeds() =
        runTest {
            val cases =
                listOf(
                    CATALOG to mapOf(WebSaveSectionIds.CATALOG to catalogPayload(level = 12)),
                    STARS to mapOf(WebSaveSectionIds.STARS to starsPayload()),
                    STATISTICS to mapOf(WebSaveSectionIds.STATISTICS to statisticsPayload()),
                    DAILY to mapOf(WebSaveSectionIds.DAILY to dailyPayload(DailyChallengePolicyV5.VERSION)),
                    PAYMENTS to mapOf(WebSaveSectionIds.PAYMENTS to paymentsPayload()),
                    ECONOMY to newerEconomyStorePair(),
                )
            for ((kind, sections) in cases) {
                val stored = WebSaveCodec.encode(SaveData(sections = sections))
                val harness = Harness(this, stored)
                harness.failingSaves += kind

                harness.start()
                runCurrent()
                harness.scheduler.markDirty()
                runCurrent()
                assertEquals(0, harness.cloud.writes, kind)
                assertTrue(stored.contentEquals(harness.cloud.stored), kind)
                assertFalse(harness.scheduler.unifiedSaveActive, kind)
                assertEquals(WebUnifiedSaveStatus.ERROR, harness.scheduler.saveStatus.value, kind)

                // Local storage recovers: the ~2s restore retry merges durably, then establishes.
                harness.failingSaves.clear()
                advanceUntilIdle()
                assertEquals(1, harness.cloud.writes, kind)
                assertTrue(harness.scheduler.unifiedSaveActive, kind)
            }
        }

    @Test
    fun aCanonicalEnvelopeWithEverySectionRestoresOnAnotherBrowser() =
        runTest {
            val first = Harness(this, WebSaveCodec.encode(SaveData(sections = newerEconomyStorePair())))
            first.start()
            advanceUntilIdle()
            val canonical = checkNotNull(first.cloud.stored)
            assertTrue(WebSaveSectionIds.STORE in checkNotNull(WebSaveCodec.decode(canonical)).sections)

            // Store belongs to the Economy group: the envelope resolves, and the newer pair lands.
            val second = Harness(this, canonical)
            second.start()
            advanceUntilIdle()
            assertEquals(1, second.cloud.writes)
            assertTrue(second.scheduler.unifiedSaveActive)
            assertEquals(
                100,
                second.controller.economyRepository
                    ?.currentSnapshot
                    ?.gems,
            )
            assertEquals(
                8,
                second.controller.storeRepository
                    ?.snapshot
                    ?.value
                    ?.quantityOf(STORE_INVENTORY_HINTS),
            )
        }

    @Test
    fun anOlderLocalEconomyStorePairNeverOverwritesANewerCloudPair() =
        runTest {
            val stored = WebSaveCodec.encode(SaveData(sections = newerEconomyStorePair()))
            val harness = Harness(this, stored)
            harness.failingSaves += STORE

            harness.start()
            advanceUntilIdle()
            harness.scheduler.markDirty()
            assertFalse(harness.scheduler.flushNow())
            advanceUntilIdle()

            // The cloud keeps the purchase; the local pair stays the old, consistent one.
            assertEquals(0, harness.cloud.writes)
            assertTrue(stored.contentEquals(harness.cloud.stored))
            assertEquals(
                WebEconomySnapshot.DEFAULT.gems,
                harness.controller.economyRepository
                    ?.currentSnapshot
                    ?.gems,
            )
            assertEquals(
                WebStoreSnapshot.DEFAULT.quantityOf(STORE_INVENTORY_HINTS),
                harness.controller.storeRepository
                    ?.snapshot
                    ?.value
                    ?.quantityOf(STORE_INVENTORY_HINTS),
            )
        }

    @Test
    fun aDailyPolicyConflictStillResolvesTheRestore() =
        runTest {
            // Stage 2.2: the cloud's other dates survive the conflict; the conflict date keeps the local record.
            val otherDate = DailyDate(2026, 8, 19)
            val cloudDaily =
                WebDailySnapshotV1(
                    days =
                        dailySnapshot(DailyChallengePolicyV6.VERSION).days +
                            (otherDate to WebDailyDayRecord(otherDate, DailyChallengePolicyV5.VERSION, solvedMask = 1)),
                )
            val stored = WebSaveCodec.encode(SaveData(sections = mapOf(WebSaveSectionIds.DAILY to WebDailyCodec.encode(cloudDaily))))
            val harness = Harness(this, stored, localDaily = dailySnapshot(DailyChallengePolicyV5.VERSION))

            harness.start()
            advanceUntilIdle()
            assertEquals(1, harness.cloud.writes)
            assertTrue(harness.scheduler.unifiedSaveActive)
            val written = checkNotNull(WebSaveCodec.decode(checkNotNull(harness.cloud.stored)))
            val writtenDaily = checkNotNull(WebDailyCodec.decode(checkNotNull(written.section(WebSaveSectionIds.DAILY))))
            assertEquals(setOf(dailyDate, otherDate), writtenDaily.days.keys)
            assertEquals(DailyChallengePolicyV5.VERSION, writtenDaily.days.getValue(dailyDate).policyVersion)
        }

    @Test
    fun anUnboundStatisticsDomainKeepsItsCloudSectionByteForByte() =
        runTest {
            val statistics = statisticsPayload()
            val stored =
                WebSaveCodec.encode(
                    SaveData(
                        sections =
                            mapOf(
                                WebSaveSectionIds.CATALOG to catalogPayload(level = 4),
                                WebSaveSectionIds.STATISTICS to statistics,
                            ),
                    ),
                )
            val harness = Harness(this, stored, statisticsBound = false)

            harness.start()
            advanceUntilIdle()
            assertEquals(1, harness.cloud.writes)
            val written = checkNotNull(WebSaveCodec.decode(checkNotNull(harness.cloud.stored)))
            assertTrue(statistics.contentEquals(written.section(WebSaveSectionIds.STATISTICS)))

            // Later writes keep carrying it while the domain stays unbound.
            harness.scheduler.markDirty()
            advanceUntilIdle()
            assertEquals(2, harness.cloud.writes)
            val rewritten = checkNotNull(WebSaveCodec.decode(checkNotNull(harness.cloud.stored)))
            assertTrue(statistics.contentEquals(rewritten.section(WebSaveSectionIds.STATISTICS)))
        }

    @Test
    fun carriedSectionsNeverReachAnotherPlayer() =
        runTest {
            var currentPlayer = 1L
            val repository = ScriptedRepository()
            val unbound = ExportingSection(WebSaveSectionIds.STATISTICS, export = null)
            val scheduler =
                WebUnifiedSaveScheduler(
                    saveManager = WebSaveManager(listOf(ExportingSection(WebSaveSectionIds.CATALOG, byteArrayOf(1)), unbound), repository),
                    scope = this,
                    isTokenCurrent = { it.value == currentPlayer },
                )

            // Player A restores a statistics section their domain does not export: it is carried.
            repository.next =
                SaveLoadResult.Found(SaveData(sections = mapOf(WebSaveSectionIds.STATISTICS to byteArrayOf(7, 7))))
            scheduler.restoreAndEstablish(WebPlayerContextToken(currentPlayer))
            assertEquals(listOf<Byte>(7, 7), repository.lastWritten?.section(WebSaveSectionIds.STATISTICS)?.toList())

            // Player B has no save at all: their first write carries nothing of A's.
            scheduler.invalidateContext()
            currentPlayer = 2L
            repository.next = SaveLoadResult.Missing
            assertEquals(WebSaveRestoreOutcome.EMPTY, scheduler.restoreAndEstablish(WebPlayerContextToken(currentPlayer)))
            assertEquals(2, repository.writes)
            assertNull(repository.lastWritten?.section(WebSaveSectionIds.STATISTICS))

            // Nor does an unresolved restore for Player C followed by a definite one.
            scheduler.invalidateContext()
            currentPlayer = 3L
            repository.next = SaveLoadResult.Failed(IllegalStateException("network"))
            scheduler.restoreAndEstablish(WebPlayerContextToken(currentPlayer))
            repository.next = SaveLoadResult.Missing
            advanceUntilIdle()
            assertEquals(3, repository.writes)
            assertNull(repository.lastWritten?.section(WebSaveSectionIds.STATISTICS))
        }

    private fun catalogPayload(level: Int): ByteArray =
        WebCatalogProgressCodec.encode(WebCatalogProgressSnapshot(levels = mapOf(balance to CatalogLevelNumber(level))))

    private fun starsPayload(): ByteArray =
        WebCatalogStarsCodec.encode(WebCatalogStarsSnapshot.EMPTY.withBest(balance, level = 1, stars = 3))

    private fun statisticsPayload(): ByteArray =
        WebStatisticsCodec.encode(
            WebStatisticsSnapshot(
                components =
                    mapOf(
                        "cloud-device-000000000001" to
                            WebStatisticsDeviceComponent(
                                buckets =
                                    mapOf(
                                        WebStatisticsBucket(PuzzleType.BALANCE, Difficulty.EASY) to WebStatisticsCounters(played = 9L),
                                    ),
                            ),
                    ),
            ),
        )

    private fun dailySnapshot(policy: DailyPolicyVersion): WebDailySnapshotV1 =
        WebDailySnapshotV1(
            days =
                mapOf(
                    dailyDate to
                        WebDailyDayRecord(
                            date = dailyDate,
                            policyVersion = policy,
                            solvedMask = WebDailyPuzzleOrder.bit(PuzzleType.WORD),
                        ),
                ),
        )

    private fun dailyPayload(policy: DailyPolicyVersion): ByteArray = WebDailyCodec.encode(dailySnapshot(policy))

    private fun paymentsPayload(): ByteArray =
        WebPaymentsCodec.encode(WebPaymentsSnapshot(fulfilledTokens = mapOf("token-1" to WebPaidProduct.GEMS_50.yandexProductId)))

    /** A cloud pair one purchase ahead of a fresh local pair: more gems and bought hints. */
    private fun newerEconomyStorePair(): Map<String, ByteArray> =
        mapOf(
            WebSaveSectionIds.ECONOMY to WebEconomyCodec.encode(WebEconomySnapshot.DEFAULT.copy(gems = 100, revision = 9L)),
            WebSaveSectionIds.STORE to
                WebStoreCodec.encode(WebStoreSnapshot.DEFAULT.copy(inventory = mapOf(STORE_INVENTORY_HINTS to 8), revision = 9L)),
        )

    /** One fresh browser for one Player, with local storage that can refuse writes per domain. */
    private inner class Harness(
        scope: TestScope,
        stored: ByteArray,
        statisticsBound: Boolean = true,
        localDaily: WebDailySnapshotV1 = WebDailySnapshotV1.EMPTY,
    ) {
        val failingSaves = mutableSetOf<String>()
        val cloud = FakeCloudGateway(stored)
        val controller: WebPlayerSessionController
        val scheduler: WebUnifiedSaveScheduler

        init {
            val identity = FixedIdentityGateway("player/A")
            controller =
                WebPlayerSessionController(
                    playerIdentityGateway = identity,
                    cloudSaveGateway = FakeCloudGateway(null),
                    progressRepositoryFactory = { playerScope ->
                        WebCatalogProgressRepository(
                            playerScope,
                            guarded(CATALOG, WebCatalogProgressSnapshot.EMPTY).asProgressStore(),
                            guarded(STARS, WebCatalogStarsSnapshot.EMPTY).asStarsStore(),
                        )
                    },
                    statisticsCloudSaveGateway = FakeCloudGateway(null),
                    statisticsRepositoryFactory = { playerScope ->
                        check(statisticsBound) { "Statistics storage is unavailable." }
                        WebStatisticsRepository(
                            playerScope,
                            INSTALLATION_ID,
                            // Already initialized, so binding writes nothing and only the merge can fail.
                            guarded(
                                STATISTICS,
                                WebStatisticsSnapshot(components = mapOf(INSTALLATION_ID to WebStatisticsDeviceComponent())),
                            ).asStatisticsStore(),
                        )
                    },
                    dailyCloudSaveGateway = FakeCloudGateway(null),
                    dailyRepositoryFactory = { playerScope ->
                        WebDailyRepository(playerScope, guarded(DAILY, localDaily).asDailyStore()) { dailyDate }
                    },
                    playerContextEvents = NoPlayerContextEvents,
                    economyRepositoryFactory = { playerScope ->
                        WebPlayerEconomyRepository(playerScope, guarded(ECONOMY, WebEconomySnapshot.DEFAULT).asEconomyStore())
                    },
                    storeRepositoryFactory = { playerScope ->
                        WebPlayerStoreRepository(playerScope, guarded(STORE, WebStoreSnapshot.DEFAULT).asItemStore())
                    },
                    purchaseTransactionStoreFactory = { MemoryPurchaseJournal() },
                    paymentsRepositoryFactory = { playerScope ->
                        WebPlayerPaymentsRepository(playerScope, guarded(PAYMENTS, WebPaymentsSnapshot.EMPTY).asPaymentsStore())
                    },
                    paymentsJournalStoreFactory = { MemoryPaymentsJournal() },
                    scope = scope,
                )
            scheduler =
                WebUnifiedSaveScheduler(
                    saveManager = WebSaveManager(WebSaveSections(controller).all(), YandexCloudSaveRepository(cloud)),
                    scope = scope,
                    isTokenCurrent = { controller.isSaveTokenCurrent(it) },
                )
            controller.unifiedSaveAccess = scheduler
            controller.postBindAction = { token -> scheduler.restoreAndEstablish(token) }
        }

        fun start() = controller.start()

        fun <T> guarded(
            kind: String,
            initial: T,
        ): GuardedStore<T> = GuardedStore(initial) { kind in failingSaves }
    }

    /** In-memory storage whose writes fail while [failing] says so. */
    private class GuardedStore<T>(
        var value: T,
        private val failing: () -> Boolean,
    ) {
        fun save(snapshot: T) {
            check(!failing()) { "Local storage refused the write." }
            value = snapshot
        }
    }

    private fun GuardedStore<WebCatalogProgressSnapshot>.asProgressStore() =
        object : WebCatalogProgressStore {
            override fun load() = value

            override fun save(snapshot: WebCatalogProgressSnapshot) = this@asProgressStore.save(snapshot)
        }

    private fun GuardedStore<WebCatalogStarsSnapshot>.asStarsStore() =
        object : WebCatalogStarsStore {
            override fun load() = value

            override fun save(snapshot: WebCatalogStarsSnapshot) = this@asStarsStore.save(snapshot)
        }

    private fun GuardedStore<WebStatisticsSnapshot>.asStatisticsStore() =
        object : WebStatisticsStore {
            override fun load() = value

            override fun save(snapshot: WebStatisticsSnapshot) = this@asStatisticsStore.save(snapshot)
        }

    private fun GuardedStore<WebDailySnapshotV1>.asDailyStore() =
        object : WebDailyStore {
            override fun load() = value

            override fun save(snapshot: WebDailySnapshotV1) = this@asDailyStore.save(snapshot)
        }

    private fun GuardedStore<WebEconomySnapshot>.asEconomyStore() =
        object : WebEconomyStore {
            override fun load() = value

            override fun save(snapshot: WebEconomySnapshot) = this@asEconomyStore.save(snapshot)
        }

    private fun GuardedStore<WebStoreSnapshot>.asItemStore() =
        object : WebStoreStore {
            override fun load() = value

            override fun save(snapshot: WebStoreSnapshot) = this@asItemStore.save(snapshot)
        }

    private fun GuardedStore<WebPaymentsSnapshot>.asPaymentsStore() =
        object : WebPaymentsStore {
            override fun load() = value

            override fun save(snapshot: WebPaymentsSnapshot) = this@asPaymentsStore.save(snapshot)
        }

    private class FakeCloudGateway(
        var stored: ByteArray?,
    ) : CloudSaveGateway {
        override val availability = CloudSaveAvailability.AVAILABLE
        var writes = 0

        override suspend fun read(): CloudSaveReadResult = stored?.let(CloudSaveReadResult::Found) ?: CloudSaveReadResult.Missing

        override suspend fun write(payload: ByteArray): CloudSaveWriteResult {
            writes += 1
            stored = payload
            return CloudSaveWriteResult.Saved
        }
    }

    private class FixedIdentityGateway(
        private val playerId: String,
    ) : PlayerIdentityGateway {
        override suspend fun identity(): PlayerIdentity =
            PlayerIdentity(playerId = playerId, authorizationState = PlayerAuthorizationState.ANONYMOUS, provider = "yandex-games")

        override suspend fun requestAuthorization(): PlayerAuthorizationResult = PlayerAuthorizationResult.Unsupported
    }

    private object NoPlayerContextEvents : WebPlayerContextEvents {
        override fun setAccountSelectionOpenedListener(listener: (() -> Unit)?) = Unit

        override fun setPlayerContextChangedListener(listener: (() -> Unit)?) = Unit
    }

    private class MemoryPurchaseJournal : WebPurchaseTransactionStore {
        private var stored: WebPurchaseTransaction? = null

        override fun load(): WebPurchaseTransaction? = stored

        override fun save(transaction: WebPurchaseTransaction) {
            stored = transaction
        }

        override fun clear() {
            stored = null
        }
    }

    private class MemoryPaymentsJournal : WebPaymentsJournalStore {
        private var stored: WebPendingPaymentFulfillment? = null

        override fun load(): WebPendingPaymentFulfillment? = stored

        override fun save(fulfillment: WebPendingPaymentFulfillment) {
            stored = fulfillment
        }

        override fun clear() {
            stored = null
        }
    }

    private class ExportingSection(
        override val id: String,
        private val export: ByteArray?,
    ) : WebSaveSection {
        override fun export(): ByteArray? = export

        override fun apply(payload: ByteArray) = true
    }

    private class ScriptedRepository : SaveRepository {
        var next: SaveLoadResult = SaveLoadResult.Missing
        var writes = 0
        var lastWritten: SaveData? = null

        override suspend fun load(): SaveLoadResult = next

        override suspend fun save(data: SaveData): Boolean {
            writes += 1
            lastWritten = data
            return true
        }
    }

    private companion object {
        const val INSTALLATION_ID = "browser-installation-000001"
        const val CATALOG = "catalog"
        const val STARS = "stars"
        const val STATISTICS = "statistics"
        const val DAILY = "daily"
        const val ECONOMY = "economy"
        const val STORE = "store"
        const val PAYMENTS = "payments"
    }
}
