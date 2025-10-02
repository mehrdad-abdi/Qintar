package io.github.mehrdad_abdi.quranbookmarks.domain.repository

import io.github.mehrdad_abdi.quranbookmarks.domain.model.Bookmark
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory repository for temporary bookmarks (e.g., random reading).
 * These bookmarks are not persisted to database.
 */
@Singleton
class TemporaryBookmarkRepository @Inject constructor() {
    companion object {
        const val TEMP_BOOKMARK_ID = -1L
    }

    private var temporaryBookmark: Bookmark? = null

    fun saveTemporaryBookmark(bookmark: Bookmark) {
        temporaryBookmark = bookmark.copy(id = TEMP_BOOKMARK_ID)
    }

    fun getTemporaryBookmark(): Bookmark? {
        return temporaryBookmark
    }

    fun clearTemporaryBookmark() {
        temporaryBookmark = null
    }
}
