package io.github.mehrdad_abdi.quranbookmarks.data.repository

import io.github.mehrdad_abdi.quranbookmarks.data.local.dao.BookmarkDao
import io.github.mehrdad_abdi.quranbookmarks.data.local.entity.toDomain
import io.github.mehrdad_abdi.quranbookmarks.data.local.entity.toEntity
import io.github.mehrdad_abdi.quranbookmarks.domain.model.Bookmark
import io.github.mehrdad_abdi.quranbookmarks.domain.repository.BookmarkRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BookmarkRepositoryImpl @Inject constructor(
    private val bookmarkDao: BookmarkDao
) : BookmarkRepository {

    override fun getAllBookmarks(): Flow<List<Bookmark>> {
        return bookmarkDao.getAllBookmarks().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getBookmarksByTag(tag: String): Flow<List<Bookmark>> {
        return bookmarkDao.getBookmarksByTag(tag).map { entities ->
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

    override suspend fun getBookmarkCount(): Int {
        return bookmarkDao.getBookmarkCount()
    }

    override suspend fun getAllTags(): List<String> {
        val allBookmarks = getAllBookmarks().first()
        return allBookmarks.flatMap { it.tags }.distinct().sorted()
    }
}