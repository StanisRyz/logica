package com.stanisryz.logica.puzzle.core

import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.GeneratorVersion
import com.stanisryz.logica.puzzle.core.model.PuzzleId
import com.stanisryz.logica.puzzle.core.model.PuzzleSeed
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import com.stanisryz.logica.puzzle.core.word.WordAllowedGuesses
import com.stanisryz.logica.puzzle.core.word.WordGameEngine
import com.stanisryz.logica.puzzle.core.word.WordGameStatus
import com.stanisryz.logica.puzzle.core.word.WordPuzzle
import com.stanisryz.logica.puzzle.core.word.WordRules
import com.stanisryz.logica.puzzle.core.word.WordSubmitResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * A Word hint opens the leftmost letter not yet guessed exactly, keeps it locked in every later row,
 * never spends a guess, and stops at two per attempt — in every language version.
 */
class WordHintTest {
    @Test
    fun aHintOpensTheLeftmostLetterInEveryLanguage() {
        listOf(2 to "слово", 3 to "scope", 4 to "giysi").forEach { (version, answer) ->
            val engine = engine(version, answer)
            val hinted = engine.revealHint(engine.start())

            assertEquals(mapOf(0 to answer[0]), hinted.revealedLetters, "V$version")
            assertEquals(answer[0], hinted.currentDraft[0])
            assertEquals(1, hinted.hintsUsed)
            assertEquals(WordRules.MAXIMUM_ATTEMPTS, hinted.remainingAttempts)
        }
    }

    @Test
    fun aRevealedLetterIsLockedAndStartsEveryNewRow() {
        val engine = engine(2, "слово")
        val hinted = engine.revealHint(engine.start())

        assertEquals(hinted, engine.clearLetter(hinted, 0))
        assertEquals(hinted, engine.setLetter(hinted, 0, 'к'))

        val typed = "лайд".foldIndexed(hinted) { index, state, letter -> engine.setLetter(state, index + 1, letter) }
        val submitted = assertIs<WordSubmitResult.Accepted>(engine.submit(typed)).state

        assertEquals(listOf('с', null, null, null, null), submitted.currentDraft.positions)
        assertEquals(mapOf(0 to 'с'), submitted.revealedLetters)
        assertEquals(WordRules.MAXIMUM_ATTEMPTS - 1, submitted.remainingAttempts)
    }

    @Test
    fun aGuessedPositionIsSkippedAndTheLimitIsTwo() {
        val engine = engine(2, "слово")
        val hinted = engine.revealHint(engine.start())
        val typed = "лайд".foldIndexed(hinted) { index, state, letter -> engine.setLetter(state, index + 1, letter) }
        // «слайд»: «л» is in place, so the second hint opens «о» at position 2.
        val submitted = assertIs<WordSubmitResult.Accepted>(engine.submit(typed)).state

        val second = engine.revealHint(submitted)
        assertEquals(mapOf(0 to 'с', 2 to 'о'), second.revealedLetters)
        assertNull(engine.nextHintPosition(second))
        assertEquals(second, engine.revealHint(second))
        assertEquals(2, WordRules.maximumHints(4))
        assertEquals(2, WordRules.maximumHints(7))
        assertEquals(1, WordRules.maximumHints(2))
    }

    @Test
    fun aFinishedGameTakesNoHint() {
        val engine = engine(2, "слово")
        val solved =
            assertIs<WordSubmitResult.Accepted>(
                engine.submit("слово".foldIndexed(engine.start()) { index, state, letter -> engine.setLetter(state, index, letter) }),
            ).state

        assertEquals(WordGameStatus.SOLVED, solved.status)
        assertNull(engine.nextHintPosition(solved))
        assertEquals(solved, engine.revealHint(solved))
    }

    private fun engine(
        version: Int,
        answer: String,
    ) = WordGameEngine(
        WordPuzzle(PuzzleId(PuzzleType.WORD, Difficulty.MEDIUM, PuzzleSeed(1L), GeneratorVersion(version)), answer),
        AnyWord,
    )

    private object AnyWord : WordAllowedGuesses {
        override val size: Int = 1

        override fun contains(normalizedWord: String): Boolean = true

        override fun all(): List<String> = emptyList()
    }
}
