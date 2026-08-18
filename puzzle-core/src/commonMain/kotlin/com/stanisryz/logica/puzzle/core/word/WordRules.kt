package com.stanisryz.logica.puzzle.core.word

import com.stanisryz.logica.puzzle.core.model.Difficulty

/** Shared Word rules. V1 always uses [V1_WORD_LENGTH]; V2 uses [wordLengthForV2]. */
object WordRules {
    const val V1_WORD_LENGTH = 5

    /** Kept as a source-compatible V1 alias. New gameplay must use its puzzle's word length. */
    const val WORD_LENGTH = V1_WORD_LENGTH
    const val MAXIMUM_ATTEMPTS = 6
    const val MINIMUM_WORD_LENGTH = 4
    const val MAXIMUM_WORD_LENGTH = 7

    fun wordLengthForV2(difficulty: Difficulty): Int =
        when (difficulty) {
            Difficulty.EASY -> 4
            Difficulty.MEDIUM -> 5
            Difficulty.HARD -> 6
            Difficulty.EXPERT -> 7
        }

    fun isSupportedLength(length: Int): Boolean = length in MINIMUM_WORD_LENGTH..MAXIMUM_WORD_LENGTH

    fun normalize(
        raw: String,
        expectedLength: Int,
    ): WordNormalization = RussianWordNormalizer.normalize(raw, expectedLength)

    fun normalize(raw: String): WordNormalization = normalize(raw, V1_WORD_LENGTH)

    fun normalizeOrNull(
        raw: String,
        expectedLength: Int,
    ): String? = RussianWordNormalizer.normalizeOrNull(raw, expectedLength)

    fun normalizeOrNull(raw: String): String? = normalizeOrNull(raw, V1_WORD_LENGTH)

    fun isNormalized(
        word: String,
        expectedLength: Int,
    ): Boolean = RussianWordNormalizer.isNormalized(word, expectedLength)

    fun isNormalized(word: String): Boolean = isNormalized(word, V1_WORD_LENGTH)

    fun requireNormalized(
        word: String,
        expectedLength: Int,
    ): String {
        require(isNormalized(word, expectedLength)) {
            "Word must be a normalized $expectedLength-letter Russian word."
        }
        return word
    }

    fun requireNormalized(word: String): String = requireNormalized(word, V1_WORD_LENGTH)

    /**
     * Two-pass feedback: exact positions are marked first and consume their answer letters, then the
     * remaining guess letters are matched against the still unused answer-letter counts only.
     */
    fun evaluate(
        answer: String,
        guess: String,
    ): List<WordLetterResult> {
        require(isSupportedLength(answer.length)) { "Unsupported Word answer length ${answer.length}." }
        requireNormalized(answer, answer.length)
        requireNormalized(guess, answer.length)

        val feedback = MutableList(guess.length) { WordLetterFeedback.ABSENT }
        val unusedAnswerLetters = mutableMapOf<Char, Int>()
        answer.forEachIndexed { index, answerLetter ->
            if (guess[index] == answerLetter) {
                feedback[index] = WordLetterFeedback.CORRECT
            } else {
                unusedAnswerLetters[answerLetter] = (unusedAnswerLetters[answerLetter] ?: 0) + 1
            }
        }

        guess.forEachIndexed { index, guessLetter ->
            if (feedback[index] == WordLetterFeedback.CORRECT) return@forEachIndexed
            val remaining = unusedAnswerLetters[guessLetter] ?: 0
            if (remaining > 0) {
                unusedAnswerLetters[guessLetter] = remaining - 1
                feedback[index] = WordLetterFeedback.PRESENT
            }
        }

        return guess.mapIndexed { index, guessLetter -> WordLetterResult(guessLetter, feedback[index]) }
    }
}
