package com.stanisryz.logica.word

import com.stanisryz.logica.puzzle.core.word.RussianWordNormalizer
import com.stanisryz.logica.puzzle.core.word.WordAllowedGuesses
import com.stanisryz.logica.puzzle.core.word.WordDraft
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
 * V2 persists the positional unfinished draft and submitted words. V1 prefix saves remain readable.
 * Letter feedback is always recomputed from the regenerated puzzle.
 */
internal object WordSessionCodecV2 {
    const val SESSION_FORMAT_VERSION = 2
    private const val V1_SESSION_FORMAT_VERSION = 1

    fun encode(
        puzzle: WordPuzzle,
        game: WordGameState,
    ): EncodedWordSession {
        require(game.puzzleId == puzzle.id) { "Word game belongs to a different puzzle." }
        require(game.wordLength == puzzle.wordLength) { "Word game length does not match its puzzle." }
        val gameplayPayload =
            buildString {
                appendLine("length=${puzzle.wordLength}")
                append("draft=${game.currentDraft.positions.joinToString(separator = "") { it?.toString() ?: EMPTY_MARKER }}")
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
        require(sessionFormatVersion == V1_SESSION_FORMAT_VERSION || sessionFormatVersion == SESSION_FORMAT_VERSION) {
            "Unsupported Word session format version: $sessionFormatVersion."
        }
        require(hintsUsed == 0) { "Word sessions never record hint usage." }

        val gameplay =
            gameplayPayload.parseGameplayPayload(
                if (sessionFormatVersion == V1_SESSION_FORMAT_VERSION) setOf("length", "input") else setOf("length", "draft"),
            )
        val length = gameplay.getValue("length").toIntOrNull() ?: error("Invalid saved word length.")
        require(length == puzzle.wordLength) { "Saved word length does not match the regenerated puzzle." }

        val currentDraft =
            if (sessionFormatVersion == V1_SESSION_FORMAT_VERSION) {
                val savedInput = gameplay.getValue("input")
                val prefix = if (savedInput == EMPTY_MARKER) "" else savedInput.requireInputPrefix(puzzle.wordLength)
                WordDraft.fromPrefix(prefix, puzzle.wordLength)
            } else {
                gameplay.getValue("draft").requireDraft(puzzle.wordLength)
            }
        val submittedWords = moveHistoryPayload.decodeSubmittedWords(puzzle.wordLength)

        val game = WordGameEngine(puzzle, allowedGuesses).restore(currentDraft, submittedWords)
        val savedStatus =
            runCatching { WordGameStatus.valueOf(status) }
                .getOrElse { error("Invalid saved game status.") }
        require(game.status == savedStatus) { "Saved game status does not match the restored state." }
        return game
    }

    private fun String.parseGameplayPayload(expectedFields: Set<String>): Map<String, String> {
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
        require(values.keys == expectedFields) { "Invalid gameplay payload fields." }
        return values
    }

    /** V1 stored a normalized left-to-right prefix, including a fully typed unsubmitted word. */
    private fun String.requireInputPrefix(expectedLength: Int): String {
        require(length in 1..expectedLength) { "Saved current input has an invalid length." }
        require(all { RussianWordNormalizer.isSupportedLetter(it) && RussianWordNormalizer.normalizeLetter(it) == it }) {
            "Saved current input is not normalized."
        }
        return this
    }

    private fun String.requireDraft(expectedLength: Int): WordDraft {
        require(length == expectedLength) { "Saved Word draft has an invalid length." }
        return WordDraft.fromPositions(
            map { saved ->
                when {
                    saved == EMPTY_MARKER.single() -> null
                    RussianWordNormalizer.isSupportedLetter(saved) &&
                        RussianWordNormalizer.normalizeLetter(saved) == saved -> saved
                    else -> error("Saved Word draft contains an invalid position.")
                }
            },
        )
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
