package io.github.mehrdad_abdi.quranbookmarks.data.repository

import io.github.mehrdad_abdi.quranbookmarks.data.local.dao.KhatmProgressDao
import io.github.mehrdad_abdi.quranbookmarks.data.local.entity.KhatmProgressEntity
import io.github.mehrdad_abdi.quranbookmarks.data.local.entity.toDomain
import io.github.mehrdad_abdi.quranbookmarks.domain.model.KhatmProgress
import io.github.mehrdad_abdi.quranbookmarks.domain.repository.KhatmProgressRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Implementation of KhatmProgressRepository
 */
class KhatmProgressRepositoryImpl @Inject constructor(
    private val dao: KhatmProgressDao
) : KhatmProgressRepository {

    override fun getProgress(): Flow<KhatmProgress> {
        return dao.getProgress().map { entity ->
            entity?.toDomain() ?: KhatmProgress() // Return default if not found
        }
    }

    override suspend fun getProgressSnapshot(): KhatmProgress {
        val entity = dao.getProgressSnapshot()
        return entity?.toDomain() ?: KhatmProgress()
    }

    override suspend fun updateLastPage(pageNumber: Int) {
        // Use insertOrUpdate to handle both insert and update cases
        dao.insertOrUpdate(
            KhatmProgressEntity(
                id = 1,
                lastPageRead = pageNumber,
                updatedAt = System.currentTimeMillis()
            )
        )
    }
}
