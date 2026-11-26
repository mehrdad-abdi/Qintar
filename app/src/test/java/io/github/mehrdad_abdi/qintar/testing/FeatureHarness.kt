package io.github.mehrdad_abdi.qintar.testing

import io.github.mehrdad_abdi.qintar.feature.bookmarks.ArrangeViewModel
import io.github.mehrdad_abdi.qintar.ui.navigation.ArrangeRoute
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
import io.github.mehrdad_abdi.qintar.data.legacy.LegacyFileStore
import io.github.mehrdad_abdi.qintar.data.legacy.LegacyMigration
import io.github.mehrdad_abdi.qintar.data.legacy.LegacyMigrationOutcome
import io.github.mehrdad_abdi.qintar.domain.backup.ImportResult
import io.github.mehrdad_abdi.qintar.domain.backup.LegacyReport
import io.github.mehrdad_abdi.qintar.domain.model.LegacyMigrationState
import io.github.mehrdad_abdi.qintar.domain.testing.FakePrefsStore
import io.github.mehrdad_abdi.qintar.feature.onboarding.OnboardingViewModel
import io.github.mehrdad_abdi.qintar.feature.player.PlayerBarViewModel
import io.github.mehrdad_abdi.qintar.domain.model.AyahRange
import io.github.mehrdad_abdi.qintar.domain.model.ReadContext
import io.github.mehrdad_abdi.qintar.domain.testing.QuranFixture
import io.github.mehrdad_abdi.qintar.playback.AudioDownloads
import io.github.mehrdad_abdi.qintar.playback.AudioStorage
import io.github.mehrdad_abdi.qintar.playback.DownloadState
import io.github.mehrdad_abdi.qintar.playback.NowPlaying
import io.github.mehrdad_abdi.qintar.playback.PlayRequest
import io.github.mehrdad_abdi.qintar.playback.PlaybackOrigin
import io.github.mehrdad_abdi.qintar.playback.PlayerController
import io.github.mehrdad_abdi.qintar.playback.PlayerState
import io.github.mehrdad_abdi.qintar.playback.RepeatSettings
import kotlinx.coroutines.flow.MutableStateFlow
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
    player: PlayerController = FakePlayerController(),
    downloads: AudioDownloads = FakeAudioDownloads(),
    log: io.github.mehrdad_abdi.qintar.domain.port.ReadLogStore = this.log,
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
    player = player,
    downloads = downloads,
    io = io,
)

/** A player that plays nothing: it records what it was asked and shows the state a test gives it. */
class FakePlayerController : PlayerController {
    override val state = MutableStateFlow(PlayerState())
    val requests = ArrayList<PlayRequest>()
    val calls = ArrayList<String>()
    val repeats = ArrayList<RepeatSettings>()

    override fun play(request: PlayRequest) {
        requests += request
    }

    override fun togglePlayPause() {
        calls += "toggle"
    }

    override fun next() {
        calls += "next"
    }

    override fun previous() {
        calls += "previous"
    }

    override fun setRepeat(repeat: RepeatSettings) {
        repeats += repeat
        state.value = state.value.copy(repeat = repeat)
    }

    override fun close() {
        calls += "close"
        state.value = PlayerState()
    }

    /** As the service would report it: [ayah] of [segment] of the reader behind [origin] is loaded. */
    fun nowAt(
        origin: PlaybackOrigin,
        segment: Int,
        ayah: AyahId,
        context: ReadContext = ReadContext.Browse,
        playing: Boolean = true,
        basmala: Boolean = false,
    ) {
        state.value = state.value.copy(
            origin = origin,
            item = NowPlaying(ayah, QuranFixture.index.ref(ayah).surah, segment, basmala, context),
            playing = playing,
        )
    }
}

class FakeAudioDownloads : AudioDownloads {
    override val state = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val requested = ArrayList<List<AyahRange>>()
    var cancelled = 0

    override fun download(ranges: List<AyahRange>) {
        requested += ranges
    }

    override fun cancel() {
        cancelled++
        state.value = DownloadState.Idle
    }

    override fun acknowledge() {
        state.value = DownloadState.Idle
    }
}

class FakeAudioStorage(var bytes: Long = 0) : AudioStorage {
    var cleared = 0

    override suspend fun sizeBytes(): Long = bytes

    override suspend fun clear() {
        cleared++
        bytes = 0
    }
}

fun DomainFixture.playerBarViewModel(player: PlayerController, io: CoroutineDispatcher) =
    PlayerBarViewModel(player = player, prefs = prefs, index = { index }, io = io)

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
    log = log,
    khatmProgress = { khatmProgress },
    clock = clock,
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

fun DomainFixture.bookmarksViewModel(
    io: CoroutineDispatcher,
    text: QuranTextSource = this.text,
) = BookmarksViewModel(
    index = { index },
    availability = availability,
    library = library,
    tracker = tracker,
    text = text,
    prefs = prefs,
    io = io,
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
    availability = availability,
    log = log,
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

fun DomainFixture.settingsViewModel(
    migration: LegacyMigration = FakeLegacyMigration(prefs),
    reports: MigrationReports = MigrationReports(),
    audio: AudioStorage = FakeAudioStorage(),
    legacyFiles: LegacyFileStore = FakeLegacyFiles(present = false),
) = SettingsViewModel(prefs = prefs, migration = migration, reports = reports, audio = audio, legacyFiles = legacyFiles)

class FakeLegacyFiles(var present: Boolean) : LegacyFileStore {
    var regenerableDeletions = 0
    var userDeletions = 0
    override suspend fun userDataPresent() = present
    override suspend fun deleteRegenerable() { regenerableDeletions++ }
    override suspend fun deleteUserData(): Boolean {
        userDeletions++
        present = false
        return true
    }
}

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

fun DomainFixture.arrangeViewModel(route: ArrangeRoute, io: CoroutineDispatcher) =
    ArrangeViewModel(
        route = route,
        index = { index },
        availability = availability,
        library = library,
        prefs = prefs,
        io = io,
    )
