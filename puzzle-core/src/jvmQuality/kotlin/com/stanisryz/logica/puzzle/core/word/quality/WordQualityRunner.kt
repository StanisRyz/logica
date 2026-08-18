package com.stanisryz.logica.puzzle.core.word.quality

import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.PuzzleSeed
import com.stanisryz.logica.puzzle.core.word.WordAllowedGuesses
import com.stanisryz.logica.puzzle.core.word.WordDifficultyEvaluator
import com.stanisryz.logica.puzzle.core.word.WordGeneratorV1
import com.stanisryz.logica.puzzle.core.word.WordGeneratorV2
import com.stanisryz.logica.puzzle.core.word.WordLexiconV1
import com.stanisryz.logica.puzzle.core.word.WordLexiconV2
import com.stanisryz.logica.puzzle.core.word.WordPossibleAnswers
import com.stanisryz.logica.puzzle.core.word.WordRules
import kotlin.system.exitProcess

/** Opt-in whole-lexicon and deterministic V1/V2 generation gate. Prints counts, never word dumps. */
object WordQualityRunner {
    @JvmStatic
    fun main(args: Array<String>) {
        val seedCount = args.singleOrNull()?.toIntOrNull()
        require(seedCount != null && seedCount > 0) { "Expected one positive seed-count argument." }

        val failures = mutableListOf<String>()
        verifyV1(failures)
        verifyV2(seedCount, failures)
        printSummary(seedCount)

        if (failures.isNotEmpty()) {
            System.err.println("Word quality check FAILED with ${failures.size} problem(s):")
            failures.take(FAILURE_PRINT_LIMIT).forEach { System.err.println("  $it") }
            if (failures.size > FAILURE_PRINT_LIMIT) {
                System.err.println("  ... and ${failures.size - FAILURE_PRINT_LIMIT} more.")
            }
            exitProcess(1)
        }
        println("Word quality check PASSED: V1 compatibility and V2 lexical/generator invariants hold.")
    }

    private fun verifyV1(failures: MutableList<String>) {
        val allowed = WordLexiconV1.allowedGuesses.all()
        val answers = WordLexiconV1.possibleAnswers.all()
        verifyWords("V1 allowed guess", allowed, failures) { it == WordRules.V1_WORD_LENGTH }
        verifyWords("V1 answer", answers, failures) { it == WordRules.V1_WORD_LENGTH }
        verifyMembership("V1", WordLexiconV1.allowedGuesses, WordLexiconV1.possibleAnswers, failures)

        val evaluator = WordDifficultyEvaluator()
        answers.forEach { answer ->
            val bundled = WordLexiconV1.possibleAnswers.difficultyOf(answer)
            if (bundled != evaluator.evaluateWord(answer)) {
                failures += "V1 answer '$answer' no longer matches its frozen rarity bucket"
            }
        }
        val representative =
            mapOf(
                Difficulty.EASY to "верба",
                Difficulty.MEDIUM to "гряда",
                Difficulty.HARD to "холод",
                Difficulty.EXPERT to "хомяк",
            )
        representative.forEach { (difficulty, expected) ->
            val actual = WordGeneratorV1().generate(PuzzleSeed(1), difficulty).answer
            if (actual != expected) failures += "V1 seed 1 / ${difficulty.name} changed from '$expected' to '$actual'"
        }
    }

    private fun verifyV2(
        seedCount: Int,
        failures: MutableList<String>,
    ) {
        val allowed = WordLexiconV2.allowedGuesses.all()
        val answers = WordLexiconV2.possibleAnswers.all()
        verifyWords("V2 allowed guess", allowed, failures, WordRules::isSupportedLength)
        verifyWords("V2 answer", answers, failures, WordRules::isSupportedLength)
        verifyMembership("V2", WordLexiconV2.allowedGuesses, WordLexiconV2.possibleAnswers, failures)

        Difficulty.entries.forEach { difficulty ->
            val expectedLength = WordRules.wordLengthForV2(difficulty)
            val pool = WordLexiconV2.possibleAnswers.answers(difficulty)
            if (pool.size < MINIMUM_V2_ANSWERS_PER_DIFFICULTY) {
                failures +=
                    "V2 ${difficulty.name} answer pool has ${pool.size} entries; " +
                    "expected at least $MINIMUM_V2_ANSWERS_PER_DIFFICULTY"
            }
            pool.forEach { answer ->
                if (answer.length != expectedLength) {
                    failures += "V2 ${difficulty.name} contains wrong-length answer '$answer'"
                }
            }
            repeat(seedCount) { index ->
                val seed = PuzzleSeed(FIRST_SEED + index)
                val first = WordGeneratorV2().generate(seed, difficulty)
                val second = WordGeneratorV2().generate(seed, difficulty)
                if (first != second) failures += "V2 seed ${seed.value} / ${difficulty.name} is not deterministic"
                if (first.answer.length != expectedLength) {
                    failures += "V2 seed ${seed.value} / ${difficulty.name} generated wrong-length '${first.answer}'"
                }
            }
        }
    }

    private fun verifyWords(
        label: String,
        words: List<String>,
        failures: MutableList<String>,
        acceptsLength: (Int) -> Boolean,
    ) {
        if (words != words.sorted()) failures += "$label data is not in stable ascending order"
        val seen = mutableSetOf<String>()
        words.forEach { word ->
            if (!acceptsLength(word.length) || !WordRules.isNormalized(word, word.length)) {
                failures += "$label '$word' is not normalized Cyrillic with a supported length"
            }
            if (!seen.add(word)) failures += "$label '$word' is duplicated or normalization-colliding"
        }
    }

    private fun verifyMembership(
        version: String,
        allowed: WordAllowedGuesses,
        possible: WordPossibleAnswers,
        failures: MutableList<String>,
    ) {
        possible.all().forEach { answer ->
            if (answer !in allowed) failures += "$version answer '$answer' is missing from allowed guesses"
            val buckets = Difficulty.entries.filter { answer in possible.answers(it) }
            if (buckets.size != 1) failures += "$version answer '$answer' belongs to ${buckets.size} buckets"
        }
        Difficulty.entries.forEach { difficulty ->
            if (possible.answers(difficulty).isEmpty()) failures += "$version ${difficulty.name} answer pool is empty"
        }
    }

    private fun printSummary(seedCount: Int) {
        println("Word V1/V2 quality check ($seedCount sequential V2 seeds per difficulty)")
        println("  V1 allowed=${WordLexiconV1.allowedGuesses.size}, answers=${WordLexiconV1.possibleAnswers.size}")
        println(
            "  V2 allowed by length: " +
                (WordRules.MINIMUM_WORD_LENGTH..WordRules.MAXIMUM_WORD_LENGTH).joinToString { length ->
                    "$length=${WordLexiconV2.allowedGuesses.all().count { it.length == length }}"
                },
        )
        println(
            "  V2 answers: " +
                Difficulty.entries.joinToString { difficulty ->
                    "${difficulty.name}=${WordLexiconV2.possibleAnswers.answers(difficulty).size}"
                },
        )
    }

    private const val FIRST_SEED = 1L
    private const val MINIMUM_V2_ANSWERS_PER_DIFFICULTY = 500
    private const val FAILURE_PRINT_LIMIT = 20
}
