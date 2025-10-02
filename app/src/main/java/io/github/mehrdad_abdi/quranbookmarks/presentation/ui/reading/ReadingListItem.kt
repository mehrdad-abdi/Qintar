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
        val verseCount: Int,
        val metadata: String, // e.g., "7 verses • Page 1 • Juz 1"
        val globalStartIndex: Int // Starting index in flat playback list
    ) : ReadingListItem() {
        override val id: String = "header_${bookmark.id}"
    }

    /**
     * Represents a single verse within a bookmark
     */
    data class VerseItem(
        val verse: VerseMetadata,
        val bookmark: Bookmark,
        val displayNumber: String, // Ayah number to display (e.g., "255")
        val globalIndex: Int // Index in flat playback list
    ) : ReadingListItem() {
        override val id: String = "verse_${globalIndex}"
    }
}
