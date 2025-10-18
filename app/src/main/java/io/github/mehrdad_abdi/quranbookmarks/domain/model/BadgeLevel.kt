package io.github.mehrdad_abdi.quranbookmarks.domain.model

import androidx.annotation.StringRes
import io.github.mehrdad_abdi.quranbookmarks.R

/**
 * Badge levels based on daily ayah reading count.
 * Each level has a threshold (minimum ayahs required) and a localized name.
 * Badge names are extracted from the authentic Hadith about Quran reading rewards.
 */
enum class BadgeLevel(
    val threshold: Int,
    val emoji: String,
    @StringRes val nameResId: Int
) {
    NONE(
        threshold = 0,
        emoji = "",
        nameResId = R.string.badge_none
    ),
    GHAIR_GHAFIL(
        threshold = 10,
        emoji = "🔹",
        nameResId = R.string.badge_ghair_ghafil
    ),
    DHAKIR(
        threshold = 50,
        emoji = "🔺",
        nameResId = R.string.badge_dhakir
    ),
    QANIT(
        threshold = 100,
        emoji = "🥉",
        nameResId = R.string.badge_qanit
    ),
    KHASHIE(
        threshold = 200,
        emoji = "🥈",
        nameResId = R.string.badge_khashie
    ),
    FAEZ(
        threshold = 300,
        emoji = "🥇",
        nameResId = R.string.badge_faez
    ),
    MUJTAHID(
        threshold = 500,
        emoji = "🎖",
        nameResId = R.string.badge_mujtahid
    ),
    SAHIB_QANTAR(
        threshold = 1000,
        emoji = "👑",
        nameResId = R.string.badge_sahib_qantar
    );

    companion object {
        /**
         * Get badge level from ayah count.
         * Returns the highest badge level achieved for the given count.
         */
        fun fromAyahCount(count: Int): BadgeLevel {
            return values()
                .filter { it.threshold <= count }
                .maxByOrNull { it.threshold }
                ?: NONE
        }

        /**
         * Get all badge levels in ascending order (excluding NONE).
         */
        fun getAllLevels(): List<BadgeLevel> {
            return values().filter { it != NONE }
        }

        /**
         * Get the next badge level after the current one.
         * Returns null if already at the highest level.
         */
        fun getNextLevel(currentLevel: BadgeLevel): BadgeLevel? {
            val allLevels = getAllLevels()
            val currentIndex = allLevels.indexOf(currentLevel)
            return if (currentIndex >= 0 && currentIndex < allLevels.size - 1) {
                allLevels[currentIndex + 1]
            } else {
                null
            }
        }
    }

    /**
     * Get ayahs needed to reach the next badge level.
     * Returns null if already at the highest level.
     */
    fun getAyahsToNextLevel(currentCount: Int): Int? {
        val nextLevel = getNextLevel(this) ?: return null
        return (nextLevel.threshold - currentCount).coerceAtLeast(0)
    }
}
