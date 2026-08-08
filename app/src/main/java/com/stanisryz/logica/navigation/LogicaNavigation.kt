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
import com.stanisryz.logica.daily.DailyChallengeRepository
import com.stanisryz.logica.puzzle.core.model.PuzzleSeed
import com.stanisryz.logica.result.GameCompletionRepository
import com.stanisryz.logica.session.GameSessionRepository
import com.stanisryz.logica.settings.SettingsRepository
import com.stanisryz.logica.settings.ThemeMode
import com.stanisryz.logica.settings.UserSettings
import com.stanisryz.logica.statistics.StatisticsRepository
import com.stanisryz.logica.ui.screens.BalanceGameRoute
import com.stanisryz.logica.ui.screens.BalanceStartScreen
import com.stanisryz.logica.ui.screens.BalanceTutorialRoute
import com.stanisryz.logica.ui.screens.CatalogScreen
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

    data class BalanceGame(
        val launch: BalanceGameLaunch,
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
    onThemeModeChanged: (ThemeMode) -> Unit,
    onSoundEnabledChanged: (Boolean) -> Unit,
    onHapticsEnabledChanged: (Boolean) -> Unit,
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
                            tutorialCompleted = settings.balanceTutorialCompleted,
                            onOpenDaily = { launch -> backStack.add(AppDestination.BalanceGame(launch)) },
                            onOpenTutorial = { backStack.add(AppDestination.BalanceTutorial) },
                        )
                    }
                    entry<AppDestination.Catalog> {
                        CatalogScreen(
                            hasActiveBalanceSession = hasActiveBalanceSession,
                            onContinueBalance = { backStack.add(AppDestination.BalanceGame(BalanceGameLaunch.Restore())) },
                            onNewBalance = { backStack.add(AppDestination.BalanceStart) },
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
        },
    )

private class CatalogSeedSource {
    private val random = SecureRandom()

    fun nextSeed(): PuzzleSeed = PuzzleSeed(random.nextLong())
}
