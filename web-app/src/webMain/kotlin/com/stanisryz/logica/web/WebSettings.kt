@file:OptIn(ExperimentalWasmJsInterop::class)

package com.stanisryz.logica.web

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.stanisryz.logica.platform.PlatformLifecycleState
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import com.stanisryz.logica.ui.components.GameSound
import com.stanisryz.logica.ui.components.GameSoundPlayer
import com.stanisryz.logica.ui.components.gameSoundUri
import kotlinx.coroutines.flow.StateFlow

internal enum class WebThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

/**
 * The Web player's own preferences: theme, sound, and colour-blind region patterns. Like the
 * tutorial offers they belong to this browser only (one `logica_settings_v1` key), are never
 * Player-scoped or synced, and fall back to the defaults when storage is unavailable.
 */
internal object WebSettings {
    var themeMode by mutableStateOf(WebThemeMode.SYSTEM)
        private set
    var soundEnabled by mutableStateOf(true)
        private set
    var regionPatterns by mutableStateOf(false)
        private set

    init {
        runCatching { settingsStorageGet(SETTINGS_KEY) }.getOrNull()?.let(::decode)
    }

    fun updateThemeMode(mode: WebThemeMode) {
        themeMode = mode
        persist()
    }

    fun updateSoundEnabled(enabled: Boolean) {
        soundEnabled = enabled
        persist()
    }

    fun updateRegionPatterns(enabled: Boolean) {
        regionPatterns = enabled
        persist()
    }

    private fun decode(stored: String) {
        stored.split(';').forEach { entry ->
            val (key, value) = entry.split('=', limit = 2).takeIf { it.size == 2 } ?: return@forEach
            when (key) {
                "theme" -> WebThemeMode.entries.firstOrNull { it.name == value }?.let { themeMode = it }
                "sound" -> soundEnabled = value == "1"
                "patterns" -> regionPatterns = value == "1"
            }
        }
    }

    private fun persist() {
        val encoded = "theme=${themeMode.name};sound=${if (soundEnabled) 1 else 0};patterns=${if (regionPatterns) 1 else 0}"
        runCatching { settingsStorageSet(SETTINGS_KEY, encoded) }
    }
}

/**
 * Plays the shared generated sounds through Web Audio. Sounds are fetched and decoded on the first
 * play (a user gesture, as browsers require), stay silent while the effective lifecycle is not
 * ACTIVE (a fullscreen ad, a hidden tab, a Yandex pause), and the audio context is suspended then.
 */
internal class WebGameSoundPlayer(
    private val lifecycleState: StateFlow<PlatformLifecycleState>,
) : GameSoundPlayer {
    private var preloaded = false

    /** Fetches and decodes every sound once, after the player has already tapped into a game. */
    fun preload() {
        if (preloaded || !WebSettings.soundEnabled) return
        preloaded = true
        runCatching { GameSound.entries.forEach { webAudioPreload(gameSoundUri(it)) } }
    }

    override fun play(sound: GameSound) {
        if (!WebSettings.soundEnabled || lifecycleState.value != PlatformLifecycleState.ACTIVE) return
        preload()
        runCatching { webAudioPlay(gameSoundUri(sound), if (sound == GameSound.TAP) TAP_VOLUME else EVENT_VOLUME) }
    }

    fun onLifecycle(state: PlatformLifecycleState) {
        runCatching { if (state == PlatformLifecycleState.ACTIVE) webAudioResume() else webAudioSuspend() }
    }

    private companion object {
        const val TAP_VOLUME = 0.45
        const val EVENT_VOLUME = 0.8
    }
}

/** The small Web settings sheet opened from the tab bar's gear. */
@Composable
internal fun WebSettingsDialog(onDismiss: () -> Unit) {
    PauseGameKeysWhileShown()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Настройки") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Тема", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                listOf(
                    WebThemeMode.SYSTEM to "Как в системе",
                    WebThemeMode.LIGHT to "Светлая",
                    WebThemeMode.DARK to "Тёмная",
                ).forEach { (mode, label) ->
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = WebSettings.themeMode == mode,
                                    role = Role.RadioButton,
                                    onClick = { WebSettings.updateThemeMode(mode) },
                                ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = WebSettings.themeMode == mode, onClick = null)
                        Text(label, modifier = Modifier.padding(start = 8.dp))
                    }
                }
                SettingSwitch("Звук", WebSettings.soundEnabled, WebSettings::updateSoundEnabled)
                SettingSwitch(
                    "Узоры регионов в «Коронах»",
                    WebSettings.regionPatterns,
                    WebSettings::updateRegionPatterns,
                    supporting = "Помогают различать регионы без цвета",
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Готово") } },
    )
}

@Composable
private fun SettingSwitch(
    label: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    supporting: String? = null,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(role = Role.Switch) { onChange(!checked) }
                .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label)
            supporting?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

private const val SETTINGS_KEY = "logica_settings_v1"

private fun settingsStorageGet(key: String): String? = js("globalThis.localStorage.getItem(key)")

private fun settingsStorageSet(
    key: String,
    value: String,
) {
    js("globalThis.localStorage.setItem(key, value)")
}

private fun webAudioPreload(url: String) {
    js(
        """
        (function () {
          var a = globalThis.__logicaAudio || (globalThis.__logicaAudio = { ctx: null, buffers: {}, pending: {} });
          if (!a.ctx) {
            var C = globalThis.AudioContext || globalThis.webkitAudioContext;
            if (!C) return;
            a.ctx = new C();
          }
          if (a.buffers[url] || a.pending[url]) return;
          a.pending[url] = true;
          fetch(url).then(function (r) { return r.arrayBuffer(); })
            .then(function (d) { return a.ctx.decodeAudioData(d); })
            .then(function (b) { a.buffers[url] = b; delete a.pending[url]; })
            .catch(function () { delete a.pending[url]; });
        })()
        """,
    )
}

private fun webAudioPlay(
    url: String,
    volume: Double,
) {
    js(
        """
        (function () {
          var a = globalThis.__logicaAudio;
          if (!a || !a.ctx) return;
          if (a.ctx.state === 'suspended') a.ctx.resume();
          var b = a.buffers[url];
          if (!b) return;
          var src = a.ctx.createBufferSource();
          src.buffer = b;
          var g = a.ctx.createGain();
          g.gain.value = volume;
          src.connect(g);
          g.connect(a.ctx.destination);
          src.start();
        })()
        """,
    )
}

private fun webAudioSuspend() {
    js("(function () { var a = globalThis.__logicaAudio; if (a && a.ctx && a.ctx.state === 'running') a.ctx.suspend(); })()")
}

private fun webAudioResume() {
    js("(function () { var a = globalThis.__logicaAudio; if (a && a.ctx && a.ctx.state === 'suspended') a.ctx.resume(); })()")
}

/**
 * The last Catalog game and difficulty this browser started, for the hub's Continue card. It is a
 * browser-local convenience (`logica_last_played_v1`), never Player-scoped or synced; the level it
 * shows is always read from the bound Player's own Catalog progress.
 */
internal object WebLastPlayed {
    var value by mutableStateOf(read())
        private set

    fun record(
        puzzleType: PuzzleType,
        difficulty: Difficulty,
    ) {
        val next = puzzleType to difficulty
        if (value == next) return
        value = next
        runCatching { settingsStorageSet(LAST_PLAYED_KEY, "${puzzleType.name}:${difficulty.name}") }
    }

    private fun read(): Pair<PuzzleType, Difficulty>? {
        val stored = runCatching { settingsStorageGet(LAST_PLAYED_KEY) }.getOrNull() ?: return null
        val (type, difficulty) = stored.split(':').takeIf { it.size == 2 } ?: return null
        val puzzleType =
            PuzzleType.entries
                .firstOrNull { it.name == type } ?: return null
        val level =
            Difficulty.entries
                .firstOrNull { it.name == difficulty } ?: return null
        return puzzleType to level
    }
}

private const val LAST_PLAYED_KEY = "logica_last_played_v1"
