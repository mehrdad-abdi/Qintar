package io.github.mehrdad_abdi.qintar.domain

import app.cash.turbine.test
import io.github.mehrdad_abdi.qintar.domain.testing.FakeDayClock
import io.github.mehrdad_abdi.qintar.domain.time.SystemDayClock
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** §4.3: the reading day, the "Day starts at" offset, and how observers learn that the day changed. */
@OptIn(ExperimentalCoroutinesApi::class)
class DayClockTest {
    private val tehran = ZoneId.of("Asia/Tehran")

    /** A wall clock the test moves by hand. */
    private class MutableClock(var instant: Instant, private val zone: ZoneId) : Clock() {
        override fun getZone(): ZoneId = zone
        override fun withZone(zone: ZoneId): Clock = MutableClock(instant, zone)
        override fun instant(): Instant = instant
    }

    /**
     * The production clock with its two external inputs under test control: the wall clock, and
     * "wait until", which here parks until the test says the awaited moment has come.
     */
    private inner class Harness(start: LocalDateTime, dayStartMinutes: Int = 0, scope: TestScope) {
        val wall = MutableClock(start.atZone(tehran).toInstant(), tehran)
        var zone: ZoneId = tehran
        val dayStart = MutableStateFlow(dayStartMinutes)
        val waits = ArrayList<Pair<Instant, CompletableDeferred<Unit>>>()

        val clock = SystemDayClock(
            clock = wall,
            zoneProvider = { zone },
            dayStartMinutes = dayStart,
            scope = scope.backgroundScope,
            initialDayStartMinutes = dayStartMinutes,
            waitUntil = { target ->
                val gate = CompletableDeferred<Unit>()
                waits += target to gate
                gate.await()
            },
        )

        /** The instant the clock is currently waiting for. */
        val awaited: Instant get() = waits.last().first

        fun setWall(dateTime: LocalDateTime) {
            wall.instant = dateTime.atZone(zone).toInstant()
        }

        /** Moves the wall clock to the awaited boundary and lets the wait return. */
        fun reachBoundary() {
            val (target, gate) = waits.last()
            wall.instant = target
            gate.complete(Unit)
        }
    }

    private fun harness(start: LocalDateTime, dayStartMinutes: Int = 0, body: suspend Harness.() -> Unit) =
        runTest(UnconfinedTestDispatcher()) {
            val harness = Harness(start, dayStartMinutes, this)
            // Let the clock's watcher reach its first wait before the test looks at it.
            testScheduler.runCurrent()
            harness.body()
        }

    @Test
    fun `today is the local calendar date when the day starts at midnight`() = harness(LocalDateTime.of(2026, 11, 15, 23, 59)) {
        assertEquals(LocalDate.of(2026, 11, 15), clock.today())
        setWall(LocalDateTime.of(2026, 11, 16, 0, 0))
        assertEquals(LocalDate.of(2026, 11, 16), clock.today())
    }

    @Test
    fun `today is computed fresh on every call and never cached`() = harness(LocalDateTime.of(2026, 11, 15, 12, 0)) {
        assertEquals(LocalDate.of(2026, 11, 15), clock.today())
        // No boundary wait has returned; the wall clock alone moved.
        setWall(LocalDateTime.of(2026, 11, 18, 12, 0))
        assertEquals(LocalDate.of(2026, 11, 18), clock.today())
        assertEquals("observers are moved along by the writer that noticed", LocalDate.of(2026, 11, 18), clock.todayFlow.value)
    }

    @Test
    fun `the day flow emits at the boundary without anyone asking`() = harness(LocalDateTime.of(2026, 11, 15, 23, 0)) {
        clock.todayFlow.test {
            assertEquals(LocalDate.of(2026, 11, 15), awaitItem())
            assertEquals("it waits for midnight", LocalDateTime.of(2026, 11, 16, 0, 0).atZone(tehran).toInstant(), awaited)

            reachBoundary()

            assertEquals(LocalDate.of(2026, 11, 16), awaitItem())
            assertEquals("and then for the next midnight", LocalDateTime.of(2026, 11, 17, 0, 0).atZone(tehran).toInstant(), awaited)
        }
    }

    @Test
    fun `with the day starting at 04_00 the small hours belong to the previous day`() =
        harness(LocalDateTime.of(2026, 11, 16, 3, 59), dayStartMinutes = 240) {
            assertEquals(LocalDate.of(2026, 11, 15), clock.today())
            assertEquals("the boundary is 04:00, not midnight", LocalDateTime.of(2026, 11, 16, 4, 0).atZone(tehran).toInstant(), awaited)

            setWall(LocalDateTime.of(2026, 11, 16, 4, 0))
            assertEquals(LocalDate.of(2026, 11, 16), clock.today())
        }

    @Test
    fun `the allowed range of day start is midnight to noon`() = harness(LocalDateTime.of(2026, 11, 16, 11, 59), dayStartMinutes = 720) {
        assertEquals(LocalDate.of(2026, 11, 15), clock.today())
        setWall(LocalDateTime.of(2026, 11, 16, 12, 0))
        assertEquals(LocalDate.of(2026, 11, 16), clock.today())
    }

    @Test
    fun `changing the day-start setting re-evaluates today at once and re-aims the boundary`() =
        harness(LocalDateTime.of(2026, 11, 16, 1, 0)) {
            clock.todayFlow.test {
                assertEquals(LocalDate.of(2026, 11, 16), awaitItem())

                dayStart.value = 180

                assertEquals("01:00 is now before the day starts", LocalDate.of(2026, 11, 15), awaitItem())
                assertEquals(LocalDateTime.of(2026, 11, 16, 3, 0).atZone(tehran).toInstant(), awaited)
            }
        }

    @Test
    fun `a time-zone change is picked up on refresh`() = harness(LocalDateTime.of(2026, 11, 16, 1, 0)) {
        clock.todayFlow.test {
            assertEquals(LocalDate.of(2026, 11, 16), awaitItem())

            // 01:00 in Tehran is still the afternoon of the 15th in New York.
            zone = ZoneId.of("America/New_York")
            clock.refresh()

            assertEquals(LocalDate.of(2026, 11, 15), awaitItem())
            assertEquals(ZoneId.of("America/New_York"), clock.zone())
            assertEquals(
                "the next boundary is New York's midnight",
                LocalDateTime.of(2026, 11, 16, 0, 0).atZone(ZoneId.of("America/New_York")).toInstant(),
                awaited,
            )
        }
    }

    @Test
    fun `the user setting the device clock back is picked up on refresh`() = harness(LocalDateTime.of(2026, 11, 16, 10, 0)) {
        clock.todayFlow.test {
            assertEquals(LocalDate.of(2026, 11, 16), awaitItem())
            setWall(LocalDateTime.of(2026, 11, 14, 10, 0))
            clock.refresh()
            assertEquals(LocalDate.of(2026, 11, 14), awaitItem())
        }
    }

    @Test
    fun `local time is the wall-clock time of day in the device zone`() = harness(LocalDateTime.of(2026, 11, 15, 8, 30)) {
        assertEquals(LocalTime.of(8, 30), clock.localTime())
        assertEquals(wall.instant, clock.now())
    }

    // ---- the test clock other modules use ----

    @Test
    fun `the fake clock applies the same reading-day rule and emits when tests move it`() = runTest {
        val clock = FakeDayClock(LocalDateTime.of(2026, 11, 15, 23, 59), dayStartMinutes = 0)
        clock.todayFlow.test {
            assertEquals(LocalDate.of(2026, 11, 15), awaitItem())

            clock.advance(Duration.ofMinutes(2))
            assertEquals(LocalDate.of(2026, 11, 16), awaitItem())

            clock.dayStartMinutes = 180
            assertEquals("00:01 now belongs to the 15th", LocalDate.of(2026, 11, 15), awaitItem())

            clock.setTime(3, 0)
            assertEquals(LocalDate.of(2026, 11, 16), awaitItem())

            clock.advanceDays(1)
            assertEquals(LocalDate.of(2026, 11, 17), awaitItem())
            assertEquals(LocalTime.of(3, 0), clock.localTime())
        }
    }
}
