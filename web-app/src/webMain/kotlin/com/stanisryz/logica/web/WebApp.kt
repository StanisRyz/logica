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
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.ui.balance.BalanceGameContent
import com.stanisryz.logica.ui.components.DifficultySelector
import com.stanisryz.logica.ui.theme.LogicaSpacing
import com.stanisryz.logica.ui.theme.LogicaTheme

@Composable
internal fun WebApp(
    controller: WebBootstrapController,
    balanceController: WebBalanceController,
    lifecycle: WebHostLifecycle,
) {
    val lifecycleState by lifecycle.state.collectAsState()

    LogicaTheme(darkTheme = false) {
        LaunchedEffect(controller) {
            withFrameNanos { }
            controller.onComposeRootRendered()
        }
        DisposableEffect(controller) {
            onDispose { controller.setGameplayActive(false) }
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
    onRendered: () -> Unit,
) {
    var balanceOpen by remember { mutableStateOf(false) }
    val balanceState = balanceController.state

    LaunchedEffect(mode) {
        withFrameNanos { }
        onRendered()
    }
    LaunchedEffect(balanceOpen, balanceState, lifecycleState) {
        controller.setGameplayActive(
            balanceOpen &&
                balanceState is WebBalanceState.Playing &&
                balanceState.game.status == BalanceGameStatus.IN_PROGRESS &&
                lifecycleState == PlatformLifecycleState.ACTIVE,
        )
    }

    if (!balanceOpen) {
        LandingContent(
            mode = mode,
            lifecycleState = lifecycleState,
            onOpenBalance = {
                balanceController.showDifficultySelector()
                balanceOpen = true
            },
        )
    } else {
        BalanceFlow(
            state = balanceState,
            controller = balanceController,
            onExitBalance = {
                balanceController.showDifficultySelector()
                balanceOpen = false
            },
        )
    }
}

@Composable
private fun LandingContent(
    mode: WebHostMode,
    lifecycleState: PlatformLifecycleState,
    onOpenBalance: () -> Unit,
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
            text = "Первая общая игра готова",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(28.dp))
        Button(onClick = onOpenBalance) { Text("Играть в Баланс") }
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
                onBack = onExitBalance,
                onStart = controller::selectDifficulty,
            )
        is WebBalanceState.Loading ->
            LoadingBalanceContent(
                difficulty = state.difficulty,
                onBack = controller::showDifficultySelector,
            )
        is WebBalanceState.Error ->
            BalanceErrorContent(
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
private fun DifficultyContent(
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
                    text = "Баланс · выберите сложность",
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
private fun LoadingBalanceContent(
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
            Spacer(Modifier.width(GAME_HEADER_BALANCE_SPACER))
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
private fun BalanceErrorContent(
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
private val GAME_HEADER_BALANCE_SPACER = 92.dp
