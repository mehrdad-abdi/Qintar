package io.github.mehrdad_abdi.quranbookmarks.domain.model

/**
 * Composite key to uniquely identify an ayah within a bookmark context
 * Used for tracking playback state and UI identification
 *
 * This prevents issues where the same ayah appears in multiple bookmarks
 * (e.g., scrolling to the wrong occurrence)
 */
data class AyahPlaybackKey(
    val bookmarkId: Long,
    val surah: Int,
    val ayah: Int
) {
    /**
     * String representation for use in unique IDs
     * Format: "bookmarkId_surah_ayah"
     */
    fun toIdString(): String = "${bookmarkId}_${surah}_${ayah}"

    companion object {
        /**
         * Create key from VerseMetadata and bookmark ID
         */
        fun from(bookmarkId: Long, verse: VerseMetadata): AyahPlaybackKey {
            return AyahPlaybackKey(bookmarkId, verse.surahNumber, verse.ayahInSurah)
        }
    }
}
