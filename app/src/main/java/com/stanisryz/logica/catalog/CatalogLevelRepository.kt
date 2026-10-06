package com.stanisryz.logica.catalog

import android.content.res.AssetManager
import com.stanisryz.logica.puzzle.core.catalog.BinaryCatalogLevelPack
import com.stanisryz.logica.puzzle.core.catalog.CatalogContentVariant
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelDefinition
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelId
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelNumber
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelPack
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelPackFormat
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelPackResult
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelPackSource
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelPackVersion
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelPacks
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.io.InputStream

/** Streams one frozen bucket out of the bundled assets; nothing is parsed at application start. */
internal class AndroidCatalogLevelPackSource(
    private val assets: AssetManager,
) : CatalogLevelPackSource {
    override fun open(
        packVersion: CatalogLevelPackVersion,
        puzzleType: PuzzleType,
        difficulty: Difficulty,
    ): InputStream? = openAsset(CatalogLevelPackFormat.assetPath(packVersion, puzzleType, difficulty))

    /** Word's language buckets (`levels/v1/word_ru/`, `word_en/`, `word_tr/`), bundled beside the old Russian V2 ones. */
    override fun openVariant(
        packVersion: CatalogLevelPackVersion,
        puzzleType: PuzzleType,
        difficulty: Difficulty,
        variant: CatalogContentVariant,
    ): InputStream? = openAsset(CatalogLevelPackFormat.assetPath(packVersion, puzzleType, difficulty, variant))

    private fun openAsset(path: String): InputStream? =
        try {
            assets.open(path, AssetManager.ACCESS_STREAMING)
        } catch (_: IOException) {
            null
        }
}

/** Missing or corrupt frozen content fails the attempt instead of substituting a random puzzle. */
internal class CatalogLevelUnavailableException(
    val detail: String,
) : Exception(detail)

/**
 * The Catalog level system as gameplay sees it: which level a game/difficulty currently stands on,
 * and which frozen puzzle a level resolves to. Progression is persisted; content never is.
 *
 * Each game plays from its active pack ([CatalogLevelPacks.activePackVersion]); the Nonogram moved to
 * Level Pack V2, whose row continues the V1 numbering: its current level is the higher of the two rows,
 * and the levels below the V1 row stay V1 levels for the gallery and replays.
 */
internal interface CatalogLevelRepository {
    fun observeCurrentLevel(
        puzzleType: PuzzleType,
        difficulty: Difficulty,
    ): Flow<CatalogLevelNumber>

    /** Current level of every difficulty of one game, for the start screen. */
    fun observeCurrentLevels(puzzleType: PuzzleType): Flow<Map<Difficulty, CatalogLevelNumber>>

    suspend fun currentLevelId(
        puzzleType: PuzzleType,
        difficulty: Difficulty,
    ): CatalogLevelId

    /** A cleared or current [levelNumber] in the pack of its range (a Nonogram level below the V1 row is a V1 level). */
    suspend fun levelId(
        puzzleType: PuzzleType,
        difficulty: Difficulty,
        levelNumber: CatalogLevelNumber,
    ): CatalogLevelId = CatalogLevelId(puzzleType, difficulty, levelNumber)

    /** Resolves the frozen definition, or throws [CatalogLevelUnavailableException]. */
    suspend fun resolve(levelId: CatalogLevelId): CatalogLevelDefinition

    /** The same level from a content variant's bucket (Word's Russian, English, and Turkish levels). */
    suspend fun resolve(
        levelId: CatalogLevelId,
        variant: CatalogContentVariant?,
    ): CatalogLevelDefinition =
        if (variant == null) resolve(levelId) else throw CatalogLevelUnavailableException("No ${variant.key} content for $levelId.")
}

internal class RoomCatalogLevelRepository(
    private val dao: CatalogLevelProgressDao,
    private val pack: CatalogLevelPack,
) : CatalogLevelRepository {
    override fun observeCurrentLevel(
        puzzleType: PuzzleType,
        difficulty: Difficulty,
    ): Flow<CatalogLevelNumber> {
        val rows =
            CatalogLevelPacks.packVersionsFor(puzzleType).map { version ->
                dao
                    .observeCurrentLevel(puzzleType.name, difficulty.name, version.value)
                    .map { found -> found.firstOrNull().toLevelNumber() }
            }
        return if (rows.size == 1) rows.single() else combine(rows) { levels -> levels.maxBy { it.value } }
    }

    override fun observeCurrentLevels(puzzleType: PuzzleType): Flow<Map<Difficulty, CatalogLevelNumber>> {
        val difficulties = Difficulty.entries
        return combine(difficulties.map { difficulty -> observeCurrentLevel(puzzleType, difficulty) }) { levels ->
            difficulties.indices.associate { index -> difficulties[index] to levels[index] }
        }
    }

    override suspend fun currentLevelId(
        puzzleType: PuzzleType,
        difficulty: Difficulty,
    ): CatalogLevelId =
        CatalogLevelId(
            puzzleType = puzzleType,
            difficulty = difficulty,
            levelNumber =
                CatalogLevelPacks
                    .packVersionsFor(
                        puzzleType,
                    ).maxOf { currentLevel(puzzleType, difficulty, it) }
                    .let(::CatalogLevelNumber),
            packVersion = CatalogLevelPacks.activePackVersion(puzzleType),
        )

    override suspend fun levelId(
        puzzleType: PuzzleType,
        difficulty: Difficulty,
        levelNumber: CatalogLevelNumber,
    ): CatalogLevelId {
        val v1Level = CatalogLevelNumber(currentLevel(puzzleType, difficulty, CatalogLevelPackVersion.V1))
        return CatalogLevelId(puzzleType, difficulty, levelNumber, CatalogLevelPacks.packVersionForLevel(puzzleType, levelNumber, v1Level))
    }

    private suspend fun currentLevel(
        puzzleType: PuzzleType,
        difficulty: Difficulty,
        packVersion: CatalogLevelPackVersion,
    ): Int = dao.findCurrentLevel(puzzleType.name, difficulty.name, packVersion.value).toLevelNumber().value

    override suspend fun resolve(levelId: CatalogLevelId): CatalogLevelDefinition = resolve(levelId, null)

    override suspend fun resolve(
        levelId: CatalogLevelId,
        variant: CatalogContentVariant?,
    ): CatalogLevelDefinition =
        when (val resolved = pack.resolve(levelId, variant)) {
            is CatalogLevelPackResult.Success -> resolved.value
            is CatalogLevelPackResult.Failure ->
                throw CatalogLevelUnavailableException("${resolved.error}: ${resolved.detail}")
        }

    /** A bucket that has never been played, or a value damaged beyond use, simply starts at 1. */
    private fun Int?.toLevelNumber(): CatalogLevelNumber =
        if (this == null || this < 1) CatalogLevelPacks.FIRST_LEVEL else CatalogLevelNumber(this)
}

internal fun createCatalogLevelRepository(
    dao: CatalogLevelProgressDao,
    assets: AssetManager,
): CatalogLevelRepository = RoomCatalogLevelRepository(dao, BinaryCatalogLevelPack(AndroidCatalogLevelPackSource(assets)))
