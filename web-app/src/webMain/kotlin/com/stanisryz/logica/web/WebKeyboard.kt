@file:OptIn(ExperimentalWasmJsInterop::class)

package com.stanisryz.logica.web

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.staticCompositionLocalOf
import com.stanisryz.logica.ui.components.GameKey
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlin.js.ExperimentalWasmJsInterop

/**
 * Desktop keyboard input for Web gameplay. One document `keydown` listener lives for the page; the
 * host enables forwarding only while a puzzle is actively played and nothing covers the board, and
 * only forwarded keys suppress the browser default (so arrows never scroll the Yandex frame then).
 */
internal class WebKeyboard {
    private val mutableKeys = MutableSharedFlow<GameKey>(extraBufferCapacity = KEY_BUFFER)
    val keys: SharedFlow<GameKey> = mutableKeys.asSharedFlow()

    /** Set by the host from its own gameplay state; nothing is forwarded while false. */
    var enabled: Boolean = false

    /** Dialogs shown over the board; while any is open, keys never reach the puzzle underneath. */
    private var openDialogs = 0

    fun dialogShown() {
        openDialogs++
    }

    fun dialogHidden() {
        openDialogs = (openDialogs - 1).coerceAtLeast(0)
    }

    fun install() {
        addGameKeyListener { key, code ->
            if (!enabled || openDialogs > 0) return@addGameKeyListener false
            val gameKey = webGameKeyOf(key, code) ?: return@addGameKeyListener false
            mutableKeys.tryEmit(gameKey)
        }
    }

    private companion object {
        const val KEY_BUFFER = 32
    }
}

/** The page keyboard, provided by the host so dialogs over the board can pause it. */
internal val LocalWebKeyboard = staticCompositionLocalOf<WebKeyboard?> { null }

/** Call inside a dialog shown over gameplay: keys stay with the dialog while it is visible. */
@Composable
internal fun PauseGameKeysWhileShown() {
    val keyboard = LocalWebKeyboard.current ?: return
    DisposableEffect(keyboard) {
        keyboard.dialogShown()
        onDispose { keyboard.dialogHidden() }
    }
}

/**
 * Maps a browser key event to a [GameKey]. Letters are Russian: a Cyrillic `key` is used as is, and
 * with any other layout the physical key position (`code`) maps through ЙЦУКЕН, so players do not
 * have to switch their keyboard layout to play Word.
 */
internal fun webGameKeyOf(
    key: String,
    code: String,
): GameKey? {
    when (key) {
        "ArrowUp" -> return GameKey.Up
        "ArrowDown" -> return GameKey.Down
        "ArrowLeft" -> return GameKey.Left
        "ArrowRight" -> return GameKey.Right
        "Enter" -> return GameKey.Enter
        "Backspace" -> return GameKey.Backspace
        "Delete" -> return GameKey.Delete
    }
    if (key.length != 1) return null
    val character = key.single()
    if (character in '0'..'9') return GameKey.Digit(character - '0')
    val lower = character.lowercaseChar()
    if (lower in 'а'..'я' || lower == 'ё') return GameKey.Letter(lower)
    return RUSSIAN_LETTER_BY_CODE[code]?.let(GameKey::Letter)
}

private val RUSSIAN_LETTER_BY_CODE: Map<String, Char> =
    mapOf(
        "KeyQ" to 'й',
        "KeyW" to 'ц',
        "KeyE" to 'у',
        "KeyR" to 'к',
        "KeyT" to 'е',
        "KeyY" to 'н',
        "KeyU" to 'г',
        "KeyI" to 'ш',
        "KeyO" to 'щ',
        "KeyP" to 'з',
        "BracketLeft" to 'х',
        "BracketRight" to 'ъ',
        "KeyA" to 'ф',
        "KeyS" to 'ы',
        "KeyD" to 'в',
        "KeyF" to 'а',
        "KeyG" to 'п',
        "KeyH" to 'р',
        "KeyJ" to 'о',
        "KeyK" to 'л',
        "KeyL" to 'д',
        "Semicolon" to 'ж',
        "Quote" to 'э',
        "KeyZ" to 'я',
        "KeyX" to 'ч',
        "KeyC" to 'с',
        "KeyV" to 'м',
        "KeyB" to 'и',
        "KeyN" to 'т',
        "KeyM" to 'ь',
        "Comma" to 'б',
        "Period" to 'ю',
        "Backquote" to 'ё',
    )

/** Shortcuts with modifiers (copy, reload, …) and IME composition always stay with the browser. */
private fun addGameKeyListener(onKey: (String, String) -> Boolean): Unit =
    js(
        "globalThis.document.addEventListener('keydown', (event) => { " +
            "if (event.ctrlKey || event.metaKey || event.altKey || event.isComposing) return; " +
            "if (onKey(event.key, event.code)) event.preventDefault(); })",
    )
