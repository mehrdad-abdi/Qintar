package io.github.mehrdad_abdi.quranbookmarks.domain.model.backup

/**
 * Strategy for handling conflicts during import.
 */
enum class ImportStrategy {
    /**
     * Replace all existing data with imported data.
     */
    REPLACE_ALL,

    /**
     * Merge imported data with existing data.
     * For bookmarks: add new ones, update existing based on ID
     * For reading activity: merge by date
     * For settings: keep existing settings
     */
    MERGE,

    /**
     * Import only bookmarks, skip other data.
     */
    BOOKMARKS_ONLY,

    /**
     * Import only reading activity, skip other data.
     */
    READING_ACTIVITY_ONLY
}

/**
 * Options for import operation.
 */
data class ImportOptions(
    val strategy: ImportStrategy = ImportStrategy.MERGE,
    val preserveExistingBookmarks: Boolean = true,
    val mergeReadingActivity: Boolean = true,
    val importSettings: Boolean = false
)

/**
 * Result of import operation.
 */
sealed class ImportResult {
    data class Success(
        val bookmarksImported: Int,
        val readingActivityImported: Int,
        val settingsImported: Boolean
    ) : ImportResult()

    data class PartialSuccess(
        val bookmarksImported: Int,
        val readingActivityImported: Int,
        val settingsImported: Boolean,
        val warnings: List<String>
    ) : ImportResult()

    data class Error(val message: String, val cause: Throwable? = null) : ImportResult()
}
