package com.stanisryz.logica.puzzle.core.word

/** Word V1 rules: five normalized Russian letters and six valid attempts for every difficulty. */
object WordRules {
    const val WORD_LENGTH = 5
    const val MAXIMUM_ATTEMPTS = 6

    fun normalize(raw: String): WordNormalization = RussianWordNormalizer.normalize(raw, WORD_LENGTH)

    fun normalizeOrNull(raw: String): String? = RussianWordNormalizer.normalizeOrNull(raw, WORD_LENGTH)

    fun isNormalized(word: String): Boolean = RussianWordNormalizer.isNormalized(word, WORD_LENGTH)

    fun requireNormalized(word: String): String {
        require(isNormalized(word)) { "Word must be a normalized $WORD_LENGTH-letter Russian word." }
        return word
    }

    /**
     * Two-pass feedback: exact positions are marked first and consume their answer letters, then the
     * remaining guess letters are matched against the still unused answer-letter counts only.
     */
    fun evaluate(
        answer: String,
        guess: String,
    ): List<WordLetterResult> {
        requireNormalized(answer)
        requireNormalized(guess)

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
