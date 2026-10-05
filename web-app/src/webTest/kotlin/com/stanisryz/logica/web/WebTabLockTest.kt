package com.stanisryz.logica.web

import com.stanisryz.logica.platform.SaveData
import com.stanisryz.logica.platform.SaveLoadResult
import com.stanisryz.logica.platform.SaveRepository
import com.stanisryz.logica.puzzle.core.daily.DailyDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Stage 2.1: one active game tab per origin, handed over by «Play here». */
@OptIn(ExperimentalCoroutinesApi::class)
class WebTabLockTest {
    /** One origin: a single lock with a FIFO wait queue and a broadcast channel shared by its tabs. */
    private class FakeOrigin(
        val supported: Boolean = true,
    ) {
        var holder: FakeTab? = null
        val waiting = ArrayDeque<Pair<FakeTab, () -> Unit>>()
        val tabs = mutableListOf<FakeTab>()

        fun tab(): FakeTab = FakeTab(this).also { tabs += it }
    }

    private class FakeTab(
        private val origin: FakeOrigin,
    ) : WebTabLockPlatform {
        private var listener: (() -> Unit)? = null
        override val supported: Boolean get() = origin.supported

        override fun request(
            ifAvailable: Boolean,
            onGranted: () -> Unit,
            onUnavailable: () -> Unit,
            onError: () -> Unit,
        ) {
            when {
                origin.holder == null -> {
                    origin.holder = this
                    onGranted()
                }
                ifAvailable -> onUnavailable()
                else -> origin.waiting.addLast(this to onGranted)
            }
        }

        override fun release() {
            if (origin.holder != this) return
            origin.holder = null
            origin.waiting.removeFirstOrNull()?.let { (tab, granted) ->
                origin.holder = tab
                granted()
            }
        }

        override fun postHandoverRequest() {
            origin.tabs.filter { it !== this }.forEach { it.listener?.invoke() }
        }

        override fun setHandoverListener(listener: (() -> Unit)?) {
            this.listener = listener
        }
    }

    /** One browser tab: its lock and its Player session, wired like `Main.kt`. */
    private class Tab(
        scope: TestScope,
        origin: FakeOrigin,
        val economyStore: MemoryEconomyStore,
    ) {
        var economyLoads = 0
        var cloudWrites = 0
        val session: WebPlayerSessionController = standaloneSession(scope, economyStore) { economyLoads++ }
        val unified =
            WebUnifiedSaveScheduler(
                saveManager =
                    WebSaveManager(
                        WebSaveSections(session).all(),
                        object : SaveRepository {
                            override suspend fun load() = SaveLoadResult.Missing

                            override suspend fun save(data: SaveData): Boolean {
                                cloudWrites += 1
                                return true
                            }
                        },
                    ),
                scope = scope,
                isTokenCurrent = { session.isSaveTokenCurrent(it) },
            )
        val lock =
            WebTabLock(
                platform = origin.tab(),
                onRelinquish = { session.relinquish() },
                onReclaim = { session.reclaim() },
            )

        init {
            session.unifiedSaveAccess = unified
            session.postBindAction = { token -> unified.restoreAndEstablish(token) }
            session.bindingAllowed = { lock.state.value.canPlay }
            lock.start()
        }
    }

    @Test
    fun aSecondTabNeitherBindsNorWrites() =
        runTest {
            val origin = FakeOrigin()
            val shared = MemoryEconomyStore()
            val first = Tab(this, origin, shared)
            val second = Tab(this, origin, shared)
            assertEquals(WebTabLockState.ACTIVE, first.lock.state.value)
            assertEquals(WebTabLockState.ELSEWHERE, second.lock.state.value)

            first.session.start()
            second.session.start() // even a start that slipped through binds nothing
            advanceUntilIdle()
            assertIs<WebEconomyBinding.Ready>(first.session.economyBinding.value)
            assertIs<WebEconomyBinding.Loading>(second.session.economyBinding.value)
            assertEquals(1, first.economyLoads)
            assertEquals(0, second.economyLoads)
            assertNull(second.session.economyRepository)
        }

    @Test
    fun playHereHandsTheGameOverAndTheOldTabStopsWriting() =
        runTest {
            val origin = FakeOrigin()
            val shared = MemoryEconomyStore()
            val first = Tab(this, origin, shared)
            val second = Tab(this, origin, shared)
            first.session.start()
            advanceUntilIdle()
            val oldEconomy = checkNotNull(first.session.economyRepository)
            val oldToken = checkNotNull(first.session.currentPlayerContextToken())
            assertEquals(1, first.cloudWrites) // establishment

            // A durable change schedules a deferred cloud write; the handover cancels it first.
            assertTrue(oldEconomy.spendGems(1))
            second.lock.playHere()
            assertEquals(WebTabLockState.ELSEWHERE, first.lock.state.value)
            assertEquals(WebTabLockState.ACTIVE, second.lock.state.value)
            // The old tab dropped its context like on an account switch: no repositories, stale tokens.
            assertNull(first.session.economyRepository)
            assertFalse(first.session.isSaveTokenCurrent(oldToken))
            assertIs<WebEconomyBinding.Loading>(first.session.economyBinding.value)

            second.session.start()
            advanceUntilIdle()
            assertIs<WebEconomyBinding.Ready>(second.session.economyBinding.value)
            assertTrue(checkNotNull(second.session.economyRepository).spendGems(3))
            assertEquals(oldEconomy.currentSnapshot.gems - 3, shared.snapshot.gems)
            assertEquals(1, first.cloudWrites)
            // Nothing in the old tab reaches a Player context any more.
            assertNull(first.session.economyRepository)
            assertFalse(first.session.isSaveTokenCurrent(oldToken))

            // And the game can move back the same way.
            first.lock.playHere()
            advanceUntilIdle()
            assertEquals(WebTabLockState.ACTIVE, first.lock.state.value)
            assertEquals(WebTabLockState.ELSEWHERE, second.lock.state.value)
            assertIs<WebEconomyBinding.Ready>(first.session.economyBinding.value)
            assertNull(second.session.economyRepository)
        }

    @Test
    fun withoutWebLocksEveryTabPlaysAsBefore() =
        runTest {
            val origin = FakeOrigin(supported = false)
            val shared = MemoryEconomyStore()
            val first = Tab(this, origin, shared)
            val second = Tab(this, origin, shared)
            assertEquals(WebTabLockState.UNSUPPORTED, first.lock.state.value)
            assertEquals(WebTabLockState.UNSUPPORTED, second.lock.state.value)
            first.session.start()
            second.session.start()
            advanceUntilIdle()
            assertIs<WebEconomyBinding.Ready>(first.session.economyBinding.value)
            assertIs<WebEconomyBinding.Ready>(second.session.economyBinding.value)
        }

    private companion object {
        fun standaloneSession(
            scope: TestScope,
            economyStore: MemoryEconomyStore,
            onEconomyLoad: () -> Unit,
        ): WebPlayerSessionController {
            lateinit var session: WebPlayerSessionController
            session =
                WebPlayerSessionController(
                    playerIdentityGateway = UnsupportedWebPlayerIdentityGateway,
                    cloudSaveGateway = UnsupportedWebCloudSaveGateway,
                    progressRepositoryFactory = { s ->
                        WebCatalogProgressRepository(
                            s,
                            object : WebCatalogProgressStore {
                                override fun load() = WebCatalogProgressSnapshot.EMPTY

                                override fun save(snapshot: WebCatalogProgressSnapshot) = Unit
                            },
                        )
                    },
                    statisticsCloudSaveGateway = UnsupportedWebCloudSaveGateway,
                    statisticsRepositoryFactory = { s ->
                        WebStatisticsRepository(
                            s,
                            "browser-installation-000001",
                            object : WebStatisticsStore {
                                override fun load() = WebStatisticsSnapshot.EMPTY

                                override fun save(snapshot: WebStatisticsSnapshot) = Unit
                            },
                        )
                    },
                    dailyCloudSaveGateway = UnsupportedWebCloudSaveGateway,
                    dailyRepositoryFactory = { s ->
                        WebDailyRepository(
                            s,
                            object : WebDailyStore {
                                override fun load() = WebDailySnapshotV1.EMPTY

                                override fun save(snapshot: WebDailySnapshotV1) = Unit
                            },
                        ) { DailyDate(2026, 10, 5) }
                    },
                    playerContextEvents =
                        object : WebPlayerContextEvents {
                            override fun setAccountSelectionOpenedListener(listener: (() -> Unit)?) = Unit

                            override fun setPlayerContextChangedListener(listener: (() -> Unit)?) = Unit
                        },
                    economyRepositoryFactory = { s ->
                        onEconomyLoad()
                        WebPlayerEconomyRepository(s, economyStore, session.activeStateRevisions)
                    },
                    storeRepositoryFactory = { s -> WebPlayerStoreRepository(s, MemoryItemStore(), session.activeStateRevisions) },
                    purchaseTransactionStoreFactory = { MemoryTransactionJournal() },
                    paymentsRepositoryFactory = { s -> WebPlayerPaymentsRepository(s, MemoryPaymentsStore()) },
                    paymentsJournalStoreFactory = { MemoryFulfillmentJournal() },
                    scope = scope,
                )
            return session
        }
    }
}
