package io.github.mehrdad_abdi.quranbookmarks.data.local.dao

import androidx.room.*
import io.github.mehrdad_abdi.quranbookmarks.data.local.entity.CachedContentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CachedContentDao {

    @Query("SELECT * FROM cached_content WHERE surah = :surah AND ayah = :ayah")
    suspend fun getCachedContent(surah: Int, ayah: Int): CachedContentEntity?

    @Query("SELECT * FROM cached_content WHERE id = :id")
    suspend fun getCachedContentById(id: String): CachedContentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCachedContent(content: CachedContentEntity)

    @Update
    suspend fun updateCachedContent(content: CachedContentEntity)

    @Delete
    suspend fun deleteCachedContent(content: CachedContentEntity)

    @Query("DELETE FROM cached_content WHERE surah = :surah AND ayah = :ayah")
    suspend fun deleteCachedContent(surah: Int, ayah: Int)

    @Query("SELECT * FROM cached_content WHERE imagePath IS NOT NULL AND audioPath IS NOT NULL")
    fun getFullyCachedContent(): Flow<List<CachedContentEntity>>

    @Query("SELECT COUNT(*) FROM cached_content WHERE imagePath IS NOT NULL AND audioPath IS NOT NULL")
    suspend fun getFullyCachedContentCount(): Int

    @Query("DELETE FROM cached_content WHERE downloadedAt < :threshold")
    suspend fun deleteOldCachedContent(threshold: Long)
}