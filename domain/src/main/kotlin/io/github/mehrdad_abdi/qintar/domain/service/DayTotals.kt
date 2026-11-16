package io.github.mehrdad_abdi.qintar.domain.service

import io.github.mehrdad_abdi.qintar.domain.model.ContextKind
import io.github.mehrdad_abdi.qintar.domain.model.ReadMethod
import io.github.mehrdad_abdi.qintar.domain.model.SourceCount
import io.github.mehrdad_abdi.qintar.domain.port.ReadLogStore
import io.github.mehrdad_abdi.qintar.domain.time.DayClock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

/** Where a period's readings came from (R-INS-4). Tallies have no context or method of their own. */
data class SourceSplit(
    val byContext: Map<ContextKind, Int>,
    val byMethod: Map<ReadMethod, Int>,
    val tallied: Int,
) {
    val total: Int get() = byContext.values.sum() + tallied
}

/**
 * The single place the day-total rule lives (§4.2): a day's total is its number of read events plus the
 * sum of its tallies. Nothing else in the app may count events toward a total.
 */
class DayTotals @Inject constructor(
    private val log: ReadLogStore,
    private val clock: DayClock,
) {
    /** Totals per day within the inclusive bounds (null = unbounded). Days with nothing read are absent. */
    fun observeTotals(from: LocalDate?, to: LocalDate?): Flow<Map<LocalDate, Int>> =
        combine(log.observeEventCountsByDay(from, to), log.observeTallySumsByDay(from, to), ::total)
            .distinctUntilChanged()

    /** Every day that has ever had a reading: the input of the streak. */
    fun observeAllTotals(): Flow<Map<LocalDate, Int>> = observeTotals(null, null)

    fun observeDay(day: LocalDate): Flow<Int> = observeTotals(day, day).map { it[day] ?: 0 }.distinctUntilChanged()

    /** Follows the reading day: after the boundary it reports the new day's total without re-subscription. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeToday(): Flow<Int> = clock.todayFlow.flatMapLatest(::observeDay)

    suspend fun totals(from: LocalDate?, to: LocalDate?): Map<LocalDate, Int> = observeTotals(from, to).first()

    suspend fun total(day: LocalDate): Int = observeDay(day).first()

    suspend fun today(): Int = total(clock.today())

    fun observeSourceSplit(from: LocalDate?, to: LocalDate?): Flow<SourceSplit> =
        combine(log.observeSourceCounts(from, to), log.observeTallySumsByDay(from, to), ::split)

    companion object {
        /** The rule itself, exposed so that it can be stated in one line and tested without a store. */
        fun total(eventCounts: Map<LocalDate, Int>, tallySums: Map<LocalDate, Int>): Map<LocalDate, Int> {
            val totals = HashMap(eventCounts)
            for ((day, sum) in tallySums) totals.merge(day, sum, Int::plus)
            return totals.filterValues { it > 0 }.toSortedMap()
        }

        fun split(sources: List<SourceCount>, tallySums: Map<LocalDate, Int>): SourceSplit = SourceSplit(
            byContext = sources.groupBy { it.context }.mapValues { (_, rows) -> rows.sumOf { it.count } },
            byMethod = sources.groupBy { it.method }.mapValues { (_, rows) -> rows.sumOf { it.count } },
            tallied = tallySums.values.sum(),
        )
    }
}
