package com.stanisryz.logica.puzzle.core.catalog.quality

import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelPackFormat
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelPackVersion
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelPacks
import com.stanisryz.logica.puzzle.core.model.Difficulty
import java.io.File
import java.security.MessageDigest

/** Developer-only checksum gate for the released V1 buckets; runtime never hashes level packs. */
object CatalogLevelPackIntegrity {
    @JvmStatic
    fun main(args: Array<String>) {
        require(args.size == 1) { "Expected <assets-dir>." }
        verify(File(args.single()))
        println("Catalog Level Pack V1 integrity verified (${expectedPaths().size} buckets).")
    }

    fun verify(assetsDirectory: File) {
        val manifest = File(assetsDirectory, MANIFEST_PATH)
        require(manifest.isFile) { "Frozen Level Pack checksum manifest is missing: ${manifest.path}" }
        val checksums = parseManifest(manifest)
        val expectedPaths = expectedPaths()
        require(checksums.keys == expectedPaths) {
            "Frozen Level Pack checksum manifest entries do not match the V1 buckets."
        }
        expectedPaths.forEach { relativePath ->
            val bucket = File(manifest.parentFile, relativePath)
            require(bucket.isFile) { "Frozen Level Pack bucket is missing: ${bucket.path}" }
            val actual = sha256(bucket.readBytes())
            require(actual == checksums.getValue(relativePath)) {
                "Frozen Level Pack V1 bucket changed: $relativePath. Restore it or create a new pack version."
            }
        }
    }

    fun sha256(bytes: ByteArray): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xFF) }

    private fun parseManifest(manifest: File): Map<String, String> =
        manifest
            .readLines()
            .filter { line -> line.isNotBlank() && !line.startsWith('#') }
            .associate { line ->
                val match =
                    CHECKSUM_LINE.matchEntire(line)
                        ?: error("Invalid checksum manifest line: $line")
                match.groupValues[2] to match.groupValues[1]
            }

    private fun expectedPaths(): Set<String> =
        CatalogLevelPacks.PUZZLE_TYPES
            .flatMap { puzzleType ->
                Difficulty.entries.map { difficulty ->
                    CatalogLevelPackFormat
                        .assetPath(CatalogLevelPackVersion.V1, puzzleType, difficulty)
                        .removePrefix("levels/v1/")
                }
            }.toSet()

    private const val MANIFEST_PATH = "levels/v1/checksums.sha256"
    private val CHECKSUM_LINE = Regex("([0-9a-f]{64})  ([a-z0-9_/.-]+)")
}
