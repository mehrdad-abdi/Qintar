package io.github.mehrdad_abdi.qintar.testing

import io.github.mehrdad_abdi.qintar.domain.model.AyahId
import io.github.mehrdad_abdi.qintar.domain.port.QuranTextSource
import io.github.mehrdad_abdi.qintar.domain.testing.DomainFixture
import io.github.mehrdad_abdi.qintar.feature.reader.ReaderRow
import io.github.mehrdad_abdi.qintar.feature.reader.ReaderViewModel
import io.github.mehrdad_abdi.qintar.feature.today.TodayViewModel
import io.github.mehrdad_abdi.qintar.ui.navigation.ReaderRoute
import java.time.DayOfWeek
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

/** The row of [ayah] in the reader's list (in [segment] for a group). */
fun ReaderViewModel.row(ayah: AyahId, segment: Int = 0): ReaderRow.Ayah {
    val model = checkNotNull(state.value.model) { "the reader has not opened" }
    val index = checkNotNull(model.indexOf(segment, ayah)) { "ayah ${ayah.value} is not in segment $segment" }
    return model.rows[index] as ReaderRow.Ayah
}

/** Today's count shown on [ayah]'s row. */
fun ReaderViewModel.countOf(ayah: AyahId, segment: Int = 0): Int = state.value.count(row(ayah, segment))
