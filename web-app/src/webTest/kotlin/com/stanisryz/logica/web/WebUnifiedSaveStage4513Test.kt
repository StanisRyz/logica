package com.stanisryz.logica.web

import com.stanisryz.logica.platform.CloudSaveAvailability
import com.stanisryz.logica.platform.CloudSaveGateway
import com.stanisryz.logica.platform.CloudSaveReadResult
import com.stanisryz.logica.platform.CloudSaveWriteResult
import com.stanisryz.logica.platform.PlayerAuthorizationResult
import com.stanisryz.logica.platform.PlayerAuthorizationState
import com.stanisryz.logica.platform.PlayerIdentity
import com.stanisryz.logica.platform.PlayerIdentityGateway
import com.stanisryz.logica.platform.PurchaseResult
import com.stanisryz.logica.platform.SaveData
import com.stanisryz.logica.platform.SaveRepository
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelNumber
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelPackVersion
import com.stanisryz.logica.puzzle.core.daily.DailyChallengePolicyV5
import com.stanisryz.logica.puzzle.core.daily.DailyDate
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Stage 45.13: the unified save becomes operational — legacy+unified migration/merge keeps the
 * domain-defined newest state and writes one canonical snapshot, Economy/Store restore stays
 * purchase-consistent across Players, and durable changes coalesce into serialized,
 * Player-context-bound unified writes.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WebUnifiedSaveStage4513Test {
    private val balance = WebCatalogProgressBucket(PuzzleType.BALANCE, Difficulty.EASY, CatalogLevelPackVersion.V1)
    private val dailyDate = DailyDate(2026, 8, 20)
    private var contextTokenValue = 42L

    // Test 1: legacy + unified migration/merge keeps domain-newest state, then converges writes.
    @Test
    fun migrationMergesLegacyLocalAndUnifiedIntoCanonicalSnapshot() =
        runTest {
            val playerA = "player/A"
            val identity = FakePlayerIdentityGateway(playerA)
            val legacyCatalogCloud = FakeCloudSaveGateway(identity)
            legacyCatalogCloud.snapshots[playerA] = catalogPayload(level = 7)
            val legacyStatisticsCloud = FakeCloudSaveGateway(identity)
            legacyStatisticsCloud.snapshots[playerA] = statisticsPayload("legacy-device-00000001", played = 7L)
            val legacyDailyCloud = FakeCloudSaveGateway(identity)
            legacyDailyCloud.snapshots[playerA] = dailyPayload(solved = PuzzleType.WORD)

            // Unified payload: older for Catalog, newer for statistics/Daily.
            val unifiedCloud = FakeCloudSaveGateway(identity)
            unifiedCloud.snapshots[playerA] =
                WebSaveCodec.encode(
                    SaveData(
                        sections =
                            mapOf(
                                WebSaveSectionIds.CATALOG to catalogSectionPayload(level = 3),
                                WebSaveSectionIds.STATISTICS to
                                    statisticsSectionPayload("unified-device-0000001", played = 9L),
                                WebSaveSectionIds.DAILY to dailySectionPayload(solved = PuzzleType.CROWNS),
                            ),
                    ),
                )

            val progressStore = FakeProgressStore(catalogSnapshot(level = 12))
            val statisticsStore =
                FakeStatisticsStore(statisticsSnapshot("browser-installation-000001", played = 12L))
            val dailyStore = FakeDailyStore(dailySnapshotValue(failed = PuzzleType.BALANCE))

            val controller =
                WebPlayerSessionController(
                    playerIdentityGateway = identity,
                    cloudSaveGateway = legacyCatalogCloud,
                    progressRepositoryFactory = { scope -> WebCatalogProgressRepository(scope, progressStore) },
                    statisticsCloudSaveGateway = legacyStatisticsCloud,
                    statisticsRepositoryFactory = { scope ->
                        WebStatisticsRepository(scope, INSTALLATION_ID, statisticsStore)
                    },
                    dailyCloudSaveGateway = legacyDailyCloud,
                    dailyRepositoryFactory = { scope -> WebDailyRepository(scope, dailyStore) { dailyDate } },
                    playerContextEvents = FakePlayerContextEvents(),
                    economyRepositoryFactory = { scope -> WebPlayerEconomyRepository(scope, FakeEconomyStore()) },
                    storeRepositoryFactory = { scope -> WebPlayerStoreRepository(scope, FakePlayerItemStore()) },
                    scope = this,
                )
            val saveManager = WebSaveManager(WebSaveSections(controller).all(), unifiedCloud.repository())
            val scheduler = WebUnifiedSaveScheduler(saveManager = saveManager, scope = this)
            controller.unifiedSaveAccess = scheduler
            controller.postBindAction = { token -> scheduler.restoreAndEstablish(token) }

            controller.start()
            advanceUntilIdle()

            // Restore merged every domain by its own semantics instead of cloud-is-authoritative.
            assertEquals(12, controller.progressRepository?.currentLevel(balance)?.value)
            assertEquals(
                28L,
                controller.statisticsRepository
                    ?.aggregate()
                    ?.totals()
                    ?.played,
            )
            val mergedDay =
                checkNotNull(
                    controller.dailyRepository
                        ?.snapshot
                        ?.value
                        ?.days
                        ?.get(dailyDate),
                )
            assertTrue(mergedDay.facts(PuzzleType.BALANCE).failedSeen)
            assertTrue(mergedDay.facts(PuzzleType.WORD).solved)
            assertTrue(mergedDay.facts(PuzzleType.CROWNS).solved)

            // A canonical unified snapshot was established from the merged state.
            assertTrue(scheduler.unifiedSaveActive)
            assertEquals(WebUnifiedSaveStatus.SYNCED, scheduler.saveStatus.value)
            val canonical = checkNotNull(WebSaveCodec.decode(unifiedCloud.snapshots.getValue(playerA)))
            assertEquals(
                12,
                checkNotNull(
                    WebCatalogProgressCodec.decode(checkNotNull(canonical.section(WebSaveSectionIds.CATALOG))),
                ).currentLevel(balance).value,
            )
            assertEquals(
                28L,
                checkNotNull(
                    WebStatisticsCodec.decode(checkNotNull(canonical.section(WebSaveSectionIds.STATISTICS))),
                ).let(WebStatisticsAggregator::aggregate).totals().played,
            )

            // After the migration boundary a durable change writes only the unified payload.
            val attempt =
                assertIs<WebCatalogLevelResolution.Resolved>(
                    WebCatalogProgressCoordinator(controller).resolveCurrentLevel(PuzzleType.BALANCE, Difficulty.EASY),
                ).attempt
            val legacyBytesBefore = legacyCatalogCloud.snapshots.getValue(playerA)
            val canonicalWritesBefore = unifiedCloud.writeCount(playerA)
            assertIs<WebCatalogCompletionState.Saved>(
                WebCatalogCompletionController(WebCatalogProgressCoordinator(controller))
                    .apply {
                        startAttempt(attempt)
                        saveSolved(attempt)
                    }.state,
            )
            advanceUntilIdle()
            // The legacy Catalog key was not rewritten after the unified save took ownership.
            assertTrue(legacyBytesBefore.contentEquals(legacyCatalogCloud.snapshots.getValue(playerA)))
            assertEquals(
                13,
                checkNotNull(
                    WebCatalogProgressCodec.decode(
                        checkNotNull(
                            WebSaveCodec
                                .decode(unifiedCloud.snapshots.getValue(playerA))
                                ?.section(WebSaveSectionIds.CATALOG),
                        ),
                    ),
                ).currentLevel(balance).value,
            )
            assertTrue(canonicalWritesBefore < unifiedCloud.writeCount(playerA))
        }

    // Test 2: coupled Economy/Store restore stays purchase-consistent; Players stay isolated.
    @Test
    fun economyAndStoreRestoreNeverSplitsAPurchaseAcrossPlayers() =
        runTest {
            // Player B purchases a hint pack: one purchase, wallet and inventory move together.
            val revisionsB = WebPlayerStateRevisions()
            val economyB =
                WebPlayerEconomyRepository(
                    WebCatalogProgressScope.STANDALONE,
                    FakeEconomyStore(startingGems = 30),
                    revisionsB,
                ).also { it.loadLocal() }
            val storeB =
                WebPlayerStoreRepository(
                    WebCatalogProgressScope.STANDALONE,
                    FakePlayerItemStore(),
                    revisionsB,
                ).also { it.loadLocal() }
            val processor = WebStoreProcessor({ economyB }, { storeB }) { 1_000L }
            assertIs<PurchaseResult.Success>(processor.purchaseById(WebStoreCatalog.ITEM_HINT_PACK))
            assertEquals(20, economyB.currentSnapshot.gems)
            assertEquals(3, storeB.snapshot.value.quantityOf(STORE_INVENTORY_HINTS))

            val cloudEconomy = economyB.currentSnapshot
            val cloudStore = storeB.snapshot.value
            assertTrue(cloudEconomy.revision > 0 && cloudStore.revision > 0)

            // Player A earned gems locally after B's purchase happened on another device.
            val revisionsA = WebPlayerStateRevisions()
            val economyA =
                WebPlayerEconomyRepository(
                    WebCatalogProgressScope.STANDALONE,
                    FakeEconomyStore(startingGems = 0),
                    revisionsA,
                ).also { it.loadLocal() }
            val storeA =
                WebPlayerStoreRepository(
                    WebCatalogProgressScope.STANDALONE,
                    FakePlayerItemStore(),
                    revisionsA,
                ).also { it.loadLocal() }
            assertTrue(economyA.addGems(5))
            assertTrue(
                maxOf(economyA.currentSnapshot.revision, storeA.snapshot.value.revision) <
                    maxOf(cloudEconomy.revision, cloudStore.revision),
            )

            // Whole-pair adoption: gems deducted AND hints granted — never a mixed state.
            val decision =
                WebEconomyStoreCoupledRestore.resolve(
                    localEconomy = economyA.currentSnapshot,
                    localStore = storeA.snapshot.value,
                    cloudEconomy = cloudEconomy,
                    cloudStore = cloudStore,
                )
            decision.economy?.let(economyA::applyExternal)
            decision.store?.let(storeA::applyExternal)
            assertEquals(20, economyA.currentSnapshot.gems)
            assertEquals(3, storeA.snapshot.value.quantityOf(STORE_INVENTORY_HINTS))

            // Tied local recency keeps local wholesale; partial payloads never mix sides.
            assertNull(
                WebEconomyStoreCoupledRestore
                    .resolve(economyA.currentSnapshot, storeA.snapshot.value, cloudEconomy, cloudStore)
                    .economy,
            )
            assertNull(
                WebEconomyStoreCoupledRestore
                    .resolve(economyA.currentSnapshot, storeA.snapshot.value, cloudEconomy, null)
                    .store,
            )

            // Player scoping is structural: applying A's decision never touched B's repositories.
            assertEquals(20, economyB.currentSnapshot.gems)
            assertEquals(3, storeB.snapshot.value.quantityOf(STORE_INVENTORY_HINTS))

            // And per-Player cloud keys remain isolated end to end.
            val storageA = mutableMapOf<String, String>()
            val storageOther = mutableMapOf<String, String>()
            val repoA = LocalSaveRepository("save_a", { storageA["k"] }, { _, v -> storageA["k"] = v })
            val repoOther = LocalSaveRepository("save_b", { storageOther["k"] }, { _, v -> storageOther["k"] = v })
            assertTrue(repoA.save(SaveData(sections = mapOf("x" to byteArrayOf(1)))))
            assertNull(repoOther.load())
        }

    // Test 3: durable changes coalesce; stale Player contexts and account switches never write.
    @Test
    fun durableChangesCoalesceAndStaleContextsNeverWrite() =
        runTest {
            var generation = 0
            val section =
                object : WebSaveSection {
                    override val id = "probe"

                    override fun export(): ByteArray = byteArrayOf(generation.toByte())

                    override fun apply(payload: ByteArray) = Unit
                }
            val repository = RecordingSaveRepository()
            val scheduler =
                WebUnifiedSaveScheduler(
                    saveManager = WebSaveManager(listOf(section), repository),
                    scope = this,
                    isTokenCurrent = { it.value == contextTokenValue },
                    debounceMs = 100L,
                )
            // Establishment writes exactly one canonical snapshot.
            scheduler.restoreAndEstablish(WebPlayerContextToken(contextTokenValue))
            assertEquals(1, repository.writes)

            // Five rapid durable changes coalesce into one full-envelope write of the newest data.
            repeat(5) {
                generation += 1
                scheduler.markDirty()
            }
            advanceUntilIdle()
            assertEquals(2, repository.writes)
            assertEquals(generation.toByte(), repository.lastSections.getValue("probe").single())
            assertEquals(WebUnifiedSaveStatus.SYNCED, scheduler.saveStatus.value)

            // A stale Player context token can never schedule a write.
            contextTokenValue = 999L
            generation += 1
            scheduler.markDirty()
            advanceUntilIdle()
            assertEquals(2, repository.writes)

            // An account switch drops everything pending mid-flight.
            contextTokenValue = 42L
            generation += 1
            scheduler.markDirty()
            scheduler.invalidateContext()
            advanceUntilIdle()
            assertEquals(2, repository.writes)
            assertFalse(scheduler.unifiedSaveActive)

            // A change arriving during an in-flight write marks dirty; the newest snapshot wins.
            scheduler.restoreAndEstablish(WebPlayerContextToken(contextTokenValue))
            assertEquals(3, repository.writes)
            repository.writeDelayMs = 100L
            generation = 10
            scheduler.markDirty()
            advanceTimeBy(150)
            runCurrent()
            generation = 11
            scheduler.markDirty()
            advanceUntilIdle()
            assertEquals(11.toByte(), repository.lastSections.getValue("probe").single())
            assertEquals(WebUnifiedSaveStatus.SYNCED, scheduler.saveStatus.value)
        }

    private fun catalogSnapshot(level: Int): WebCatalogProgressSnapshot =
        WebCatalogProgressSnapshot(levels = mapOf(balance to CatalogLevelNumber(level)))

    private fun catalogPayload(level: Int): ByteArray = WebCatalogProgressCodec.encode(catalogSnapshot(level))

    private fun catalogSectionPayload(level: Int): ByteArray = catalogPayload(level)

    private fun statisticsSnapshot(
        deviceId: String,
        played: Long,
    ): WebStatisticsSnapshot =
        WebStatisticsSnapshot(
            components =
                mapOf(
                    deviceId to
                        WebStatisticsDeviceComponent(
                            buckets =
                                mapOf(
                                    WebStatisticsBucket(PuzzleType.BALANCE, Difficulty.EASY) to
                                        WebStatisticsCounters(played = played),
                                ),
                        ),
                ),
        )

    private fun statisticsPayload(
        deviceId: String,
        played: Long,
    ): ByteArray = WebStatisticsCodec.encode(statisticsSnapshot(deviceId, played))

    private fun statisticsSectionPayload(
        deviceId: String,
        played: Long,
    ): ByteArray = statisticsPayload(deviceId, played)

    private fun dailySnapshotValue(
        failed: PuzzleType? = null,
        solved: PuzzleType? = null,
    ): WebDailySnapshotV1 {
        val record =
            WebDailyDayRecord(
                date = dailyDate,
                policyVersion = DailyChallengePolicyV5.VERSION,
                failedMask = failed?.let(WebDailyPuzzleOrder::bit) ?: 0,
                solvedMask = solved?.let(WebDailyPuzzleOrder::bit) ?: 0,
            )
        return WebDailySnapshotV1(days = mapOf(dailyDate to record))
    }

    private fun dailyPayload(solved: PuzzleType): ByteArray = WebDailyCodec.encode(dailySnapshotValue(solved = solved))

    private fun dailySectionPayload(solved: PuzzleType): ByteArray = dailyPayload(solved)

    private fun FakeCloudSaveGateway.repository(): SaveRepository =
        object : SaveRepository {
            override suspend fun load(): SaveData? =
                when (val result = read()) {
                    is CloudSaveReadResult.Found -> WebSaveCodec.decode(result.payload)
                    else -> null
                }

            override suspend fun save(data: SaveData): Boolean = write(WebSaveCodec.encode(data)) == CloudSaveWriteResult.Saved
        }

    private class RecordingSaveRepository : SaveRepository {
        var writes = 0
        var writeDelayMs = 0L
        var lastSections: Map<String, ByteArray> = emptyMap()

        override suspend fun load(): SaveData? = null

        override suspend fun save(data: SaveData): Boolean {
            if (writeDelayMs > 0) delay(writeDelayMs)
            writes += 1
            lastSections = data.sections
            return true
        }
    }

    private class FakePlayerIdentityGateway(
        val playerId: String,
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
        private val writesPerPlayer = mutableMapOf<String, Int>()

        override suspend fun read(): CloudSaveReadResult =
            snapshots[identity.playerId]?.let(CloudSaveReadResult::Found) ?: CloudSaveReadResult.Missing

        override suspend fun write(payload: ByteArray): CloudSaveWriteResult {
            snapshots[identity.playerId] = payload
            writesPerPlayer[identity.playerId] = (writesPerPlayer[identity.playerId] ?: 0) + 1
            return CloudSaveWriteResult.Saved
        }

        fun writeCount(playerId: String): Int = writesPerPlayer[playerId] ?: 0
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

    private class FakeEconomyStore(
        startingGems: Int = -1,
    ) : WebEconomyStore {
        private var snapshot =
            if (startingGems >= 0) WebEconomySnapshot.DEFAULT.copy(gems = startingGems) else WebEconomySnapshot.DEFAULT

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

    private companion object {
        const val INSTALLATION_ID = "browser-installation-000001"
    }
}
