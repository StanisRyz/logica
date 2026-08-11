package com.stanisryz.logica.puzzle.core.sudoku

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

fun interface SudokuDatasetSource {
    /** Returns one complete immutable difficulty asset, or null when it is unavailable. */
    fun readAsset(
        version: SudokuDatasetVersion,
        difficulty: SudokuDifficulty,
    ): ByteArray?
}

enum class SudokuDatasetError {
    MISSING_ASSET,
    CORRUPT_ASSET,
    PUZZLE_NOT_FOUND,
    EMPTY_BUCKET,
}

sealed interface SudokuDatasetResult<out T> {
    data class Success<T>(
        val value: T,
    ) : SudokuDatasetResult<T>

    data class Failure(
        val error: SudokuDatasetError,
        val detail: String,
    ) : SudokuDatasetResult<Nothing>
}

interface SudokuDataset {
    fun availableCount(
        version: SudokuDatasetVersion,
        difficulty: SudokuDifficulty,
    ): SudokuDatasetResult<Int>

    fun getPuzzle(id: SudokuPuzzleId): SudokuDatasetResult<SudokuPuzzle>

    fun selectPuzzle(
        version: SudokuDatasetVersion,
        difficulty: SudokuDifficulty,
        selector: Long,
    ): SudokuDatasetResult<SudokuPuzzle>
}

/** Read-only parser for the fixed-width, fingerprint-sorted Sudoku dataset assets. */
class BinarySudokuDataset(
    private val source: SudokuDatasetSource,
) : SudokuDataset {
    private val cache = mutableMapOf<BucketKey, Bucket>()

    override fun availableCount(
        version: SudokuDatasetVersion,
        difficulty: SudokuDifficulty,
    ): SudokuDatasetResult<Int> =
        when (val bucket = loadBucket(version, difficulty)) {
            is SudokuDatasetResult.Failure -> bucket
            is SudokuDatasetResult.Success -> SudokuDatasetResult.Success(bucket.value.count)
        }

    override fun getPuzzle(id: SudokuPuzzleId): SudokuDatasetResult<SudokuPuzzle> {
        val bucket =
            when (val loaded = loadBucket(id.datasetVersion, id.difficulty)) {
                is SudokuDatasetResult.Failure -> return loaded
                is SudokuDatasetResult.Success -> loaded.value
            }
        val target = id.fingerprint.hexToBytes()
        var low = 0
        var high = bucket.count - 1
        while (low <= high) {
            val middle = (low + high).ushr(1)
            val comparison = bucket.compareFingerprint(middle, target)
            when {
                comparison < 0 -> low = middle + 1
                comparison > 0 -> high = middle - 1
                else -> return bucket.parseRecord(middle)
            }
        }
        return SudokuDatasetResult.Failure(
            SudokuDatasetError.PUZZLE_NOT_FOUND,
            "Sudoku puzzle ${id.fingerprint} is not present in ${id.datasetVersion}/${id.difficulty}.",
        )
    }

    override fun selectPuzzle(
        version: SudokuDatasetVersion,
        difficulty: SudokuDifficulty,
        selector: Long,
    ): SudokuDatasetResult<SudokuPuzzle> {
        val bucket =
            when (val loaded = loadBucket(version, difficulty)) {
                is SudokuDatasetResult.Failure -> return loaded
                is SudokuDatasetResult.Success -> loaded.value
            }
        if (bucket.count == 0) {
            return SudokuDatasetResult.Failure(
                SudokuDatasetError.EMPTY_BUCKET,
                "Sudoku dataset $version/$difficulty contains no puzzles.",
            )
        }
        // V1 contract: SHA-256 of this exact UTF-8 material, then the unsigned big-endian first
        // 32 bits modulo the fingerprint-sorted bucket size. Changes require a new dataset version.
        val selectorMaterial = "logica:sudoku:selector:v1|${version.value}|${difficulty.name}|$selector"
        val digest = sha256(selectorMaterial.toByteArray(StandardCharsets.UTF_8))
        val unsignedPrefix =
            ((digest[0].toLong() and 0xFF) shl 24) or
                ((digest[1].toLong() and 0xFF) shl 16) or
                ((digest[2].toLong() and 0xFF) shl 8) or
                (digest[3].toLong() and 0xFF)
        return bucket.parseRecord((unsignedPrefix % bucket.count).toInt())
    }

    @Synchronized
    private fun loadBucket(
        version: SudokuDatasetVersion,
        difficulty: SudokuDifficulty,
    ): SudokuDatasetResult<Bucket> {
        val key = BucketKey(version, difficulty)
        cache[key]?.let { return SudokuDatasetResult.Success(it) }
        val bytes =
            try {
                source.readAsset(version, difficulty)
            } catch (error: Exception) {
                return SudokuDatasetResult.Failure(
                    SudokuDatasetError.MISSING_ASSET,
                    "Unable to open Sudoku dataset $version/$difficulty: ${error.message.orEmpty()}",
                )
            } ?: return SudokuDatasetResult.Failure(
                SudokuDatasetError.MISSING_ASSET,
                "Sudoku dataset asset $version/$difficulty is missing.",
            )
        val bucket = Bucket.parse(bytes, version, difficulty)
        if (bucket is SudokuDatasetResult.Success) cache[key] = bucket.value
        return bucket
    }

    private data class BucketKey(
        val version: SudokuDatasetVersion,
        val difficulty: SudokuDifficulty,
    )

    private class Bucket(
        private val bytes: ByteArray,
        private val version: SudokuDatasetVersion,
        private val difficulty: SudokuDifficulty,
        val count: Int,
    ) {
        fun compareFingerprint(
            index: Int,
            target: ByteArray,
        ): Int {
            val offset = recordOffset(index)
            for (byteIndex in 0 until FINGERPRINT_BYTES) {
                val current = bytes[offset + byteIndex].toInt() and 0xFF
                val expected = target[byteIndex].toInt() and 0xFF
                if (current != expected) return current.compareTo(expected)
            }
            return 0
        }

        fun parseRecord(index: Int): SudokuDatasetResult<SudokuPuzzle> {
            if (index !in 0 until count) return corrupt("Record index $index is outside the asset.")
            val offset = recordOffset(index)
            val fingerprintBytes = bytes.copyOfRange(offset, offset + FINGERPRINT_BYTES)
            val ratingOffset = offset + FINGERPRINT_BYTES
            val ratingTenths = readUnsignedShort(bytes, ratingOffset)
            val givensOffset = ratingOffset + RATING_BYTES
            val solutionOffset = givensOffset + PACKED_CELLS_BYTES
            val givens =
                unpackCells(bytes, givensOffset, allowZero = true)
                    ?: return corrupt("Record $index has malformed givens.")
            val solution =
                unpackCells(bytes, solutionOffset, allowZero = false)
                    ?: return corrupt("Record $index has a malformed solution.")
            if (!sha256(givens.toByteArray(StandardCharsets.US_ASCII)).contentEquals(fingerprintBytes)) {
                return corrupt("Record $index fingerprint does not match its givens.")
            }
            if (ratingTenths <= 0 || !isValidSolution(givens, solution)) {
                return corrupt("Record $index has inconsistent rating, givens, or solution.")
            }
            val id = SudokuPuzzleId(version, difficulty, fingerprintBytes.toHex())
            return SudokuDatasetResult.Success(SudokuPuzzle(id, givens, solution, ratingTenths))
        }

        private fun recordOffset(index: Int): Int = HEADER_SIZE + index * RECORD_SIZE

        private fun corrupt(detail: String) = SudokuDatasetResult.Failure(SudokuDatasetError.CORRUPT_ASSET, detail)

        companion object {
            fun parse(
                bytes: ByteArray,
                version: SudokuDatasetVersion,
                difficulty: SudokuDifficulty,
            ): SudokuDatasetResult<Bucket> {
                if (bytes.size < HEADER_SIZE || !bytes.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) {
                    return corruptHeader("Invalid or truncated Sudoku dataset header.")
                }
                val storedVersion = bytes[MAGIC.size].toInt() and 0xFF
                val storedDifficulty = bytes[MAGIC.size + 1].toInt() and 0xFF
                val count = readInt(bytes, MAGIC.size + 2)
                val recordSize = readUnsignedShort(bytes, MAGIC.size + 6)
                if (storedVersion != version.value || storedDifficulty != difficulty.assetCode) {
                    return corruptHeader("Sudoku asset version or difficulty does not match its path.")
                }
                val expectedSize = HEADER_SIZE.toLong() + count.toLong() * RECORD_SIZE
                if (count < 0 || recordSize != RECORD_SIZE || expectedSize != bytes.size.toLong()) {
                    return corruptHeader("Sudoku asset length or record metadata is invalid.")
                }
                return SudokuDatasetResult.Success(Bucket(bytes, version, difficulty, count))
            }

            private fun corruptHeader(detail: String) = SudokuDatasetResult.Failure(SudokuDatasetError.CORRUPT_ASSET, detail)
        }
    }

    private companion object {
        val MAGIC = "LOGSDK01".toByteArray(StandardCharsets.US_ASCII)
        const val HEADER_SIZE = 16
        const val RECORD_SIZE = 116
        const val FINGERPRINT_BYTES = 32
        const val RATING_BYTES = 2
        const val PACKED_CELLS_BYTES = 41
        const val CELL_COUNT = 81

        fun readInt(
            bytes: ByteArray,
            offset: Int,
        ): Int =
            ((bytes[offset].toInt() and 0xFF) shl 24) or
                ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
                ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
                (bytes[offset + 3].toInt() and 0xFF)

        fun readUnsignedShort(
            bytes: ByteArray,
            offset: Int,
        ): Int = ((bytes[offset].toInt() and 0xFF) shl 8) or (bytes[offset + 1].toInt() and 0xFF)

        fun unpackCells(
            bytes: ByteArray,
            offset: Int,
            allowZero: Boolean,
        ): String? {
            if (bytes[offset + PACKED_CELLS_BYTES - 1].toInt() and 0x0F != 0) return null
            val result = StringBuilder(CELL_COUNT)
            for (index in 0 until CELL_COUNT) {
                val packed = bytes[offset + index / 2].toInt() and 0xFF
                val value = if (index % 2 == 0) packed ushr 4 else packed and 0x0F
                if (value !in (if (allowZero) 0 else 1)..9) return null
                result.append(('0'.code + value).toChar())
            }
            return result.toString()
        }

        fun isValidSolution(
            givens: String,
            solution: String,
        ): Boolean {
            for (index in 0 until CELL_COUNT) {
                if (givens[index] != '0' && givens[index] != solution[index]) return false
            }
            for (unit in 0 until 9) {
                var rowMask = 0
                var columnMask = 0
                var blockMask = 0
                val blockRow = (unit / 3) * 3
                val blockColumn = (unit % 3) * 3
                for (offset in 0 until 9) {
                    rowMask = rowMask or (1 shl solution[unit * 9 + offset].digitToInt())
                    columnMask = columnMask or (1 shl solution[offset * 9 + unit].digitToInt())
                    val blockIndex = (blockRow + offset / 3) * 9 + blockColumn + offset % 3
                    blockMask = blockMask or (1 shl solution[blockIndex].digitToInt())
                }
                if (rowMask != 0x3FE || columnMask != 0x3FE || blockMask != 0x3FE) return false
            }
            return true
        }

        fun sha256(value: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(value)

        fun ByteArray.toHex(): String = joinToString(separator = "") { "%02x".format(it.toInt() and 0xFF) }

        fun String.hexToBytes(): ByteArray = chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }
}
