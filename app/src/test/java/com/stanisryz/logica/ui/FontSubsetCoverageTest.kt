package com.stanisryz.logica.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.awt.Font
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * The shipped Rubik subsets (tools/fonts/subset_rubik.py) keep every character the app's strings use:
 * whatever the full source font could draw, each subset still draws.
 */
class FontSubsetCoverageTest {
    private val root = listOf(File("."), File("..")).first { File(it, "tools/fonts/source").isDirectory }

    @Test
    fun everyStringCharacterTheFullFontHasIsInEverySubset() {
        val characters = stringCharacters()
        assertTrue("No strings found", characters.size > 100)
        listOf("regular", "medium", "semibold", "bold").forEach { weight ->
            val full = font("tools/fonts/source/rubik_$weight.ttf")
            val shipped = font("shared-ui/src/commonMain/composeResources/font/rubik_$weight.ttf")
            val missing = characters.filter { full.canDisplay(it) && !shipped.canDisplay(it) }
            assertTrue("rubik_$weight lacks ${missing.joinToString { "U+%04X".format(it.code) }}", missing.isEmpty())
        }
    }

    private fun font(path: String): Font = File(root, path).inputStream().use { Font.createFont(Font.TRUETYPE_FONT, it) }

    private fun stringCharacters(): Set<Char> {
        val files =
            listOf("shared-ui/src/commonMain/composeResources", "web-app/src/commonMain/composeResources", "app/src/main/res")
                .flatMap { base -> File(root, base).listFiles().orEmpty().filter { it.name.startsWith("values") } }
                .map { File(it, "strings.xml") }
                .filter(File::isFile)
        val builder = DocumentBuilderFactory.newInstance().newDocumentBuilder()
        return files
            .flatMap { file ->
                val items = builder.parse(file).documentElement.getElementsByTagName("*")
                (0 until items.length).map { (items.item(it) as Element).textContent }
            }.flatMap { it.toList() }
            .filter { !it.isWhitespace() && !it.isISOControl() }
            .toSet()
    }
}
