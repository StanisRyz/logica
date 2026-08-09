package com.stanisryz.logica.puzzle.core.word

import com.stanisryz.logica.puzzle.core.model.PuzzleId

/** Semantic letter outcome. Colors and other visual terminology belong to the presentation layer. */
enum class WordLetterFeedback(
    val strength: Int,
) {
    ABSENT(0),
    PRESENT(1),
    CORRECT(2),
}

data class WordLetterResult(
    val letter: Char,
    val feedback: WordLetterFeedback,
)

enum class WordGameStatus {
    IN_PROGRESS,
    SOLVED,
    FAILED,
}

/** A submitted, final guess. Submitted attempts are never undone. */
class WordAttempt internal constructor(
    val word: String,
    letters: Iterable<WordLetterResult>,
) {
    val letters: List<WordLetterResult> = letters.toList()

    init {
        WordRules.requireNormalized(word)
        require(this.letters.size == word.length) { "Feedback must cover every letter of the attempt." }
        require(this.letters.mapIndexed { index, result -> result.letter == word[index] }.all { it }) {
            "Feedback letters must match the attempted word."
        }
    }

    val isCorrect: Boolean = this.letters.all { it.feedback == WordLetterFeedback.CORRECT }

    override fun equals(other: Any?): Boolean = this === other || other is WordAttempt && word == other.word && letters == other.letters

    override fun hashCode(): Int = 31 * word.hashCode() + letters.hashCode()

    override fun toString(): String = "WordAttempt(word=$word, letters=$letters)"
}

/**
 * Strongest known feedback per normalized letter across every submitted attempt. A confirmed
 * [WordLetterFeedback.CORRECT] or [WordLetterFeedback.PRESENT] is never downgraded by later evidence.
 */
class WordLetterKnowledge internal constructor(
    byLetter: Map<Char, WordLetterFeedback>,
) {
    val byLetter: Map<Char, WordLetterFeedback> = byLetter.toMap()

    operator fun get(letter: Char): WordLetterFeedback? = this.byLetter[RussianWordNormalizer.normalizeLetter(letter)]

    override fun equals(other: Any?): Boolean = this === other || other is WordLetterKnowledge && byLetter == other.byLetter

    override fun hashCode(): Int = byLetter.hashCode()

    override fun toString(): String = "WordLetterKnowledge(byLetter=$byLetter)"

    companion object {
        fun from(attempts: Iterable<WordAttempt>): WordLetterKnowledge {
            val strongest = mutableMapOf<Char, WordLetterFeedback>()
            attempts.forEach { attempt ->
                attempt.letters.forEach { (letter, feedback) ->
                    val known = strongest[letter]
                    if (known == null || feedback.strength > known.strength) {
                        strongest[letter] = feedback
                    }
                }
            }
            return WordLetterKnowledge(strongest)
        }
    }
}

class WordGameState internal constructor(
    val puzzleId: PuzzleId,
    val currentInput: String,
    attempts: Iterable<WordAttempt>,
    val status: WordGameStatus,
) {
    val attempts: List<WordAttempt> = attempts.toList()
    val letterKnowledge: WordLetterKnowledge = WordLetterKnowledge.from(this.attempts)

    init {
        require(currentInput.length <= WordRules.WORD_LENGTH) { "Current input is longer than a word." }
        require(currentInput.all(RussianWordNormalizer::isSupportedLetter)) {
            "Current input must contain supported Russian letters only."
        }
        require(currentInput == currentInput.map(RussianWordNormalizer::normalizeLetter).joinToString("")) {
            "Current input must already be normalized."
        }
        require(this.attempts.size <= WordRules.MAXIMUM_ATTEMPTS) { "Too many submitted attempts." }
    }

    val remainingAttempts: Int = WordRules.MAXIMUM_ATTEMPTS - this.attempts.size
    val isFinished: Boolean = status != WordGameStatus.IN_PROGRESS

    override fun equals(other: Any?): Boolean =
        this === other ||
            other is WordGameState &&
            puzzleId == other.puzzleId &&
            currentInput == other.currentInput &&
            attempts == other.attempts &&
            status == other.status

    override fun hashCode(): Int {
        var result = puzzleId.hashCode()
        result = 31 * result + currentInput.hashCode()
        result = 31 * result + attempts.hashCode()
        result = 31 * result + status.hashCode()
        return result
    }

    override fun toString(): String = "WordGameState(puzzleId=$puzzleId, currentInput=$currentInput, attempts=$attempts, status=$status)"
}
