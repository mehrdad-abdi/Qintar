package io.github.mehrdad_abdi.quranbookmarks.data.local.dao

import androidx.room.*
import io.github.mehrdad_abdi.quranbookmarks.data.local.entity.KhatmProgressEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface KhatmProgressDao {

    @Query("SELECT * FROM khatm_progress WHERE id = 1 LIMIT 1")
    fun getProgress(): Flow<KhatmProgressEntity?>

    @Query("SELECT * FROM khatm_progress WHERE id = 1 LIMIT 1")
    suspend fun getProgressSnapshot(): KhatmProgressEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(progress: KhatmProgressEntity)

    @Query("UPDATE khatm_progress SET lastPageRead = :pageNumber, updatedAt = :timestamp WHERE id = 1")
    suspend fun updatePage(pageNumber: Int, timestamp: Long = System.currentTimeMillis())
}
