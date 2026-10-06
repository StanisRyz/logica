package com.stanisryz.logica.web

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.stanisryz.logica.ui.components.LocalGameSounds
import com.stanisryz.logica.ui.theme.LogicaTheme
import com.stanisryz.logica.web.generated.resources.web_fatal_message
import com.stanisryz.logica.web.generated.resources.web_fatal_title
import com.stanisryz.logica.web.generated.resources.web_init_timeout_body
import com.stanisryz.logica.web.generated.resources.web_init_timeout_title
import com.stanisryz.logica.web.generated.resources.web_loading
import com.stanisryz.logica.web.generated.resources.web_retry
import com.stanisryz.logica.web.generated.resources.web_tab_elsewhere_body
import com.stanisryz.logica.web.generated.resources.web_tab_elsewhere_title
import com.stanisryz.logica.web.generated.resources.web_tab_play_here
import org.jetbrains.compose.resources.stringResource
import com.stanisryz.logica.web.generated.resources.Res as WebRes

@Composable
internal fun WebApp(
    controller: WebBootstrapController,
    balanceController: WebBalanceController,
    crownsController: WebCrownsController,
    wordController: WebWordController,
    sudokuController: WebSudokuController,
    game2048Controller: Web2048Controller,
    nonogramController: WebNonogramController,
    blockSudokuController: WebBlockSudokuController,
    lifecycle: WebHostLifecycle,
    playerSession: WebPlayerSessionController,
    dailyCoordinator: WebDailyGameplayCoordinator,
    storeProcessor: WebStoreProcessor,
    paymentsCoordinator: WebPaymentsCoordinator,
    rewardedAds: WebRewardedAds,
    leaderboard: WebLeaderboardController,
    interstitialController: WebInterstitialContinuationController,
    stickyBannerController: WebStickyBannerController,
    tabLock: WebTabLock,
) {
    val lifecycleState by lifecycle.state.collectAsState()
    val tabLockState by tabLock.state.collectAsState()
    // The sound player follows the host's audio conditions and the Sound setting synchronously itself.
    val soundPlayer = remember(lifecycle) { WebGameSoundPlayer(lifecycle) }

    // Like Android's default, the Web follows the device's light/dark preference unless the player
    // picked a theme in the Web settings.
    val darkTheme =
        when (WebSettings.themeMode) {
            WebThemeMode.SYSTEM -> isSystemInDarkTheme()
            WebThemeMode.LIGHT -> false
            WebThemeMode.DARK -> true
        }
    LogicaTheme(darkTheme = darkTheme) {
        CompositionLocalProvider(LocalGameSounds provides soundPlayer) {
            LaunchedEffect(controller) {
                withFrameNanos { }
                controller.onComposeRootRendered()
                runCatching { removeStartupLoader() }
            }
            DisposableEffect(
                controller,
                balanceController,
                crownsController,
                wordController,
                sudokuController,
                game2048Controller,
                nonogramController,
                blockSudokuController,
                playerSession,
            ) {
                onDispose {
                    controller.setGameplayActive(false)
                    balanceController.dispose()
                    crownsController.dispose()
                    wordController.dispose()
                    sudokuController.dispose()
                    game2048Controller.dispose()
                    nonogramController.dispose()
                    blockSudokuController.dispose()
                    playerSession.dispose()
                }
            }

            PortraitHostSurface {
                when (val state = controller.state) {
                    WebBootstrapState.Loading -> LoadingContent()
                    // Another tab runs the game: this one binds, loads, and writes nothing.
                    is WebBootstrapState.Ready if !tabLockState.canPlay ->
                        TabElsewhereContent(
                            state = tabLockState,
                            onPlayHere = tabLock::playHere,
                            onRendered = controller::onInitialHostUiReady,
                        )
                    is WebBootstrapState.Ready ->
                        ReadyContent(
                            mode = state.mode,
                            lifecycleState = lifecycleState,
                            controller = controller,
                            balanceController = balanceController,
                            crownsController = crownsController,
                            wordController = wordController,
                            sudokuController = sudokuController,
                            game2048Controller = game2048Controller,
                            nonogramController = nonogramController,
                            blockSudokuController = blockSudokuController,
                            playerSession = playerSession,
                            dailyCoordinator = dailyCoordinator,
                            storeProcessor = storeProcessor,
                            rewardedAds = rewardedAds,
                            leaderboard = leaderboard,
                            interstitialController = interstitialController,
                            stickyBannerController = stickyBannerController,
                            paymentsCoordinator = paymentsCoordinator,
                            onRendered = controller::onInitialHostUiReady,
                        )
                    WebBootstrapState.FatalError -> FatalContent()
                    WebBootstrapState.TimedOut -> InitTimedOutContent(onRetry = controller::retryInitialization)
                }
            }
        }
    }
}

@Composable
private fun PortraitHostSurface(content: @Composable () -> Unit) {
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        // A landscape window (a desktop browser, Yandex Games on a computer) gets the whole width;
        // a portrait or narrow one gets a phone column. The column fills a phone's whole screen,
        // also when a sticky banner takes part of its height, and narrows only in a window too
        // wide for a phone layout, so text never wraps because the window became shorter.
        val wide = maxWidth >= WIDE_HOST_MIN_WIDTH && maxWidth > maxHeight * WIDE_HOST_ASPECT
        if (wide) {
            // The sides take the screens' own colour, so the game reaches the window's edges
            // (requirement 1.6.2.1) instead of sitting in a lighter frame.
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface), contentAlignment = Alignment.Center) {
                Surface(
                    modifier = Modifier.widthIn(max = WIDE_HOST_MAX_WIDTH).fillMaxSize(),
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    CompositionLocalProvider(LocalWebWideLayout provides true) { content() }
                }
            }
            return@BoxWithConstraints
        }
        val columnWidth = minOf(maxWidth, maxHeight * PORTRAIT_COLUMN_MAX_ASPECT)
        Surface(
            modifier = Modifier.width(columnWidth).fillMaxHeight(),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = if (columnWidth < maxWidth) 8.dp else 0.dp,
        ) {
            content()
        }
    }
}

/** True when the Web host fills a landscape window instead of the 9:16 column. */
internal val LocalWebWideLayout = staticCompositionLocalOf { false }

/** Centres a tab's content at a readable width in the wide host; the phone column is untouched. */
@Composable
internal fun WideReadableColumn(
    maxWidth: Dp,
    content: @Composable () -> Unit,
) {
    if (!LocalWebWideLayout.current) {
        content()
        return
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.widthIn(max = maxWidth).fillMaxHeight()) { content() }
    }
}

/** «The game is open in another tab», with the way to move it here. */
@Composable
private fun TabElsewhereContent(
    state: WebTabLockState,
    onPlayHere: () -> Unit,
    onRendered: () -> Unit,
) {
    if (state == WebTabLockState.CHECKING) {
        LoadingContent()
        return
    }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        onRendered()
    }
    CenteredColumn {
        Text(
            text = stringResource(WebRes.string.web_tab_elsewhere_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(WebRes.string.web_tab_elsewhere_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onPlayHere, enabled = state == WebTabLockState.ELSEWHERE) {
            Text(stringResource(WebRes.string.web_tab_play_here))
        }
    }
}

@Composable
private fun LoadingContent() {
    CenteredColumn {
        CircularProgressIndicator()
        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(WebRes.string.web_loading),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** `YaGames.init()` did not answer in time: say so and offer a retry. */
@Composable
private fun InitTimedOutContent(onRetry: () -> Unit) {
    CenteredColumn {
        Text(
            text = stringResource(WebRes.string.web_init_timeout_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(WebRes.string.web_init_timeout_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onRetry) { Text(stringResource(WebRes.string.web_retry)) }
    }
}

@Composable
private fun FatalContent() {
    CenteredColumn {
        Text(
            text = stringResource(WebRes.string.web_fatal_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.error,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(WebRes.string.web_fatal_message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
internal fun CenteredColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content,
    )
}

private val WIDE_HOST_MIN_WIDTH = 720.dp

private val WIDE_HOST_MAX_WIDTH = 1440.dp

private const val WIDE_HOST_ASPECT = 1.2f

/** The widest phone column, width over height, before a window counts as too wide for it. */
private const val PORTRAIT_COLUMN_MAX_ASPECT = 0.8f
