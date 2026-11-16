package io.github.mehrdad_abdi.qintar.domain.model

import java.time.Instant
import java.time.LocalDate

/** One pass through the whole Quran (§4.6). Coverage, position and progress are derived from the log. */
data class Khatm(
    val id: Long,
    /** The user's name or goal for it. */
    val name: String,
    val startedDay: LocalDate,
    /** Optional pages-per-day goal. A guide only. */
    val dailyPages: Float?,
    /** AyahId value; 0 = none. Ayahs 1..this count as covered without events. */
    val assumedCoveredUpTo: Int,
    val completedAt: Instant?,
) {
    init {
        require(assumedCoveredUpTo in 0..Quran.AYAH_COUNT) { "assumedCoveredUpTo out of range: $assumedCoveredUpTo" }
        require(dailyPages == null || dailyPages > 0f) { "dailyPages must be positive" }
    }
}
