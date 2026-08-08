package com.stanisryz.logica.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.stanisryz.logica.settings.ThemeMode
import com.stanisryz.logica.settings.UserSettings

@Composable
fun SettingsScreen(
    settings: UserSettings,
    onThemeModeChanged: (ThemeMode) -> Unit,
    onSoundEnabledChanged: (Boolean) -> Unit,
    onHapticsEnabledChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
    ) {
        Text("Тема", style = MaterialTheme.typography.titleMedium)
        ThemeMode.entries.forEach { themeMode ->
            ThemeModeOption(
                themeMode = themeMode,
                selected = settings.themeMode == themeMode,
                onSelected = { onThemeModeChanged(themeMode) },
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

        SettingsSwitch(
            label = "Звук",
            checked = settings.soundEnabled,
            onCheckedChange = onSoundEnabledChanged,
        )
        SettingsSwitch(
            label = "Вибрация",
            checked = settings.hapticsEnabled,
            onCheckedChange = onHapticsEnabledChanged,
        )
    }
}

@Composable
private fun ThemeModeOption(
    themeMode: ThemeMode,
    selected: Boolean,
    onSelected: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onSelected)
                .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = onSelected,
        )
        Text(
            text = themeMode.label,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

@Composable
private fun SettingsSwitch(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { onCheckedChange(!checked) }
                .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label)
        Spacer(modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

private val ThemeMode.label: String
    get() =
        when (this) {
            ThemeMode.SYSTEM -> "Как в системе"
            ThemeMode.LIGHT -> "Светлая"
            ThemeMode.DARK -> "Тёмная"
        }
