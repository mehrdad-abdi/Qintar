package io.github.mehrdad_abdi.quranbookmarks.domain.model

/**
 * Sealed class representing the context in which audio playback is happening.
 * Used by AudioService to determine how to prefetch the next ayah.
 */
sealed class PlaybackContext {
    /**
     * Playing ayahs from a single bookmark
     */
    data class SingleBookmark(
        val bookmarkId: Long,
        val allAyahs: List<VerseMetadata>,
        val currentIndex: Int
    ) : PlaybackContext()

    /**
     * Playing ayahs from multiple bookmarks sequentially (Play All)
     */
    data class AllBookmarks(
        val allAyahs: List<AyahWithBookmark>,
        val currentIndex: Int
    ) : PlaybackContext()

    /**
     * Playing ayahs during Khatm reading (page by page)
     */
    data class KhatmReading(
        val currentPageNumber: Int,
        val allAyahsOnPage: List<VerseMetadata>,
        val currentIndex: Int
    ) : PlaybackContext()

    /**
     * Playing random ayahs - no prefetching needed
     */
    data object Random : PlaybackContext()
}

/**
 * Wrapper class to uniquely identify an ayah with its bookmark context
 */
data class AyahWithBookmark(
    val bookmarkId: Long,
    val verse: VerseMetadata
)
