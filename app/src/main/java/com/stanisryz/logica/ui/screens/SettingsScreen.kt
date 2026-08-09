package com.stanisryz.logica.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import com.stanisryz.logica.R
import com.stanisryz.logica.settings.ThemeMode
import com.stanisryz.logica.settings.UserSettings
import com.stanisryz.logica.ui.components.LogicaCard
import com.stanisryz.logica.ui.components.ScreenColumn
import com.stanisryz.logica.ui.components.ScreenSection
import com.stanisryz.logica.ui.components.SectionTitle
import com.stanisryz.logica.ui.theme.LogicaSpacing

@Composable
internal fun SettingsScreen(
    settings: UserSettings,
    onThemeModeChanged: (ThemeMode) -> Unit,
    onSoundEnabledChanged: (Boolean) -> Unit,
    onHapticsEnabledChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    ScreenColumn(modifier) {
        ScreenSection(title = stringResource(R.string.settings_appearance)) {
            LogicaCard(verticalSpacing = LogicaSpacing.text) {
                SectionTitle(stringResource(R.string.settings_theme))
                Column(Modifier.fillMaxWidth().selectableGroup()) {
                    ThemeMode.entries.forEach { themeMode ->
                        ThemeModeOption(
                            themeMode = themeMode,
                            selected = settings.themeMode == themeMode,
                            onSelected = { onThemeModeChanged(themeMode) },
                        )
                    }
                }
            }
        }

        ScreenSection(title = stringResource(R.string.settings_gameplay)) {
            LogicaCard(verticalSpacing = LogicaSpacing.text) {
                SettingsSwitch(
                    label = stringResource(R.string.settings_sound),
                    checked = settings.soundEnabled,
                    onCheckedChange = onSoundEnabledChanged,
                )
                SettingsSwitch(
                    label = stringResource(R.string.settings_haptics),
                    checked = settings.hapticsEnabled,
                    onCheckedChange = onHapticsEnabledChanged,
                )
            }
        }
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
                .selectable(selected = selected, role = Role.RadioButton, onClick = onSelected)
                .padding(vertical = LogicaSpacing.text),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(LogicaSpacing.text),
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(stringResource(themeMode.labelResource()), style = MaterialTheme.typography.bodyLarge)
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
                .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
                .padding(vertical = LogicaSpacing.text),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(LogicaSpacing.action),
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null)
    }
}

private fun ThemeMode.labelResource(): Int =
    when (this) {
        ThemeMode.SYSTEM -> R.string.settings_theme_system
        ThemeMode.LIGHT -> R.string.settings_theme_light
        ThemeMode.DARK -> R.string.settings_theme_dark
    }
