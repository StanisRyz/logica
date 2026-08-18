package com.stanisryz.logica.web

import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelDefinition
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelId
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelPack
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelPackResult
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelPackVersion
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelPacks
import com.stanisryz.logica.puzzle.core.contract.PuzzleGenerator
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.GeneratorVersion
import com.stanisryz.logica.puzzle.core.model.PuzzleId
import com.stanisryz.logica.puzzle.core.model.PuzzleSeed
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import com.stanisryz.logica.puzzle.core.word.WordAllowedGuesses
import com.stanisryz.logica.puzzle.core.word.WordGameStatus
import com.stanisryz.logica.puzzle.core.word.WordPuzzle
import com.stanisryz.logica.puzzle.core.word.WordRuntime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class WebWordControllerTest {
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun frozenLevelOneLoadsItsRuntimeAndSubmitsThroughCommonEngine() =
        runTest {
            var loadedDifficulty: Difficulty? = null
            var loadedResources: List<String>? = null
            val controller =
                WebWordController(
                    loadPack = { loadedDifficulty = it },
                    loadRuntimeResources = { loadedResources = it },
                    levelPack = fixedEasyLevelOne,
                    runtimeResolver = { testRuntime },
                    scope = this,
                )

            controller.selectDifficulty(Difficulty.EASY)
            advanceUntilIdle()

            val playing = assertIs<WebWordState.Playing>(controller.state)
            assertEquals(Difficulty.EASY, loadedDifficulty)
            assertEquals(testRuntime.requiredResourcePaths, loadedResources)
            assertEquals(PuzzleSeed(17), playing.definition.seed)
            TEST_ANSWER.forEachIndexed(controller::setLetter)
            controller.submit()

            val solved = assertIs<WebWordState.Playing>(controller.state)
            assertEquals(WordGameStatus.SOLVED, solved.game.status)
            assertEquals(1, solved.acceptedAttemptRevision)
            assertFalse(solved.isTerminalRevealReady)
            controller.onAcceptedAttemptRevealed(solved.acceptedAttemptRevision)
            assertTrue(assertIs<WebWordState.Playing>(controller.state).isTerminalRevealReady)
        }

    private val testRuntime =
        WordRuntime(
            generator =
                object : PuzzleGenerator<WordPuzzle> {
                    override val type = PuzzleType.WORD
                    override val version = GeneratorVersion(2)

                    override fun generate(
                        seed: PuzzleSeed,
                        difficulty: Difficulty,
                    ): WordPuzzle = WordPuzzle(PuzzleId(type, difficulty, seed, version), TEST_ANSWER)
                },
            allowedGuesses =
                object : WordAllowedGuesses {
                    override val size = 1

                    override fun contains(normalizedWord: String): Boolean = normalizedWord == TEST_ANSWER

                    override fun all(): List<String> = listOf(TEST_ANSWER)
                },
            requiredResourcePaths = listOf("/word/v2/test_answers.txt", "/word/v2/test_guesses.txt"),
        )

    private val fixedEasyLevelOne =
        object : CatalogLevelPack {
            override fun resolve(levelId: CatalogLevelId): CatalogLevelPackResult<CatalogLevelDefinition> {
                assertEquals(PuzzleType.WORD, levelId.puzzleType)
                assertEquals(Difficulty.EASY, levelId.difficulty)
                assertEquals(CatalogLevelPacks.FIRST_LEVEL, levelId.levelNumber)
                assertEquals(CatalogLevelPackVersion.V1, levelId.packVersion)
                return CatalogLevelPackResult.Success(
                    CatalogLevelDefinition(
                        levelId = levelId,
                        seed = PuzzleSeed(17),
                        generatorVersion = GeneratorVersion(2),
                    ),
                )
            }
        }

    private companion object {
        const val TEST_ANSWER = "мама"
    }
}
