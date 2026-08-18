package com.stanisryz.logica.web

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stanisryz.logica.platform.PlatformLifecycleState
import com.stanisryz.logica.puzzle.core.balance.BalanceGameStatus
import com.stanisryz.logica.puzzle.core.crowns.CrownsGameStatus
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.word.WordGameStatus
import com.stanisryz.logica.ui.balance.BalanceGameContent
import com.stanisryz.logica.ui.components.DifficultySelector
import com.stanisryz.logica.ui.crowns.CrownsGameContent
import com.stanisryz.logica.ui.theme.LogicaSpacing
import com.stanisryz.logica.ui.theme.LogicaTheme
import com.stanisryz.logica.ui.word.WordGameContent

private sealed interface WebRoute {
    data object Landing : WebRoute

    data object Balance : WebRoute

    data object Crowns : WebRoute

    data object Word : WebRoute
}

@Composable
internal fun WebApp(
    controller: WebBootstrapController,
    balanceController: WebBalanceController,
    crownsController: WebCrownsController,
    wordController: WebWordController,
    lifecycle: WebHostLifecycle,
) {
    val lifecycleState by lifecycle.state.collectAsState()

    LogicaTheme(darkTheme = false) {
        LaunchedEffect(controller) {
            withFrameNanos { }
            controller.onComposeRootRendered()
        }
        DisposableEffect(controller, balanceController, crownsController, wordController) {
            onDispose {
                controller.setGameplayActive(false)
                balanceController.dispose()
                crownsController.dispose()
                wordController.dispose()
            }
        }

        PortraitHostSurface {
            when (val state = controller.state) {
                WebBootstrapState.Loading -> LoadingContent()
                is WebBootstrapState.Ready ->
                    ReadyContent(
                        mode = state.mode,
                        lifecycleState = lifecycleState,
                        controller = controller,
                        balanceController = balanceController,
                        crownsController = crownsController,
                        wordController = wordController,
                        onRendered = controller::onInitialHostUiReady,
                    )
                is WebBootstrapState.FatalError -> FatalContent(state.message)
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
        val widthLimited = maxWidth * 16f <= maxHeight * 9f
        val portraitWidth = if (widthLimited) maxWidth else maxHeight * 9f / 16f
        val portraitHeight = if (widthLimited) maxWidth * 16f / 9f else maxHeight

        Surface(
            modifier = Modifier.width(portraitWidth).height(portraitHeight),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 8.dp,
        ) {
            content()
        }
    }
}

@Composable
private fun LoadingContent() {
    CenteredColumn {
        CircularProgressIndicator()
        Spacer(Modifier.height(24.dp))
        Text(
            text = "Логика загружается",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun ReadyContent(
    mode: WebHostMode,
    lifecycleState: PlatformLifecycleState,
    controller: WebBootstrapController,
    balanceController: WebBalanceController,
    crownsController: WebCrownsController,
    wordController: WebWordController,
    onRendered: () -> Unit,
) {
    var route by remember { mutableStateOf<WebRoute>(WebRoute.Landing) }
    val balanceState = balanceController.state
    val crownsState = crownsController.state
    val wordState = wordController.state

    LaunchedEffect(mode) {
        withFrameNanos { }
        onRendered()
    }
    LaunchedEffect(route, balanceState, crownsState, wordState, lifecycleState) {
        controller.setGameplayActive(
            when (route) {
                WebRoute.Landing -> false
                WebRoute.Balance ->
                    balanceState is WebBalanceState.Playing &&
                        balanceState.game.status == BalanceGameStatus.IN_PROGRESS
                WebRoute.Crowns ->
                    crownsState is WebCrownsState.Playing &&
                        crownsState.game.status == CrownsGameStatus.IN_PROGRESS
                WebRoute.Word ->
                    wordState is WebWordState.Playing &&
                        wordState.game.status == WordGameStatus.IN_PROGRESS
            } &&
                lifecycleState == PlatformLifecycleState.ACTIVE,
        )
    }

    when (route) {
        WebRoute.Landing ->
            LandingContent(
                mode = mode,
                lifecycleState = lifecycleState,
                onOpenBalance = {
                    balanceController.showDifficultySelector()
                    route = WebRoute.Balance
                },
                onOpenCrowns = {
                    crownsController.showDifficultySelector()
                    route = WebRoute.Crowns
                },
                onOpenWord = {
                    wordController.showDifficultySelector()
                    route = WebRoute.Word
                },
            )
        WebRoute.Balance ->
            BalanceFlow(
                state = balanceState,
                controller = balanceController,
                onExitBalance = {
                    balanceController.showDifficultySelector()
                    route = WebRoute.Landing
                },
            )
        WebRoute.Crowns ->
            CrownsFlow(
                state = crownsState,
                controller = crownsController,
                onExitCrowns = {
                    crownsController.showDifficultySelector()
                    route = WebRoute.Landing
                },
            )
        WebRoute.Word ->
            WordFlow(
                state = wordState,
                controller = wordController,
                onExitWord = {
                    wordController.showDifficultySelector()
                    route = WebRoute.Landing
                },
            )
    }
}

@Composable
private fun LandingContent(
    mode: WebHostMode,
    lifecycleState: PlatformLifecycleState,
    onOpenBalance: () -> Unit,
    onOpenCrowns: () -> Unit,
    onOpenWord: () -> Unit,
) {
    CenteredColumn {
        Box(
            modifier = Modifier.size(88.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Л",
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontSize = 42.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(28.dp))
        Text("Логика", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Общие игры Android и Web",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(28.dp))
        Button(onClick = onOpenBalance) { Text("Играть в Баланс") }
        Spacer(Modifier.height(12.dp))
        Button(onClick = onOpenCrowns) { Text("Играть в Короны") }
        Spacer(Modifier.height(12.dp))
        Button(onClick = onOpenWord) { Text("Играть в Слово") }
        Spacer(Modifier.height(24.dp))
        Text(
            text =
                when (mode) {
                    WebHostMode.YANDEX -> "Яндекс Игры подключены"
                    WebHostMode.STANDALONE -> "Локальный автономный режим"
                },
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text =
                when (lifecycleState) {
                    PlatformLifecycleState.ACTIVE -> "Хост активен"
                    PlatformLifecycleState.INACTIVE -> "Хост на паузе"
                },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun BalanceFlow(
    state: WebBalanceState,
    controller: WebBalanceController,
    onExitBalance: () -> Unit,
) {
    when (state) {
        WebBalanceState.DifficultySelection ->
            DifficultyContent(
                gameTitle = "Баланс",
                onBack = onExitBalance,
                onStart = controller::selectDifficulty,
            )
        is WebBalanceState.Loading ->
            LoadingLevelContent(
                difficulty = state.difficulty,
                onBack = controller::showDifficultySelector,
            )
        is WebBalanceState.Error ->
            LevelErrorContent(
                detail = state.detail,
                onRetry = { controller.selectDifficulty(state.difficulty) },
                onBack = controller::showDifficultySelector,
            )
        is WebBalanceState.Playing ->
            PlayingBalanceContent(
                state = state,
                controller = controller,
            )
    }
}

@Composable
private fun CrownsFlow(
    state: WebCrownsState,
    controller: WebCrownsController,
    onExitCrowns: () -> Unit,
) {
    when (state) {
        WebCrownsState.DifficultySelection ->
            DifficultyContent(
                gameTitle = "Короны",
                onBack = onExitCrowns,
                onStart = controller::selectDifficulty,
            )
        is WebCrownsState.Loading ->
            LoadingLevelContent(
                difficulty = state.difficulty,
                onBack = controller::showDifficultySelector,
            )
        is WebCrownsState.Error ->
            LevelErrorContent(
                detail = state.detail,
                onRetry = { controller.selectDifficulty(state.difficulty) },
                onBack = controller::showDifficultySelector,
            )
        is WebCrownsState.Playing ->
            PlayingCrownsContent(
                state = state,
                controller = controller,
            )
    }
}

@Composable
private fun WordFlow(
    state: WebWordState,
    controller: WebWordController,
    onExitWord: () -> Unit,
) {
    when (state) {
        WebWordState.DifficultySelection ->
            DifficultyContent(
                gameTitle = "Слово",
                onBack = onExitWord,
                onStart = controller::selectDifficulty,
            )
        is WebWordState.Loading ->
            LoadingLevelContent(
                difficulty = state.difficulty,
                onBack = controller::showDifficultySelector,
            )
        is WebWordState.Error ->
            LevelErrorContent(
                detail = state.detail,
                onRetry = { controller.selectDifficulty(state.difficulty) },
                onBack = controller::showDifficultySelector,
            )
        is WebWordState.Playing ->
            PlayingWordContent(
                state = state,
                controller = controller,
            )
    }
}

@Composable
private fun DifficultyContent(
    gameTitle: String,
    onBack: () -> Unit,
    onStart: (Difficulty) -> Unit,
) {
    BoxWithConstraints(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(
                    horizontal = LogicaSpacing.screenHorizontal,
                    vertical = LogicaSpacing.screenVertical,
                ),
    ) {
        val cardHeight =
            ((maxHeight - DIFFICULTY_HEADER_HEIGHT - LogicaSpacing.section - LogicaSpacing.item * 3) / 4)
                .coerceIn(MIN_DIFFICULTY_CARD_HEIGHT, MAX_DIFFICULTY_CARD_HEIGHT)
        Column(verticalArrangement = Arrangement.spacedBy(LogicaSpacing.section)) {
            Row(
                modifier = Modifier.fillMaxWidth().height(DIFFICULTY_HEADER_HEIGHT),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onBack) { Text("Назад") }
                Text(
                    text = "$gameTitle · выберите сложность",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            DifficultySelector(
                onStart = onStart,
                enabled = true,
                cardHeight = cardHeight,
            )
        }
    }
}

@Composable
private fun LoadingLevelContent(
    difficulty: Difficulty,
    onBack: () -> Unit,
) {
    CenteredColumn {
        CircularProgressIndicator()
        Spacer(Modifier.height(20.dp))
        Text("Загружаем уровень 1: ${difficulty.webLabel()}")
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onBack) { Text("Назад к сложности") }
    }
}

@Composable
private fun PlayingBalanceContent(
    state: WebBalanceState.Playing,
    controller: WebBalanceController,
) {
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().height(GAME_HEADER_HEIGHT).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = controller::showDifficultySelector) { Text("К сложности") }
            Spacer(Modifier.weight(1f))
            Text("Баланс", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.width(GAME_HEADER_TITLE_SPACER))
        }
        BalanceGameContent(
            puzzle = state.puzzle,
            game = state.game,
            difficulty = state.definition.difficulty,
            levelNumber = state.definition.levelNumber.value,
            selectedValue = state.selectedValue,
            isPencilMode = state.isPencilMode,
            isHintLoading = state.isHintLoading,
            gameplayEnabled = state.game.status == BalanceGameStatus.IN_PROGRESS,
            onCellTapped = controller::onCellTapped,
            onSelectValue = controller::selectValue,
            onTogglePencil = controller::togglePencilMode,
            onHint = controller::requestHint,
            modifier = Modifier.weight(1f),
        )
    }

    if (state.game.status.isTerminal) {
        AlertDialog(
            onDismissRequest = {},
            title = {
                Text(
                    if (state.game.status == BalanceGameStatus.SOLVED) {
                        "Уровень решён"
                    } else {
                        "Попытка не пройдена"
                    },
                )
            },
            text = { Text("Уровень 1 можно пройти ещё раз или выбрать другую сложность.") },
            confirmButton = {
                TextButton(onClick = controller::retry) { Text("Пройти заново") }
            },
            dismissButton = {
                TextButton(onClick = controller::showDifficultySelector) { Text("К сложности") }
            },
        )
    }
}

@Composable
private fun PlayingCrownsContent(
    state: WebCrownsState.Playing,
    controller: WebCrownsController,
) {
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().height(GAME_HEADER_HEIGHT).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = controller::showDifficultySelector) { Text("К сложности") }
            Spacer(Modifier.weight(1f))
            Text("Короны", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.width(GAME_HEADER_TITLE_SPACER))
        }
        CrownsGameContent(
            puzzle = state.puzzle,
            game = state.game,
            difficulty = state.definition.difficulty,
            levelNumber = state.definition.levelNumber.value,
            selectedValue = state.selectedValue,
            isPencilMode = state.isPencilMode,
            isHintLoading = state.isHintLoading,
            gameplayEnabled = state.game.status == CrownsGameStatus.IN_PROGRESS,
            onCellTapped = controller::onCellTapped,
            onSelectValue = controller::selectValue,
            onTogglePencil = controller::togglePencilMode,
            onHint = controller::requestHint,
            modifier = Modifier.weight(1f),
        )
    }

    if (state.game.status.isTerminal) {
        AlertDialog(
            onDismissRequest = {},
            title = {
                Text(
                    if (state.game.status == CrownsGameStatus.SOLVED) {
                        "Уровень решён"
                    } else {
                        "Попытка не пройдена"
                    },
                )
            },
            text = { Text("Уровень 1 можно пройти ещё раз или выбрать другую сложность.") },
            confirmButton = {
                TextButton(onClick = controller::retry) { Text("Пройти заново") }
            },
            dismissButton = {
                TextButton(onClick = controller::showDifficultySelector) { Text("К сложности") }
            },
        )
    }
}

@Composable
private fun PlayingWordContent(
    state: WebWordState.Playing,
    controller: WebWordController,
) {
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().height(GAME_HEADER_HEIGHT).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = controller::showDifficultySelector) { Text("К сложности") }
            Spacer(Modifier.weight(1f))
            Text("Слово", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.width(GAME_HEADER_TITLE_SPACER))
        }
        WordGameContent(
            puzzle = state.puzzle,
            game = state.game,
            levelNumber = state.definition.levelNumber.value,
            rejection = state.rejection,
            rejectionRevision = state.rejectionRevision,
            acceptedAttemptRevision = state.acceptedAttemptRevision,
            gameplayEnabled = state.game.status == WordGameStatus.IN_PROGRESS,
            onLetter = controller::setLetter,
            onClearLetter = controller::clearLetter,
            onSubmit = controller::submit,
            onDismissRejection = controller::dismissRejection,
            onAcceptedAttemptRevealed = controller::onAcceptedAttemptRevealed,
            modifier = Modifier.weight(1f),
        )
    }

    if (state.isTerminalRevealReady) {
        AlertDialog(
            onDismissRequest = {},
            title = {
                Text(
                    if (state.game.status == WordGameStatus.SOLVED) {
                        "Слово отгадано"
                    } else {
                        "Попытки закончились"
                    },
                )
            },
            text = {
                Text(
                    if (state.game.status == WordGameStatus.SOLVED) {
                        "Уровень 1 пройден за ${state.game.attempts.size} попыток."
                    } else {
                        "Загаданное слово: ${state.puzzle.answer.uppercase()}"
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = controller::retry) { Text("Пройти заново") }
            },
            dismissButton = {
                TextButton(onClick = controller::showDifficultySelector) { Text("К сложности") }
            },
        )
    }
}

@Composable
private fun LevelErrorContent(
    detail: String,
    onRetry: () -> Unit,
    onBack: () -> Unit,
) {
    CenteredColumn {
        Text(
            text = "Не удалось открыть уровень 1",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = detail,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = onRetry) { Text("Повторить") }
        TextButton(onClick = onBack) { Text("К сложности") }
    }
}

@Composable
private fun FatalContent(message: String) {
    CenteredColumn {
        Text(
            text = "Не удалось запустить Web-версию",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.error,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun CenteredColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content,
    )
}

private fun Difficulty.webLabel(): String =
    when (this) {
        Difficulty.EASY -> "Легко"
        Difficulty.MEDIUM -> "Средне"
        Difficulty.HARD -> "Сложно"
        Difficulty.EXPERT -> "Эксперт"
    }

private val DIFFICULTY_HEADER_HEIGHT = 48.dp
private val MIN_DIFFICULTY_CARD_HEIGHT = 96.dp
private val MAX_DIFFICULTY_CARD_HEIGHT = 152.dp
private val GAME_HEADER_HEIGHT = 52.dp
private val GAME_HEADER_TITLE_SPACER = 92.dp
