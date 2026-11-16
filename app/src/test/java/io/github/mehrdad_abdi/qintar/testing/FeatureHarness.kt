package io.github.mehrdad_abdi.qintar.testing

import io.github.mehrdad_abdi.qintar.domain.model.AyahId
import io.github.mehrdad_abdi.qintar.domain.port.QuranTextSource
import io.github.mehrdad_abdi.qintar.domain.testing.DomainFixture
import io.github.mehrdad_abdi.qintar.feature.bookmarks.BookmarkEditorViewModel
import io.github.mehrdad_abdi.qintar.feature.bookmarks.BookmarksViewModel
import io.github.mehrdad_abdi.qintar.feature.bookmarks.GroupEditorViewModel
import io.github.mehrdad_abdi.qintar.feature.insights.DayDetailViewModel
import io.github.mehrdad_abdi.qintar.feature.insights.InsightsViewModel
import io.github.mehrdad_abdi.qintar.feature.insights.LogReadingViewModel
import io.github.mehrdad_abdi.qintar.feature.khatm.KhatmDetailViewModel
import io.github.mehrdad_abdi.qintar.feature.khatm.KhatmListViewModel
import io.github.mehrdad_abdi.qintar.feature.khatm.NewKhatmViewModel
import io.github.mehrdad_abdi.qintar.data.backup.BackupCodec
import io.github.mehrdad_abdi.qintar.data.legacy.LegacyMigration
import io.github.mehrdad_abdi.qintar.data.legacy.LegacyMigrationOutcome
import io.github.mehrdad_abdi.qintar.domain.backup.ImportResult
import io.github.mehrdad_abdi.qintar.domain.backup.LegacyReport
import io.github.mehrdad_abdi.qintar.domain.model.LegacyMigrationState
import io.github.mehrdad_abdi.qintar.domain.testing.FakePrefsStore
import io.github.mehrdad_abdi.qintar.feature.onboarding.OnboardingViewModel
import io.github.mehrdad_abdi.qintar.feature.settings.BackupViewModel
import io.github.mehrdad_abdi.qintar.feature.settings.MigrationNoticeViewModel
import io.github.mehrdad_abdi.qintar.feature.settings.MigrationReports
import io.github.mehrdad_abdi.qintar.feature.settings.SettingsViewModel
import io.github.mehrdad_abdi.qintar.feature.read.ReadViewModel
import io.github.mehrdad_abdi.qintar.feature.reader.ReaderRow
import io.github.mehrdad_abdi.qintar.feature.reader.ReaderViewModel
import io.github.mehrdad_abdi.qintar.feature.today.TodayViewModel
import io.github.mehrdad_abdi.qintar.ui.navigation.BookmarkEditorRoute
import io.github.mehrdad_abdi.qintar.ui.navigation.DayDetailRoute
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

fun DomainFixture.logReadingViewModel(io: CoroutineDispatcher) = LogReadingViewModel(
    index = { index },
    tracker = tracker,
    clock = clock,
    io = io,
)

fun DomainFixture.dayDetailViewModel(isoDate: String, io: CoroutineDispatcher) = DayDetailViewModel(
    route = DayDetailRoute(isoDate),
    index = { index },
    log = log,
    totals = totals,
    khatmStore = khatmStore,
    bookmarkStore = bookmarkStore,
    tracker = tracker,
    clock = clock,
    io = io,
)

fun DomainFixture.insightsViewModel(io: CoroutineDispatcher, systemFirstDay: DayOfWeek = DayOfWeek.SATURDAY) = InsightsViewModel(
    index = { index },
    revisit = { revisit },
    totals = totals,
    prefs = prefs,
    clock = clock,
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


/**
 * A stand-in for the v1 migration: a retry of a `FAILED` migration succeeds when [succeedOnRetry] is
 * set, and records `MIGRATED` the way the real one does.
 */
class FakeLegacyMigration(private val prefs: FakePrefsStore) : LegacyMigration {
    var succeedOnRetry = true
    var calls = 0
    var retryCalls = 0

    val report = LegacyReport(
        bookmarks = 4, groups = 2, events = 9, talliedAyahs = 60, days = 3,
        khatmCarried = true, skippedAyahIds = 2, skippedBookmarks = 0, skippedDays = 0,
    )

    override suspend fun migrate(khatmName: String, retryFailed: Boolean): LegacyMigrationOutcome {
        calls++
        val state = prefs.current.legacyMigrationState
        if (state != LegacyMigrationState.FAILED || !retryFailed) return LegacyMigrationOutcome.AlreadyHandled(state)
        retryCalls++
        if (!succeedOnRetry) return LegacyMigrationOutcome.Failed(IllegalStateException("still broken"))
        prefs.update { it.copy(legacyMigrationState = LegacyMigrationState.MIGRATED) }
        return LegacyMigrationOutcome.Migrated(report, ImportResult(9, 0, 1, 0, 4, 2, 1))
    }
}

fun DomainFixture.settingsViewModel(migration: LegacyMigration = FakeLegacyMigration(prefs), reports: MigrationReports = MigrationReports()) =
    SettingsViewModel(prefs = prefs, migration = migration, reports = reports)

fun DomainFixture.backupViewModel(io: CoroutineDispatcher) = BackupViewModel(
    backup = backup,
    codec = BackupCodec(),
    index = { index },
    clock = clock,
    io = io,
)

fun DomainFixture.migrationNoticeViewModel(migration: LegacyMigration = FakeLegacyMigration(prefs), reports: MigrationReports = MigrationReports()) =
    MigrationNoticeViewModel(prefs = prefs, migration = migration, reports = reports)

fun DomainFixture.onboardingViewModel() = OnboardingViewModel(prefs = prefs)
