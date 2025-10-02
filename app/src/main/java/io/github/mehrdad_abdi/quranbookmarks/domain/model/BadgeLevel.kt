package io.github.mehrdad_abdi.quranbookmarks.domain.model

/**
 * Badge levels based on daily ayah reading count.
 * Each level has a threshold (minimum ayahs required) and associated emoji.
 */
enum class BadgeLevel(
    val threshold: Int,
    val emoji: String,
    val arabicName: String,
    val displayName: String
) {
    NONE(
        threshold = 0,
        emoji = "",
        arabicName = "",
        displayName = "No Badge"
    ),
    GHAIR_GHAFIL(
        threshold = 10,
        emoji = "🔹",
        arabicName = "غیر غافل",
        displayName = "Ghair Ghafil"
    ),
    DHAKIR(
        threshold = 50,
        emoji = "🔺",
        arabicName = "ذاکر",
        displayName = "Dhakir"
    ),
    QANIT(
        threshold = 100,
        emoji = "🥉",
        arabicName = "قانت",
        displayName = "Qanit"
    ),
    KHASHIE(
        threshold = 200,
        emoji = "🥈",
        arabicName = "خاشع",
        displayName = "Khashie"
    ),
    FAEZ(
        threshold = 300,
        emoji = "🥇",
        arabicName = "فائز",
        displayName = "Faez"
    ),
    MUJTAHID(
        threshold = 500,
        emoji = "🎖️",
        arabicName = "مجتهد",
        displayName = "Mujtahid"
    ),
    SAHIB_QANTAR(
        threshold = 1000,
        emoji = "👑",
        arabicName = "صاحب القنطار",
        displayName = "Sahib Al-Qantar"
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

    /**
     * Get formatted display string with emoji and name.
     */
    fun getDisplayString(): String {
        return if (this == NONE) {
            displayName
        } else {
            "$emoji $arabicName"
        }
    }
}
