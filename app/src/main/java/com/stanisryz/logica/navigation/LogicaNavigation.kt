package com.stanisryz.logica.navigation

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.stanisryz.logica.R
import com.stanisryz.logica.balance.BalanceGameLaunch
import com.stanisryz.logica.crowns.CrownsGameLaunch
import com.stanisryz.logica.daily.DailyChallengeRepository
import com.stanisryz.logica.daily.DailyGameLaunch
import com.stanisryz.logica.puzzle.core.model.PuzzleSeed
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import com.stanisryz.logica.result.GameCompletionRepository
import com.stanisryz.logica.session.GameSessionRepository
import com.stanisryz.logica.settings.SettingsRepository
import com.stanisryz.logica.settings.ThemeMode
import com.stanisryz.logica.settings.UserSettings
import com.stanisryz.logica.statistics.StatisticsRepository
import com.stanisryz.logica.ui.screens.BalanceGameRoute
import com.stanisryz.logica.ui.screens.BalanceStartScreen
import com.stanisryz.logica.ui.screens.BalanceTutorialRoute
import com.stanisryz.logica.ui.screens.CatalogPuzzleCard
import com.stanisryz.logica.ui.screens.CatalogScreen
import com.stanisryz.logica.ui.screens.CrownsGameRoute
import com.stanisryz.logica.ui.screens.CrownsStartScreen
import com.stanisryz.logica.ui.screens.CrownsTutorialRoute
import com.stanisryz.logica.ui.screens.SettingsScreen
import com.stanisryz.logica.ui.screens.StatisticsRoute
import com.stanisryz.logica.ui.screens.TodayRoute
import java.security.SecureRandom

private sealed interface AppDestination {
    data object Today : AppDestination

    data object Catalog : AppDestination

    data object Statistics : AppDestination

    data object Settings : AppDestination

    data object BalanceStart : AppDestination

    data object BalanceTutorial : AppDestination

    data object CrownsStart : AppDestination

    data object CrownsTutorial : AppDestination

    data class BalanceGame(
        val launch: BalanceGameLaunch,
    ) : AppDestination

    data class CrownsGame(
        val launch: CrownsGameLaunch,
    ) : AppDestination
}

private val primaryDestinations = listOf(AppDestination.Today, AppDestination.Catalog, AppDestination.Statistics)

@Composable
internal fun LogicaNavigation(
    settings: UserSettings,
    settingsRepository: SettingsRepository,
    gameSessionRepository: GameSessionRepository,
    gameCompletionRepository: GameCompletionRepository,
    dailyChallengeRepository: DailyChallengeRepository,
    statisticsRepository: StatisticsRepository,
    hasActiveBalanceSession: Boolean,
    hasActiveCrownsSession: Boolean,
    onThemeModeChanged: (ThemeMode) -> Unit,
    onSoundEnabledChanged: (Boolean) -> Unit,
    onHapticsEnabledChanged: (Boolean) -> Unit,
    onCrownsTutorialCompleted: (Boolean) -> Unit,
) {
    val backStack = remember { mutableStateListOf<AppDestination>(AppDestination.Today) }
    val catalogSeedSource = remember { CatalogSeedSource() }
    val currentDestination = backStack.last()
    val isPrimaryDestination = currentDestination in primaryDestinations

    Scaffold(
        topBar = {
            AppTopBar(
                destination = currentDestination,
                onBack = { backStack.removeLastOrNull() },
                onOpenSettings = { if (isPrimaryDestination) backStack.add(AppDestination.Settings) },
            )
        },
        bottomBar = {
            if (isPrimaryDestination) {
                AppBottomBar(currentDestination) { destination ->
                    backStack.clear()
                    backStack.add(destination)
                }
            }
        },
    ) { contentPadding ->
        NavDisplay(
            backStack = backStack,
            modifier = Modifier.padding(contentPadding),
            onBack = { backStack.removeLastOrNull() },
            entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator(), rememberViewModelStoreNavEntryDecorator()),
            entryProvider =
                entryProvider {
                    entry<AppDestination.Today> {
                        TodayRoute(
                            dailyChallengeRepository = dailyChallengeRepository,
                            gameSessionRepository = gameSessionRepository,
                            statisticsRepository = statisticsRepository,
                            balanceTutorialCompleted = settings.balanceTutorialCompleted,
                            crownsTutorialCompleted = settings.crownsTutorialCompleted,
                            onOpenDaily = { dailyLaunch ->
                                backStack.add(
                                    when (dailyLaunch) {
                                        is DailyGameLaunch.Balance -> AppDestination.BalanceGame(dailyLaunch.launch)
                                        is DailyGameLaunch.Crowns -> AppDestination.CrownsGame(dailyLaunch.launch)
                                    },
                                )
                            },
                            onOpenTutorial = { puzzleType ->
                                when (puzzleType) {
                                    PuzzleType.BALANCE -> backStack.add(AppDestination.BalanceTutorial)
                                    PuzzleType.CROWNS -> {
                                        onCrownsTutorialCompleted(true)
                                        backStack.add(AppDestination.CrownsTutorial)
                                    }
                                    else -> Unit
                                }
                            },
                        )
                    }
                    entry<AppDestination.Catalog> {
                        CatalogScreen(
                            puzzles =
                                listOf(
                                    CatalogPuzzleCard(
                                        titleResource = R.string.balance,
                                        descriptionResource = R.string.balance_catalog_description,
                                        hasActiveSession = hasActiveBalanceSession,
                                        onContinue = { backStack.add(AppDestination.BalanceGame(BalanceGameLaunch.Restore())) },
                                        onNew = { backStack.add(AppDestination.BalanceStart) },
                                    ),
                                    CatalogPuzzleCard(
                                        titleResource = R.string.crowns,
                                        descriptionResource = R.string.crowns_catalog_description,
                                        hasActiveSession = hasActiveCrownsSession,
                                        onContinue = { backStack.add(AppDestination.CrownsGame(CrownsGameLaunch.Restore())) },
                                        onNew = { backStack.add(AppDestination.CrownsStart) },
                                    ),
                                ),
                        )
                    }
                    entry<AppDestination.Statistics> { StatisticsRoute(statisticsRepository) }
                    entry<AppDestination.Settings> {
                        SettingsScreen(settings, onThemeModeChanged, onSoundEnabledChanged, onHapticsEnabledChanged)
                    }
                    entry<AppDestination.BalanceStart> {
                        BalanceStartScreen(
                            hasActiveSession = hasActiveBalanceSession,
                            tutorialCompleted = settings.balanceTutorialCompleted,
                            onOpenTutorial = { backStack.add(AppDestination.BalanceTutorial) },
                            onStart = { difficulty ->
                                backStack.add(AppDestination.BalanceGame(BalanceGameLaunch.New(difficulty, catalogSeedSource.nextSeed())))
                            },
                        )
                    }
                    entry<AppDestination.BalanceTutorial> {
                        BalanceTutorialRoute(settingsRepository = settingsRepository, onDone = { backStack.removeLastOrNull() })
                    }
                    entry<AppDestination.CrownsStart> {
                        CrownsStartScreen(
                            hasActiveSession = hasActiveCrownsSession,
                            tutorialCompleted = settings.crownsTutorialCompleted,
                            onOpenTutorial = {
                                onCrownsTutorialCompleted(true)
                                backStack.add(AppDestination.CrownsTutorial)
                            },
                            onStart = { difficulty ->
                                onCrownsTutorialCompleted(true)
                                backStack.add(
                                    AppDestination.CrownsGame(
                                        CrownsGameLaunch.New(difficulty, catalogSeedSource.nextSeed()),
                                    ),
                                )
                            },
                        )
                    }
                    entry<AppDestination.CrownsTutorial> {
                        CrownsTutorialRoute(
                            settingsRepository = settingsRepository,
                            hapticsEnabled = settings.hapticsEnabled,
                            onDone = { backStack.removeLastOrNull() },
                        )
                    }
                    entry<AppDestination.BalanceGame> { destination ->
                        BalanceGameRoute(
                            launch = destination.launch,
                            sessionRepository = gameSessionRepository,
                            completionRepository = gameCompletionRepository,
                            hapticsEnabled = settings.hapticsEnabled,
                            onBack = { backStack.removeLastOrNull() },
                            onNewPuzzle = { difficulty ->
                                backStack.removeLastOrNull()
                                backStack.add(AppDestination.BalanceGame(BalanceGameLaunch.New(difficulty, catalogSeedSource.nextSeed())))
                            },
                            onStartNew = {
                                backStack.removeLastOrNull()
                                backStack.add(AppDestination.BalanceStart)
                            },
                            onCatalog = {
                                backStack.clear()
                                backStack.add(AppDestination.Catalog)
                            },
                            onToday = {
                                backStack.clear()
                                backStack.add(AppDestination.Today)
                            },
                        )
                    }
                    entry<AppDestination.CrownsGame> { destination ->
                        CrownsGameRoute(
                            launch = destination.launch,
                            sessionRepository = gameSessionRepository,
                            completionRepository = gameCompletionRepository,
                            hapticsEnabled = settings.hapticsEnabled,
                            onBack = { backStack.removeLastOrNull() },
                            onNewPuzzle = { difficulty ->
                                backStack.removeLastOrNull()
                                backStack.add(
                                    AppDestination.CrownsGame(
                                        CrownsGameLaunch.New(difficulty, catalogSeedSource.nextSeed()),
                                    ),
                                )
                            },
                            onStartNew = {
                                backStack.removeLastOrNull()
                                backStack.add(AppDestination.CrownsStart)
                            },
                            onCatalog = {
                                backStack.clear()
                                backStack.add(AppDestination.Catalog)
                            },
                            onToday = {
                                backStack.clear()
                                backStack.add(AppDestination.Today)
                            },
                        )
                    }
                },
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun AppTopBar(
    destination: AppDestination,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    TopAppBar(
        title = { Text(destinationTitle(destination)) },
        navigationIcon = {
            if (destination !in primaryDestinations) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                }
            }
        },
        actions = {
            if (destination in primaryDestinations) {
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.settings))
                }
            }
        },
    )
}

@Composable
private fun AppBottomBar(
    selectedDestination: AppDestination,
    onDestinationSelected: (AppDestination) -> Unit,
) {
    NavigationBar(modifier = Modifier.fillMaxWidth()) {
        primaryDestinations.forEach { destination ->
            NavigationBarItem(
                selected = selectedDestination == destination,
                onClick = { onDestinationSelected(destination) },
                icon = {
                    when (destination) {
                        AppDestination.Today -> Icon(Icons.Filled.CalendarToday, null)
                        AppDestination.Catalog -> Icon(Icons.Filled.CollectionsBookmark, null)
                        AppDestination.Statistics -> Icon(Icons.Filled.BarChart, null)
                        else -> Unit
                    }
                },
                label = { Text(destinationTitle(destination)) },
            )
        }
    }
}

@Composable
private fun destinationTitle(destination: AppDestination): String =
    stringResource(
        when (destination) {
            AppDestination.Today -> R.string.today
            AppDestination.Catalog -> R.string.catalog
            AppDestination.Statistics -> R.string.statistics
            AppDestination.Settings -> R.string.settings
            AppDestination.BalanceStart, is AppDestination.BalanceGame -> R.string.balance
            AppDestination.BalanceTutorial -> R.string.balance_tutorial_title
            AppDestination.CrownsStart, is AppDestination.CrownsGame -> R.string.crowns
            AppDestination.CrownsTutorial -> R.string.crowns_tutorial_title
        },
    )

private class CatalogSeedSource {
    private val random = SecureRandom()

    fun nextSeed(): PuzzleSeed = PuzzleSeed(random.nextLong())
}
