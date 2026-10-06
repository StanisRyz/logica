package com.stanisryz.logica.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.stanisryz.logica.R
import com.stanisryz.logica.economy.PlayerEconomy
import com.stanisryz.logica.ui.components.EconomyBar
import com.stanisryz.logica.ui.profile.ProfilePage
import com.stanisryz.logica.ui.theme.LogicaSpacing

/**
 * The shared header of every screen: what you are looking at, the wallet where it belongs, and the
 * Settings gear on all three primary tabs. The wallet always remains on that same line; its compact
 * presentation protects normal portrait phones without introducing a second header row.
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun AppTopBar(
    title: String,
    showBack: Boolean,
    showWallet: Boolean,
    showSettings: Boolean,
    economy: PlayerEconomy,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenLives: () -> Unit,
    onOpenStore: () -> Unit,
) {
    TopAppBar(
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = {
            if (showBack) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back))
                }
            }
        },
        actions = {
            if (showWallet) {
                EconomyBar(
                    economy = economy,
                    onOpenLives = onOpenLives,
                    onOpenGemStore = onOpenStore,
                    compact = true,
                )
            }
            if (showSettings) {
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Rounded.Settings, contentDescription = stringResource(R.string.settings))
                }
            }
        },
        // The bar shares the screen background, so no seam runs under it.
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
    )
}

/** The primary tabs as a rail on the left of a wide window, the same three tabs as the bottom bar. */
@Composable
internal fun AppNavigationRail(
    selectedTab: PrimaryTab,
    onTabSelected: (PrimaryTab) -> Unit,
) {
    NavigationRail(modifier = Modifier.fillMaxHeight(), windowInsets = WindowInsets(0, 0, 0, 0)) {
        Spacer(Modifier.height(LogicaSpacing.section))
        PrimaryTab.entries.forEach { tab ->
            NavigationRailItem(
                selected = selectedTab == tab,
                onClick = { onTabSelected(tab) },
                icon = { Icon(tab.icon(), null) },
                label = { Text(stringResource(tab.titleResource)) },
            )
        }
    }
}

/** Profile and Store keep a readable width in a wide window, centred like on the Web. */
@Composable
internal fun ReadableWidth(
    wide: Boolean,
    maxWidth: Dp,
    content: @Composable () -> Unit,
) {
    if (!wide) {
        content()
        return
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.widthIn(max = maxWidth).fillMaxHeight()) { content() }
    }
}

private fun PrimaryTab.icon(): ImageVector =
    when (this) {
        PrimaryTab.GAME -> Icons.Rounded.SportsEsports
        PrimaryTab.STORE -> Icons.Rounded.Storefront
        PrimaryTab.PROFILE -> Icons.Rounded.Person
    }

@Composable
internal fun AppBottomBar(
    selectedTab: PrimaryTab,
    onTabSelected: (PrimaryTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavigationBar(
        modifier = modifier.fillMaxWidth(),
        windowInsets = WindowInsets(0, 0, 0, 0),
    ) {
        PrimaryTab.entries.forEach { tab ->
            NavigationBarItem(
                selected = selectedTab == tab,
                onClick = { onTabSelected(tab) },
                icon = { Icon(tab.icon(), null) },
                label = { Text(stringResource(tab.titleResource)) },
            )
        }
    }
}

/** A lower-profile game HUD keeps navigation and the wallet reachable without a full app bar. */
@Composable
internal fun GameTopBar(
    title: String,
    backLabel: String,
    onHelp: () -> Unit,
    economy: PlayerEconomy,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenLives: () -> Unit,
    onOpenStore: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .height(GAME_TOP_BAR_HEIGHT)
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = GAME_TOP_BAR_HORIZONTAL_PADDING),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(GAME_TOP_BAR_CONTENT_GAP),
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(GAME_TOP_BAR_HEIGHT)) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = backLabel)
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        IconButton(onClick = onHelp, modifier = Modifier.size(HELP_BUTTON_SIZE)) {
            Icon(
                Icons.AutoMirrored.Rounded.HelpOutline,
                contentDescription = stringResource(R.string.game_rules),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(HELP_ICON_SIZE),
            )
        }
        Spacer(Modifier.weight(1f))
        EconomyBar(
            economy = economy,
            onOpenLives = onOpenLives,
            onOpenGemStore = onOpenStore,
            compact = true,
        )
        IconButton(onClick = onOpenSettings, modifier = Modifier.size(GAME_TOP_BAR_HEIGHT)) {
            Icon(Icons.Rounded.Settings, contentDescription = stringResource(R.string.settings))
        }
    }
}

private val HELP_BUTTON_SIZE = 48.dp

private val HELP_ICON_SIZE = 20.dp

private val GAME_TOP_BAR_HEIGHT = 48.dp

private val GAME_TOP_BAR_HORIZONTAL_PADDING = 4.dp

private val GAME_TOP_BAR_CONTENT_GAP = 4.dp

@Composable
internal fun destinationTitle(
    destination: AppDestination,
    tab: PrimaryTab,
): String =
    stringResource(
        when (destination) {
            AppDestination.Home -> tab.titleResource
            AppDestination.Settings -> R.string.settings
            AppDestination.Licenses -> R.string.licenses
            AppDestination.Achievements -> R.string.achievements
            AppDestination.DailyArchive, is AppDestination.DailyArchiveDay -> R.string.daily_archive
            is AppDestination.ProfileSection ->
                when (destination.page) {
                    ProfilePage.DAILY -> R.string.profile_daily_calendar
                    ProfilePage.GAMES, ProfilePage.RATING, ProfilePage.TOURNAMENT -> R.string.profile_game_statistics
                }
            AppDestination.BalanceStart, is AppDestination.BalanceGame -> R.string.balance
            AppDestination.BalanceTutorial -> R.string.balance_tutorial_title
            AppDestination.CrownsStart, is AppDestination.CrownsGame -> R.string.crowns
            AppDestination.CrownsTutorial -> R.string.crowns_tutorial_title
            AppDestination.WordStart, is AppDestination.WordGame -> R.string.word
            AppDestination.WordTutorial -> R.string.word_tutorial_title
            AppDestination.SudokuStart, is AppDestination.SudokuGame -> R.string.sudoku
            AppDestination.SudokuTutorial -> R.string.sudoku_tutorial_title
            AppDestination.Game2048Start, is AppDestination.Game2048Game -> R.string.game_2048_title
            AppDestination.Game2048Tutorial -> R.string.game_2048_tutorial_title
            AppDestination.NonogramStart, is AppDestination.NonogramGame -> R.string.nonogram
            AppDestination.NonogramTutorial -> R.string.nonogram_tutorial_title
            AppDestination.BlockSudokuStart, is AppDestination.BlockSudokuGame -> R.string.block_sudoku
            AppDestination.BlockSudokuTutorial -> R.string.block_sudoku_tutorial_title
        },
    )
