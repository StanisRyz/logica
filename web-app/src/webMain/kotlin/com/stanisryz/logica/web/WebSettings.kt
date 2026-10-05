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
import com.stanisryz.logica.web.generated.resources.web_done
import com.stanisryz.logica.web.generated.resources.web_settings
import com.stanisryz.logica.web.generated.resources.web_settings_sound
import com.stanisryz.logica.web.generated.resources.web_settings_theme
import com.stanisryz.logica.web.generated.resources.web_theme_dark
import com.stanisryz.logica.web.generated.resources.web_theme_light
import com.stanisryz.logica.web.generated.resources.web_theme_system
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.compose.resources.stringResource
import com.stanisryz.logica.web.generated.resources.Res as WebRes

internal enum class WebThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

/**
 * The Web player's own preferences: theme and sound. Like the tutorial offers they belong to this
 * browser only (one `logica_settings_v1` key), are never Player-scoped or synced, and fall back to
 * the defaults when storage is unavailable.
 */
internal object WebSettings {
    var themeMode by mutableStateOf(WebThemeMode.SYSTEM)
        private set
    var soundEnabled by mutableStateOf(true)
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

    private fun decode(stored: String) {
        stored.split(';').forEach { entry ->
            val (key, value) = entry.split('=', limit = 2).takeIf { it.size == 2 } ?: return@forEach
            when (key) {
                "theme" -> WebThemeMode.entries.firstOrNull { it.name == value }?.let { themeMode = it }
                "sound" -> soundEnabled = value == "1"
            }
        }
    }

    private fun persist() {
        val encoded = "theme=${themeMode.name};sound=${if (soundEnabled) 1 else 0}"
        runCatching { settingsStorageSet(SETTINGS_KEY, encoded) }
    }
}

/**
 * Plays the shared generated sounds through Web Audio. Sounds are fetched and decoded once the
 * player has tapped into a game (and later, if Sound was off then), stay silent while the effective
 * lifecycle is not ACTIVE (a fullscreen ad, a hidden tab, a Yandex pause) or Sound is off, and the
 * audio context is suspended then; only an allowed context is woken by a tap. A sound requested
 * before its file is decoded still plays if it is ready within [LATE_SOUND_MILLIS].
 */
internal class WebGameSoundPlayer(
    private val lifecycleState: StateFlow<PlatformLifecycleState>,
) : GameSoundPlayer {
    private var preloadRequested = false
    private var preloaded = false
    private val urls = mutableMapOf<GameSound, String>()

    init {
        runCatching { webAudioInstall() }
    }

    /** Fetches and decodes every sound once, after the player has already tapped into a game. */
    fun preload() {
        preloadRequested = true
        if (preloaded || !WebSettings.soundEnabled) return
        preloaded = true
        runCatching { GameSound.entries.forEach { webAudioLoad(urlOf(it)) } }
    }

    override fun play(sound: GameSound) {
        if (!webSoundAudible(WebSettings.soundEnabled, lifecycleState.value)) return
        preload()
        runCatching {
            webAudioPlay(urlOf(sound), if (sound == GameSound.TAP) TAP_VOLUME else EVENT_VOLUME, LATE_SOUND_MILLIS)
        }
    }

    /** Follows the effective lifecycle and the Sound setting: the context runs only while both allow it. */
    fun onHostAudio(
        lifecycle: PlatformLifecycleState,
        soundEnabled: Boolean,
    ) {
        val allowed = webSoundAudible(soundEnabled, lifecycle)
        runCatching { webAudioSetAllowed(allowed) }
        if (allowed && preloadRequested) preload()
    }

    // `Res.getUri` prefixes the page's own path, which breaks under a page named `index.html`
    // (Yandex serves the game that way from a nested folder), so the URL is resolved relative to the page.
    private fun urlOf(sound: GameSound): String =
        urls.getOrPut(sound) { runCatching { webAudioResolve(gameSoundUri(sound)) }.getOrDefault("") }

    private companion object {
        const val TAP_VOLUME = 0.45
        const val EVENT_VOLUME = 0.8

        /** A sound that would start later than this after its request is dropped: late is worse than silent. */
        const val LATE_SOUND_MILLIS = 300.0
    }
}

/** Whether game sounds may play at all: the Sound setting is on and the effective lifecycle is ACTIVE. */
internal fun webSoundAudible(
    soundEnabled: Boolean,
    lifecycle: PlatformLifecycleState,
): Boolean = soundEnabled && lifecycle == PlatformLifecycleState.ACTIVE

/** The small Web settings sheet opened from the tab bar's gear. */
@Composable
internal fun WebSettingsDialog(onDismiss: () -> Unit) {
    PauseGameKeysWhileShown()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(WebRes.string.web_settings)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(WebRes.string.web_settings_theme),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                listOf(
                    WebThemeMode.SYSTEM to stringResource(WebRes.string.web_theme_system),
                    WebThemeMode.LIGHT to stringResource(WebRes.string.web_theme_light),
                    WebThemeMode.DARK to stringResource(WebRes.string.web_theme_dark),
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
                SettingSwitch(stringResource(WebRes.string.web_settings_sound), WebSettings.soundEnabled, WebSettings::updateSoundEnabled)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(WebRes.string.web_done)) } },
    )
}

@Composable
private fun SettingSwitch(
    label: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(role = Role.Switch) { onChange(!checked) }
                .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f))
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

/**
 * Installs the one page-wide audio state: the context (created on the first load), decoded buffers,
 * loads in flight, the latest waiting request per sound, and whether the host allows sound now.
 */
private fun webAudioInstall() {
    js(
        """
        (function () {
          if (globalThis.__logicaAudio) return;
          var now = function () { return globalThis.performance ? performance.now() : Date.now(); };
          var quiet = function (p) { if (p && p.catch) p.catch(function () {}); };
          var a = globalThis.__logicaAudio = { ctx: null, buffers: {}, pending: {}, waiting: {}, allowed: false };
          a.ensure = function () {
            if (a.ctx) return true;
            var C = globalThis.AudioContext || globalThis.webkitAudioContext;
            if (!C) return false;
            a.ctx = new C();
            // Mobile Safari starts audio only from inside a tap handler while sounds are played later
            // from state changes, so a tap resumes the context, but only while the host allows sound.
            var unlock = function () { if (a.allowed && a.ctx.state !== 'running') quiet(a.ctx.resume()); };
            ['pointerdown', 'touchend', 'keydown'].forEach(function (e) { globalThis.addEventListener(e, unlock, true); });
            if (!a.allowed) quiet(a.ctx.suspend());
            return true;
          };
          a.start = function (b, volume) {
            if (a.ctx.state === 'suspended') quiet(a.ctx.resume());
            var src = a.ctx.createBufferSource();
            src.buffer = b;
            var g = a.ctx.createGain();
            g.gain.value = volume;
            src.connect(g);
            g.connect(a.ctx.destination);
            src.start(0);
          };
          a.load = function (url) {
            if (!url || !a.ensure() || a.buffers[url] || a.pending[url]) return;
            a.pending[url] = true;
            fetch(url).then(function (r) { if (!r.ok) throw new Error('HTTP ' + r.status); return r.arrayBuffer(); })
              .then(function (d) {
                // Old webkitAudioContext only knows the callback form; newer ones also return a promise.
                return new Promise(function (resolve, reject) {
                  var p = a.ctx.decodeAudioData(d, resolve, reject);
                  if (p && p.then) p.then(resolve, reject);
                });
              })
              .then(function (b) {
                a.buffers[url] = b;
                delete a.pending[url];
                var w = a.waiting[url];
                delete a.waiting[url];
                if (w && a.allowed && now() - w.at <= w.lateMs) a.start(b, w.volume);
              })
              .catch(function () { delete a.pending[url]; delete a.waiting[url]; });
          };
          a.play = function (url, volume, lateMs) {
            if (!a.allowed || !a.ensure()) return;
            var b = a.buffers[url];
            if (b) { a.start(b, volume); return; }
            a.waiting[url] = { at: now(), volume: volume, lateMs: lateMs };
            a.load(url);
          };
          a.setAllowed = function (allowed) {
            a.allowed = allowed;
            if (!allowed) a.waiting = {};
            if (!a.ctx) return;
            if (allowed && a.ctx.state === 'suspended') quiet(a.ctx.resume());
            if (!allowed && a.ctx.state === 'running') quiet(a.ctx.suspend());
          };
        })()
        """,
    )
}

private fun webAudioResolve(uri: String): String =
    js(
        """
        (function () {
          var l = globalThis.location;
          if (!l || !globalThis.document) return uri;
          var page = l.origin + l.pathname;
          var relative = uri.indexOf(page) === 0 ? uri.substring(page.length) : uri;
          return new URL(relative, document.baseURI).href;
        })()
        """,
    )

private fun webAudioLoad(url: String) {
    js("globalThis.__logicaAudio.load(url)")
}

private fun webAudioPlay(
    url: String,
    volume: Double,
    lateMillis: Double,
) {
    js("globalThis.__logicaAudio.play(url, volume, lateMillis)")
}

private fun webAudioSetAllowed(allowed: Boolean) {
    js("globalThis.__logicaAudio.setAllowed(allowed)")
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

/**
 * Which achievements were already announced to this browser's Player scope
 * (`logica_achievements_seen_v1:<scope>`). The first look at a scope only records what is already
 * reached, so existing players are not greeted by a burst of old achievements.
 */
internal object WebAchievementsSeen {
    fun newlyUnlocked(
        scope: WebCatalogProgressScope,
        unlocked: Set<String>,
    ): List<String> {
        val key = "$SEEN_KEY_PREFIX:${scope.keySuffix}"
        val stored = runCatching { settingsStorageGet(key) }.getOrNull()
        val seen = stored?.split(',')?.filter(String::isNotEmpty)?.toSet()
        val fresh = if (seen == null) emptyList() else unlocked.filterNot(seen::contains)
        if (seen == null || fresh.isNotEmpty()) {
            runCatching { settingsStorageSet(key, (seen.orEmpty() + unlocked).joinToString(",")) }
        }
        return fresh
    }

    private const val SEEN_KEY_PREFIX = "logica_achievements_seen_v1"
}
