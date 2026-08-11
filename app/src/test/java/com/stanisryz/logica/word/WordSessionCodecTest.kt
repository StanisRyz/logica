package com.stanisryz.logica.word

import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.GeneratorVersion
import com.stanisryz.logica.puzzle.core.model.PuzzleId
import com.stanisryz.logica.puzzle.core.model.PuzzleSeed
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import com.stanisryz.logica.puzzle.core.word.WordGameEngine
import com.stanisryz.logica.puzzle.core.word.WordGameState
import com.stanisryz.logica.puzzle.core.word.WordGameStatus
import com.stanisryz.logica.puzzle.core.word.WordGeneratorV2
import com.stanisryz.logica.puzzle.core.word.WordLetterFeedback
import com.stanisryz.logica.puzzle.core.word.WordLexiconV1
import com.stanisryz.logica.puzzle.core.word.WordLexiconV2
import com.stanisryz.logica.puzzle.core.word.WordPuzzle
import com.stanisryz.logica.puzzle.core.word.WordSubmitResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class WordSessionCodecTest {
    private val puzzle =
        WordPuzzle(
            id = PuzzleId(PuzzleType.WORD, Difficulty.MEDIUM, PuzzleSeed(77), GeneratorVersion(1)),
            answer = "полка",
        )
    private val engine = WordGameEngine(puzzle, WordLexiconV1.allowedGuesses)

    @Test
    fun v1PrefixRestoreAndV2RoundTripPreserveThePositionalDraft() {
        var played = submit(submit(engine.start(), "лампа"), "весна")
        played = engine.setLetter(played, 0, 'п')
        played = engine.setLetter(played, 2, 'л')
        val encoded = WordSessionCodecV2.encode(puzzle, played)

        val restored = decode(encoded.gameplayPayload, encoded.moveHistoryPayload, encoded.status)
        val restoredV1 =
            decode(
                gameplayPayload = "length=5\ninput=ком",
                moveHistoryPayload = "",
                status = WordGameStatus.IN_PROGRESS.name,
                sessionFormatVersion = 1,
            )

        assertEquals(played, restored)
        assertEquals(listOf('п', null, 'л', null, null), restored.currentDraft.positions)
        assertEquals(listOf('к', 'о', 'м', null, null), restoredV1.currentDraft.positions)
        assertEquals(listOf("лампа", "весна"), restored.attempts.map { it.word })
        assertEquals(WordGameStatus.IN_PROGRESS, restored.status)
        assertEquals(4, restored.remainingAttempts)
        // Feedback is recomputed from the regenerated answer rather than persisted.
        assertEquals(
            WordLetterFeedback.CORRECT,
            restored.attempts
                .first()
                .letters
                .last()
                .feedback,
        )
        assertEquals(WordLetterFeedback.CORRECT, restored.letterKnowledge['а'])
        assertEquals(0, encoded.hintsUsed)
    }

    @Test
    fun decodeRejectsUnsupportedVersionsAndCorruptedPayloads() {
        val encoded = WordSessionCodecV2.encode(puzzle, submit(engine.start(), "лампа"))

        assertThrows(IllegalArgumentException::class.java) {
            WordSessionCodecV2.decode(
                puzzle = puzzle,
                allowedGuesses = WordLexiconV1.allowedGuesses,
                sessionFormatVersion = WordSessionCodecV2.SESSION_FORMAT_VERSION + 1,
                gameplayPayload = encoded.gameplayPayload,
                moveHistoryPayload = encoded.moveHistoryPayload,
                hintsUsed = encoded.hintsUsed,
                status = encoded.status,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            decode(encoded.gameplayPayload.replace("length=5", "length=6"), encoded.moveHistoryPayload, encoded.status)
        }
        assertThrows(IllegalArgumentException::class.java) {
            decode(encoded.gameplayPayload, "лампа\nнетслово", encoded.status)
        }
        assertThrows(IllegalStateException::class.java) {
            decode(encoded.gameplayPayload, encoded.moveHistoryPayload, "NOT_A_STATUS")
        }
    }

    @Test
    fun v2EasyAndExpertSessionsRoundTripWithTheirPuzzleSpecificLengths() {
        listOf(Difficulty.EASY, Difficulty.EXPERT).forEach { difficulty ->
            val variablePuzzle = WordGeneratorV2().generate(PuzzleSeed(9000L + difficulty.ordinal), difficulty)
            val variableEngine = WordGameEngine(variablePuzzle, WordLexiconV2.allowedGuesses)
            val wrongGuess =
                WordLexiconV2.allowedGuesses.all().first {
                    it.length == variablePuzzle.wordLength && it != variablePuzzle.answer
                }
            val accepted =
                variableEngine.submit(type(variableEngine, variableEngine.start(), wrongGuess))
                    as WordSubmitResult.Accepted
            val partial = variablePuzzle.answer.take(variablePuzzle.wordLength - 1)
            val played = type(variableEngine, accepted.state, partial)
            val encoded = WordSessionCodecV2.encode(variablePuzzle, played)

            val restored =
                WordSessionCodecV2.decode(
                    puzzle = variablePuzzle,
                    allowedGuesses = WordLexiconV2.allowedGuesses,
                    sessionFormatVersion = WordSessionCodecV2.SESSION_FORMAT_VERSION,
                    gameplayPayload = encoded.gameplayPayload,
                    moveHistoryPayload = encoded.moveHistoryPayload,
                    hintsUsed = 0,
                    status = encoded.status,
                )

            assertEquals(variablePuzzle.wordLength, restored.wordLength)
            assertEquals(played, restored)
        }
    }

    private fun decode(
        gameplayPayload: String,
        moveHistoryPayload: String,
        status: String,
        sessionFormatVersion: Int = WordSessionCodecV2.SESSION_FORMAT_VERSION,
    ): WordGameState =
        WordSessionCodecV2.decode(
            puzzle = puzzle,
            allowedGuesses = WordLexiconV1.allowedGuesses,
            sessionFormatVersion = sessionFormatVersion,
            gameplayPayload = gameplayPayload,
            moveHistoryPayload = moveHistoryPayload,
            hintsUsed = 0,
            status = status,
        )

    private fun submit(
        state: WordGameState,
        word: String,
    ): WordGameState = (engine.submit(type(state, word)) as WordSubmitResult.Accepted).state

    private fun type(
        state: WordGameState,
        input: String,
    ): WordGameState = type(engine, state, input)

    private fun type(
        targetEngine: WordGameEngine,
        state: WordGameState,
        input: String,
    ): WordGameState =
        input.foldIndexed(state) { index, current, letter ->
            targetEngine.setLetter(current, index, letter)
        }
}
