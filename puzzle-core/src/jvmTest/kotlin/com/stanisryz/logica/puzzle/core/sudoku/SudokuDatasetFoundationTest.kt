package com.stanisryz.logica.puzzle.core.sudoku

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.security.MessageDigest

class SudokuDatasetFoundationTest {
    @Test
    fun `solver finds the expected unique solution and rejects invalid or ambiguous givens`() {
        val unique = SudokuSolver.solve(KNOWN_GIVENS)

        assertEquals(SudokuSolveStatus.UNIQUE, unique.status)
        assertEquals(KNOWN_SOLUTION, unique.solution)
        assertEquals(SudokuSolveStatus.INVALID_GIVENS, SudokuSolver.solve("11" + "0".repeat(79)).status)
        assertEquals(SudokuSolveStatus.MULTIPLE_SOLUTIONS, SudokuSolver.solve("0".repeat(81)).status)
    }

    @Test
    fun `binary record parsing preserves stable identity and reports corrupt assets`() {
        val dataset = fixtureDataset()
        val expectedId = SudokuPuzzleId(SudokuDatasetVersion.V1, SudokuDifficulty.EASY, KNOWN_FINGERPRINT)

        val count = dataset.availableCount(SudokuDatasetVersion.V1, SudokuDifficulty.EASY)
        val loaded = dataset.getPuzzle(expectedId)

        assertEquals(2, (count as SudokuDatasetResult.Success).value)
        val puzzle = (loaded as SudokuDatasetResult.Success).value
        assertEquals(expectedId, puzzle.id)
        assertEquals(KNOWN_GIVENS, puzzle.givens)
        assertEquals(KNOWN_SOLUTION, puzzle.solution)
        assertEquals(12, puzzle.upstreamRatingTenths)

        val corrupt =
            BinarySudokuDataset { _, _ -> byteArrayOf(1, 2, 3) }
                .availableCount(SudokuDatasetVersion.V1, SudokuDifficulty.EASY)
        assertEquals(SudokuDatasetError.CORRUPT_ASSET, (corrupt as SudokuDatasetResult.Failure).error)
    }

    @Test
    fun `selector mapping is deterministic and resolves stable puzzle ids`() {
        val dataset = fixtureDataset()

        val first = dataset.selectPuzzle(SudokuDatasetVersion.V1, SudokuDifficulty.EASY, 0)
        val repeated = dataset.selectPuzzle(SudokuDatasetVersion.V1, SudokuDifficulty.EASY, 0)
        val other = dataset.selectPuzzle(SudokuDatasetVersion.V1, SudokuDifficulty.EASY, 4)

        val firstId = (first as SudokuDatasetResult.Success).value.id
        val otherId = (other as SudokuDatasetResult.Success).value.id
        assertEquals(firstId, (repeated as SudokuDatasetResult.Success).value.id)
        assertNotEquals(firstId, otherId)
        assertEquals(SECOND_FINGERPRINT, firstId.fingerprint)
        assertEquals(KNOWN_FINGERPRINT, otherId.fingerprint)
        assertTrue(dataset.getPuzzle(firstId) is SudokuDatasetResult.Success)
    }

    private fun fixtureDataset(): BinarySudokuDataset {
        val records =
            listOf(
                Record(KNOWN_GIVENS, KNOWN_SOLUTION, 12),
                Record(SECOND_GIVENS, SECOND_SOLUTION, 12),
            ).sortedBy { it.fingerprint.toHex() }
        val output = ByteArrayOutputStream()
        DataOutputStream(output).use { data ->
            data.writeBytes("LOGSDK01")
            data.writeByte(1)
            data.writeByte(1)
            data.writeInt(records.size)
            data.writeShort(116)
            records.forEach { record ->
                data.write(record.fingerprint)
                data.writeShort(record.ratingTenths)
                data.write(pack(record.givens))
                data.write(pack(record.solution))
            }
        }
        val bytes = output.toByteArray()
        return BinarySudokuDataset { version, difficulty ->
            bytes.takeIf { version == SudokuDatasetVersion.V1 && difficulty == SudokuDifficulty.EASY }
        }
    }

    private fun pack(cells: String): ByteArray {
        val packed = ByteArray(41)
        cells.forEachIndexed { index, character ->
            val digit = character.digitToInt()
            val byteIndex = index / 2
            packed[byteIndex] =
                if (index % 2 == 0) {
                    (digit shl 4).toByte()
                } else {
                    (packed[byteIndex].toInt() or digit).toByte()
                }
        }
        return packed
    }

    private data class Record(
        val givens: String,
        val solution: String,
        val ratingTenths: Int,
    ) {
        val fingerprint: ByteArray = MessageDigest.getInstance("SHA-256").digest(givens.toByteArray())
    }

    private fun ByteArray.toHex(): String = joinToString(separator = "") { "%02x".format(it.toInt() and 0xFF) }

    private companion object {
        const val KNOWN_GIVENS =
            "050703060007000800000816000000030000005000100730040086906000204840572093000409000"
        const val KNOWN_SOLUTION =
            "158723469367954821294816375619238547485697132732145986976381254841572693523469718"
        const val KNOWN_FINGERPRINT = "dfe20863da651e55a9ac79a23e69134faa375a25f50ec4b8518b84199ede492d"
        const val SECOND_GIVENS =
            "302401809001000300000000000040708010780502036000090000200609003900000008800070005"
        const val SECOND_SOLUTION =
            "372451869691827354458936271543768912789512436126394587215689743937145628864273195"
        const val SECOND_FINGERPRINT = "72fac0d485bf10fe0c79e82d1565723af661ea12e3743bc078cfae0b01b297f8"
    }
}
