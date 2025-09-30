package io.github.mehrdad_abdi.quranbookmarks.domain.repository

import io.github.mehrdad_abdi.quranbookmarks.domain.model.Bookmark
import io.github.mehrdad_abdi.quranbookmarks.domain.model.BookmarkGroup
import kotlinx.coroutines.flow.Flow

interface BookmarkRepository {

    // BookmarkGroup operations
    fun getAllGroups(): Flow<List<BookmarkGroup>>
    suspend fun getGroupById(id: Long): BookmarkGroup?
    suspend fun insertGroup(group: BookmarkGroup): Long
    suspend fun updateGroup(group: BookmarkGroup)
    suspend fun deleteGroup(group: BookmarkGroup)
    suspend fun deleteGroupById(id: Long)
    suspend fun getGroupCount(): Int

    // Bookmark operations
    fun getBookmarksByGroupId(groupId: Long): Flow<List<Bookmark>>
    suspend fun getBookmarkById(id: Long): Bookmark?
    suspend fun insertBookmark(bookmark: Bookmark): Long
    suspend fun updateBookmark(bookmark: Bookmark)
    suspend fun deleteBookmark(bookmark: Bookmark)
    suspend fun deleteBookmarkById(id: Long)
    suspend fun getBookmarkCountByGroupId(groupId: Long): Int
    fun getAllBookmarks(): Flow<List<Bookmark>>
}