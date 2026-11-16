package io.github.mehrdad_abdi.qintar.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.mehrdad_abdi.qintar.domain.port.KhatmStore
import io.github.mehrdad_abdi.qintar.domain.port.PrefsStore
import io.github.mehrdad_abdi.qintar.domain.port.ReadLogStore
import io.github.mehrdad_abdi.qintar.domain.port.TransactionRunner
import io.github.mehrdad_abdi.qintar.domain.service.DefaultReadingTracker
import io.github.mehrdad_abdi.qintar.domain.service.ReadingLogImporter
import io.github.mehrdad_abdi.qintar.domain.service.ReadingTracker
import io.github.mehrdad_abdi.qintar.domain.time.DayClock
import io.github.mehrdad_abdi.qintar.domain.time.SystemDayClock
import java.time.Clock
import java.time.ZoneId
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

/** The scope that lives as long as the process: the day-boundary watcher, start-up work. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

/** Where blocking work goes (Quran text, the index's first load). Tests pass their own dispatcher. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

/**
 * What `:app` adds to the graph on top of `:data`: the clock, the application scope and the one
 * writer of the reading log. Domain services (`DayTotals`, `KhatmProgress`, `Availability`, …) have
 * `@Inject` constructors and need no entry here.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    @ApplicationScope
    fun applicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    @IoDispatcher
    fun ioDispatcher(): CoroutineDispatcher = Dispatchers.IO

    /**
     * One clock for the whole app (§4.3). The "Day starts at" offset is read once, blocking, so that
     * the very first `today()` is already right; later changes arrive through the flow.
     */
    @Provides
    @Singleton
    fun systemDayClock(prefs: PrefsStore, @ApplicationScope scope: CoroutineScope): SystemDayClock =
        SystemDayClock(
            clock = Clock.systemUTC(),
            zoneProvider = { ZoneId.systemDefault() },
            dayStartMinutes = prefs.prefs.map { it.dayStartMinutes },
            scope = scope,
            initialDayStartMinutes = runBlocking { prefs.prefs.first().dayStartMinutes },
        )

    @Provides
    fun dayClock(clock: SystemDayClock): DayClock = clock

    /** A single instance behind both interfaces, so that the log has exactly one writer (§4.2). */
    @Provides
    @Singleton
    fun defaultReadingTracker(
        log: ReadLogStore,
        khatms: KhatmStore,
        clock: DayClock,
        tx: TransactionRunner,
    ): DefaultReadingTracker = DefaultReadingTracker(log, khatms, clock, tx)

    @Provides
    fun readingTracker(tracker: DefaultReadingTracker): ReadingTracker = tracker

    @Provides
    fun readingLogImporter(tracker: DefaultReadingTracker): ReadingLogImporter = tracker
}
