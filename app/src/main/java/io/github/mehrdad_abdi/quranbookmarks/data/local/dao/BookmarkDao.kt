package io.github.mehrdad_abdi.quranbookmarks.data.local.dao

import androidx.room.*
import io.github.mehrdad_abdi.quranbookmarks.data.local.entity.BookmarkEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BookmarkDao {

    @Query("SELECT * FROM bookmarks ORDER BY createdAt ASC")
    fun getAllBookmarks(): Flow<List<BookmarkEntity>>

    @Query("SELECT * FROM bookmarks WHERE id = :id")
    suspend fun getBookmarkById(id: Long): BookmarkEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(bookmark: BookmarkEntity): Long

    @Update
    suspend fun updateBookmark(bookmark: BookmarkEntity)

    @Delete
    suspend fun deleteBookmark(bookmark: BookmarkEntity)

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun deleteBookmarkById(id: Long)

    @Query("SELECT * FROM bookmarks WHERE tags LIKE '%' || :tag || '%' ORDER BY createdAt ASC")
    fun getBookmarksByTag(tag: String): Flow<List<BookmarkEntity>>

    @Query("SELECT COUNT(*) FROM bookmarks")
    suspend fun getBookmarkCount(): Int
}