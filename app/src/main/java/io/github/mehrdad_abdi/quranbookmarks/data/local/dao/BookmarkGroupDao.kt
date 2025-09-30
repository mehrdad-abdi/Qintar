package io.github.mehrdad_abdi.quranbookmarks.data.local.dao

import androidx.room.*
import io.github.mehrdad_abdi.quranbookmarks.data.local.entity.BookmarkGroupEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BookmarkGroupDao {

    @Query("SELECT * FROM bookmark_groups ORDER BY updatedAt DESC")
    fun getAllGroups(): Flow<List<BookmarkGroupEntity>>

    @Query("SELECT * FROM bookmark_groups WHERE id = :id")
    suspend fun getGroupById(id: Long): BookmarkGroupEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroup(group: BookmarkGroupEntity): Long

    @Update
    suspend fun updateGroup(group: BookmarkGroupEntity)

    @Delete
    suspend fun deleteGroup(group: BookmarkGroupEntity)

    @Query("DELETE FROM bookmark_groups WHERE id = :id")
    suspend fun deleteGroupById(id: Long)

    @Query("SELECT COUNT(*) FROM bookmark_groups")
    suspend fun getGroupCount(): Int
}