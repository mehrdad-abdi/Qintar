package io.github.mehrdad_abdi.quranbookmarks.domain.model

/**
 * Domain model representing Khatm (complete Quran) reading progress
 * Tracks the last page read by the user
 */
data class KhatmProgress(
    val lastPageRead: Int = 1, // Page number 1-604
    val updatedAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val TOTAL_PAGES = 604
        const val MIN_PAGE = 1
        const val MAX_PAGE = TOTAL_PAGES
    }

    /**
     * Validates if the page number is within valid range
     */
    fun isValidPage(page: Int): Boolean = page in MIN_PAGE..MAX_PAGE
}
