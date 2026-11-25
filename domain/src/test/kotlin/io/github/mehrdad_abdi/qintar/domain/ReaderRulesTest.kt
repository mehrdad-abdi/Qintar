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
        assertEquals("the end marker stays on the last line", "alpha beta\u00A0$sala ۝١", AyahTextFormatter.format(text, 1, breakAtPauseMarks = true))
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
        assertEquals("alpha\u00A0$sala beta ۝٥", AyahTextFormatter.format(text, 5, breakAtPauseMarks = false))
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
            assertTrue(AyahTextFormatter.format(text, 7, true).replace('\u00A0', ' ').startsWith(broken))
        }
    }

    @Test
    fun `every pause mark is tied to the word before it by a no-break space`() {
        val text = "one $sala two $la three $threeDots four $jim five"

        val tied = AyahTextFormatter.tiePauseMarks(text)

        assertEquals("one\u00A0$sala two\u00A0$la three\u00A0$threeDots four\u00A0$jim five", tied)
        assertEquals("only spaces changed", text, tied.replace('\u00A0', ' '))
        assertSame("nothing to tie", "alpha beta", AyahTextFormatter.tiePauseMarks("alpha beta"))
    }

    @Test
    fun `the display string ties marks and still breaks after the breaking ones`() {
        val shown = AyahTextFormatter.format("one $sala two $la three", 3, breakAtPauseMarks = true)

        assertEquals("one\u00A0$sala\ntwo\u00A0$la three ۝٣", shown)
        for (line in shown.lines()) assertFalse("no line starts with a mark", line.first() in '\u06D6'..'\u06DC')
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

    private fun parse(input: String) = GoToParser.parse(input, index)

    /** The fixture's index with real names for the surahs the name tests use. */
    private val named = index.surahs.map { s ->
        when (s.number) {
            36 -> s.copy(nameAr = "يس", nameLatin = "Yaseen", nameEn = "Yaseen")
            67 -> s.copy(nameAr = "الملك", nameLatin = "Al-Mulk", nameEn = "The Sovereignty")
            73 -> s.copy(nameAr = "المزمل", nameLatin = "Al-Muzzammil", nameEn = "The Enshrouded One")
            else -> s.copy(nameAr = "سورة", nameLatin = "Surah", nameEn = "Surah")
        }
    }

    private fun parseNamed(input: String) = GoToParser.parse(input, index, named)

    @Test
    fun `surah colon ayah goes to that ayah`() {
        assertEquals(GoToResult.Ayah(AyahRef(2, 255), app.ayah(2, 255)), parse("2:255"))
        assertEquals(app.ayah(114, 6), parse("114:6").target)
        assertEquals(AyahId(1), parse("1:1").target)
        assertEquals("spaces are forgiven", app.ayah(2, 255), parse(" 2 : 255 ").target)
    }

    @Test
    fun `a bare number is a surah`() {
        // Owner, 2026-11-25: with names, the "bare ayah numbers refer to" chooser is gone; 36 is Yaseen.
        assertEquals(GoToResult.Surah(36, app.ayah(36, 1)), parse("36"))
        assertEquals(GoToResult.Surah(36, app.ayah(36, 1)), parse("۳۶"))
        assertEquals(GoToResult.Invalid(Reason.SURAH_OUT_OF_RANGE), parse("115"))
        assertEquals(GoToResult.Invalid(Reason.SURAH_OUT_OF_RANGE), parse("0"))
    }

    @Test
    fun `a surah by name, in any script and without vowels, alone or with an ayah number`() {
        assertEquals(GoToResult.Surah(36, app.ayah(36, 1)), parseNamed("yasin"))
        assertEquals(GoToResult.Ayah(AyahRef(36, 12), app.ayah(36, 12)), parseNamed("yasin 12"))
        assertEquals(GoToResult.Ayah(AyahRef(36, 12), app.ayah(36, 12)), parseNamed("Yaseen:12"))
        assertEquals(GoToResult.Ayah(AyahRef(36, 12), app.ayah(36, 12)), parseNamed("یس ۱۲"))
        assertEquals(GoToResult.Ayah(AyahRef(67, 3), app.ayah(67, 3)), parseNamed("ملک3"))
        assertEquals(GoToResult.Surah(73, app.ayah(73, 1)), parseNamed("المُزَّمِّل"))
        assertEquals("the word surah before the name or number is forgiven", app.ayah(67, 1), parseNamed("surah mulk").target)
        assertEquals(app.ayah(36, 1), parseNamed("sura 36").target)
        assertEquals(GoToResult.Invalid(Reason.AYAH_OUT_OF_RANGE), parseNamed("mulk 31"))
        assertEquals(GoToResult.Invalid(Reason.UNRECOGNIZED), parseNamed("qqq 3"))
    }

    @Test
    fun `suggestions are the surahs a typed name may be, only while no number is typed`() {
        assertEquals(listOf(67, 73), GoToParser.suggestions("mu", index, named).map { it.number }.take(2))
        assertEquals(emptyList<Int>(), GoToParser.suggestions("mulk 3", index, named).map { it.number })
        assertEquals(emptyList<Int>(), GoToParser.suggestions("p30", index, named).map { it.number })
        assertEquals(emptyList<Int>(), GoToParser.suggestions("2:255", index, named).map { it.number })
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
    fun `Persian and Arabic keyboards work too - ج for juz, ص for page, with any digits`() {
        // Owner, 2026-11-22: ج12 jumps to juz 12.
        assertEquals(GoToResult.Juz(12, index.juzRange(12).start), parse("ج12"))
        assertEquals(GoToResult.Juz(12, index.juzRange(12).start), parse("ج۱۲"))
        assertEquals(GoToResult.Juz(12, index.juzRange(12).start), parse("ج ١٢"))
        assertEquals(GoToResult.Juz(30, index.juzRange(30).start), parse("جزء30"))
        assertEquals(GoToResult.Juz(30, index.juzRange(30).start), parse("جزء ۳۰"))
        assertEquals(GoToResult.Page(300, index.pageRange(300).start), parse("ص300"))
        assertEquals(GoToResult.Page(300, index.pageRange(300).start), parse("ص۳۰۰"))
        assertEquals(GoToResult.Page(300, index.pageRange(300).start), parse("صفحه ۳۰۰"))
        assertEquals(GoToResult.Page(300, index.pageRange(300).start), parse("صفحة ٣٠٠"))
        assertEquals(GoToResult.Invalid(Reason.JUZ_OUT_OF_RANGE), parse("ج31"))
        assertEquals("an Arabic or Persian colon-like separator", app.ayah(2, 255), parse("۲/۲۵۵").target)
    }

    @Test
    fun `a surah by its number, with any keyboard in any app language`() {
        // Owner, 2026-11-22: s36 (or س36) opens Ya-Sin at its first ayah; Latin forms work in a Persian app too.
        assertEquals(GoToResult.Surah(36, app.ayah(36, 1)), parse("s36"))
        assertEquals(app.ayah(36, 1), parse("S36").target)
        assertEquals(app.ayah(36, 1), parse("س۳۶").target)
        assertEquals(app.ayah(36, 1), parse("سوره ۳۶").target)
        assertEquals(app.ayah(36, 1), parse("سورة ٣٦").target)
        assertEquals(GoToResult.Invalid(Reason.SURAH_OUT_OF_RANGE), parse("s115"))
        assertEquals(GoToResult.Invalid(Reason.SURAH_OUT_OF_RANGE), parse("s0"))
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
            assertEquals(input, GoToResult.Invalid(Reason.UNRECOGNIZED), parse(input))
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
