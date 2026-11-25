package io.github.mehrdad_abdi.qintar.feature.bookmarks

import io.github.mehrdad_abdi.qintar.domain.model.BookmarkSort
import io.github.mehrdad_abdi.qintar.domain.service.ArrangeKey
import io.github.mehrdad_abdi.qintar.domain.service.BoardItem
import io.github.mehrdad_abdi.qintar.domain.service.BookmarkDrafts
import io.github.mehrdad_abdi.qintar.domain.testing.DomainFixture
import io.github.mehrdad_abdi.qintar.testing.MainDispatcherRule
import io.github.mehrdad_abdi.qintar.testing.arrangeViewModel
import io.github.mehrdad_abdi.qintar.ui.navigation.ArrangeRoute
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Arrange (owner, 2026-11-25): titles moved by dragging; each drag is saved as "My order". */
class ArrangeViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val app = DomainFixture()
    private var kahf = 0L
    private var fatiha = 0L
    private var mulk = 0L
    private var yasin = 0L
    private var night = 0L

    @Before
    fun seed() = runTest {
        kahf = app.library.create(BookmarkDrafts.surah(18, app.index)).id
        night = app.library.createGroup("Night").id
        mulk = app.library.create(BookmarkDrafts.surah(67, app.index).copy(groupId = night)).id
        fatiha = app.library.create(BookmarkDrafts.surah(1, app.index)).id
        yasin = app.library.create(BookmarkDrafts.surah(36, app.index).copy(groupId = night)).id
    }

    private suspend fun shown(): List<String> = app.availability.board().items.map { item ->
        when (item) {
            is BoardItem.Group -> "G${item.state.group.id}" + item.state.bookmarks.map { it.bookmark.id }
            is BoardItem.Loose -> "${item.state.bookmark.id}"
        }
    }

    @Test
    fun `the top level starts as shown, and a drag saves it as My order, keeping the groups' order`() = runTest {
        // Quran order: Al-Fatiha, Al-Kahf, then the group at Ya-Sin (Ya-Sin, Al-Mulk).
        val vm = app.arrangeViewModel(ArrangeRoute(), main.dispatcher)
        assertEquals(
            listOf(ArrangeKey.Bookmark(fatiha), ArrangeKey.Bookmark(kahf), ArrangeKey.Group(night)),
            vm.state.value.lines.map { it.key },
        )
        assertEquals(2, vm.state.value.lines.last().count)

        vm.onMove(2, 0)
        vm.onMoveDone()

        assertEquals(BookmarkSort.CUSTOM, app.prefs.current.bookmarkSort)
        assertEquals(listOf("G$night[$yasin, $mulk]", "$fatiha", "$kahf"), shown())
    }

    @Test
    fun `a group's bookmarks are arranged on their own, and the rest stays as it was shown`() = runTest {
        val vm = app.arrangeViewModel(ArrangeRoute(night), main.dispatcher)
        assertEquals("Night", vm.state.value.groupName)
        assertEquals(listOf(yasin, mulk), vm.state.value.lines.map { it.key.id })

        vm.onMove(1, 0)
        vm.onMoveDone()

        assertEquals(listOf("$fatiha", "$kahf", "G$night[$mulk, $yasin]"), shown())
    }
}
