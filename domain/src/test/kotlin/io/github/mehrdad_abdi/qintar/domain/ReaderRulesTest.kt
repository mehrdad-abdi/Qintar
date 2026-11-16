package io.github.mehrdad_abdi.qintar.domain

import io.github.mehrdad_abdi.qintar.domain.model.AyahId
import io.github.mehrdad_abdi.qintar.domain.model.AyahRange
import io.github.mehrdad_abdi.qintar.domain.model.AyahRef
import io.github.mehrdad_abdi.qintar.domain.model.RangeMode
import io.github.mehrdad_abdi.qintar.domain.model.ReadContext
import io.github.mehrdad_abdi.qintar.domain.model.ReadMethod
import io.github.mehrdad_abdi.qintar.domain.service.AyahTextFormatter
import io.github.mehrdad_abdi.qintar.domain.service.GoToParser
import io.github.mehrdad_abdi.qintar.domain.service.GoToResult
import io.github.mehrdad_abdi.qintar.domain.service.GoToResult.Reason
import io.github.mehrdad_abdi.qintar.domain.testing.DomainFixture
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** §5.4.2 ayah formatting, §5.3 "Go to…", and §4.2.2 "mark up to here". */
class ReaderRulesTest {
    private val app = DomainFixture()
    private val index = app.index

    // Pause marks, written as escapes so the test does not depend on how an editor shows them.
    private val sala = "ۖ"
    private val qala = "ۗ"
    private val mim = "ۘ"
    private val la = "ۙ"
    private val jim = "ۚ"
    private val threeDots = "ۛ"

    // ---- formatting ----

    @Test
    fun `a line break replaces the first space after each of the four pause marks`() {
        for (mark in listOf(sala, qala, mim, jim)) {
            assertEquals("alpha $mark\nbeta gamma", AyahTextFormatter.withPauseBreaks("alpha $mark beta gamma"))
            assertEquals("a mark attached to its word", "alpha$mark\nbeta gamma", AyahTextFormatter.withPauseBreaks("alpha$mark beta gamma"))
        }
    }

    @Test
    fun `no break at the la mark, which means do not stop, nor at the paired three dots`() {
        for (mark in listOf(la, threeDots)) {
            val text = "alpha $mark beta $mark gamma"
            assertEquals(text, AyahTextFormatter.withPauseBreaks(text))
        }
    }

    @Test
    fun `an ayah with several pause marks breaks after each and nowhere else`() {
        val text = "one two $sala three four $la five $jim six seven $threeDots eight $qala nine"

        assertEquals(
            "one two $sala\nthree four $la five $jim\nsix seven $threeDots eight $qala\nnine",
            AyahTextFormatter.withPauseBreaks(text),
        )
    }

    @Test
    fun `a pause mark at the very end of an ayah adds no break`() {
        val text = "alpha beta $sala"
        assertEquals(text, AyahTextFormatter.withPauseBreaks(text))
        assertEquals("the end marker stays on the last line", "$text ۝١", AyahTextFormatter.format(text, 1, breakAtPauseMarks = true))
    }

    @Test
    fun `two pause marks before one space produce a single break`() {
        assertEquals("alpha $sala$jim\nbeta", AyahTextFormatter.withPauseBreaks("alpha $sala$jim beta"))
    }

    @Test
    fun `text without pause marks is returned untouched`() {
        val text = "alpha beta gamma"
        assertSame(text, AyahTextFormatter.withPauseBreaks(text))
    }

    @Test
    fun `with the setting off no break is inserted`() {
        val text = "alpha $sala beta"
        assertEquals("$text ۝٥", AyahTextFormatter.format(text, 5, breakAtPauseMarks = false))
    }

    @Test
    fun `formatting never alters the source text beyond turning those spaces into line breaks`() {
        // A sample with every mark, combining characters, and doubled spaces.
        val samples = listOf(
            "يَسْـَٔلُونَكَ $sala قُلِ $qala x $mim y $la z $jim w $threeDots v",
            "a$sala  b",
            "$sala",
            "",
            " $jim ",
        )
        for (text in samples) {
            val broken = AyahTextFormatter.withPauseBreaks(text)
            assertEquals("same length", text.length, broken.length)
            assertEquals("restoring the spaces gives back the source", text, broken.replace('\n', ' '))
            assertTrue(AyahTextFormatter.format(text, 7, true).startsWith(broken))
        }
    }

    @Test
    fun `the ayah end is U+06DD followed by the number in Arabic-Indic digits`() {
        assertEquals("۝١", AyahTextFormatter.ayahEndMarker(1))
        assertEquals("۝٢٥", AyahTextFormatter.ayahEndMarker(25))
        assertEquals("۝٢٨٦", AyahTextFormatter.ayahEndMarker(286))
        assertEquals("١٠٩", AyahTextFormatter.arabicIndic(109))
    }

    @Test
    fun `the end marker is always Arabic-Indic and follows the text after one space`() {
        val shown = AyahTextFormatter.format("alpha", 12, breakAtPauseMarks = true)

        assertEquals("alpha ۝١٢", shown)
        assertFalse("no Latin or Persian digits", shown.any { it in '0'..'9' || it in '۰'..'۹' })
    }

    // ---- go to ----

    private fun parse(input: String, surah: Int? = null) = GoToParser.parse(input, surah, index)

    @Test
    fun `surah colon ayah goes to that ayah`() {
        assertEquals(GoToResult.Ayah(AyahRef(2, 255), app.ayah(2, 255)), parse("2:255"))
        assertEquals(app.ayah(114, 6), parse("114:6").target)
        assertEquals(AyahId(1), parse("1:1").target)
        assertEquals("spaces are forgiven", app.ayah(2, 255), parse(" 2 : 255 ").target)
    }

    @Test
    fun `a bare number is an ayah of the chosen surah`() {
        assertEquals(GoToResult.Ayah(AyahRef(2, 255), app.ayah(2, 255)), parse("255", surah = 2))
        assertEquals(GoToResult.Invalid(Reason.NEEDS_SURAH), parse("255"))
        assertEquals(GoToResult.Invalid(Reason.AYAH_OUT_OF_RANGE), parse("255", surah = 1))
    }

    @Test
    fun `p goes to a page and j to a juz, in either letter case`() {
        assertEquals(GoToResult.Page(300, index.pageRange(300).start), parse("p300"))
        assertEquals(GoToResult.Page(1, AyahId(1)), parse("P1"))
        assertEquals(GoToResult.Page(604, index.pageRange(604).start), parse("p 604"))
        assertEquals(GoToResult.Juz(12, index.juzRange(12).start), parse("j12"))
        assertEquals(GoToResult.Juz(30, index.juzRange(30).start), parse("J30"))
    }

    @Test
    fun `numbers outside the mushaf are refused with the reason`() {
        assertEquals(GoToResult.Invalid(Reason.SURAH_OUT_OF_RANGE), parse("115:1"))
        assertEquals(GoToResult.Invalid(Reason.SURAH_OUT_OF_RANGE), parse("0:1"))
        assertEquals(GoToResult.Invalid(Reason.AYAH_OUT_OF_RANGE), parse("2:287"))
        assertEquals(GoToResult.Invalid(Reason.AYAH_OUT_OF_RANGE), parse("2:0"))
        assertEquals(GoToResult.Invalid(Reason.PAGE_OUT_OF_RANGE), parse("p0"))
        assertEquals(GoToResult.Invalid(Reason.PAGE_OUT_OF_RANGE), parse("p605"))
        assertEquals(GoToResult.Invalid(Reason.JUZ_OUT_OF_RANGE), parse("j31"))
        assertEquals(GoToResult.Invalid(Reason.JUZ_OUT_OF_RANGE), parse("j0"))
        assertEquals("absurdly long numbers do not overflow into something valid", GoToResult.Invalid(Reason.PAGE_OUT_OF_RANGE), parse("p99999999999999999999"))
    }

    @Test
    fun `anything else is unrecognized, and nothing is empty`() {
        assertEquals(GoToResult.Invalid(Reason.EMPTY), parse(""))
        assertEquals(GoToResult.Invalid(Reason.EMPTY), parse("   "))
        for (input in listOf("abc", "2:", ":5", "2:5:1", "p", "j", "x12", "2-255", "-5", "p-3", "2.5")) {
            assertEquals(input, GoToResult.Invalid(Reason.UNRECOGNIZED), parse(input, surah = 2))
        }
        assertNull(parse("abc").target)
    }

    @Test
    fun `Persian and Arabic-Indic digits are accepted`() {
        assertEquals(app.ayah(2, 255), parse("۲:۲۵۵").target)
        assertEquals(app.ayah(2, 255), parse("٢:٢٥٥").target)
        assertEquals(GoToResult.Page(300, index.pageRange(300).start), parse("p۳۰۰"))
    }

    // ---- mark up to here ----

    @Test
    fun `mark up to here runs from the scope start when nothing before the tapped ayah is read`() = runTest {
        val bookmark = ReadContext.Bookmark(1)
        val range = index.surahRange(36)

        val resolved = app.bulk.upToHere(app.ayah(36, 12), bookmark, scopeStart = range.start)

        assertEquals(AyahRange(app.ayah(36, 1), app.ayah(36, 12)), resolved)
    }

    @Test
    fun `mark up to here starts after the nearest earlier ayah read today in this context`() = runTest {
        val browse = ReadContext.Browse
        app.tracker.mark(app.ayah(36, 3), browse, ReadMethod.TAP)
        app.tracker.mark(app.ayah(36, 7), browse, ReadMethod.TAP)
        // Read in another context: irrelevant to this one.
        app.tracker.mark(app.ayah(36, 10), ReadContext.Random, ReadMethod.TAP)

        val resolved = app.bulk.upToHere(app.ayah(36, 12), browse, scopeStart = app.ayah(36, 1))

        assertEquals(AyahRange(app.ayah(36, 8), app.ayah(36, 12)), resolved)

        val batch = app.tracker.markRange(resolved, browse, ReadMethod.BULK, RangeMode.FILL)
        assertEquals(5, batch.added)
    }

    @Test
    fun `mark up to here ignores yesterday's readings outside a khatm`() = runTest {
        app.tracker.mark(app.ayah(36, 7), ReadContext.Browse, ReadMethod.TAP)
        app.clock.advanceDays(1)

        val resolved = app.bulk.upToHere(app.ayah(36, 12), ReadContext.Browse, scopeStart = app.ayah(36, 1))

        assertEquals(app.ayah(36, 1), resolved.start)
    }

    @Test
    fun `in a khatm mark up to here starts after the nearest covered ayah, even one covered on an earlier day`() = runTest {
        val khatm = app.khatms.start("Khatm 1").id
        val context = ReadContext.Khatm(khatm)
        app.tracker.mark(app.ayah(36, 7), context, ReadMethod.TAP)
        app.clock.advanceDays(1)

        val resolved = app.bulk.upToHere(app.ayah(36, 12), context, scopeStart = app.ayah(36, 1))

        assertEquals(AyahRange(app.ayah(36, 8), app.ayah(36, 12)), resolved)
    }

    @Test
    fun `in a khatm the assumed prefix counts as read`() = runTest {
        val khatm = app.khatms.start("Khatm 1", startFrom = app.ayah(36, 5)).id

        val resolved = app.bulk.upToHere(app.ayah(36, 12), ReadContext.Khatm(khatm), scopeStart = app.ayah(36, 1))

        assertEquals(AyahRange(app.ayah(36, 5), app.ayah(36, 12)), resolved)
    }

    @Test
    fun `the scope start of a browse or khatm reader is the opened surah's start or the resume position, whichever is later`() {
        assertEquals(app.ayah(36, 1), app.bulk.scopeStart(firstOnScreenAtOpen = app.ayah(36, 40)))
        assertEquals(app.ayah(36, 30), app.bulk.scopeStart(firstOnScreenAtOpen = app.ayah(36, 40), resume = app.ayah(36, 30)))
        assertEquals("a resume position in an earlier surah does not widen the scope", app.ayah(36, 1),
            app.bulk.scopeStart(firstOnScreenAtOpen = app.ayah(36, 40), resume = app.ayah(2, 5)))
    }

    @Test
    fun `mark up to here never reaches back before the scope start`() = runTest {
        val resolved = app.bulk.upToHere(app.ayah(36, 45), ReadContext.Browse, scopeStart = app.ayah(36, 30))
        assertEquals(AyahRange(app.ayah(36, 30), app.ayah(36, 45)), resolved)

        assertEquals("tapping the scope start itself marks just it", AyahRange.single(app.ayah(36, 30)),
            app.bulk.upToHere(app.ayah(36, 30), ReadContext.Browse, scopeStart = app.ayah(36, 30)))
    }

    @Test
    fun `tapping above the scope start is bounded by that ayah's own surah`() = runTest {
        val resolved = app.bulk.upToHere(app.ayah(35, 10), ReadContext.Browse, scopeStart = app.ayah(36, 30))
        assertEquals(AyahRange(app.ayah(35, 1), app.ayah(35, 10)), resolved)
    }

    @Test
    fun `mark page covers the page's ayahs inside the reader's scope and fills only the unread ones`() = runTest {
        val page = index.pageRange(300)
        app.tracker.mark(page.start, ReadContext.Browse, ReadMethod.TAP)

        val batch = app.tracker.markRange(app.bulk.page(300)!!, ReadContext.Browse, ReadMethod.BULK, RangeMode.FILL)
        assertEquals(page.size - 1, batch.added)

        val bookmarkScope = AyahRange(AyahId(page.start.value + 2), AyahId(page.end.value + 50))
        assertEquals(AyahRange(bookmarkScope.start, page.end), app.bulk.page(300, bookmarkScope))
        assertNull("a page outside the scope has nothing to mark", app.bulk.page(1, bookmarkScope))
    }
}
