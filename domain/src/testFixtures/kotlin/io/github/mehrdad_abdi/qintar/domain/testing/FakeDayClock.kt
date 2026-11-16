package io.github.mehrdad_abdi.qintar.domain.testing

import io.github.mehrdad_abdi.qintar.domain.time.DayClock
import io.github.mehrdad_abdi.qintar.domain.time.readingDay
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A [DayClock] whose time the test sets and advances. Every change updates [todayFlow] synchronously,
 * so "advance past midnight" is one call with no waiting.
 *
 * It applies the same reading-day rule as the production clock ([readingDay]), including the
 * "Day starts at" offset.
 */
class FakeDayClock(
    start: LocalDateTime = LocalDateTime.of(2026, 11, 15, 9, 0),
    zone: ZoneId = ZoneId.of("Asia/Tehran"),
    dayStartMinutes: Int = 0,
) : DayClock {
    private var instant: Instant = start.atZone(zone).toInstant()
    private var currentZone: ZoneId = zone

    var dayStartMinutes: Int = dayStartMinutes
        set(value) {
            field = value
            publish()
        }

    private val today = MutableStateFlow(readingDay(instant, zone, dayStartMinutes))

    override val todayFlow: StateFlow<LocalDate> = today.asStateFlow()

    override fun now(): Instant = instant

    override fun zone(): ZoneId = currentZone

    override fun today(): LocalDate = readingDay(instant, currentZone, dayStartMinutes)

    /** Local wall-clock date and time. */
    fun localDateTime(): LocalDateTime = LocalDateTime.ofInstant(instant, currentZone)

    /** Jumps to a local date and time in the current zone. */
    fun set(dateTime: LocalDateTime) {
        instant = dateTime.atZone(currentZone).toInstant()
        publish()
    }

    fun set(date: LocalDate, time: LocalTime) = set(LocalDateTime.of(date, time))

    /** Keeps the calendar date and moves the time of day. */
    fun setTime(time: LocalTime) = set(localDateTime().toLocalDate(), time)

    fun setTime(hour: Int, minute: Int = 0) = setTime(LocalTime.of(hour, minute))

    fun advance(duration: Duration) {
        instant += duration
        publish()
    }

    fun advanceMinutes(minutes: Long) = advance(Duration.ofMinutes(minutes))

    fun advanceDays(days: Long) = set(localDateTime().plusDays(days))

    /** The same instant, seen from another zone (a flight, or a manual time-zone change). */
    fun setZone(zone: ZoneId) {
        currentZone = zone
        publish()
    }

    private fun publish() {
        today.value = today()
    }
}
