package com.stanisryz.logica.web

import com.stanisryz.logica.platform.PlayerAuthorizationResult
import com.stanisryz.logica.platform.PlayerAuthorizationState
import com.stanisryz.logica.platform.PlayerIdentity
import com.stanisryz.logica.platform.PlayerIdentityGateway
import com.stanisryz.logica.puzzle.core.daily.DailyDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** Stage 2.2: SDK startup never hangs and a failed LoadingAPI.ready() is not fatal. */
@OptIn(ExperimentalCoroutinesApi::class)
class WebStartupTest {
    private class FakeBridge : WebSdkBootstrapBridge {
        var answerInit = false
        var readyError: String? = null
        var onReady: (() -> Unit)? = null
        override val isAvailable = true
        override var isReady = false

        override fun initialize(
            lifecycleListener: YandexLifecycleListener,
            onReady: () -> Unit,
            onFailure: (String) -> Unit,
        ) {
            this.onReady = {
                isReady = true
                onReady()
            }
            if (answerInit) this.onReady?.invoke()
        }

        override fun platformLanguage(): String? = "ru"

        override fun serverTimeMs(): Long? = null

        override fun notifyLoadingReady(): String? = readyError

        override fun setGameplayActive(active: Boolean): String? = null

        override fun dispose() = Unit
    }

    private object FakeLifecycle : WebBootstrapLifecycle {
        override fun start() = Unit

        override fun dispose() = Unit

        override fun onPause() = Unit

        override fun onResume() = Unit
    }

    @Test
    fun anInitThatNeverAnswersEndsOnARetryableErrorScreen() =
        runTest {
            val bridge = FakeBridge()
            var reloads = 0
            val controller =
                WebBootstrapController(bridge, BrowserPuzzleDataLoader(), FakeLifecycle, scope = this, reloadPage = { reloads++ })
            controller.start()
            advanceTimeBy(14_000)
            runCurrent()
            assertEquals(WebBootstrapState.Loading, controller.state)
            advanceTimeBy(1_500)
            runCurrent()
            assertEquals(WebBootstrapState.TimedOut, controller.state)

            controller.retryInitialization()
            assertEquals(1, reloads)

            // An init that answers late still opens the game.
            bridge.onReady?.invoke()
            assertIs<WebBootstrapState.Ready>(controller.state)
        }

    @Test
    fun aFailedLoadingReadyIsNotFatal() =
        runTest {
            val bridge =
                FakeBridge().apply {
                    answerInit = true
                    readyError = "LoadingAPI.ready() threw"
                }
            val controller = WebBootstrapController(bridge, BrowserPuzzleDataLoader(), FakeLifecycle, scope = this)
            controller.start()
            controller.onComposeRootRendered()
            controller.onInitialHostUiReady()
            advanceUntilIdle()
            assertIs<WebBootstrapState.Ready>(controller.state)
        }

    @Test
    fun aPlayerThatNeverAnswersEndsUnavailableAndRetryStillWorks() =
        runTest {
            var hang = true
            val identity =
                object : PlayerIdentityGateway {
                    override suspend fun identity(): PlayerIdentity {
                        if (hang) awaitCancellation()
                        return PlayerIdentity(
                            playerId = "player/A",
                            authorizationState = PlayerAuthorizationState.ANONYMOUS,
                            provider = "yandex-games",
                        )
                    }

                    override suspend fun requestAuthorization(): PlayerAuthorizationResult = PlayerAuthorizationResult.Unsupported
                }
            val session =
                WebPlayerSessionController(
                    playerIdentityGateway = identity,
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
                    economyRepositoryFactory = { s -> WebPlayerEconomyRepository(s, MemoryEconomyStore()) },
                    storeRepositoryFactory = { s -> WebPlayerStoreRepository(s, MemoryItemStore()) },
                    purchaseTransactionStoreFactory = { MemoryTransactionJournal() },
                    paymentsRepositoryFactory = { s -> WebPlayerPaymentsRepository(s, MemoryPaymentsStore()) },
                    paymentsJournalStoreFactory = { MemoryFulfillmentJournal() },
                    scope = this,
                )
            session.start()
            advanceTimeBy(10_500)
            runCurrent()
            assertIs<WebCatalogProgressBinding.Unavailable>(session.progressBinding.value)

            hang = false
            session.retryCurrentContext()
            advanceUntilIdle()
            assertIs<WebCatalogProgressBinding.Ready>(session.progressBinding.value)
        }
}
