package com.stanisryz.logica.word

import com.stanisryz.logica.puzzle.core.word.RussianWordNormalizer
import com.stanisryz.logica.puzzle.core.word.WordAllowedGuesses
import com.stanisryz.logica.puzzle.core.word.WordGameEngine
import com.stanisryz.logica.puzzle.core.word.WordGameState
import com.stanisryz.logica.puzzle.core.word.WordGameStatus
import com.stanisryz.logica.puzzle.core.word.WordPuzzle

internal data class EncodedWordSession(
    val gameplayPayload: String,
    val moveHistoryPayload: String,
    val hintsUsed: Int,
    val status: String,
)

/**
 * Persists only the current unfinished input and the submitted words. Letter feedback is never
 * stored: it is recomputed from the regenerated puzzle during restore, so the answer stays the single
 * source of truth.
 */
internal object WordSessionCodec {
    const val SESSION_FORMAT_VERSION = 1

    fun encode(
        puzzle: WordPuzzle,
        game: WordGameState,
    ): EncodedWordSession {
        require(game.puzzleId == puzzle.id) { "Word game belongs to a different puzzle." }
        require(game.wordLength == puzzle.wordLength) { "Word game length does not match its puzzle." }
        val gameplayPayload =
            buildString {
                appendLine("length=${puzzle.wordLength}")
                append("input=${game.currentInput.ifEmpty { EMPTY_MARKER }}")
            }
        return EncodedWordSession(
            gameplayPayload = gameplayPayload,
            moveHistoryPayload = game.attempts.joinToString(separator = "\n") { it.word },
            // Word has no hint mechanic; the shared session column stays zero.
            hintsUsed = 0,
            status = game.status.name,
        )
    }

    fun decode(
        puzzle: WordPuzzle,
        allowedGuesses: WordAllowedGuesses,
        sessionFormatVersion: Int,
        gameplayPayload: String,
        moveHistoryPayload: String,
        hintsUsed: Int,
        status: String,
    ): WordGameState {
        require(sessionFormatVersion == SESSION_FORMAT_VERSION) {
            "Unsupported Word session format version: $sessionFormatVersion."
        }
        require(hintsUsed == 0) { "Word sessions never record hint usage." }

        val gameplay = gameplayPayload.parseGameplayPayload()
        val length = gameplay.getValue("length").toIntOrNull() ?: error("Invalid saved word length.")
        require(length == puzzle.wordLength) { "Saved word length does not match the regenerated puzzle." }

        val savedInput = gameplay.getValue("input")
        val currentInput = if (savedInput == EMPTY_MARKER) "" else savedInput.requireInputPrefix(puzzle.wordLength)
        val submittedWords = moveHistoryPayload.decodeSubmittedWords(puzzle.wordLength)

        val game = WordGameEngine(puzzle, allowedGuesses).restore(currentInput, submittedWords)
        val savedStatus =
            runCatching { WordGameStatus.valueOf(status) }
                .getOrElse { error("Invalid saved game status.") }
        require(game.status == savedStatus) { "Saved game status does not match the restored state." }
        return game
    }

    private fun String.parseGameplayPayload(): Map<String, String> {
        val entries =
            lineSequence()
                .filter(String::isNotBlank)
                .map { line ->
                    val parts = line.split('=', limit = 2)
                    require(parts.size == 2 && parts[0].isNotBlank()) { "Invalid gameplay payload line." }
                    parts[0] to parts[1]
                }.toList()
        val values = entries.toMap()
        require(entries.size == values.size) { "Gameplay payload contains duplicate fields." }
        require(values.keys == setOf("length", "input")) { "Invalid gameplay payload fields." }
        return values
    }

    /** A partial guess is any normalized prefix shorter than a full word. */
    private fun String.requireInputPrefix(expectedLength: Int): String {
        require(length in 1 until expectedLength) { "Saved current input has an invalid length." }
        require(all { RussianWordNormalizer.isSupportedLetter(it) && RussianWordNormalizer.normalizeLetter(it) == it }) {
            "Saved current input is not normalized."
        }
        return this
    }

    private fun String.decodeSubmittedWords(expectedLength: Int): List<String> {
        if (isBlank()) return emptyList()
        return lineSequence()
            .map { line ->
                require(RussianWordNormalizer.isNormalized(line, expectedLength)) { "Invalid saved submitted word." }
                line
            }.toList()
    }

    private const val EMPTY_MARKER = "-"
}
