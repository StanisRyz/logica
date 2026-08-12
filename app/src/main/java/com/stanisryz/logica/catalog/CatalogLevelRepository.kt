package com.stanisryz.logica.catalog

import android.content.res.AssetManager
import com.stanisryz.logica.puzzle.core.catalog.BinaryCatalogLevelPack
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
    ): InputStream? =
        try {
            assets.open(
                CatalogLevelPackFormat.assetPath(packVersion, puzzleType, difficulty),
                AssetManager.ACCESS_STREAMING,
            )
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
 */
internal interface CatalogLevelRepository {
    val packVersion: CatalogLevelPackVersion

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

    /** Resolves the frozen definition, or throws [CatalogLevelUnavailableException]. */
    suspend fun resolve(levelId: CatalogLevelId): CatalogLevelDefinition
}

internal class RoomCatalogLevelRepository(
    private val dao: CatalogLevelProgressDao,
    private val pack: CatalogLevelPack,
    override val packVersion: CatalogLevelPackVersion = CatalogLevelPackVersion.V1,
) : CatalogLevelRepository {
    override fun observeCurrentLevel(
        puzzleType: PuzzleType,
        difficulty: Difficulty,
    ): Flow<CatalogLevelNumber> =
        dao
            .observeCurrentLevel(puzzleType.name, difficulty.name, packVersion.value)
            .map { rows -> rows.firstOrNull().toLevelNumber() }

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
            levelNumber = dao.findCurrentLevel(puzzleType.name, difficulty.name, packVersion.value).toLevelNumber(),
            packVersion = packVersion,
        )

    override suspend fun resolve(levelId: CatalogLevelId): CatalogLevelDefinition =
        when (val resolved = pack.resolve(levelId)) {
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
