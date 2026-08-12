package com.stanisryz.logica.puzzle.core.sudoku

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * The frozen V1 selector: SHA-256 of this exact UTF-8 material, then the unsigned big-endian first
 * 32 bits modulo the fingerprint-sorted bucket size. Changing it changes which record a seed picks,
 * so it requires a new dataset version. Offline tooling reuses this instead of restating the rule.
 */
object SudokuSelectorV1 {
    fun index(
        version: SudokuDatasetVersion,
        difficulty: SudokuDifficulty,
        selector: Long,
        recordCount: Int,
    ): Int {
        require(recordCount > 0) { "Sudoku bucket must contain at least one record." }
        val material = "logica:sudoku:selector:v1|${version.value}|${difficulty.name}|$selector"
        val digest = MessageDigest.getInstance("SHA-256").digest(material.toByteArray(StandardCharsets.UTF_8))
        val unsignedPrefix =
            ((digest[0].toLong() and 0xFF) shl 24) or
                ((digest[1].toLong() and 0xFF) shl 16) or
                ((digest[2].toLong() and 0xFF) shl 8) or
                (digest[3].toLong() and 0xFF)
        return (unsignedPrefix % recordCount).toInt()
    }
}
