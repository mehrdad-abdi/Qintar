package io.github.mehrdad_abdi.quranbookmarks.presentation.ui.reading

import io.github.mehrdad_abdi.quranbookmarks.domain.model.Bookmark
import io.github.mehrdad_abdi.quranbookmarks.domain.model.VerseMetadata

/**
 * Sealed class representing items in the reading view
 * Used for displaying a bookmark and its verses in a flat LazyColumn
 */
sealed class ReadingListItem {
    abstract val id: String

    /**
     * Represents a bookmark header with metadata
     */
    data class BookmarkHeader(
        val bookmark: Bookmark,
        val displayText: String,
        val metadata: String // e.g., "7 verses • Page 1 • Juz 1"
    ) : ReadingListItem() {
        override val id: String = "header_${bookmark.id}"
    }

    /**
     * Represents a single verse
     * Contains the verse data and its global index for playback tracking
     */
    data class VerseItem(
        val verse: VerseMetadata,
        val globalIndex: Int // Index in flat playback list (same as globalAyahNumber)
    ) : ReadingListItem() {
        override val id: String = "verse_${verse.globalAyahNumber}"
    }
}
