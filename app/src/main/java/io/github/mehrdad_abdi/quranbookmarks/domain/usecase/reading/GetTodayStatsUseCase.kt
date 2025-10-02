package io.github.mehrdad_abdi.quranbookmarks.domain.usecase.reading

import io.github.mehrdad_abdi.quranbookmarks.domain.model.ReadingActivity
import io.github.mehrdad_abdi.quranbookmarks.domain.repository.ReadingActivityRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject

/**
 * Use case to get today's reading statistics.
 * Returns a Flow for reactive updates when user tracks/untracks ayahs.
 */
class GetTodayStatsUseCase @Inject constructor(
    private val repository: ReadingActivityRepository
) {
    /**
     * Get today's reading activity as Flow.
     * Returns empty activity if no ayahs tracked today.
     */
    operator fun invoke(): Flow<ReadingActivity> {
        val today = LocalDate.now()
        return repository.getActivityByDateFlow(today)
            .map { it ?: ReadingActivity.empty(today) }
    }

    /**
     * Get reading activity for a specific date as Flow.
     */
    fun forDate(date: LocalDate): Flow<ReadingActivity> {
        return repository.getActivityByDateFlow(date)
            .map { it ?: ReadingActivity.empty(date) }
    }

    /**
     * Get today's reading activity (non-reactive, one-shot).
     */
    suspend fun getTodaySnapshot(): ReadingActivity {
        val today = LocalDate.now()
        return repository.getActivityByDate(today)
            ?: ReadingActivity.empty(today)
    }
}
