package com.stanisryz.logica.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import com.stanisryz.logica.shared.ui.generated.resources.Res

/**
 * The game sound effects. Their bytes are the generated `files/sounds/<fileName>.wav` resources
 * (see `tools/sounds/generate_game_sounds.py`), shared by every platform.
 */
enum class GameSound(
    val fileName: String,
) {
    TAP("tap"),
    CORRECT("correct"),
    MISTAKE("mistake"),
    HINT("hint"),
    MERGE("merge"),
    WIN("win"),
    FAIL("fail"),
    REWARD("reward"),
    ;

    /** The shared resource path, for `Res.readBytes` or `Res.getUri`. */
    val resourcePath: String get() = "files/sounds/$fileName.wav"
}

/**
 * Where shared presentation reports sound events. Hosts decide whether and how to play them (the
 * sound setting, the platform audio API, the Web lifecycle); the default plays nothing.
 */
fun interface GameSoundPlayer {
    fun play(sound: GameSound)
}

val LocalGameSounds = staticCompositionLocalOf<GameSoundPlayer> { GameSoundPlayer { } }

/**
 * Sounds for the cell games, derived from their state alone: a new correct cell, a mistake, a hint,
 * and the end of the attempt. Only increases count, so a retry or a restored state is silent, and
 * the first composition never plays anything.
 */
@Composable
internal fun CellGameSounds(
    correctCells: Int,
    mistakesUsed: Int,
    hintsUsed: Int,
    solved: Boolean,
    failed: Boolean,
) {
    val player = LocalGameSounds.current
    val last = remember { IntArray(3) { -1 } }
    val lastOutcome = remember { BooleanArray(2) }
    LaunchedEffect(correctCells, mistakesUsed, hintsUsed, solved, failed) {
        val first = last[0] < 0
        val sound =
            when {
                first -> null
                solved && !lastOutcome[0] -> GameSound.WIN
                failed && !lastOutcome[1] -> GameSound.FAIL
                hintsUsed > last[2] -> GameSound.HINT
                mistakesUsed > last[1] -> GameSound.MISTAKE
                correctCells > last[0] -> GameSound.CORRECT
                else -> null
            }
        last[0] = correctCells
        last[1] = mistakesUsed
        last[2] = hintsUsed
        lastOutcome[0] = solved
        lastOutcome[1] = failed
        sound?.let(player::play)
    }
}

/** The generated WAV bytes of [sound], for hosts that load sounds from memory or a file. */
suspend fun readGameSoundBytes(sound: GameSound): ByteArray = Res.readBytes(sound.resourcePath)

/** A URI of [sound] the platform can fetch directly (the Web audio player). */
fun gameSoundUri(sound: GameSound): String = Res.getUri(sound.resourcePath)
