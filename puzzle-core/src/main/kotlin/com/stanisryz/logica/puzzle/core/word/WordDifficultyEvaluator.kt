package com.stanisryz.logica.puzzle.core.word

import com.stanisryz.logica.puzzle.core.contract.PuzzleDifficultyEvaluator
import com.stanisryz.logica.puzzle.core.model.Difficulty

/**
 * Transparent, deterministic guessing-challenge score. Every difficulty keeps five letters and six
 * attempts; only how hard the answer is to deduce changes.
 *
 * The signals are letter rarity, repeated letters, and vowel scarcity. Obscurity is deliberately not
 * a signal: the answer pool is curated to common words, so EXPERT means "common but hard to crack".
 */
class WordDifficultyEvaluator : PuzzleDifficultyEvaluator<WordPuzzle> {
    override fun evaluate(puzzle: WordPuzzle): Difficulty = evaluateWord(puzzle.answer)

    fun evaluateWord(word: String): Difficulty {
        val score = score(word)
        return when {
            score <= EASY_MAXIMUM_SCORE -> Difficulty.EASY
            score <= MEDIUM_MAXIMUM_SCORE -> Difficulty.MEDIUM
            score <= HARD_MAXIMUM_SCORE -> Difficulty.HARD
            else -> Difficulty.EXPERT
        }
    }

    fun score(word: String): Int {
        WordRules.requireNormalized(word)
        val distinctLetters = word.toSet()
        val rarityPenalty = distinctLetters.sumOf(::letterRarityPenalty)
        val duplicates = word.length - distinctLetters.size
        val repeatPenalty = if (duplicates == 0) 0 else FIRST_DUPLICATE_PENALTY + (duplicates - 1) * FURTHER_DUPLICATE_PENALTY
        val vowelPenalty = if (word.count { it in VOWELS } <= 1) VOWEL_SCARCITY_PENALTY else 0
        return rarityPenalty + repeatPenalty + vowelPenalty
    }

    private fun letterRarityPenalty(letter: Char): Int =
        when (letter) {
            in COMMON_LETTERS -> 0
            in RARE_LETTERS -> RARE_LETTER_PENALTY
            else -> MEDIUM_LETTER_PENALTY
        }

    private companion object {
        /** Most frequent Russian letters; they rarely narrow a guess down on their own. */
        const val COMMON_LETTERS = "оеаинтсрвл"

        /** Least frequent Russian letters; hitting them is a strong deduction. */
        const val RARE_LETTERS = "хжшюцщэфъ"
        const val VOWELS = "аеиоуыэюя"

        const val MEDIUM_LETTER_PENALTY = 2
        const val RARE_LETTER_PENALTY = 5
        const val FIRST_DUPLICATE_PENALTY = 3
        const val FURTHER_DUPLICATE_PENALTY = 6
        const val VOWEL_SCARCITY_PENALTY = 4

        const val EASY_MAXIMUM_SCORE = 2
        const val MEDIUM_MAXIMUM_SCORE = 6
        const val HARD_MAXIMUM_SCORE = 10
    }
}
