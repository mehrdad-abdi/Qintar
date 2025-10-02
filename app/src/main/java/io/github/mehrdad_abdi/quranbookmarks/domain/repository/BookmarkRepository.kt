package io.github.mehrdad_abdi.quranbookmarks.domain.repository

import io.github.mehrdad_abdi.quranbookmarks.domain.model.Bookmark
import kotlinx.coroutines.flow.Flow

interface BookmarkRepository {

    // Bookmark operations
    fun getAllBookmarks(): Flow<List<Bookmark>>
    fun getBookmarksByTag(tag: String): Flow<List<Bookmark>>
    suspend fun getBookmarkById(id: Long): Bookmark?
    suspend fun insertBookmark(bookmark: Bookmark): Long
    suspend fun updateBookmark(bookmark: Bookmark)
    suspend fun deleteBookmark(bookmark: Bookmark)
    suspend fun deleteBookmarkById(id: Long)
    suspend fun getBookmarkCount(): Int
    suspend fun getAllTags(): List<String>
}