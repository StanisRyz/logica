package com.stanisryz.logica.web

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.stanisryz.logica.puzzle.core.balance.BalanceGameStatus
import com.stanisryz.logica.puzzle.core.blocksudoku.BlockSudokuStatus
import com.stanisryz.logica.puzzle.core.crowns.CrownsGameStatus
import com.stanisryz.logica.puzzle.core.nonogram.NonogramGameStatus
import com.stanisryz.logica.puzzle.core.sudoku.SudokuGameStatus
import com.stanisryz.logica.puzzle.core.word.WordGameStatus
import com.stanisryz.logica.shared.ui.generated.resources.Res
import com.stanisryz.logica.shared.ui.generated.resources.primary_games
import com.stanisryz.logica.shared.ui.generated.resources.primary_profile
import com.stanisryz.logica.shared.ui.generated.resources.primary_store
import com.stanisryz.logica.ui.theme.LogicaSpacing
import com.stanisryz.logica.web.generated.resources.web_settings
import org.jetbrains.compose.resources.stringResource
import com.stanisryz.logica.web.generated.resources.Res as WebRes

internal val PRIMARY_ROUTES = setOf<WebRoute>(WebRoute.GameHub, WebRoute.Profile, WebRoute.Store)

internal sealed interface WebRoute {
    data object GameHub : WebRoute

    data object Profile : WebRoute

    data object Store : WebRoute

    /** The Daily archive, opened from the Game hub or a past day of the Profile calendar. */
    data object DailyArchive : WebRoute

    data object Balance : WebRoute

    data object Crowns : WebRoute

    data object Word : WebRoute

    data object Sudoku : WebRoute

    data object Game2048 : WebRoute

    data object Nonogram : WebRoute

    data object BlockSudoku : WebRoute
}

/**
 * Where a page reachable from both the Game hub and the Profile was opened: the weekly tournament
 * (the hub's line or the Profile row) and the Daily archive (the hub's button or a past day of the
 * Profile calendar). Its way back returns there. Host state only, never saved or synced.
 */
internal enum class WebPageOrigin { GAME_HUB, PROFILE }

/** Where the Daily archive's way back leads. */
internal enum class WebArchiveBack { DAY_LIST, GAME_HUB, PROFILE_CALENDAR }

/**
 * The archive's way back: opened from the Profile calendar it returns to the calendar (that day was
 * opened directly, so its list is skipped); opened from the hub a day returns to the list of days
 * and the list to the hub.
 */
internal fun webArchiveBack(
    origin: WebPageOrigin,
    selectedDay: Long?,
): WebArchiveBack =
    when {
        origin == WebPageOrigin.PROFILE -> WebArchiveBack.PROFILE_CALENDAR
        selectedDay != null -> WebArchiveBack.DAY_LIST
        else -> WebArchiveBack.GAME_HUB
    }

internal fun routeHasActivePuzzle(
    route: WebRoute,
    balanceState: WebBalanceState,
    crownsState: WebCrownsState,
    wordState: WebWordState,
    sudokuState: WebSudokuState,
    game2048State: Web2048State,
    nonogramState: WebNonogramState,
    blockSudokuState: WebBlockSudokuState,
): Boolean =
    when (route) {
        WebRoute.GameHub, WebRoute.Profile, WebRoute.Store, WebRoute.DailyArchive -> false
        WebRoute.Balance ->
            balanceState is WebBalanceState.Playing &&
                balanceState.game.status == BalanceGameStatus.IN_PROGRESS
        WebRoute.Crowns ->
            crownsState is WebCrownsState.Playing &&
                crownsState.game.status == CrownsGameStatus.IN_PROGRESS
        WebRoute.Word ->
            wordState is WebWordState.Playing &&
                wordState.game.status == WordGameStatus.IN_PROGRESS
        WebRoute.Sudoku ->
            sudokuState is WebSudokuState.Playing &&
                sudokuState.game.status == SudokuGameStatus.IN_PROGRESS
        WebRoute.Game2048 ->
            game2048State is Web2048State.Playing && !game2048State.isOver
        WebRoute.Nonogram ->
            nonogramState is WebNonogramState.Playing &&
                nonogramState.game.status == NonogramGameStatus.IN_PROGRESS
        WebRoute.BlockSudoku ->
            blockSudokuState is WebBlockSudokuState.Playing &&
                blockSudokuState.game.status == BlockSudokuStatus.IN_PROGRESS
    }

/**
 * Initial sticky-banner policy: hub/profile/store show the Yandex-rendered banner; a game route
 * hides it while a puzzle is actively being played. Isolated here so real Yandex layout testing
 * can tune it without touching navigation or ad plumbing.
 */
internal fun stickyBannerVisible(
    route: WebRoute,
    hasActivePuzzle: Boolean,
): Boolean =
    when (route) {
        WebRoute.GameHub, WebRoute.Profile, WebRoute.Store, WebRoute.DailyArchive -> true
        else -> !hasActivePuzzle
    }

@Composable
internal fun PrimaryDestinationShell(
    selected: WebRoute,
    onSelect: (WebRoute) -> Unit,
    content: @Composable () -> Unit,
) {
    val wide = LocalWebWideLayout.current
    val destinations =
        listOf(
            Triple(WebRoute.GameHub, Icons.Outlined.SportsEsports, Res.string.primary_games),
            Triple(WebRoute.Profile, Icons.Outlined.PersonOutline, Res.string.primary_profile),
            Triple(WebRoute.Store, Icons.Outlined.ShoppingCart, Res.string.primary_store),
        )
    Row(Modifier.fillMaxSize()) {
        // A landscape window keeps the tabs in a rail on the left, so the content gets the height.
        if (wide) {
            NavigationRail(modifier = Modifier.fillMaxHeight()) {
                Spacer(Modifier.height(LogicaSpacing.section))
                destinations.forEach { (route, icon, label) ->
                    NavigationRailItem(
                        selected = selected == route,
                        onClick = { onSelect(route) },
                        icon = { Icon(icon, contentDescription = null) },
                        label = { Text(stringResource(label)) },
                    )
                }
            }
        }
        Column(Modifier.weight(1f).fillMaxHeight()) {
            // Every tab opens with its name and the wallet, the Store included.
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(GAME_HEADER_HEIGHT)
                        .padding(start = LogicaSpacing.screenHorizontal, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(destinations.first { it.first == selected }.third),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                WebGameplayWallet()
                var settingsOpen by remember { mutableStateOf(false) }
                IconButton(onClick = { settingsOpen = true }) {
                    Icon(Icons.Outlined.Settings, contentDescription = stringResource(WebRes.string.web_settings))
                }
                if (settingsOpen) WebSettingsDialog(onDismiss = { settingsOpen = false })
            }
            Box(Modifier.weight(1f)) { content() }
            if (!wide) {
                NavigationBar(modifier = Modifier.fillMaxWidth().height(PRIMARY_NAVIGATION_HEIGHT)) {
                    destinations.forEach { (route, icon, label) ->
                        NavigationBarItem(
                            selected = selected == route,
                            onClick = { onSelect(route) },
                            icon = { Icon(icon, contentDescription = null) },
                            label = { Text(stringResource(label)) },
                        )
                    }
                }
            }
        }
    }
}

private val PRIMARY_NAVIGATION_HEIGHT = 64.dp
