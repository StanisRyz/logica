@file:OptIn(ExperimentalWasmJsInterop::class)

package com.stanisryz.logica.web

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.stanisryz.logica.platform.AppLog
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import com.stanisryz.logica.shared.ui.generated.resources.licenses_title
import com.stanisryz.logica.ui.components.GameSound
import com.stanisryz.logica.ui.components.GameSoundPlayer
import com.stanisryz.logica.ui.components.LicensesContent
import com.stanisryz.logica.ui.components.gameSoundUri
import com.stanisryz.logica.ui.components.licenseNoticesFor
import com.stanisryz.logica.web.generated.resources.web_done
import com.stanisryz.logica.web.generated.resources.web_settings
import com.stanisryz.logica.web.generated.resources.web_settings_sound
import com.stanisryz.logica.web.generated.resources.web_settings_theme
import com.stanisryz.logica.web.generated.resources.web_theme_dark
import com.stanisryz.logica.web.generated.resources.web_theme_light
import com.stanisryz.logica.web.generated.resources.web_theme_system
import org.jetbrains.compose.resources.stringResource
import com.stanisryz.logica.shared.ui.generated.resources.Res as SharedRes
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

    /** Told at once whenever Sound is switched, so the audio context follows inside the same tap. */
    var onSoundEnabledChanged: (() -> Unit)? = null

    fun updateSoundEnabled(enabled: Boolean) {
        soundEnabled = enabled
        persist()
        onSoundEnabledChanged?.invoke()
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
 * Plays the shared generated sounds through Web Audio. Whether sound may play is decided here and
 * handed to the page's audio state synchronously, from the host's audio conditions and the Sound
 * setting ([webSoundAudible]): never from a later frame, because a tap must find it already decided.
 * The page creates and wakes its audio context only inside a tap or key press while sound is allowed
 * (mobile Safari starts audio from nothing else) and starts loading every sound then, so the first
 * move already has its buffer; a sound requested before its file is decoded still plays if it is
 * ready within [LATE_SOUND_MILLIS]. A failed load or a context that a tap could not start is logged
 * once through [AppLog].
 */
internal class WebGameSoundPlayer(
    private val lifecycle: WebHostLifecycle,
) : GameSoundPlayer {
    private var allowed = false

    init {
        runCatching {
            webAudioInstall(onGesture = lifecycle::onPageInteraction) { problem -> AppLog.warn(TAG, problem) }
            webAudioSetSounds(GameSound.entries.joinToString("\n") { resolveSoundUrl(it) })
        }.onFailure { AppLog.warn(TAG, "Web Audio could not be installed", it) }
        lifecycle.onAudioConditionsChanged = ::refresh
        WebSettings.onSoundEnabledChanged = ::refresh
        refresh()
    }

    /** Called when a game opens: loads every sound if a tap has already started the audio context. */
    fun preload() {
        if (allowed) runCatching { webAudioLoadAll() }
    }

    override fun play(sound: GameSound) {
        if (!allowed) return
        runCatching {
            webAudioPlay(resolveSoundUrl(sound), if (sound == GameSound.TAP) TAP_VOLUME else EVENT_VOLUME, LATE_SOUND_MILLIS)
        }
    }

    private fun refresh() {
        allowed = webSoundAudible(WebSettings.soundEnabled, lifecycle.audioConditionsMet)
        val blockers = listOfNotNull("Sound off".takeUnless { WebSettings.soundEnabled }, lifecycle.audioBlockers.ifEmpty { null })
        runCatching { webAudioSetAllowed(allowed, blockers.joinToString()) }
    }

    private companion object {
        const val TAG = "WebSound"
        const val TAP_VOLUME = 0.45
        const val EVENT_VOLUME = 0.8

        /** A sound that would start later than this after its request is dropped: late is worse than silent. */
        const val LATE_SOUND_MILLIS = 300.0
    }
}

private val soundUrls = mutableMapOf<GameSound, String>()

// `Res.getUri` prefixes the page's own path, which breaks under a page named `index.html`
// (Yandex serves the game that way from a nested folder), so the URL is resolved relative to the page.
private fun resolveSoundUrl(sound: GameSound): String =
    soundUrls.getOrPut(sound) { runCatching { webAudioResolve(gameSoundUri(sound)) }.getOrDefault("") }

/**
 * Whether game sounds may play: the Sound setting is on and the host's audio conditions hold — the
 * host started, its tab visible, no Yandex pause, no fullscreen advertisement, and no window blur
 * since the player's last focus, tap, or key press. A game that never had focus still sounds: inside
 * the Yandex page `document.hasFocus()` starts false while the player plays.
 */
internal fun webSoundAudible(
    soundEnabled: Boolean,
    audioConditionsMet: Boolean,
): Boolean = soundEnabled && audioConditionsMet

/** The small Web settings sheet opened from the tab bar's gear. */
@Composable
internal fun WebSettingsDialog(onDismiss: () -> Unit) {
    PauseGameKeysWhileShown()
    var licensesOpen by remember { mutableStateOf(false) }
    if (licensesOpen) {
        WebLicensesDialog(onClose = { licensesOpen = false })
        return
    }
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
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable(role = Role.Button) { licensesOpen = true }
                            .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(SharedRes.string.licenses_title), modifier = Modifier.weight(1f))
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(WebRes.string.web_done)) } },
    )
}

/** The shared «Licences» page over the whole window, closed by its button, Back, or Esc. */
@Composable
private fun WebLicensesDialog(onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(WebRes.string.web_done))
                    }
                    Text(stringResource(SharedRes.string.licenses_title), style = MaterialTheme.typography.titleLarge)
                }
                LicensesContent(licenseNoticesFor(android = false), Modifier.fillMaxWidth().weight(1f))
            }
        }
    }
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

/** Fades out and removes the `index.html` loader once Compose has drawn its first frame. */
internal fun removeStartupLoader() {
    js(
        """
        (function () {
          var loader = globalThis.document && document.getElementById('logica-loader');
          if (!loader) return;
          loader.style.opacity = '0';
          setTimeout(function () { if (loader.parentNode) loader.parentNode.removeChild(loader); }, 300);
        })()
        """,
    )
}

private fun settingsStorageGet(key: String): String? = js("globalThis.localStorage.getItem(key)")

private fun settingsStorageSet(
    key: String,
    value: String,
) {
    js("globalThis.localStorage.setItem(key, value)")
}

/**
 * Installs the one page-wide audio state: the context, decoded buffers, loads in flight, the latest
 * waiting request per sound, the sounds to load, whether the host allows sound now, and the
 * problems seen (`globalThis.__logicaAudio`, readable from the browser console). The tap and key
 * listener is installed here, at startup: inside the gesture, while sound is allowed, it creates or
 * wakes the context and starts every load. Outside a gesture nothing creates a context.
 */
private fun webAudioInstall(
    onGesture: () -> Unit,
    onProblem: (String) -> Unit,
) {
    js(
        """
        (function () {
          if (globalThis.__logicaAudio) return;
          var now = function () { return globalThis.performance ? performance.now() : Date.now(); };
          var quiet = function (p) { if (p && p.catch) p.catch(function () {}); };
          var a = globalThis.__logicaAudio = {
            ctx: null, buffers: {}, pending: {}, waiting: {}, allowed: false, blockedBy: '', urls: [], problems: [], reported: {}, gestures: 0
          };
          a.report = function (kind, text) {
            a.problems.push(text);
            if (a.reported[kind]) return;
            a.reported[kind] = true;
            try { onProblem(text); } catch (e) {}
          };
          var C = globalThis.AudioContext || globalThis.webkitAudioContext;
          // Inside a tap or key press only: mobile Safari starts audio from nothing else, and a context
          // made earlier would stay suspended. Loads start here too, so the first move has its sound.
          var gesture = function () {
            // The host hears of the tap first, so a blur ended by this very tap allows sound again now.
            try { onGesture(); } catch (e) {}
            if (!a.allowed || !C) return;
            a.gestures++;
            if (!a.ctx) {
              try { a.ctx = new C(); } catch (e) { a.report('create', 'Web Audio context could not be created: ' + e); return; }
            }
            if (a.ctx.state !== 'running') {
              quiet(a.ctx.resume());
              var ctx = a.ctx;
              setTimeout(function () {
                if (a.allowed && ctx.state !== 'running') a.report('start', 'Web Audio context did not start after a tap: ' + ctx.state);
              }, 1000);
            }
            a.loadAll();
          };
          ['pointerdown', 'pointerup', 'touchend', 'click', 'keydown'].forEach(function (e) { globalThis.addEventListener(e, gesture, true); });
          a.start = function (b, volume) {
            var src = a.ctx.createBufferSource();
            src.buffer = b;
            var g = a.ctx.createGain();
            g.gain.value = volume;
            src.connect(g);
            g.connect(a.ctx.destination);
            src.start(0);
          };
          var name = function (url) { return String(url).replace(/^.*\//, ''); };
          a.load = function (url) {
            if (!url || !a.ctx || a.buffers[url] || a.pending[url]) return;
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
              .catch(function (e) {
                delete a.pending[url];
                delete a.waiting[url];
                a.report('load', 'Sound ' + name(url) + ' could not be loaded: ' + e);
              });
          };
          a.loadAll = function () { a.urls.forEach(a.load); };
          a.play = function (url, volume, lateMs) {
            // No context yet means no tap yet, and nothing could be heard anyway.
            if (!a.allowed || !a.ctx) return;
            var b = a.buffers[url];
            if (b) { a.start(b, volume); return; }
            a.waiting[url] = { at: now(), volume: volume, lateMs: lateMs };
            a.load(url);
          };
          a.setAllowed = function (allowed, blockers) {
            a.allowed = allowed;
            // Why sound is off right now, for the console: Sound off, Yandex pause, fullscreen ad, hidden tab.
            a.blockedBy = blockers || '';
            if (!allowed) a.waiting = {};
            if (!a.ctx) return;
            // A context a tap already started may be woken again outside a gesture; the next tap retries.
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

private fun webAudioSetSounds(urls: String) {
    js("globalThis.__logicaAudio.urls = urls.split('\\n').filter(function (u) { return u; })")
}

private fun webAudioLoadAll() {
    js("globalThis.__logicaAudio.loadAll()")
}

private fun webAudioPlay(
    url: String,
    volume: Double,
    lateMillis: Double,
) {
    js("globalThis.__logicaAudio.play(url, volume, lateMillis)")
}

private fun webAudioSetAllowed(
    allowed: Boolean,
    blockers: String,
) {
    js("globalThis.__logicaAudio.setAllowed(allowed, blockers)")
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
