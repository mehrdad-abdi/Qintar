package io.github.mehrdad_abdi.qintar.data.quran

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

/** Files under `tools/quran-data/`, located from the test's working directory (the module). */
internal object QuranDataFiles {
    val dir: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "tools/quran-data") }
            .firstOrNull { it.isDirectory }
            ?: error("tools/quran-data not found above ${File("").absolutePath}")
    }

    fun file(name: String) = File(dir, name)
    fun sourceFile(name: String) = File(File(dir, "source"), name)
}

/** The Tanzil sources, parsed independently of the generator, as the reference for the tests. */
internal object TanzilSources {

    data class SurahMeta(val number: Int, val ayahs: Int, val start: Int)

    val surahs: List<SurahMeta> by lazy {
        elements(QuranDataFiles.sourceFile("quran-data.xml"), "sura")
            // The metadata has <sura> only under <suras>.
            .map {
                SurahMeta(
                    number = it.getAttribute("index").toInt(),
                    ayahs = it.getAttribute("ayas").toInt(),
                    start = it.getAttribute("start").toInt(),
                )
            }
    }

    /** Ayah texts of the Tanzil XML in document order, i.e. by global id. */
    val texts: List<String> by lazy {
        elements(QuranDataFiles.sourceFile("quran-uthmani.xml"), "aya").map { it.getAttribute("text") }
    }

    private fun elements(file: File, tag: String): List<Element> {
        val factory = DocumentBuilderFactory.newInstance().apply {
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        }
        val nodes = factory.newDocumentBuilder().parse(file).getElementsByTagName(tag)
        return (0 until nodes.length).map { nodes.item(it) as Element }
    }
}
