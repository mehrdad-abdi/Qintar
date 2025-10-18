package io.github.mehrdad_abdi.quranbookmarks.domain.repository

import io.github.mehrdad_abdi.quranbookmarks.domain.model.KhatmProgress
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for managing Khatm (complete Quran reading) progress
 */
interface KhatmProgressRepository {

    /**
     * Get the current Khatm reading progress as a Flow
     * @return Flow emitting current progress or default progress if none exists
     */
    fun getProgress(): Flow<KhatmProgress>

    /**
     * Get a snapshot of current progress
     * @return Current progress or default progress if none exists
     */
    suspend fun getProgressSnapshot(): KhatmProgress

    /**
     * Update the last page read
     * @param pageNumber The page number (1-604) to save as last read
     */
    suspend fun updateLastPage(pageNumber: Int)
}
