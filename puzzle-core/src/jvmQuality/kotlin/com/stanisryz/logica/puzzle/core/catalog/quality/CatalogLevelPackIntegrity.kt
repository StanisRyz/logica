package com.stanisryz.logica.puzzle.core.catalog.quality

import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelPackFormat
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelPackVersion
import com.stanisryz.logica.puzzle.core.model.Difficulty
import java.io.File
import java.security.MessageDigest

/** Developer-only checksum gate for the released buckets of every pack; runtime never hashes level packs. */
object CatalogLevelPackIntegrity {
    @JvmStatic
    fun main(args: Array<String>) {
        require(args.size == 1) { "Expected <puzzle-data-dir>." }
        verify(File(args.single()))
        val counts = packVersions().joinToString { "V${it.value}: ${expectedPaths(it).size}" }
        println("Catalog Level Pack integrity verified ($counts buckets).")
    }

    fun verify(puzzleDataDirectory: File) {
        packVersions().forEach { verify(puzzleDataDirectory, it) }
    }

    fun manifestPath(packVersion: CatalogLevelPackVersion): String = "levels/v${packVersion.value}/checksums.sha256"

    private fun packVersions(): List<CatalogLevelPackVersion> = CatalogLevelPackBuilder.ALL_TARGETS.map { it.packVersion }.distinct()

    private fun verify(
        puzzleDataDirectory: File,
        packVersion: CatalogLevelPackVersion,
    ) {
        val manifest = File(puzzleDataDirectory, manifestPath(packVersion))
        require(manifest.isFile) { "Frozen Level Pack checksum manifest is missing: ${manifest.path}" }
        val checksums = parseManifest(manifest)
        val expectedPaths = expectedPaths(packVersion)
        require(checksums.keys == expectedPaths) {
            "Frozen Level Pack checksum manifest entries do not match the V${packVersion.value} buckets."
        }
        expectedPaths.forEach { relativePath ->
            val bucket = File(manifest.parentFile, relativePath)
            require(bucket.isFile) { "Frozen Level Pack bucket is missing: ${bucket.path}" }
            val actual = sha256(bucket.readBytes())
            require(actual == checksums.getValue(relativePath)) {
                "Frozen Level Pack V${packVersion.value} bucket changed: $relativePath. Restore it or create a new pack version."
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

    private fun expectedPaths(packVersion: CatalogLevelPackVersion): Set<String> =
        CatalogLevelPackBuilder.ALL_TARGETS
            .filter { it.packVersion == packVersion }
            .flatMap { target ->
                Difficulty.entries.map { difficulty ->
                    CatalogLevelPackFormat
                        .assetPath(packVersion, target.puzzleType, difficulty, target.variant)
                        .removePrefix("levels/v${packVersion.value}/")
                }
            }.toSet()

    private val CHECKSUM_LINE = Regex("([0-9a-f]{64})  ([a-z0-9_/.-]+)")
}
