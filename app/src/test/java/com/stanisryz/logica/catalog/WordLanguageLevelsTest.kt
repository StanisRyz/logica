package com.stanisryz.logica.catalog

import com.stanisryz.logica.puzzle.core.catalog.BinaryCatalogLevelPack
import com.stanisryz.logica.puzzle.core.catalog.CatalogContentVariant
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelDefinition
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelId
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelNumber
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelPackFormat
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelPackResult
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelPackSource
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelPackVersion
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.GeneratorVersion
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import com.stanisryz.logica.puzzle.core.word.WordLanguage
import com.stanisryz.logica.puzzle.core.word.WordRuntimeResolver
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Word plays in the interface language with one progression: level N is the same level everywhere,
 * and only its content comes from the language's frozen bucket.
 */
class WordLanguageLevelsTest {
    private val level3 = CatalogLevelId(PuzzleType.WORD, Difficulty.MEDIUM, CatalogLevelNumber(3))

    @Test
    fun theInterfaceLanguagePicksTheWordLanguage() {
        assertEquals(WordLanguage.RUSSIAN, WordLanguage.forInterfaceTag("ru"))
        assertEquals(WordLanguage.ENGLISH, WordLanguage.forInterfaceTag("en"))
        assertEquals(WordLanguage.TURKISH, WordLanguage.forInterfaceTag("tr"))
    }

    @Test
    fun eachLanguageTakesLevelNFromItsOwnBucketWithTheSameLevelIdentity() =
        runBlocking {
            val attempts =
                WordLanguage.entries.associateWith { language ->
                    GameAttemptFactory(BundledLevels, wordLanguage = { language }) { "attempt" }
                        .create(GameAttemptLaunch.Level(level3), PuzzleType.WORD)
                }
            assertEquals(GeneratorVersion(2), attempts.getValue(WordLanguage.RUSSIAN).generatorVersion)
            assertEquals(GeneratorVersion(3), attempts.getValue(WordLanguage.ENGLISH).generatorVersion)
            assertEquals(GeneratorVersion(4), attempts.getValue(WordLanguage.TURKISH).generatorVersion)
            attempts.forEach { (language, attempt) ->
                val puzzle = WordRuntimeResolver.resolve(attempt.generatorVersion).generator.generate(attempt.seed, attempt.difficulty)
                assertEquals(language, puzzle.language)
                // One progression: the level, and so the completion identity and the progress it moves, carry no language.
                assertEquals(level3, attempt.levelId)
                assertEquals("catalog:1:WORD:MEDIUM:3:attempt", attempt.resultId)
            }
            // Same-sized pools give the English and Turkish buckets the same seeds; the words still differ.
            val answers =
                attempts.values.map {
                    WordRuntimeResolver
                        .resolve(it.generatorVersion)
                        .generator
                        .generate(it.seed, it.difficulty)
                        .answer
                }
            assertEquals(answers.size, answers.toSet().size)
        }

    @Test
    fun aBucketOfAnotherLanguageFailsCleanly() {
        // A source that hands the Russian bucket to every variant: the English level must not play Russian words.
        val wrong =
            BundledLevels.copy(
                source =
                    object : CatalogLevelPackSource by BundledFiles {
                        override fun openVariant(
                            packVersion: CatalogLevelPackVersion,
                            puzzleType: PuzzleType,
                            difficulty: Difficulty,
                            variant: CatalogContentVariant,
                        ) = BundledFiles.open(packVersion, puzzleType, difficulty)
                    },
            )
        assertThrows(CatalogLevelUnavailableException::class.java) {
            runBlocking {
                GameAttemptFactory(
                    wrong,
                    wordLanguage = { WordLanguage.ENGLISH },
                ).create(GameAttemptLaunch.Level(level3), PuzzleType.WORD)
            }
        }
        // Other games ignore the language.
        val balance =
            runBlocking {
                GameAttemptFactory(BundledLevels, wordLanguage = {
                    WordLanguage.TURKISH
                }).create(GameAttemptLaunch.Level(level3.copy(puzzleType = PuzzleType.BALANCE)), PuzzleType.BALANCE)
            }
        assertTrue(balance.generatorVersion.value == 1)
    }

    /** Reads the canonical corpus, variant buckets included, straight off disk. */
    private object BundledFiles : CatalogLevelPackSource {
        private val root = listOf(File("puzzle-data"), File("../puzzle-data")).first(File::isDirectory)

        override fun open(
            packVersion: CatalogLevelPackVersion,
            puzzleType: PuzzleType,
            difficulty: Difficulty,
        ) = File(root, CatalogLevelPackFormat.assetPath(packVersion, puzzleType, difficulty)).takeIf(File::isFile)?.inputStream()

        override fun openVariant(
            packVersion: CatalogLevelPackVersion,
            puzzleType: PuzzleType,
            difficulty: Difficulty,
            variant: CatalogContentVariant,
        ) = File(root, CatalogLevelPackFormat.assetPath(packVersion, puzzleType, difficulty, variant)).takeIf(File::isFile)?.inputStream()
    }

    private data class Levels(
        val source: CatalogLevelPackSource,
    ) : CatalogLevelRepository {
        private val pack = BinaryCatalogLevelPack(source)

        override fun observeCurrentLevel(
            puzzleType: PuzzleType,
            difficulty: Difficulty,
        ): Flow<CatalogLevelNumber> = MutableStateFlow(CatalogLevelNumber(1))

        override fun observeCurrentLevels(puzzleType: PuzzleType): Flow<Map<Difficulty, CatalogLevelNumber>> = MutableStateFlow(emptyMap())

        override suspend fun currentLevelId(
            puzzleType: PuzzleType,
            difficulty: Difficulty,
        ): CatalogLevelId = CatalogLevelId(puzzleType, difficulty, CatalogLevelNumber(1))

        override suspend fun resolve(levelId: CatalogLevelId): CatalogLevelDefinition = resolve(levelId, null)

        override suspend fun resolve(
            levelId: CatalogLevelId,
            variant: CatalogContentVariant?,
        ): CatalogLevelDefinition =
            when (val resolved = pack.resolve(levelId, variant)) {
                is CatalogLevelPackResult.Success -> resolved.value
                is CatalogLevelPackResult.Failure -> throw CatalogLevelUnavailableException(resolved.detail)
            }
    }

    private companion object {
        val BundledLevels = Levels(BundledFiles)
    }
}
