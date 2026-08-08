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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.stanisryz.logica.R

@Composable
fun CatalogScreen(
    hasActiveBalanceSession: Boolean,
    onContinueBalance: () -> Unit,
    onNewBalance: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().padding(24.dp)) {
        Text(stringResource(R.string.puzzles), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 16.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(stringResource(R.string.balance), style = MaterialTheme.typography.titleLarge)
                Text(
                    stringResource(R.string.balance_catalog_description),
                    modifier = Modifier.padding(vertical = 12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (hasActiveBalanceSession) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onContinueBalance) { Text(stringResource(R.string.continue_game)) }
                        Button(onClick = onNewBalance) { Text(stringResource(R.string.new_game)) }
                    }
                } else {
                    Button(onClick = onNewBalance) { Text(stringResource(R.string.play)) }
                }
            }
        }
    }
}
