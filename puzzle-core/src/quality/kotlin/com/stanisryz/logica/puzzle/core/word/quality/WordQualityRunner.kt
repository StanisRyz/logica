package com.stanisryz.logica.puzzle.core.word.quality

import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.PuzzleSeed
import com.stanisryz.logica.puzzle.core.word.RussianWordNormalizer
import com.stanisryz.logica.puzzle.core.word.WordDifficultyEvaluator
import com.stanisryz.logica.puzzle.core.word.WordGeneratorV1
import com.stanisryz.logica.puzzle.core.word.WordLexiconV1
import com.stanisryz.logica.puzzle.core.word.WordRules
import java.util.Locale
import kotlin.system.exitProcess

/** Opt-in whole-lexicon and Generator V1 verification. Prints a summary, never full word lists. */
object WordQualityRunner {
    @JvmStatic
    fun main(args: Array<String>) {
        val seedCount = args.singleOrNull()?.toIntOrNull()
        require(seedCount != null && seedCount > 0) { "Expected one positive seed-count argument." }

        val failures = mutableListOf<String>()
        val allowedGuesses = WordLexiconV1.allowedGuesses.all()
        val answers = WordLexiconV1.possibleAnswers.all()

        verifyWords("allowed guess", allowedGuesses, failures)
        verifyWords("answer", answers, failures)
        verifyAnswerMembership(answers, failures)
        verifyDifficultyBuckets(answers, failures)
        verifyDeterminism(seedCount, failures)

        printSummary(allowedGuesses, answers, seedCount)

        if (failures.isNotEmpty()) {
            System.err.println("Word quality check FAILED with ${failures.size} problem(s):")
            failures.take(FAILURE_PRINT_LIMIT).forEach { System.err.println("  $it") }
            if (failures.size > FAILURE_PRINT_LIMIT) {
                System.err.println("  ... and ${failures.size - FAILURE_PRINT_LIMIT} more.")
            }
            exitProcess(1)
        }
        println("Word quality check PASSED: no lexicon or determinism problems.")
    }

    private fun verifyWords(
        label: String,
        words: List<String>,
        failures: MutableList<String>,
    ) {
        val seen = mutableSetOf<String>()
        words.forEach { word ->
            if (!WordRules.isNormalized(word)) {
                failures += "$label '$word' is not a normalized ${WordRules.WORD_LENGTH}-letter Cyrillic word"
            }
            if (!seen.add(word)) failures += "$label '$word' is duplicated"
        }
        val collisions = words.count { it.any { letter -> !RussianWordNormalizer.isSupportedLetter(letter) } }
        if (collisions > 0) failures += "$collisions $label entries contain unsupported characters"
    }

    private fun verifyAnswerMembership(
        answers: List<String>,
        failures: MutableList<String>,
    ) {
        answers.forEach { answer ->
            if (answer !in WordLexiconV1.allowedGuesses) {
                failures += "answer '$answer' is missing from the allowed guesses"
            }
            val buckets = Difficulty.entries.filter { answer in WordLexiconV1.possibleAnswers.answers(it) }
            if (buckets.size != 1) {
                failures += "answer '$answer' belongs to ${buckets.size} difficulty buckets instead of exactly one"
            }
        }
    }

    private fun verifyDifficultyBuckets(
        answers: List<String>,
        failures: MutableList<String>,
    ) {
        val evaluator = WordDifficultyEvaluator()
        Difficulty.entries.forEach { difficulty ->
            if (WordLexiconV1.possibleAnswers.answers(difficulty).isEmpty()) {
                failures += "the ${difficulty.name} answer pool is empty"
            }
        }
        answers.forEach { answer ->
            val bundled = WordLexiconV1.possibleAnswers.difficultyOf(answer)
            val evaluated = evaluator.evaluateWord(answer)
            if (bundled != evaluated) {
                failures +=
                    "answer '$answer' is bundled as $bundled but scores as $evaluated; " +
                    "regenerate the lexicon and bump the generator version if V1 output changes"
            }
        }
    }

    private fun verifyDeterminism(
        seedCount: Int,
        failures: MutableList<String>,
    ) {
        val generator = WordGeneratorV1()
        Difficulty.entries.forEach { difficulty ->
            repeat(seedCount) { seedIndex ->
                val seed = PuzzleSeed(FIRST_SEED + seedIndex)
                val first = generator.generate(seed, difficulty)
                val second = WordGeneratorV1().generate(seed, difficulty)
                if (first != second) {
                    failures += "seed ${seed.value} / ${difficulty.name} generated '${first.answer}' then '${second.answer}'"
                }
                if (WordLexiconV1.possibleAnswers.difficultyOf(first.answer) != difficulty) {
                    failures += "seed ${seed.value} / ${difficulty.name} generated out-of-bucket answer '${first.answer}'"
                }
            }
        }
    }

    private fun printSummary(
        allowedGuesses: List<String>,
        answers: List<String>,
        seedCount: Int,
    ) {
        println("WordLexiconV1 / WordGeneratorV1 quality check ($seedCount sequential seeds per difficulty)")
        println("  allowed guesses: ${allowedGuesses.size}")
        println("  answers: ${answers.size}")
        Difficulty.entries.forEach { difficulty ->
            println("  answers ${difficulty.name}: ${WordLexiconV1.possibleAnswers.answers(difficulty).size}")
        }
        println("  answers with repeated letters: ${answers.count { it.toSet().size < it.length }.ratio(answers.size)}")
        println("  Ё folding and rejected collisions are reported by :puzzle-core:wordLexiconPrepare")
    }

    private fun Int.ratio(total: Int): String {
        if (total == 0) return "n/a"
        return "$this/$total (${String.format(Locale.ROOT, "%.1f", this * 100.0 / total)}%)"
    }

    private const val FIRST_SEED = 1L
    private const val FAILURE_PRINT_LIMIT = 20
}
