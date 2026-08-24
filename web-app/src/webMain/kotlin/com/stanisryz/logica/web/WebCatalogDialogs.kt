package com.stanisryz.logica.web

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.stanisryz.logica.puzzle.core.model.Difficulty

@Composable
internal fun WebCatalogLoadingContent(
    difficulty: Difficulty,
    levelNumber: Int?,
    onBack: () -> Unit,
    isDaily: Boolean = false,
) {
    CenteredColumn {
        CircularProgressIndicator()
        Spacer(Modifier.height(20.dp))
        Text(
            if (levelNumber == null) {
                "Загружаем прогресс: ${difficulty.webCatalogLabel()}"
            } else {
                "Загружаем уровень $levelNumber: ${difficulty.webCatalogLabel()}"
            },
        )
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onBack) { Text(if (isDaily) "К играм" else "Назад к сложности") }
    }
}

@Composable
internal fun WebCatalogLevelErrorContent(
    levelNumber: Int?,
    detail: String,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    isDaily: Boolean = false,
) {
    CenteredColumn {
        Text(
            text =
                when {
                    isDaily -> "Не удалось открыть задачу дня"
                    levelNumber != null -> "Не удалось открыть уровень $levelNumber"
                    else -> "Не удалось загрузить прогресс"
                },
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
        TextButton(onClick = onBack) { Text(if (isDaily) "К играм" else "К сложности") }
    }
}

@Composable
internal fun WebOrdinaryCatalogTerminalDialog(
    visible: Boolean,
    levelNumber: Int,
    solved: Boolean,
    completion: WebCatalogCompletionState,
    solvedDetail: String? = null,
    failedDetail: String? = null,
    onNextLevel: () -> Unit,
    onRetry: () -> Unit,
    onRetrySave: () -> Unit,
    onBack: () -> Unit,
) {
    if (!visible) return
    val saveError = completion as? WebCatalogCompletionState.SaveError
    AlertDialog(
        onDismissRequest = {},
        title = {
            Text(
                when {
                    !solved -> "Уровень $levelNumber не пройден"
                    saveError != null -> "Прогресс не сохранён"
                    else -> "Уровень $levelNumber пройден"
                },
            )
        },
        text = {
            Text(
                when {
                    !solved -> failedDetail ?: "Попробуйте ещё раз."
                    saveError != null ->
                        "Уровень решён, но прогресс не сохранён. ${saveError.detail}"
                    completion is WebCatalogCompletionState.Saved ->
                        solvedDetail ?: "Можно перейти к следующему уровню."
                    else -> "Сохраняем прогресс…"
                },
            )
        },
        confirmButton = {
            when {
                !solved ->
                    TextButton(onClick = onRetry) { Text("Повторить") }
                saveError != null ->
                    TextButton(onClick = onRetrySave) { Text("Сохранить ещё раз") }
                completion is WebCatalogCompletionState.Saved ->
                    TextButton(onClick = onNextLevel) { Text("Следующий уровень") }
                else ->
                    TextButton(onClick = {}, enabled = false) { Text("Сохраняем…") }
            }
        },
        dismissButton = {
            TextButton(onClick = onBack) { Text("К сложности") }
        },
    )
}

@Composable
internal fun WebDailyOrdinaryTerminalDialog(
    visible: Boolean,
    solved: Boolean,
    completion: WebDailyCompletionState,
    scoreDetail: String? = null,
    onRetry: () -> Unit,
    onRetrySave: () -> Unit,
    onExit: () -> Unit,
) {
    if (!visible) return
    AlertDialog(
        onDismissRequest = {},
        title = { Text(if (solved) "Задача дня выполнена ✓" else "Задача дня не пройдена") },
        text = {
            Text(
                when {
                    completion is WebDailyCompletionState.SaveError ->
                        "Игра завершена, но прогресс задачи дня не сохранён. ${completion.detail}"
                    completion is WebDailyCompletionState.Saved ->
                        buildString {
                            append("Результат сохранён.")
                            if (!solved) append(" Попробуйте ещё раз.")
                            scoreDetail?.let { append(" $it") }
                        }
                    else -> "Сохраняем результат…"
                },
            )
        },
        confirmButton = {
            when {
                completion is WebDailyCompletionState.SaveError ->
                    TextButton(onClick = onRetrySave) { Text("Сохранить ещё раз") }
                completion is WebDailyCompletionState.Saved && !solved ->
                    // A failed Daily entry stays open for a fresh real attempt of the same puzzle.
                    TextButton(onClick = onRetry) { Text("Повторить") }
                completion is WebDailyCompletionState.Idle ->
                    TextButton(onClick = {}, enabled = false) { Text("Сохраняем…") }
                // Saved + solved: no replay, no next level; returning to Games is the only action.
                else -> {}
            }
        },
        dismissButton = { TextButton(onClick = onExit) { Text("К играм") } },
    )
}

@Composable
internal fun WebCatalogSaveErrorBanner(
    completion: WebCatalogCompletionState,
    onRetrySave: () -> Unit,
) {
    val error = completion as? WebCatalogCompletionState.SaveError ?: return
    Surface(color = MaterialTheme.colorScheme.errorContainer) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Прогресс не сохранён: ${error.detail}",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
            )
            TextButton(onClick = onRetrySave) { Text("Повторить") }
        }
    }
}

@Composable
internal fun Web2048CatalogTerminalDialog(
    visible: Boolean,
    levelNumber: Int,
    goalReached: Boolean,
    score: String,
    completion: WebCatalogCompletionState,
    onNextLevel: () -> Unit,
    onRetry: () -> Unit,
    onRetrySave: () -> Unit,
    onBack: () -> Unit,
) {
    if (!visible) return
    WebOrdinaryCatalogTerminalDialog(
        visible = true,
        levelNumber = levelNumber,
        solved = goalReached,
        completion = completion,
        solvedDetail = "Итоговый счёт: $score.",
        failedDetail = "Цель не достигнута. Итоговый счёт: $score.",
        onNextLevel = onNextLevel,
        onRetry = onRetry,
        onRetrySave = onRetrySave,
        onBack = onBack,
    )
}

private fun Difficulty.webCatalogLabel(): String =
    when (this) {
        Difficulty.EASY -> "Легко"
        Difficulty.MEDIUM -> "Средне"
        Difficulty.HARD -> "Сложно"
        Difficulty.EXPERT -> "Эксперт"
    }
