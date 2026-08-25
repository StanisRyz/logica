@file:OptIn(ExperimentalWasmJsInterop::class)

package com.stanisryz.logica.web

import androidx.compose.ui.ExperimentalComposeUiApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import androidx.compose.ui.window.ComposeViewport
import kotlin.js.ExperimentalWasmJsInterop

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    val lifecycle = WebHostLifecycle()
    val bridge = YandexGamesBridge()
    val controller =
        WebBootstrapController(
            bridge = bridge,
            puzzleDataLoader = BrowserPuzzleDataLoader(),
            lifecycle = lifecycle,
        )
    val progressRepositoryFactory =
        WebCatalogProgressRepositoryFactory { scope ->
            WebCatalogProgressRepository(
                scope = scope,
                localStore = WebCatalogProgressLocalStore(scope),
            )
        }
    val installationIdProvider = WebInstallationIdProvider()
    val statisticsRepositoryFactory =
        WebStatisticsRepositoryFactory { scope ->
            WebStatisticsRepository(
                scope = scope,
                installationId = installationIdProvider.getOrCreate(),
                localStore = WebStatisticsLocalStore(scope),
            )
        }
    val dailyRepositoryFactory =
        WebDailyRepositoryFactory { scope ->
            WebDailyRepository(
                scope = scope,
                localStore = WebDailyLocalStore(scope),
                dateProvider = BrowserLocalWebDailyDateProvider,
            )
        }
    val playerSession =
        if (bridge.isAvailable) {
            WebPlayerSessionController(
                playerIdentityGateway = YandexPlayerIdentityGateway(bridge),
                cloudSaveGateway = YandexCloudSaveGateway(bridge),
                progressRepositoryFactory = progressRepositoryFactory,
                statisticsCloudSaveGateway =
                    YandexCloudSaveGateway(
                        bridge,
                        dataKey = YandexCloudSaveGateway.STATISTICS_STATE_KEY,
                    ),
                statisticsRepositoryFactory = statisticsRepositoryFactory,
                dailyCloudSaveGateway =
                    YandexCloudSaveGateway(
                        bridge,
                        dataKey = YandexCloudSaveGateway.DAILY_STATE_KEY,
                    ),
                dailyRepositoryFactory = dailyRepositoryFactory,
                playerContextEvents = bridge,
            )
        } else {
            WebPlayerSessionController(
                playerIdentityGateway = UnsupportedWebPlayerIdentityGateway,
                cloudSaveGateway = UnsupportedWebCloudSaveGateway,
                progressRepositoryFactory = progressRepositoryFactory,
                statisticsCloudSaveGateway = UnsupportedWebCloudSaveGateway,
                statisticsRepositoryFactory = statisticsRepositoryFactory,
                dailyCloudSaveGateway = UnsupportedWebCloudSaveGateway,
                dailyRepositoryFactory = dailyRepositoryFactory,
                playerContextEvents = bridge,
            )
        }
    val progressCoordinator = WebCatalogProgressCoordinator(playerSession)
    val statisticsCoordinator = WebGameplayStatisticsCoordinator(playerSession)
    val dailyCoordinator = WebDailyGameplayCoordinator(playerSession)
    val economyCoordinator = WebGameplayEconomyCoordinator(playerSession)
    val storeCoordinator = WebGameplayStoreCoordinator(playerSession)
    val storeProcessor =
        WebStoreProcessor(
            economyRepository = { playerSession.economyRepository },
            storeRepository = { playerSession.storeRepository },
            currentTimeMs = ::currentTimeMillis,
            revisionsProvider = { playerSession.activeStateRevisions },
            transactionStoreProvider = { playerSession.purchaseTransactionStore },
        )
    // Real Yandex ad product flows over the existing monetization foundation. Without the SDK
    // every provider degrades safely: rewarded -> Unavailable, interstitial -> skip+continue,
    // sticky banner -> no-op, so standalone mode keeps working unchanged.
    val monetizationAnalytics = WebMonetizationAnalytics()
    val adPolicy = WebAdPolicy()
    val adRewardService =
        WebRewardService(
            economyRepository = { playerSession.economyRepository },
            storeRepository = { playerSession.storeRepository },
        )
    // Fullscreen ads participate in the real effective lifecycle: WebHostLifecycle itself owns
    // the suppression flag, so no competing lifecycle and no dependency cycle can exist.
    val rewardedHintsController =
        WebStoreRewardedHintsController(
            provider = YandexRewardedAdProvider(bridge),
            policy = adPolicy,
            rewardService = adRewardService,
            analytics = monetizationAnalytics,
            fullscreenAdActivity = lifecycle,
            currentPlayerContext = { playerSession.currentPlayerContextToken() },
            currentTimeMs = ::currentTimeMillis,
        )
    val interstitialController =
        WebInterstitialContinuationController(
            provider = YandexInterstitialAdProvider(bridge),
            policy = adPolicy,
            analytics = monetizationAnalytics,
            fullscreenAdActivity = lifecycle,
            currentTimeMs = ::currentTimeMillis,
        )
    val stickyBannerController = WebStickyBannerController(bridge)
    // Real-money consumable pipeline (client-side Yandex Payments flow).
    val paymentsCoordinator =
        WebPaymentsCoordinator(
            provider = YandexPaymentsProvider(bridge),
            economyRepository = { playerSession.economyRepository },
            paymentsRepository = { playerSession.paymentsRepository },
            journalStore = { playerSession.paymentsJournalStore },
            revisions = { playerSession.activeStateRevisions },
            unifiedSaveAccess = { playerSession.unifiedSaveAccess },
            currentPlayerContext = { playerSession.currentPlayerContextToken() },
            scope = rememberMainScope(),
        )
    playerSession.pendingPaymentsRecoveryAction = { paymentsCoordinator.recoverPendingFulfillment() }
    playerSession.postRestoreAction = { paymentsCoordinator.reconcilePendingPurchases() }
    // Unified cloud save foundation: identity through the SDK, payload over a dedicated key.
    // Standalone development keeps the unified orchestration fully local and isolated.
    val playerProvider =
        YandexPlayerProvider(
            if (bridge.isAvailable) {
                YandexPlayerIdentityGateway(bridge)
            } else {
                UnsupportedWebPlayerIdentityGateway
            },
        )
    val unifiedSaveRepository =
        if (bridge.isAvailable) {
            YandexCloudSaveRepository(
                YandexCloudSaveGateway(bridge, dataKey = UNIFIED_SAVE_STATE_KEY),
            )
        } else {
            LocalSaveRepository(
                STANDALONE_UNIFIED_SAVE_KEY,
                ::standaloneStorageGet,
                ::standaloneStorageSet,
            )
        }
    val saveManager = WebSaveManager(WebSaveSections(playerSession).all(), unifiedSaveRepository)
    val saveScheduler =
        WebUnifiedSaveScheduler(
            saveManager = saveManager,
            isTokenCurrent = { token -> playerSession.isSaveTokenCurrent(token) },
        )
    playerSession.unifiedSaveAccess = saveScheduler
    // Migration + canonical write happen once per bound Player context, after all legacy
    // per-domain cloud merges; afterwards durable changes coalesce into unified writes only.
    playerSession.postBindAction = { token -> saveScheduler.restoreAndEstablish(token) }
    val balanceController =
        WebBalanceController.create(
            controller.puzzleDataLoader,
            progressCoordinator,
            statisticsCoordinator,
            dailyCoordinator,
            economyCoordinator,
        )
    val crownsController =
        WebCrownsController.create(
            controller.puzzleDataLoader,
            progressCoordinator,
            statisticsCoordinator,
            dailyCoordinator,
            economyCoordinator,
        )
    val wordController =
        WebWordController.create(
            controller.puzzleDataLoader,
            progressCoordinator,
            statisticsCoordinator,
            dailyCoordinator,
            economyCoordinator,
        )
    val sudokuController =
        WebSudokuController.create(
            controller.puzzleDataLoader,
            progressCoordinator,
            statisticsCoordinator,
            dailyCoordinator,
            economyCoordinator,
            storeCoordinator,
        )
    val game2048Controller =
        Web2048Controller.create(
            controller.puzzleDataLoader,
            progressCoordinator,
            statisticsCoordinator,
            dailyCoordinator,
            economyCoordinator,
        )

    ComposeViewport {
        WebApp(
            controller,
            balanceController,
            crownsController,
            wordController,
            sudokuController,
            game2048Controller,
            lifecycle,
            playerSession,
            dailyCoordinator,
            storeProcessor,
            paymentsCoordinator,
            rewardedHintsController,
            interstitialController,
            stickyBannerController,
        )
    }
    controller.start()
}

private fun currentTimeMillis(): Long = js("Date.now()")

private fun standaloneStorageGet(key: String): String? = js("globalThis.localStorage.getItem(key)")

private fun standaloneStorageSet(
    key: String,
    value: String,
) {
    js("globalThis.localStorage.setItem(key, value)")
}

private const val UNIFIED_SAVE_STATE_KEY = "logica_unified_save_v1"

/** Isolated browser-local key; standalone data never migrates into a real Yandex account. */
private const val STANDALONE_UNIFIED_SAVE_KEY = "logica_unified_save_standalone_v1"

private fun rememberMainScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
