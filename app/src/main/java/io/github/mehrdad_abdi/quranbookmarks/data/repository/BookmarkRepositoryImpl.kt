package io.github.mehrdad_abdi.quranbookmarks.data.repository

import io.github.mehrdad_abdi.quranbookmarks.data.local.dao.BookmarkDao
import io.github.mehrdad_abdi.quranbookmarks.data.local.dao.BookmarkGroupDao
import io.github.mehrdad_abdi.quranbookmarks.data.local.entity.toDomain
import io.github.mehrdad_abdi.quranbookmarks.data.local.entity.toEntity
import io.github.mehrdad_abdi.quranbookmarks.domain.model.Bookmark
import io.github.mehrdad_abdi.quranbookmarks.domain.model.BookmarkGroup
import io.github.mehrdad_abdi.quranbookmarks.domain.repository.BookmarkRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BookmarkRepositoryImpl @Inject constructor(
    private val bookmarkGroupDao: BookmarkGroupDao,
    private val bookmarkDao: BookmarkDao
) : BookmarkRepository {

    override fun getAllGroups(): Flow<List<BookmarkGroup>> {
        return bookmarkGroupDao.getAllGroups().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getGroupById(id: Long): BookmarkGroup? {
        return bookmarkGroupDao.getGroupById(id)?.toDomain()
    }

    override suspend fun insertGroup(group: BookmarkGroup): Long {
        val currentTime = System.currentTimeMillis()
        val entityToInsert = group.copy(
            createdAt = if (group.id == 0L) currentTime else group.createdAt,
            updatedAt = currentTime
        ).toEntity()
        return bookmarkGroupDao.insertGroup(entityToInsert)
    }

    override suspend fun updateGroup(group: BookmarkGroup) {
        val updatedGroup = group.copy(updatedAt = System.currentTimeMillis())
        bookmarkGroupDao.updateGroup(updatedGroup.toEntity())
    }

    override suspend fun deleteGroup(group: BookmarkGroup) {
        bookmarkGroupDao.deleteGroup(group.toEntity())
    }

    override suspend fun deleteGroupById(id: Long) {
        bookmarkGroupDao.deleteGroupById(id)
    }

    override suspend fun getGroupCount(): Int {
        return bookmarkGroupDao.getGroupCount()
    }

    override fun getBookmarksByGroupId(groupId: Long): Flow<List<Bookmark>> {
        return bookmarkDao.getBookmarksByGroupId(groupId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getBookmarkById(id: Long): Bookmark? {
        return bookmarkDao.getBookmarkById(id)?.toDomain()
    }

    override suspend fun insertBookmark(bookmark: Bookmark): Long {
        val currentTime = System.currentTimeMillis()
        val entityToInsert = bookmark.copy(
            createdAt = if (bookmark.id == 0L) currentTime else bookmark.createdAt,
            updatedAt = currentTime
        ).toEntity()
        return bookmarkDao.insertBookmark(entityToInsert)
    }

    override suspend fun updateBookmark(bookmark: Bookmark) {
        val updatedBookmark = bookmark.copy(updatedAt = System.currentTimeMillis())
        bookmarkDao.updateBookmark(updatedBookmark.toEntity())
    }

    override suspend fun deleteBookmark(bookmark: Bookmark) {
        bookmarkDao.deleteBookmark(bookmark.toEntity())
    }

    override suspend fun deleteBookmarkById(id: Long) {
        bookmarkDao.deleteBookmarkById(id)
    }

    override suspend fun getBookmarkCountByGroupId(groupId: Long): Int {
        return bookmarkDao.getBookmarkCountByGroupId(groupId)
    }

    override fun getAllBookmarks(): Flow<List<Bookmark>> {
        return bookmarkDao.getAllBookmarks().map { entities ->
            entities.map { it.toDomain() }
        }
    }
}