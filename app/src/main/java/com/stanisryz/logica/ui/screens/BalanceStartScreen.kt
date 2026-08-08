package com.stanisryz.logica.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.stanisryz.logica.puzzle.core.model.Difficulty

@Composable
fun BalanceStartScreen(
    onStart: (Difficulty) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedDifficulty by rememberSaveable { mutableStateOf(Difficulty.EASY) }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
    ) {
        Text("Баланс", style = MaterialTheme.typography.headlineMedium)
        Text(
            text =
                "В каждой строке и столбце должно быть поровну ○ и ●. " +
                    "Нельзя ставить три одинаковых символа подряд, а завершённые строки и столбцы не повторяются.",
            modifier = Modifier.padding(vertical = 16.dp),
            style = MaterialTheme.typography.bodyLarge,
        )
        Text("Сложность", style = MaterialTheme.typography.titleMedium)
        Difficulty.entries.forEach { difficulty ->
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable { selectedDifficulty = difficulty }
                        .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(
                    selected = selectedDifficulty == difficulty,
                    onClick = { selectedDifficulty = difficulty },
                )
                Text(difficulty.russianLabel, modifier = Modifier.padding(start = 8.dp))
            }
        }
        Button(
            onClick = { onStart(selectedDifficulty) },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp),
        ) {
            Text("Начать")
        }
    }
}

internal val Difficulty.russianLabel: String
    get() =
        when (this) {
            Difficulty.EASY -> "Легко"
            Difficulty.MEDIUM -> "Средне"
            Difficulty.HARD -> "Сложно"
            Difficulty.EXPERT -> "Эксперт"
        }
