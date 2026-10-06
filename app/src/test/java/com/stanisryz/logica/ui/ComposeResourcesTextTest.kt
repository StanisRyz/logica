package com.stanisryz.logica.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Compose Multiplatform resources keep a string's text as written: unlike Android's `res`, a `\'`
 * there reaches the screen with its backslash. Shared and Web strings write the apostrophe plainly.
 */
class ComposeResourcesTextTest {
    private val root = listOf(File("."), File("..")).first { File(it, "shared-ui/src/commonMain/composeResources").isDirectory }

    @Test
    fun noComposeStringEscapesItsApostrophe() {
        val files =
            listOf("shared-ui/src/commonMain/composeResources", "web-app/src/commonMain/composeResources")
                .flatMap { base -> File(root, base).listFiles().orEmpty().filter { it.name.startsWith("values") } }
                .map { File(it, "strings.xml") }
                .filter(File::isFile)
        assertTrue("No strings found", files.size >= 6)
        val escaped =
            files.flatMap { file ->
                file
                    .readLines()
                    .withIndex()
                    .filter { (_, line) -> "\\'" in line }
                    .map { (index, _) -> "${file.path}:${index + 1}" }
            }
        assertTrue("Escaped apostrophes show their backslash: $escaped", escaped.isEmpty())
    }
}
