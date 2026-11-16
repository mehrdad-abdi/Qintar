package io.github.mehrdad_abdi.qintar.testing

import io.github.mehrdad_abdi.qintar.domain.model.AyahId
import io.github.mehrdad_abdi.qintar.domain.port.QuranTextSource
import io.github.mehrdad_abdi.qintar.domain.testing.DomainFixture
import io.github.mehrdad_abdi.qintar.feature.bookmarks.BookmarkEditorViewModel
import io.github.mehrdad_abdi.qintar.feature.bookmarks.BookmarksViewModel
import io.github.mehrdad_abdi.qintar.feature.bookmarks.GroupEditorViewModel
import io.github.mehrdad_abdi.qintar.feature.khatm.KhatmDetailViewModel
import io.github.mehrdad_abdi.qintar.feature.khatm.KhatmListViewModel
import io.github.mehrdad_abdi.qintar.feature.khatm.NewKhatmViewModel
import io.github.mehrdad_abdi.qintar.feature.read.ReadViewModel
import io.github.mehrdad_abdi.qintar.feature.reader.ReaderRow
import io.github.mehrdad_abdi.qintar.feature.reader.ReaderViewModel
import io.github.mehrdad_abdi.qintar.feature.today.TodayViewModel
import io.github.mehrdad_abdi.qintar.ui.navigation.BookmarkEditorRoute
import io.github.mehrdad_abdi.qintar.ui.navigation.GroupEditorRoute
import io.github.mehrdad_abdi.qintar.ui.navigation.KhatmDetailRoute
import io.github.mehrdad_abdi.qintar.ui.navigation.ReaderRoute
import java.time.DayOfWeek
import kotlin.random.Random
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * Makes `viewModelScope` run on a test dispatcher. Unconfined, so a user action has had its effect
 * when the call returns; time (the rank effect's three seconds, debounces) moves only when the test
 * advances [dispatcher]'s scheduler.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(val dispatcher: TestDispatcher = UnconfinedTestDispatcher()) : TestWatcher() {
    override fun starting(description: Description) = Dispatchers.setMain(dispatcher)

    override fun finished(description: Description) = Dispatchers.resetMain()

    fun advanceTimeBy(millis: Long) {
        dispatcher.scheduler.advanceTimeBy(millis)
        dispatcher.scheduler.runCurrent()
    }
}

/*
 * The feature view models over the in-memory domain. Feature tests build screens' view models with
 * these, act through the view model (or through the real screen), and assert on its state and on the
 * fixture's log.
 */

fun DomainFixture.readerViewModel(
    route: ReaderRoute,
    io: CoroutineDispatcher,
    text: QuranTextSource = this.text,
) = ReaderViewModel(
    route = route,
    index = { index },
    text = text,
    log = log,
    prefs = prefs,
    clock = clock,
    tracker = tracker,
    totals = totals,
    khatmProgress = { khatmProgress },
    availability = availability,
    library = library,
    bulk = { bulk },
    io = io,
)

fun DomainFixture.todayViewModel(
    io: CoroutineDispatcher,
    systemFirstDay: DayOfWeek = DayOfWeek.SATURDAY,
    ticks: Flow<Unit> = emptyFlow(),
) = TodayViewModel(
    index = { index },
    prefs = prefs,
    clock = clock,
    totals = totals,
    khatmProgress = { khatmProgress },
    khatms = khatms,
    availability = availability,
    io = io,
    systemFirstDay = { systemFirstDay },
    ticks = ticks,
)

fun DomainFixture.readViewModel(io: CoroutineDispatcher, random: Random = Random(7)) = ReadViewModel(
    index = { index },
    prefs = prefs,
    revisit = { revisit },
    khatmProgress = { khatmProgress },
    io = io,
    random = random,
)

fun DomainFixture.khatmListViewModel(io: CoroutineDispatcher) = KhatmListViewModel(
    index = { index },
    khatmProgress = { khatmProgress },
    io = io,
)

fun DomainFixture.newKhatmViewModel(io: CoroutineDispatcher) = NewKhatmViewModel(
    index = { index },
    khatms = khatms,
    clock = clock,
    io = io,
)

fun DomainFixture.khatmDetailViewModel(id: Long, io: CoroutineDispatcher) = KhatmDetailViewModel(
    route = KhatmDetailRoute(id),
    index = { index },
    khatmProgress = { khatmProgress },
    khatms = khatms,
    io = io,
)

fun DomainFixture.bookmarksViewModel(io: CoroutineDispatcher, ticks: Flow<Unit> = emptyFlow()) = BookmarksViewModel(
    index = { index },
    availability = availability,
    library = library,
    tracker = tracker,
    prefs = prefs,
    clock = clock,
    io = io,
    ticks = ticks,
)

fun DomainFixture.bookmarkEditorViewModel(
    route: BookmarkEditorRoute,
    io: CoroutineDispatcher,
    text: QuranTextSource = this.text,
    systemFirstDay: DayOfWeek = DayOfWeek.SATURDAY,
) = BookmarkEditorViewModel(
    route = route,
    index = { index },
    text = text,
    library = library,
    availability = availability,
    prefs = prefs,
    io = io,
    systemFirstDay = { systemFirstDay },
)

fun DomainFixture.groupEditorViewModel(
    route: GroupEditorRoute,
    io: CoroutineDispatcher,
    systemFirstDay: DayOfWeek = DayOfWeek.SATURDAY,
) = GroupEditorViewModel(
    route = route,
    index = { index },
    library = library,
    availability = availability,
    prefs = prefs,
    io = io,
    systemFirstDay = { systemFirstDay },
)

/** The row of [ayah] in the reader's list (in [segment] for a group). */
fun ReaderViewModel.row(ayah: AyahId, segment: Int = 0): ReaderRow.Ayah {
    val model = checkNotNull(state.value.model) { "the reader has not opened" }
    val index = checkNotNull(model.indexOf(segment, ayah)) { "ayah ${ayah.value} is not in segment $segment" }
    return model.rows[index] as ReaderRow.Ayah
}

/** Today's count shown on [ayah]'s row. */
fun ReaderViewModel.countOf(ayah: AyahId, segment: Int = 0): Int = state.value.count(row(ayah, segment))
