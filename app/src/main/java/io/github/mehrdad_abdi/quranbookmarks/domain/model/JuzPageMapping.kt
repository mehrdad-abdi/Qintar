package io.github.mehrdad_abdi.quranbookmarks.domain.model

/**
 * Mapping of Juz numbers to their starting page numbers in the Quran
 */
object JuzPageMapping {
    // Maps Juz number (1-30) to starting page number (1-604)
    private val juzToPage = mapOf(
        1 to 1,
        2 to 22,
        3 to 42,
        4 to 62,
        5 to 82,
        6 to 102,
        7 to 122,
        8 to 142,
        9 to 162,
        10 to 182,
        11 to 202,
        12 to 222,
        13 to 242,
        14 to 262,
        15 to 282,
        16 to 302,
        17 to 322,
        18 to 342,
        19 to 362,
        20 to 382,
        21 to 402,
        22 to 422,
        23 to 442,
        24 to 462,
        25 to 482,
        26 to 502,
        27 to 522,
        28 to 542,
        29 to 562,
        30 to 582
    )

    /**
     * Get the starting page for a given Juz
     * @param juzNumber The Juz number (1-30)
     * @return The starting page number, or null if invalid Juz
     */
    fun getStartingPage(juzNumber: Int): Int? {
        return juzToPage[juzNumber]
    }

    /**
     * Get all Juz numbers (1-30)
     */
    fun getAllJuz(): List<Int> = (1..30).toList()
}
