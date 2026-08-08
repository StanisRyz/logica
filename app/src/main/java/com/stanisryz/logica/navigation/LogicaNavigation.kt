package com.stanisryz.logica.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.stanisryz.logica.settings.ThemeMode
import com.stanisryz.logica.settings.UserSettings
import com.stanisryz.logica.ui.screens.CatalogScreen
import com.stanisryz.logica.ui.screens.SettingsScreen
import com.stanisryz.logica.ui.screens.StatisticsScreen
import com.stanisryz.logica.ui.screens.TodayScreen

private sealed interface AppDestination {
    data object Today : AppDestination

    data object Catalog : AppDestination

    data object Statistics : AppDestination

    data object Settings : AppDestination
}

private val primaryDestinations =
    listOf(
        AppDestination.Today,
        AppDestination.Catalog,
        AppDestination.Statistics,
    )

@Composable
fun LogicaNavigation(
    settings: UserSettings,
    onThemeModeChanged: (ThemeMode) -> Unit,
    onSoundEnabledChanged: (Boolean) -> Unit,
    onHapticsEnabledChanged: (Boolean) -> Unit,
) {
    val backStack =
        remember {
            mutableStateListOf<AppDestination>(AppDestination.Today)
        }
    val currentDestination = backStack.last()
    val isSettings = currentDestination == AppDestination.Settings

    Scaffold(
        topBar = {
            AppTopBar(
                destination = currentDestination,
                onBack = { backStack.removeLastOrNull() },
                onOpenSettings = {
                    if (!isSettings) {
                        backStack.add(AppDestination.Settings)
                    }
                },
            )
        },
        bottomBar = {
            if (!isSettings) {
                AppBottomBar(
                    selectedDestination = currentDestination,
                    onDestinationSelected = { destination ->
                        backStack.clear()
                        backStack.add(destination)
                    },
                )
            }
        },
    ) { contentPadding ->
        NavDisplay(
            backStack = backStack,
            modifier = Modifier.padding(contentPadding),
            onBack = { backStack.removeLastOrNull() },
            entryProvider =
                entryProvider {
                    entry<AppDestination.Today> {
                        TodayScreen()
                    }
                    entry<AppDestination.Catalog> {
                        CatalogScreen()
                    }
                    entry<AppDestination.Statistics> {
                        StatisticsScreen()
                    }
                    entry<AppDestination.Settings> {
                        SettingsScreen(
                            settings = settings,
                            onThemeModeChanged = onThemeModeChanged,
                            onSoundEnabledChanged = onSoundEnabledChanged,
                            onHapticsEnabledChanged = onHapticsEnabledChanged,
                        )
                    }
                },
        )
    }
}

@Composable
private fun AppTopBar(
    destination: AppDestination,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Surface(tonalElevation = 2.dp) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (destination == AppDestination.Settings) {
                TextButton(onClick = onBack) {
                    Text("Назад")
                }
            }
            Text(
                text = destination.title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleLarge,
            )
            if (destination != AppDestination.Settings) {
                TextButton(onClick = onOpenSettings) {
                    Text("Настройки")
                }
            }
        }
    }
}

@Composable
private fun AppBottomBar(
    selectedDestination: AppDestination,
    onDestinationSelected: (AppDestination) -> Unit,
) {
    NavigationBar {
        primaryDestinations.forEach { destination ->
            NavigationBarItem(
                selected = selectedDestination == destination,
                onClick = { onDestinationSelected(destination) },
                icon = { Text(destination.symbol) },
                label = { Text(destination.title) },
            )
        }
    }
}

private val AppDestination.title: String
    get() =
        when (this) {
            AppDestination.Today -> "Сегодня"
            AppDestination.Catalog -> "Каталог"
            AppDestination.Statistics -> "Статистика"
            AppDestination.Settings -> "Настройки"
        }

private val AppDestination.symbol: String
    get() =
        when (this) {
            AppDestination.Today -> "●"
            AppDestination.Catalog -> "▦"
            AppDestination.Statistics -> "≡"
            AppDestination.Settings -> ""
        }
