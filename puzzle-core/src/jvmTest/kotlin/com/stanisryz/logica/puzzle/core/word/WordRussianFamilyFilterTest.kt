package com.stanisryz.logica.puzzle.core.word

import com.stanisryz.logica.puzzle.core.catalog.BinaryCatalogLevelPack
import com.stanisryz.logica.puzzle.core.catalog.CatalogContentVariant
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelId
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelNumber
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelPackFormat
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelPackResult
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelPackSource
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelPackVersion
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.GeneratorVersion
import com.stanisryz.logica.puzzle.core.model.PuzzleSeed
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Word V5: Russian with V2's rules and the family filter of `lexicon/word/v5/topic_words.txt`. The
 * filter touches answers only, and Russian levels come from the frozen `word_ru` buckets.
 */
class WordRussianFamilyFilterTest {
    private val root = listOf(File("."), File("..")).first { File(it, "puzzle-data/levels").isDirectory }

    private val topicWords: Set<String> =
        File(root, "lexicon/word/v5/topic_words.txt")
            .readLines()
            .map { it.substringBefore('#').trim() }
            .filter(String::isNotEmpty)
            .map { it.split(Regex("\\s+")).first() }
            .toSet()

    private val v5Answers = Difficulty.entries.flatMap { WordLexiconV5.possibleAnswers.answers(it) }.toSet()
    private val v2Answers = Difficulty.entries.flatMap { WordLexiconV2.possibleAnswers.answers(it) }.toSet()

    @Test
    fun everyLengthKeepsAtLeastFiveHundredAnswersAllAllowedAsGuesses() {
        Difficulty.entries.forEach { difficulty ->
            val pool = WordLexiconV5.possibleAnswers.answers(difficulty)
            assertTrue(pool.size >= 500, "$difficulty has ${pool.size} answers")
            assertTrue(pool.all { it.length == WordRules.wordLengthForV2(difficulty) && it in WordLexiconV5.allowedGuesses })
        }
    }

    @Test
    fun noAnswerIsAFilteredWordAndEveryFilteredWordStaysAGuess() {
        assertEquals(emptyList(), v5Answers.filter { it in topicWords })
        assertTrue(topicWords.size > 100)
        assertEquals(emptyList(), topicWords.filterNot { it in WordLexiconV5.allowedGuesses })
        // Every V2 answer the filter removed can still be typed.
        val removed = v2Answers - v5Answers
        assertTrue(removed.isNotEmpty())
        assertTrue(removed.all { it in WordLexiconV5.allowedGuesses })
        // The architect's examples are gone; military words stay by the owner's decision.
        val controls =
            listOf("мечеть", "церковь", "библия", "политик", "депутат", "шейх", "война", "террор", "труп", "могила", "ведьма", "швед")
        assertEquals(emptyList(), controls.filter { it in v5Answers })
        assertTrue(listOf("армия", "солдат", "танк", "ракета").all { it in v5Answers })
    }

    @Test
    fun theRuntimeResolvesVersionFiveAsRussianOverTheVersionTwoGuesses() {
        val runtime = WordRuntimeResolver.resolve(GeneratorVersion(5))
        assertIs<WordGeneratorByLength>(runtime.generator)
        assertEquals(WordLanguage.RUSSIAN, runtime.language)
        assertEquals(WordLanguage.RUSSIAN, WordRuntimeResolver.language(GeneratorVersion(5)))
        assertEquals(listOf(WordLexiconV2.ALLOWED_GUESSES_RESOURCE, WordLexiconV5.ANSWERS_RESOURCE), runtime.requiredResourcePaths)
        assertEquals(WordCatalogContent.RUSSIAN_VARIANT, WordCatalogContent.variant(WordLanguage.RUSSIAN))
        assertEquals(GeneratorVersion(5), WordCatalogContent.generatorVersion(WordLanguage.RUSSIAN))
    }

    /** Recorded from the generated V5 lexicon; a change means the frozen answer pool changed. */
    @Test
    fun goldenAnswersByIndexAndSeed() {
        val byIndex =
            Difficulty.entries.flatMap { difficulty ->
                val pool = WordLexiconV5.possibleAnswers.answers(difficulty)
                listOf(0, 250, pool.lastIndex).map { pool[it] }
            }
        assertEquals(GOLDEN_BY_INDEX, byIndex.joinToString(" "))
        val generator = WordRuntimeResolver.resolve(GeneratorVersion(5)).generator
        val bySeed =
            Difficulty.entries.flatMap { difficulty ->
                listOf(1L, 2L).map { generator.generate(PuzzleSeed(it), difficulty).answer }
            }
        assertEquals(GOLDEN_BY_SEED, bySeed.joinToString(" "))
    }

    @Test
    fun russianLevelNComesFromTheFrozenWordRuBucketAtVersionFive() {
        val pack = BinaryCatalogLevelPack(bundledFiles)
        Difficulty.entries.forEach { difficulty ->
            val levelId = CatalogLevelId(PuzzleType.WORD, difficulty, CatalogLevelNumber(15))
            val resolved = WordCatalogContent.resolve(pack, levelId, WordLanguage.RUSSIAN)
            assertIs<CatalogLevelPackResult.Success<*>>(resolved)
            val definition = (resolved as CatalogLevelPackResult.Success).value
            assertEquals(GeneratorVersion(5), definition.generatorVersion)
            val answer =
                WordRuntimeResolver
                    .resolve(definition.generatorVersion)
                    .generator
                    .generate(definition.seed, difficulty)
                    .answer
            assertTrue(answer in WordLexiconV5.possibleAnswers.answers(difficulty))
        }
        // Every answer of a difficulty comes before any repeat, as in the other Word buckets.
        val generator = WordRuntimeResolver.resolve(GeneratorVersion(5)).generator
        val firstLevels =
            (1..500).map { level ->
                val definition =
                    (
                        pack.resolve(
                            CatalogLevelId(PuzzleType.WORD, Difficulty.EASY, CatalogLevelNumber(level)),
                            WordCatalogContent.RUSSIAN_VARIANT,
                        ) as CatalogLevelPackResult.Success
                    ).value
                generator.generate(definition.seed, Difficulty.EASY).answer
            }
        assertEquals(500, firstLevels.toSet().size)
    }

    @Test
    fun theOldVersionTwoBucketHandedToRussianFailsCleanly() {
        // A source that serves the default V2 `word/` bucket for every variant.
        val wrong =
            BinaryCatalogLevelPack(
                object : CatalogLevelPackSource by bundledFiles {
                    override fun openVariant(
                        packVersion: CatalogLevelPackVersion,
                        puzzleType: PuzzleType,
                        difficulty: Difficulty,
                        variant: CatalogContentVariant,
                    ) = bundledFiles.open(packVersion, puzzleType, difficulty)
                },
            )
        val levelId = CatalogLevelId(PuzzleType.WORD, Difficulty.MEDIUM, CatalogLevelNumber(11))
        assertIs<CatalogLevelPackResult.Failure>(WordCatalogContent.resolve(wrong, levelId, WordLanguage.RUSSIAN))
        // The V2 bucket itself is unchanged and still resolves as V2 for older app versions.
        val old = (BinaryCatalogLevelPack(bundledFiles).resolve(levelId) as CatalogLevelPackResult.Success).value
        assertEquals(GeneratorVersion(2), old.generatorVersion)
    }

    private val bundledFiles =
        object : CatalogLevelPackSource {
            override fun open(
                packVersion: CatalogLevelPackVersion,
                puzzleType: PuzzleType,
                difficulty: Difficulty,
            ) = File(root, "puzzle-data/" + CatalogLevelPackFormat.assetPath(packVersion, puzzleType, difficulty))
                .takeIf(File::isFile)
                ?.inputStream()

            override fun openVariant(
                packVersion: CatalogLevelPackVersion,
                puzzleType: PuzzleType,
                difficulty: Difficulty,
                variant: CatalogContentVariant,
            ) = File(root, "puzzle-data/" + CatalogLevelPackFormat.assetPath(packVersion, puzzleType, difficulty, variant))
                .takeIf(File::isFile)
                ?.inputStream()
        }

    private companion object {
        const val GOLDEN_BY_INDEX = "авто нрав ящик абзац обход ясень авария основа ярость авиация общение ясность"
        const val GOLDEN_BY_SEED = "мощь воин налет ветка никель восход надпись вратарь"
    }
}
