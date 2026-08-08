package com.stanisryz.logica.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun CatalogScreen(
    hasActiveBalanceSession: Boolean,
    onContinueBalance: () -> Unit,
    onNewBalance: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(24.dp),
    ) {
        Text(
            text = "Головоломки",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 16.dp),
        )
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Баланс", style = MaterialTheme.typography.titleLarge)
                Text(
                    text = "Заполните поле нулями и единицами, соблюдая баланс и уникальность линий.",
                    modifier = Modifier.padding(vertical = 12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (hasActiveBalanceSession) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onContinueBalance) {
                            Text("Продолжить")
                        }
                        Button(onClick = onNewBalance) {
                            Text("Новая игра")
                        }
                    }
                } else {
                    Button(onClick = onNewBalance) {
                        Text("Играть")
                    }
                }
            }
        }
    }
}
