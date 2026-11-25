package io.github.mehrdad_abdi.qintar.feature.read

import io.github.mehrdad_abdi.qintar.domain.model.AyahId
import io.github.mehrdad_abdi.qintar.domain.model.RangeMode
import io.github.mehrdad_abdi.qintar.domain.model.ReadContext
import io.github.mehrdad_abdi.qintar.domain.model.ReadMethod
import io.github.mehrdad_abdi.qintar.domain.testing.DomainFixture
import io.github.mehrdad_abdi.qintar.testing.MainDispatcherRule
import io.github.mehrdad_abdi.qintar.testing.readViewModel
import io.github.mehrdad_abdi.qintar.ui.common.GoToProblem
import io.github.mehrdad_abdi.qintar.ui.navigation.ReaderEntry
import io.github.mehrdad_abdi.qintar.ui.navigation.ReaderRoute
import kotlin.random.Random
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** The Read tab's use cases through its view model: go to, random, the indexes, khatms. */
class ReadViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val app = DomainFixture()
    private fun read(random: Random = Random(7)) = app.readViewModel(main.dispatcher, random)

    private fun ReadViewModel.goTo(input: String): ReaderRoute? {
        onGoToInput(input)
        return submitGoTo()
    }

    // ---- Go to -----------------------------------------------------------------------------------

    @Test
    fun `go to takes surah and ayah, a page and a juz, and opens them in Browse`() = runTest {
        val read = read()
        assertEquals(ReaderRoute.browse(app.ayah(2, 255).value), read.goTo("2:255"))
        assertEquals(ReaderRoute.browse(app.index.pageRange(123).start.value), read.goTo("p123"))
        assertEquals(ReaderRoute.browse(app.index.juzRange(5).start.value), read.goTo("J5"))
        assertEquals(ReaderRoute.browse(app.ayah(2, 255).value), read.goTo(" 2 : 255 "))
        assertNull(read.state.value.goToProblem)
    }

    @Test
    fun `a bare number is a surah, and a typed name suggests surahs`() = runTest {
        // Owner, 2026-11-25: no "bare ayah numbers refer to" chooser any more.
        val read = read()
        assertNull(read.goTo("255"))
        assertEquals(GoToProblem.SURAH_OUT_OF_RANGE, read.state.value.goToProblem)
        assertEquals(ReaderRoute.browse(app.ayah(36, 1).value), read.goTo("36"))
        assertNull("the error clears when the input is accepted", read.state.value.goToProblem)

        // The fixture's surahs are called "Surah 1" … "Surah 114".
        read.onGoToInput("sur")
        assertTrue(read.state.value.goToSuggestions.isNotEmpty())
        read.onGoToInput("2:25")
        assertTrue(read.state.value.goToSuggestions.isEmpty())
    }

    @Test
    fun `Persian and Arabic-Indic digits are understood`() = runTest {
        val read = read()
        val kursi = ReaderRoute.browse(app.ayah(2, 255).value)
        assertEquals(kursi, read.goTo("۲:۲۵۵"))
        assertEquals(kursi, read.goTo("٢:٢٥٥"))
        assertEquals(ReaderRoute.browse(app.index.pageRange(123).start.value), read.goTo("p۱۲۳"))
        assertEquals(ReaderRoute.browse(app.index.juzRange(12).start.value), read.goTo("j١٢"))
        assertEquals(ReaderRoute.browse(app.ayah(36, 1).value), read.goTo("۳۶"))
        // Owner, 2026-11-22: the Persian and Arabic letters, too.
        assertEquals(ReaderRoute.browse(app.index.juzRange(12).start.value), read.goTo("ج12"))
        assertEquals(ReaderRoute.browse(app.index.pageRange(300).start.value), read.goTo("ص۳۰۰"))
        assertEquals(ReaderRoute.browse(app.ayah(36, 1).value), read.goTo("s36"))
        assertEquals(ReaderRoute.browse(app.ayah(36, 1).value), read.goTo("س۳۶"))
    }

    @Test
    fun `a refused input says why, and nothing opens`() = runTest {
        val read = read()
        val cases = listOf(
            "" to GoToProblem.EMPTY,
            "   " to GoToProblem.EMPTY,
            "hello" to GoToProblem.UNRECOGNIZED,
            "2:" to GoToProblem.UNRECOGNIZED,
            "115:1" to GoToProblem.SURAH_OUT_OF_RANGE,
            "0:1" to GoToProblem.SURAH_OUT_OF_RANGE,
            "2:287" to GoToProblem.AYAH_OUT_OF_RANGE,
            "2:0" to GoToProblem.AYAH_OUT_OF_RANGE,
            "p605" to GoToProblem.PAGE_OUT_OF_RANGE,
            "p0" to GoToProblem.PAGE_OUT_OF_RANGE,
            "j31" to GoToProblem.JUZ_OUT_OF_RANGE,
            "j0" to GoToProblem.JUZ_OUT_OF_RANGE,
        )
        for ((input, problem) in cases) {
            assertNull("'$input' must not open", read.goTo(input))
            assertEquals("'$input'", problem, read.state.value.goToProblem)
        }
        assertNull("114 has 6 ayahs", read.goTo("114:7"))
        assertEquals(GoToProblem.AYAH_OUT_OF_RANGE, read.state.value.goToProblem)

        read.onGoToInput("2:255")
        assertNull("typing clears the message", read.state.value.goToProblem)
    }

    // ---- Random ----------------------------------------------------------------------------------

    @Test
    fun `a random ayah is any ayah of the Quran and opens as that one ayah`() = runTest {
        val read = read(Random(1))
        val ids = (1..2000).map {
            val route = checkNotNull(read.randomAyah())
            assertEquals(ReaderEntry.RANDOM, route.entry)
            assertEquals(route.startAyahId, route.scopeFirstAyahId)
            assertEquals(route.scopeFirstAyahId, route.scopeLastAyahId)
            route.startAyahId
        }
        assertTrue(ids.all { it in 1..6236 })
        assertTrue("not stuck on the short surahs: ${ids.max()}", ids.max() > 6000 && ids.min() < 200)
    }

    @Test
    fun `a random page opens with the whole page as its scope`() = runTest {
        val read = read(Random(3))
        val pages = (1..8000).map {
            val route = checkNotNull(read.randomPage())
            val page = app.index.pageOf(AyahId(route.scopeFirstAyahId))
            assertEquals(app.index.pageRange(page).start.value, route.scopeFirstAyahId)
            assertEquals(app.index.pageRange(page).end.value, route.scopeLastAyahId)
            assertEquals(route.scopeFirstAyahId, route.startAyahId)
            page
        }
        assertEquals("every page can come up", 604, pages.toSet().size)
    }

    // ---- The indexes -----------------------------------------------------------------------------

    @Test
    fun `the surah bars show what was read today, from anywhere, and start again the next day`() = runTest {
        // Owner, 2026-11-25: the khatm has its own view; the surah list shows today's reading.
        app.tracker.markRange(app.index.surahRange(112), ReadContext.Browse, ReadMethod.BULK, RangeMode.FILL)
        app.clock.advanceDays(1)
        val read = read()
        assertTrue("nothing read today, no bars", read.state.value.surahs.all { (it.coverage ?: 0f) == 0f })

        val khatm = app.khatms.start("Ramadan")
        app.tracker.markRange(app.index.surahRange(1), ReadContext.Khatm(khatm.id), ReadMethod.BULK, RangeMode.FILL)
        app.tracker.mark(app.ayah(2, 1), ReadContext.Browse, ReadMethod.TAP)
        app.tracker.mark(app.ayah(2, 1), ReadContext.Browse, ReadMethod.TAP)
        val rows = read.state.value.surahs
        assertEquals(114, rows.size)
        assertEquals("a khatm reading counts", 1f, rows[0].coverage!!, 0f)
        assertEquals("one ayah, read twice", 1f / 286, rows[1].coverage!!, 0.0001f)
        assertEquals("read yesterday", 0f, rows[111].coverage ?: 0f, 0f)

        app.clock.advanceDays(1)
        assertTrue("a new day starts empty", read.state.value.surahs.all { (it.coverage ?: 0f) == 0f })
    }

    @Test
    fun `the surah filter matches number, Arabic name and transliteration`() = runTest {
        val read = read()
        read.onFilter("18")
        assertEquals(listOf(18), read.state.value.visibleSurahs.map { it.surah.number })
        read.onFilter("۱۸")
        assertEquals("Persian digits", listOf(18), read.state.value.visibleSurahs.map { it.surah.number })
        read.onFilter("surah 11")
        assertEquals(listOf(11, 110, 111, 112, 113, 114), read.state.value.visibleSurahs.map { it.surah.number }.sorted())
        read.onFilter("zzz")
        assertTrue(read.state.value.visibleSurahs.isEmpty())
        read.onFilter("")
        assertEquals(114, read.state.value.visibleSurahs.size)
    }

    @Test
    fun `juz rows say where the juz starts and each list opens the reader there`() = runTest {
        val read = read()
        val juz = read.state.value.juz
        assertEquals(30, juz.size)
        assertEquals(app.index.juzRange(7).start, app.index.ayahId(juz[6].start))
        assertEquals(ReaderRoute.browse(app.index.surahRange(36).start.value), read.surahRoute(36))
        assertEquals(ReaderRoute.browse(app.index.juzRange(7).start.value), read.juzRoute(7))
        assertEquals(ReaderRoute.browse(app.index.pageRange(300).start.value), read.pageRoute(300))
    }

    @Test
    fun `continue browsing follows the last Browse position`() = runTest {
        val read = read()
        assertNull(read.state.value.continueBrowsing)
        assertNull(read.continueRoute())

        app.prefs.update { it.copy(lastBrowseAyahId = app.ayah(18, 10)) }

        val last = checkNotNull(read.state.value.continueBrowsing)
        assertEquals(18, last.surah.number)
        assertEquals(10, last.ayah)
        assertEquals(ReaderRoute.browse(app.ayah(18, 10).value), read.continueRoute())
    }

    @Test
    fun `the tab shows every khatm in progress`() = runTest {
        val read = read()
        assertTrue(read.state.value.khatms.isEmpty())
        app.khatms.start("Personal")
        app.khatms.start("Ramadan", dailyPages = 4f)

        assertEquals(setOf("Personal", "Ramadan"), read.state.value.khatms.map { it.name }.toSet())
        assertEquals(4f, read.state.value.khatms.first { it.name == "Ramadan" }.dailyPages)
    }
}
