package com.stanisryz.logica.catalog

import com.stanisryz.logica.puzzle.core.balance.BalanceGeneratorV1
import com.stanisryz.logica.puzzle.core.catalog.BinaryCatalogLevelPack
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelId
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelNumber
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelPackFormat
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelPackResult
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelPackSource
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelPackVersion
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelPacks
import com.stanisryz.logica.puzzle.core.crowns.CrownsGeneratorV1
import com.stanisryz.logica.puzzle.core.game2048.Game2048Engine
import com.stanisryz.logica.puzzle.core.game2048.Game2048GeneratorVersion
import com.stanisryz.logica.puzzle.core.game2048.Game2048PuzzleId
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import com.stanisryz.logica.puzzle.core.word.WordGeneratorV2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * The one invariant the whole level system rests on: a public level is the same puzzle for everyone,
 * forever, and the 10 000-slot cycle keeps producing new levels without new content.
 */
class FrozenCatalogLevelPackTest {
    private val pack = BinaryCatalogLevelPack(BundledLevelPackFiles)

    @Test
    fun representativeFrozenLevelsKeepTheirContentIdentityAndStableDifficultyCodes() {
        assertEquals(1, CatalogLevelPackFormat.difficultyCode(Difficulty.EASY))
        assertEquals(2, CatalogLevelPackFormat.difficultyCode(Difficulty.MEDIUM))
        assertEquals(3, CatalogLevelPackFormat.difficultyCode(Difficulty.HARD))
        assertEquals(4, CatalogLevelPackFormat.difficultyCode(Difficulty.EXPERT))

        val actual =
            listOf(
                GoldenLevel(PuzzleType.BALANCE, Difficulty.EASY, 1),
                GoldenLevel(PuzzleType.CROWNS, Difficulty.MEDIUM, 5_000),
                GoldenLevel(PuzzleType.WORD, Difficulty.EXPERT, 10_000),
                GoldenLevel(PuzzleType.GAME_2048, Difficulty.HARD, 5_000),
            ).associate { golden -> golden.label to frozenContentHash(golden) }

        assertEquals(
            mapOf(
                "BALANCE/EASY/1" to "4996a75cb27e1eea6f4fea3c4e40dff090185c4204b28d0404eda01b0fde7f12",
                "CROWNS/MEDIUM/5000" to "0259790ea6821428e43d969f0d5142fdfee32f272502dc9b69d962aa73482366",
                "WORD/EXPERT/10000" to "3b406637754a1239b6a53b5d2a39a3ae34b7509dbf3be52fb2d62c45ad79e0f0",
                "GAME_2048/HARD/5000" to "a2cc4e51a7c71a8628a03b8605627bcc3800e3972b1afbe4cc6e1b43037b4043",
            ),
            actual,
        )
    }

    @Test
    fun levelTenThousandAndOneReusesContentSlotOneButStaysItsOwnLevel() {
        val first = CatalogLevelId(PuzzleType.BALANCE, Difficulty.MEDIUM, CatalogLevelNumber(1))
        val cycled =
            CatalogLevelId(
                PuzzleType.BALANCE,
                Difficulty.MEDIUM,
                CatalogLevelNumber(CatalogLevelPacks.SLOTS_PER_BUCKET + 1),
            )

        assertEquals(1, cycled.contentSlot.value)
        assertEquals(pack.require(first).seed, pack.require(cycled).seed)
        // Same content, different level: a different displayed number and a different completion.
        assertNotEquals(first, cycled)
        assertNotEquals(pack.require(first).levelId, pack.require(cycled).levelId)
        assertNotEquals(resultIdOf(first), resultIdOf(cycled))
        assertEquals(
            2,
            pack
                .require(CatalogLevelId(PuzzleType.BALANCE, Difficulty.MEDIUM, CatalogLevelNumber(10_002)))
                .levelId.contentSlot.value,
        )
    }

    @Test
    fun missingContentFailsCleanlyInsteadOfSubstitutingAnotherPuzzle() {
        val emptyPack = BinaryCatalogLevelPack(CatalogLevelPackSource { _, _, _ -> null })
        val failure = emptyPack.resolve(CatalogLevelId(PuzzleType.CROWNS, Difficulty.HARD, CatalogLevelNumber(3)))

        assertTrue("$failure", failure is CatalogLevelPackResult.Failure)
    }

    private fun BinaryCatalogLevelPack.require(levelId: CatalogLevelId) =
        when (val resolved = resolve(levelId)) {
            is CatalogLevelPackResult.Success -> resolved.value
            is CatalogLevelPackResult.Failure -> error("${levelId.puzzleType}/${levelId.difficulty}: ${resolved.detail}")
        }

    private fun frozenContentHash(golden: GoldenLevel): String {
        val definition =
            pack.require(
                CatalogLevelId(golden.puzzleType, golden.difficulty, CatalogLevelNumber(golden.levelNumber)),
            )
        val content =
            when (golden.puzzleType) {
                PuzzleType.BALANCE -> {
                    val puzzle = BalanceGeneratorV1().generate(definition.seed, golden.difficulty)
                    puzzle.size.toString() +
                        puzzle.fixedClues.entries
                            .sortedWith(compareBy({ it.key.row }, { it.key.column }))
                            .joinToString(",") { "${it.key.row}:${it.key.column}=${it.value.name}" }
                }
                PuzzleType.CROWNS -> {
                    val puzzle = CrownsGeneratorV1().generate(definition.seed, golden.difficulty)
                    puzzle.size.toString() +
                        puzzle.regionAssignments.entries
                            .sortedWith(compareBy({ it.key.row }, { it.key.column }))
                            .joinToString(",") { it.value.value.toString() }
                }
                PuzzleType.WORD -> WordGeneratorV2().generate(definition.seed, golden.difficulty).answer
                PuzzleType.GAME_2048 -> {
                    val game =
                        Game2048Engine(
                            Game2048PuzzleId(definition.seed, golden.difficulty, Game2048GeneratorVersion.V2),
                        ).start()
                    game.board.joinToString(",")
                }
                else -> error("${golden.puzzleType} is not a generator-based golden case.")
            }
        return MessageDigest
            .getInstance("SHA-256")
            .digest(content.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xFF) }
    }

    private data class GoldenLevel(
        val puzzleType: PuzzleType,
        val difficulty: Difficulty,
        val levelNumber: Int,
    ) {
        val label: String get() = "${puzzleType.name}/${difficulty.name}/$levelNumber"
    }

    /** The completion identity a Catalog attempt at this level would carry. */
    private fun resultIdOf(levelId: CatalogLevelId): String =
        GameAttempt(
            attemptId = "attempt",
            puzzleType = levelId.puzzleType,
            difficulty = levelId.difficulty,
            seed = pack.require(levelId).seed,
            generatorVersion = pack.require(levelId).generatorVersion,
            context = GameAttemptContext.Catalog(levelId),
        ).resultId

    /** Reads the canonical shared corpus straight off disk; Android packages these exact bytes. */
    private object BundledLevelPackFiles : CatalogLevelPackSource {
        private val puzzleDataDirectory: File =
            listOf(File("puzzle-data"), File("../puzzle-data"))
                .firstOrNull(File::isDirectory)
                ?: error("The canonical puzzle-data directory was not found.")

        override fun open(
            packVersion: CatalogLevelPackVersion,
            puzzleType: PuzzleType,
            difficulty: Difficulty,
        ) = File(puzzleDataDirectory, CatalogLevelPackFormat.assetPath(packVersion, puzzleType, difficulty))
            .takeIf(File::isFile)
            ?.inputStream()
    }
}
