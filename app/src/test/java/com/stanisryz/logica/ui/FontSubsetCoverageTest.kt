package com.stanisryz.logica.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.awt.Font
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * The shipped Rubik subsets (tools/fonts/subset_rubik.py) keep every character the app's strings use,
 * and every Latin and Cyrillic character of the full source font, because player names in the
 * leaderboards come from outside: whatever the full font could draw there, each subset still draws.
 */
class FontSubsetCoverageTest {
    private val root = listOf(File("."), File("..")).first { File(it, "tools/fonts/source").isDirectory }

    @Test
    fun everyStringCharacterTheFullFontHasIsInEverySubset() {
        val characters = stringCharacters()
        assertTrue("No strings found", characters.size > 100)
        WEIGHTS.forEach { weight ->
            val full = font("tools/fonts/source/rubik_$weight.ttf")
            val shipped = font("shared-ui/src/commonMain/composeResources/font/rubik_$weight.ttf")
            val missing = characters.filter { full.canDisplay(it) && !shipped.canDisplay(it) }
            assertTrue("rubik_$weight lacks ${missing.joinToString { "U+%04X".format(it.code) }}", missing.isEmpty())
        }
    }

    @Test
    fun everyLatinAndCyrillicCharacterOfTheFullFontIsInEverySubset() {
        WEIGHTS.forEach { weight ->
            val full = font("tools/fonts/source/rubik_$weight.ttf")
            val shipped = font("shared-ui/src/commonMain/composeResources/font/rubik_$weight.ttf")
            val missing = SCRIPT_RANGES.flatMap { it.toList() }.filter { full.canDisplay(it) && !shipped.canDisplay(it) }
            assertTrue("rubik_$weight lacks ${missing.joinToString { "U+%04X".format(it) }}", missing.isEmpty())
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

    private companion object {
        val WEIGHTS = listOf("regular", "medium", "semibold", "bold")

        /** Latin (with Extended-A, -B, and Additional) and Cyrillic with its Supplement. */
        val SCRIPT_RANGES = listOf(0x0000..0x024F, 0x1E00..0x1EFF, 0x0400..0x052F)
    }
}
