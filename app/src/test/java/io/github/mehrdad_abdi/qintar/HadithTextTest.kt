package io.github.mehrdad_abdi.qintar

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * The hadith of the ranks (`values/hadith.xml`), shown in all three languages on onboarding and in
 * the app language in Settings → About. Owner, 2026-11-26 (notes item 45): the whole hadith, to the
 * explanation of the qintar; the salawat without "and companions"; no book named under it.
 */
class HadithTextTest {

    private val resDir = listOf(File("src/main/res"), File("app/src/main/res")).first { it.isDirectory }

    private val texts: Map<String, String> = run {
        val root = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(File(resDir, "values/hadith.xml")).documentElement
        val nodes = root.getElementsByTagName("string")
        (0 until nodes.length).map { nodes.item(it) as Element }.associate { it.getAttribute("name") to it.textContent }
    }

    private val arabic = texts.getValue("hadith_text_ar")
    private val english = texts.getValue("hadith_text_en")
    private val persian = texts.getValue("hadith_text_fa")

    @Test
    fun `the hadith goes on to say what a qintar is`() {
        assertTrue(arabic, arabic.contains("خَمْسَةَ عَشَرَ أَلْفَ مِثْقَالٍ") && arabic.contains("جَبَلِ أُحُدٍ"))
        assertTrue(english, english.contains("fifteen thousand mithqals") && english.contains("Uhud"))
        assertTrue(persian, persian.contains("پانزده هزار مثقال") && persian.contains("کوه احد"))
    }

    @Test
    fun `the salawat does not mention the companions`() {
        assertFalse(arabic, arabic.contains("صحب"))
        assertFalse(persian, persian.contains("صحب"))
        assertFalse(english, english.contains("companions"))
        assertTrue(english, english.contains("(peace be upon him and his family)"))
    }

    @Test
    fun `each text uses its own letters`() {
        assertFalse("Arabic has Persian yeh or kaf", arabic.any { it == 'ی' || it == 'ک' })
        assertFalse("Persian has Arabic yeh or kaf", persian.any { it == 'ي' || it == 'ك' })
    }
}
