package io.github.mehrdad_abdi.qintar.domain.service

import io.github.mehrdad_abdi.qintar.domain.model.Rank
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

enum class ReminderKind { DAILY, STREAK }

/** When a reminder should next go off, and the reading day it will be judged against. */
data class ReminderTrigger(val at: Instant, val readingDay: LocalDate)

/** What a reminder that has gone off should do. */
sealed interface ReminderDecision {
    /** Nothing to say: the goal (or the streak threshold) is already met, or the alarm came far too late. */
    data object Skip : ReminderDecision

    /** Daily reminder, nothing read yet. */
    data class NothingRead(val target: Rank) : ReminderDecision

    /** Daily reminder, some ayahs read: [remaining] more reach [target]. */
    data class InProgress(val total: Int, val remaining: Int, val target: Rank) : ReminderDecision

    /** Streak reminder: [total] ayahs so far, [remaining] short of the streak threshold. */
    data class StreakAtRisk(val total: Int, val remaining: Int) : ReminderDecision
}

/**
 * The decisions behind the reminders (§5.11, §7.9), free of Android so that they can be tested: when
 * the next alarm is due and whether a fired alarm should show anything.
 *
 * Alarms are inexact, so the system may deliver one late; [decide] drops one that is more than
 * [MAX_LATENESS] after its time, rather than nag about a moment long gone.
 */
object ReminderPlanner {
    val MAX_LATENESS: Duration = Duration.ofHours(6)

    /** A reading day counts for the streak from this many ayahs (R-RNK-3). */
    const val STREAK_THRESHOLD = StreakCalculator.QUALIFYING_TOTAL

    /**
     * The first moment strictly after [now] at which the wall clock reads [time] in [zone] (a time that
     * does not exist on a daylight-saving day moves forward, as `ZonedDateTime` does), with the reading
     * day that moment belongs to, which is the previous calendar day while it is before "day starts at".
     */
    fun next(now: Instant, zone: ZoneId, time: LocalTime, dayStartMinutes: Int): ReminderTrigger {
        val local = now.atZone(zone).toLocalDate()
        var date = local
        var candidate = ZonedDateTime.of(date, time, zone)
        while (!candidate.toInstant().isAfter(now)) {
            date = date.plusDays(1)
            candidate = ZonedDateTime.of(date, time, zone)
        }
        return ReminderTrigger(candidate.toInstant(), readingDayAt(candidate.toInstant(), zone, dayStartMinutes))
    }

    /** The reading day of a moment: the calendar day, shifted back while the wall clock is before "day starts at". */
    fun readingDayAt(instant: Instant, zone: ZoneId, dayStartMinutes: Int): LocalDate =
        instant.atZone(zone).minusMinutes(dayStartMinutes.toLong()).toLocalDate()

    /** The count a day must reach for the daily reminder to stay quiet: the goal, else the first rank. */
    fun target(goal: Rank?): Rank = goal ?: Rank.NOT_HEEDLESS

    /**
     * @param total the ayahs of the reading day the alarm was set for ([ReminderTrigger.readingDay]).
     * @param scheduledAt the time the alarm was meant for; null skips the lateness check.
     */
    fun decide(
        kind: ReminderKind,
        total: Int,
        goal: Rank?,
        now: Instant,
        scheduledAt: Instant? = null,
    ): ReminderDecision {
        if (scheduledAt != null && Duration.between(scheduledAt, now) > MAX_LATENESS) return ReminderDecision.Skip
        return when (kind) {
            ReminderKind.DAILY -> {
                val target = target(goal)
                when {
                    total >= target.threshold -> ReminderDecision.Skip
                    total == 0 -> ReminderDecision.NothingRead(target)
                    else -> ReminderDecision.InProgress(total, target.threshold - total, target)
                }
            }
            ReminderKind.STREAK ->
                if (total >= STREAK_THRESHOLD) ReminderDecision.Skip
                else ReminderDecision.StreakAtRisk(total, STREAK_THRESHOLD - total)
        }
    }
}
