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

    private fun ReadViewModel.goTo(input: String, surah: Int? = null): ReaderRoute? {
        onGoToSurah(surah)
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
    fun `a bare ayah number means the chosen surah`() = runTest {
        val read = read()
        assertNull(read.goTo("255"))
        assertEquals(GoToProblem.NEEDS_SURAH, read.state.value.goToProblem)

        assertEquals(ReaderRoute.browse(app.ayah(2, 255).value), read.goTo("255", surah = 2))
        assertNull("the error clears when the input is accepted", read.state.value.goToProblem)
        assertEquals(2, read.state.value.goToSurah?.number)
    }

    @Test
    fun `Persian and Arabic-Indic digits are understood`() = runTest {
        val read = read()
        val kursi = ReaderRoute.browse(app.ayah(2, 255).value)
        assertEquals(kursi, read.goTo("۲:۲۵۵"))
        assertEquals(kursi, read.goTo("٢:٢٥٥"))
        assertEquals(ReaderRoute.browse(app.index.pageRange(123).start.value), read.goTo("p۱۲۳"))
        assertEquals(ReaderRoute.browse(app.index.juzRange(12).start.value), read.goTo("j١٢"))
        assertEquals(kursi, read.goTo("۲۵۵", surah = 2))
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
        assertNull("114 has 6 ayahs", read.goTo("7", surah = 114))
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
    fun `with a surah chosen the random ayah stays in that surah`() = runTest {
        val read = read(Random(2))
        read.onGoToSurah(18)
        val range = app.index.surahRange(18)
        repeat(300) {
            val id = checkNotNull(read.randomAyah()).startAyahId
            assertTrue(id in range.start.value..range.end.value)
        }
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
    fun `the surah list has 114 rows with coverage of what was ever read`() = runTest {
        app.tracker.markRange(app.index.surahRange(112), ReadContext.Browse, ReadMethod.BULK, RangeMode.FILL)
        app.tracker.mark(app.ayah(114, 1), ReadContext.Browse, ReadMethod.TAP)
        val read = read()
        val rows = read.state.value.surahs
        assertEquals(114, rows.size)
        assertEquals(1f, rows[111].coverage, 0f)
        assertEquals(1f / 6, rows[113].coverage, 0.001f)
        assertEquals(0f, rows[0].coverage, 0f)

        app.tracker.mark(app.ayah(1, 1), ReadContext.Random, ReadMethod.TAP)
        assertEquals("live", 1f / 7, read.state.value.surahs[0].coverage, 0.001f)
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
