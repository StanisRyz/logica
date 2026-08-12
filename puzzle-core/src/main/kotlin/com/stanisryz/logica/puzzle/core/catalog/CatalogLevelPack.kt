package com.stanisryz.logica.puzzle.core.catalog

import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.GeneratorVersion
import com.stanisryz.logica.puzzle.core.model.PuzzleSeed
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import java.io.InputStream
import java.nio.charset.StandardCharsets

/**
 * The binary layout of a frozen level bucket. One bucket is one game at one difficulty: a small
 * header plus [CatalogLevelPacks.SLOTS_PER_BUCKET] big-endian seeds, so the runtime can read the one
 * record it needs without materialising the bucket.
 */
object CatalogLevelPackFormat {
    val MAGIC: ByteArray = "LOGLVP01".toByteArray(StandardCharsets.US_ASCII)
    const val FORMAT_VERSION = 1
    const val HEADER_SIZE = 20
    const val RECORD_SIZE = 8

    fun assetPath(
        packVersion: CatalogLevelPackVersion,
        puzzleType: PuzzleType,
        difficulty: Difficulty,
    ): String = "levels/v${packVersion.value}/${puzzleType.name.lowercase()}/${difficulty.name.lowercase()}.lvp"

    fun puzzleTypeCode(puzzleType: PuzzleType): Int =
        when (puzzleType) {
            PuzzleType.BALANCE -> 1
            PuzzleType.CROWNS -> 2
            PuzzleType.WORD -> 3
            PuzzleType.SUDOKU -> 4
            PuzzleType.GAME_2048 -> 5
            else -> error("$puzzleType has no Catalog level pack.")
        }

    fun difficultyCode(difficulty: Difficulty): Int = difficulty.ordinal + 1

    fun header(
        packVersion: CatalogLevelPackVersion,
        puzzleType: PuzzleType,
        difficulty: Difficulty,
        recordCount: Int,
        generatorVersion: GeneratorVersion,
    ): ByteArray {
        val header = ByteArray(HEADER_SIZE)
        MAGIC.copyInto(header)
        header[8] = FORMAT_VERSION.toByte()
        header[9] = packVersion.value.toByte()
        header[10] = puzzleTypeCode(puzzleType).toByte()
        header[11] = difficultyCode(difficulty).toByte()
        header[12] = (recordCount ushr 24).toByte()
        header[13] = (recordCount ushr 16).toByte()
        header[14] = (recordCount ushr 8).toByte()
        header[15] = recordCount.toByte()
        header[16] = (RECORD_SIZE ushr 8).toByte()
        header[17] = RECORD_SIZE.toByte()
        header[18] = (generatorVersion.value ushr 8).toByte()
        header[19] = generatorVersion.value.toByte()
        return header
    }

    fun record(seed: PuzzleSeed): ByteArray = ByteArray(RECORD_SIZE) { index -> (seed.value ushr ((RECORD_SIZE - 1 - index) * 8)).toByte() }
}

enum class CatalogLevelPackError {
    MISSING_ASSET,
    CORRUPT_ASSET,
}

sealed interface CatalogLevelPackResult<out T> {
    data class Success<T>(
        val value: T,
    ) : CatalogLevelPackResult<T>

    data class Failure(
        val error: CatalogLevelPackError,
        val detail: String,
    ) : CatalogLevelPackResult<Nothing>
}

fun interface CatalogLevelPackSource {
    /** Opens one frozen bucket for reading, or returns null when the asset is unavailable. */
    fun open(
        packVersion: CatalogLevelPackVersion,
        puzzleType: PuzzleType,
        difficulty: Difficulty,
    ): InputStream?
}

interface CatalogLevelPack {
    fun resolve(levelId: CatalogLevelId): CatalogLevelPackResult<CatalogLevelDefinition>
}

/**
 * Read-only resolver over the bundled frozen buckets. It validates the pack metadata, seeks to the
 * one record the requested level maps to, and fails cleanly instead of ever substituting a random
 * puzzle for missing or corrupt content.
 */
class BinaryCatalogLevelPack(
    private val source: CatalogLevelPackSource,
    private val expectedRecordCount: Int = CatalogLevelPacks.SLOTS_PER_BUCKET,
) : CatalogLevelPack {
    override fun resolve(levelId: CatalogLevelId): CatalogLevelPackResult<CatalogLevelDefinition> {
        val stream =
            try {
                source.open(levelId.packVersion, levelId.puzzleType, levelId.difficulty)
            } catch (error: Exception) {
                return missing(levelId, error.message.orEmpty())
            } ?: return missing(levelId, "asset is absent")

        return stream.use { open -> read(open, levelId) }
    }

    private fun read(
        stream: InputStream,
        levelId: CatalogLevelId,
    ): CatalogLevelPackResult<CatalogLevelDefinition> {
        val header = ByteArray(CatalogLevelPackFormat.HEADER_SIZE)
        try {
            if (!stream.readFully(header)) return corrupt(levelId, "truncated header")
            if (!header.copyOfRange(0, CatalogLevelPackFormat.MAGIC.size).contentEquals(CatalogLevelPackFormat.MAGIC)) {
                return corrupt(levelId, "unexpected magic")
            }
            if (header.byteAt(8) != CatalogLevelPackFormat.FORMAT_VERSION) {
                return corrupt(levelId, "unsupported format version ${header.byteAt(8)}")
            }
            if (header.byteAt(9) != levelId.packVersion.value) return corrupt(levelId, "pack version mismatch")
            if (header.byteAt(10) != CatalogLevelPackFormat.puzzleTypeCode(levelId.puzzleType)) {
                return corrupt(levelId, "puzzle type mismatch")
            }
            if (header.byteAt(11) != CatalogLevelPackFormat.difficultyCode(levelId.difficulty)) {
                return corrupt(levelId, "difficulty mismatch")
            }
            val recordCount = header.intAt(12)
            val recordSize = header.shortAt(16)
            val generatorVersion = header.shortAt(18)
            if (recordCount != expectedRecordCount) return corrupt(levelId, "record count $recordCount")
            if (recordSize != CatalogLevelPackFormat.RECORD_SIZE) return corrupt(levelId, "record size $recordSize")
            if (generatorVersion <= 0) return corrupt(levelId, "generator version $generatorVersion")

            val offset = levelId.contentSlot.index.toLong() * CatalogLevelPackFormat.RECORD_SIZE
            if (!stream.skipFully(offset)) return corrupt(levelId, "asset ends before slot ${levelId.contentSlot.value}")
            val record = ByteArray(CatalogLevelPackFormat.RECORD_SIZE)
            if (!stream.readFully(record)) return corrupt(levelId, "truncated slot ${levelId.contentSlot.value}")

            var seed = 0L
            record.forEach { byte -> seed = (seed shl 8) or (byte.toLong() and 0xFF) }
            return CatalogLevelPackResult.Success(
                CatalogLevelDefinition(levelId, PuzzleSeed(seed), GeneratorVersion(generatorVersion)),
            )
        } catch (error: Exception) {
            return corrupt(levelId, error.message.orEmpty())
        }
    }

    private fun missing(
        levelId: CatalogLevelId,
        detail: String,
    ) = CatalogLevelPackResult.Failure(CatalogLevelPackError.MISSING_ASSET, "${levelId.describe()}: $detail")

    private fun corrupt(
        levelId: CatalogLevelId,
        detail: String,
    ) = CatalogLevelPackResult.Failure(CatalogLevelPackError.CORRUPT_ASSET, "${levelId.describe()}: $detail")

    private fun CatalogLevelId.describe(): String =
        "Level pack v${packVersion.value} ${puzzleType.name}/${difficulty.name} level ${levelNumber.value}"

    private fun ByteArray.byteAt(offset: Int): Int = this[offset].toInt() and 0xFF

    private fun ByteArray.shortAt(offset: Int): Int = (byteAt(offset) shl 8) or byteAt(offset + 1)

    private fun ByteArray.intAt(offset: Int): Int =
        (byteAt(offset) shl 24) or (byteAt(offset + 1) shl 16) or (byteAt(offset + 2) shl 8) or byteAt(offset + 3)

    private fun InputStream.readFully(target: ByteArray): Boolean {
        var read = 0
        while (read < target.size) {
            val count = read(target, read, target.size - read)
            if (count < 0) return false
            read += count
        }
        return true
    }

    private fun InputStream.skipFully(bytes: Long): Boolean {
        var remaining = bytes
        while (remaining > 0L) {
            val skipped = skip(remaining)
            if (skipped > 0L) {
                remaining -= skipped
            } else if (read() < 0) {
                return false
            } else {
                remaining -= 1L
            }
        }
        return true
    }
}
