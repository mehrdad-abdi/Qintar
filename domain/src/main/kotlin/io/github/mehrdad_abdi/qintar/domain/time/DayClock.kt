package io.github.mehrdad_abdi.qintar.domain.time

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** The only source of time in production code (§4.3). */
interface DayClock {
    fun now(): Instant

    /** The reading day at this moment. Always computed fresh; never cache the result. */
    fun today(): LocalDate

    /** Emits when the reading day changes. Readers key their queries on it. */
    val todayFlow: StateFlow<LocalDate>

    /** The device zone at this moment. */
    fun zone(): ZoneId

    /** Wall-clock time of day in the device zone, for schedule windows (§4.7). */
    fun localTime(): LocalTime = LocalDateTime.ofInstant(now(), zone()).toLocalTime()

    /**
     * Re-evaluates the reading day now and publishes it on [todayFlow]. Called when the app comes
     * to the foreground and when the user taps the date on Today: a phone that slept may not have
     * woken the boundary wait (notes item 41).
     */
    fun refresh()
}

/** The reading day of [instant]: the local date after stepping back by the "Day starts at" offset. */
fun readingDay(instant: Instant, zone: ZoneId, dayStartMinutes: Int): LocalDate =
    LocalDateTime.ofInstant(instant, zone).minusMinutes(dayStartMinutes.toLong()).toLocalDate()

/** The moment [day] ends and the next reading day begins. */
fun endOfReadingDay(day: LocalDate, zone: ZoneId, dayStartMinutes: Int): Instant =
    day.plusDays(1).atStartOfDay().plusMinutes(dayStartMinutes.toLong()).atZone(zone).toInstant()

/**
 * Production [DayClock].
 *
 * @param clock the wall clock; the single place the app reads the system time.
 * @param zoneProvider asked on every call, so a time-zone change takes effect without a restart.
 * @param dayStartMinutes the "Day starts at" setting as it changes.
 * @param scope where the boundary watcher runs; it lives as long as the app.
 * @param initialDayStartMinutes the offset used until [dayStartMinutes] first emits.
 * @param waitUntil suspends until the wall clock reaches an instant. Injectable so tests decide when a
 *   boundary "arrives"; the default sleeps for the remaining duration.
 */
class SystemDayClock(
    private val clock: Clock,
    private val zoneProvider: () -> ZoneId,
    dayStartMinutes: Flow<Int>,
    scope: CoroutineScope,
    initialDayStartMinutes: Int = 0,
    private val waitUntil: suspend (Instant) -> Unit = { target ->
        delay(Duration.between(clock.instant(), target).toMillis().coerceAtLeast(0))
    },
) : DayClock {

    @Volatile
    private var offsetMinutes = initialDayStartMinutes

    private val refreshes = MutableStateFlow(0)
    private val today = MutableStateFlow(compute())

    override val todayFlow: StateFlow<LocalDate> = today.asStateFlow()

    init {
        scope.launch {
            // collectLatest cancels a pending wait whenever the offset changes or refresh() is called,
            // so the boundary is always recomputed from the current settings and clock.
            combine(dayStartMinutes, refreshes) { offset, _ -> offset }.collectLatest { offset ->
                offsetMinutes = offset
                while (true) {
                    val day = today()
                    waitUntil(endOfReadingDay(day, zoneProvider(), offset))
                }
            }
        }
    }

    override fun now(): Instant = clock.instant()

    override fun zone(): ZoneId = zoneProvider()

    override fun today(): LocalDate {
        val day = compute()
        // Publishing here means a writer that notices the new day first also moves every observer,
        // instead of observers lagging until the watcher wakes up.
        today.value = day
        return day
    }

    /**
     * Re-evaluates the reading day and restarts the boundary wait. The app calls this on
     * ACTION_TIME_CHANGED, ACTION_TIMEZONE_CHANGED and ACTION_DATE_CHANGED.
     */
    override fun refresh() {
        today()
        refreshes.value += 1
    }

    private fun compute(): LocalDate = readingDay(clock.instant(), zoneProvider(), offsetMinutes)
}
