package io.github.mehrdad_abdi.qintar

import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * DESIGN.md §6.6 and §8.1: Arabic resources use Arabic letters, Persian resources use Persian
 * letters, and no key is missing from a locale. Reads every resource file under `values*`, so
 * files added later by features are covered without touching this test.
 */
class StringResourcesTest {

    private val resDir = listOf(File("src/main/res"), File("app/src/main/res")).first { it.isDirectory }

    /** name -> text for every string, plural item and string-array item of one locale folder. */
    private fun strings(folder: String): Map<String, String> {
        val out = linkedMapOf<String, String>()
        val files = File(resDir, folder).listFiles { f -> f.extension == "xml" }.orEmpty()
        for (file in files) {
            val root = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).documentElement
            val nodes = root.childNodes
            for (i in 0 until nodes.length) {
                val el = nodes.item(i) as? Element ?: continue
                if (el.getAttribute("translatable") == "false") continue
                when (el.tagName) {
                    "string" -> out[el.getAttribute("name")] = el.textContent
                    "plurals", "string-array" -> out[el.getAttribute("name")] = el.textContent
                }
            }
        }
        return out
    }

    private val english = strings("values")
    private val persian = strings("values-fa")
    private val arabic = strings("values-ar")

    @Test
    fun `no screen shows an untranslated string in fa or ar`() {
        val problems = buildList {
            for ((locale, map) in listOf("fa" to persian, "ar" to arabic)) {
                (english.keys - map.keys).forEach { add("$it is missing from values-$locale") }
                (map.keys - english.keys).forEach { add("$it is in values-$locale but not in values") }
            }
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
        assertTrue("expected the design-system strings to be present", english.size > 20)
    }

    @Test
    fun `Arabic strings do not contain Persian yeh or kaf`() {
        val hits = arabic.filterValues { text -> text.any { it == 'ی' || it == 'ک' } }.keys
        assertTrue("values-ar uses Persian letters (U+06CC/U+06A9) in: $hits", hits.isEmpty())
    }

    @Test
    fun `Persian strings do not contain Arabic yeh or kaf`() {
        val hits = persian
            .filterKeys { it !in ARABIC_QUOTATIONS_IN_PERSIAN }
            .filterValues { text -> text.any { it == 'ي' || it == 'ك' } }
            .keys
        assertTrue("values-fa uses Arabic letters (U+064A/U+0643) in: $hits", hits.isEmpty())
    }

    @Test
    fun `translations keep the format arguments of the English string`() {
        val placeholder = Regex("%\\d+\\$[sd]")
        val problems = buildList {
            for ((locale, map) in listOf("fa" to persian, "ar" to arabic)) {
                for ((key, text) in english) {
                    val expected = placeholder.findAll(text).map { it.value }.toSet()
                    val actual = placeholder.findAll(map[key].orEmpty()).map { it.value }.toSet()
                    if (map.containsKey(key) && expected != actual) add("$key in values-$locale has $actual, expected $expected")
                }
            }
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    /** Someone who tried another language must find the way back (owner, 2026-11-26, notes item 44). */
    @Test
    fun `the Language setting also says Language in English in fa and ar`() {
        assertTrue(english.getValue("settings_language"), english.getValue("settings_language") == "Language")
        for ((locale, map) in listOf("fa" to persian, "ar" to arabic)) {
            val text = map.getValue("settings_language")
            assertTrue("values-$locale: $text", text.endsWith("(Language)"))
        }
    }

    private companion object {
        /** Keys of Persian strings that deliberately quote Arabic (§6.6). None yet. */
        val ARABIC_QUOTATIONS_IN_PERSIAN = emptySet<String>()
    }
}
