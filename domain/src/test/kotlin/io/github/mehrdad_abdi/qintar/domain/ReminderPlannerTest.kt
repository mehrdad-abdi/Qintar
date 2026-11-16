package io.github.mehrdad_abdi.qintar.domain

import io.github.mehrdad_abdi.qintar.domain.model.Rank
import io.github.mehrdad_abdi.qintar.domain.service.ReminderDecision
import io.github.mehrdad_abdi.qintar.domain.service.ReminderKind
import io.github.mehrdad_abdi.qintar.domain.service.ReminderPlanner
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class ReminderPlannerTest {
    private val tehran = ZoneId.of("Asia/Tehran")
    private val utc = ZoneId.of("UTC")

    private fun at(zone: ZoneId, y: Int, m: Int, d: Int, h: Int, min: Int = 0): Instant =
        LocalDateTime.of(y, m, d, h, min).atZone(zone).toInstant()

    // ---- the next trigger -----------------------------------------------------------------------------

    @Test
    fun `a time later today is today, one already past is tomorrow`() {
        val now = at(utc, 2026, 11, 15, 9)
        assertEquals(at(utc, 2026, 11, 15, 20), ReminderPlanner.next(now, utc, LocalTime.of(20, 0), 0).at)
        assertEquals(at(utc, 2026, 11, 16, 8), ReminderPlanner.next(now, utc, LocalTime.of(8, 0), 0).at)
    }

    @Test
    fun `exactly now is not next, so an alarm cannot re-fire at once`() {
        val now = at(utc, 2026, 11, 15, 20)
        assertEquals(at(utc, 2026, 11, 16, 20), ReminderPlanner.next(now, utc, LocalTime.of(20, 0), 0).at)
    }

    @Test
    fun `across midnight the next trigger is on the next calendar day`() {
        val now = at(utc, 2026, 11, 15, 23, 59)
        val next = ReminderPlanner.next(now, utc, LocalTime.of(0, 30), 0)
        assertEquals(at(utc, 2026, 11, 16, 0, 30), next.at)
        assertEquals(LocalDate.of(2026, 11, 16), next.readingDay)
    }

    @Test
    fun `before the day start a trigger belongs to the previous reading day`() {
        val dayStart = 3 * 60
        val now = at(utc, 2026, 11, 15, 22)
        val early = ReminderPlanner.next(now, utc, LocalTime.of(1, 30), dayStart)
        assertEquals(at(utc, 2026, 11, 16, 1, 30), early.at)
        assertEquals(LocalDate.of(2026, 11, 15), early.readingDay)

        val late = ReminderPlanner.next(now, utc, LocalTime.of(3, 0), dayStart)
        assertEquals(LocalDate.of(2026, 11, 16), late.readingDay)
    }

    @Test
    fun `the day start does not move when the alarm rings, only which day it judges`() {
        val now = at(utc, 2026, 11, 15, 9)
        for (dayStart in listOf(0, 60, 6 * 60, 12 * 60)) {
            assertEquals(at(utc, 2026, 11, 15, 20), ReminderPlanner.next(now, utc, LocalTime.of(20, 0), dayStart).at)
        }
        assertEquals(LocalDate.of(2026, 11, 15), ReminderPlanner.next(now, utc, LocalTime.of(20, 0), 12 * 60).readingDay)
        assertEquals(LocalDate.of(2026, 11, 14), ReminderPlanner.next(now, utc, LocalTime.of(11, 0), 12 * 60).readingDay)
    }

    @Test
    fun `the zone decides what the wall clock reads`() {
        val now = at(utc, 2026, 11, 15, 12) // 15:30 in Tehran
        assertEquals(at(tehran, 2026, 11, 15, 20), ReminderPlanner.next(now, tehran, LocalTime.of(20, 0), 0).at)
        assertEquals(at(tehran, 2026, 11, 16, 9), ReminderPlanner.next(now, tehran, LocalTime.of(9, 0), 0).at)
    }

    @Test
    fun `a time that does not exist on a clock-change day moves forward instead of failing`() {
        val berlin = ZoneId.of("Europe/Berlin") // 2026-03-29 02:00 -> 03:00
        val now = at(berlin, 2026, 3, 28, 12)
        val next = ReminderPlanner.next(now, berlin, LocalTime.of(2, 30), 0)
        assertEquals(LocalDate.of(2026, 3, 29), next.at.atZone(berlin).toLocalDate())
        assertEquals(LocalTime.of(3, 30), next.at.atZone(berlin).toLocalTime())
    }

    // ---- the decision ---------------------------------------------------------------------------------

    private val now = at(utc, 2026, 11, 15, 20)

    @Test
    fun `the daily reminder speaks when nothing was read and when some was`() {
        assertEquals(ReminderDecision.NothingRead(Rank.NOT_HEEDLESS), ReminderPlanner.decide(ReminderKind.DAILY, 0, null, now))
        assertEquals(ReminderDecision.InProgress(7, 3, Rank.NOT_HEEDLESS), ReminderPlanner.decide(ReminderKind.DAILY, 7, null, now))
    }

    @Test
    fun `the daily reminder aims at the goal and is quiet once it is met`() {
        assertEquals(ReminderDecision.InProgress(47, 3, Rank.DHAKIR), ReminderPlanner.decide(ReminderKind.DAILY, 47, Rank.DHAKIR, now))
        assertEquals(ReminderDecision.Skip, ReminderPlanner.decide(ReminderKind.DAILY, 50, Rank.DHAKIR, now))
        assertEquals(ReminderDecision.Skip, ReminderPlanner.decide(ReminderKind.DAILY, 10, null, now))
    }

    @Test
    fun `the streak reminder fires below ten and is skipped from ten`() {
        assertEquals(ReminderDecision.StreakAtRisk(0, 10), ReminderPlanner.decide(ReminderKind.STREAK, 0, null, now))
        assertEquals(ReminderDecision.StreakAtRisk(9, 1), ReminderPlanner.decide(ReminderKind.STREAK, 9, Rank.QANIT, now))
        assertEquals(ReminderDecision.Skip, ReminderPlanner.decide(ReminderKind.STREAK, 10, null, now))
        assertEquals(ReminderDecision.Skip, ReminderPlanner.decide(ReminderKind.STREAK, 400, null, now))
    }

    @Test
    fun `an alarm delivered many hours late is dropped`() {
        val scheduled = at(utc, 2026, 11, 15, 8)
        assertEquals(ReminderDecision.Skip, ReminderPlanner.decide(ReminderKind.DAILY, 0, null, now, scheduled))
        val soon = scheduled.plusSeconds(20 * 60)
        assertEquals(ReminderDecision.NothingRead(Rank.NOT_HEEDLESS), ReminderPlanner.decide(ReminderKind.DAILY, 0, null, soon, scheduled))
    }
}
