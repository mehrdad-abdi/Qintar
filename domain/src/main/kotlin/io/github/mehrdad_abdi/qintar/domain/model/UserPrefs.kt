package io.github.mehrdad_abdi.qintar.domain.model

import java.time.DayOfWeek
import java.time.LocalTime

enum class AppLanguage { SYSTEM, ENGLISH, ARABIC, PERSIAN }

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class WeekStart {
    SYSTEM, SATURDAY, SUNDAY, MONDAY;

    /** The weekday a week begins on, given the device locale's own first day. */
    fun firstDay(systemFirstDay: DayOfWeek): DayOfWeek = when (this) {
        SYSTEM -> systemFirstDay
        SATURDAY -> DayOfWeek.SATURDAY
        SUNDAY -> DayOfWeek.SUNDAY
        MONDAY -> DayOfWeek.MONDAY
    }
}

enum class CalendarSystem { GREGORIAN, PERSIAN, ISLAMIC }

enum class BookmarkSort { MUSHAF, CUSTOM, RECENT }

enum class LegacyMigrationState {
    /** Not looked for yet. */
    PENDING,

    /** No v1 database was found. */
    NOT_NEEDED,

    /** v1 data was carried over; the "carried over" card has not been dismissed. */
    MIGRATED,

    /** Carried over and the card was shown once. */
    ACKNOWLEDGED,
    FAILED,

    /**
     * v1 data is being written. Set before the import and replaced by [MIGRATED] after it, so that a
     * process that died in between is recognised at the next start: the import may have committed
     * without the state having been recorded, and running it again must not duplicate anything.
     */
    IN_PROGRESS,
}

/** The one typed preferences object (§7.5). */
data class UserPrefs(
    val language: AppLanguage = AppLanguage.SYSTEM,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val pureBlack: Boolean = false,
    val quranFontSp: Int = DEFAULT_FONT_SP,
    val breakAtPauseMarks: Boolean = true,
    val keepScreenOn: Boolean = true,
    /** R-RNK-5; null = no goal chosen. */
    val goalRank: Rank? = null,
    /** "Day starts at", minutes after midnight, 0…[MAX_DAY_START_MINUTES]. */
    val dayStartMinutes: Int = 0,
    val weekStart: WeekStart = WeekStart.SYSTEM,
    val calendarSystem: CalendarSystem = CalendarSystem.GREGORIAN,
    val latinNumerals: Boolean = false,
    val reciterId: String = DEFAULT_RECITER,
    val audioQuality: Int = DEFAULT_AUDIO_QUALITY,
    val playbackSpeed: Float = 1f,
    val countListened: Boolean = true,
    /** Playback pauses before a sajda ayah until the listener plays it. */
    val stopBeforeSajda: Boolean = false,
    /** After a sajda ayah, the sajda dhikr plays and playback waits for the listener. */
    val sajdaDhikr: Boolean = true,
    val wifiOnlyDownloads: Boolean = true,
    val reminderEnabled: Boolean = false,
    val reminderTime: LocalTime = LocalTime.of(20, 0),
    val streakReminderEnabled: Boolean = false,
    val streakReminderTime: LocalTime = LocalTime.of(21, 0),
    val bookmarkSort: BookmarkSort = BookmarkSort.MUSHAF,
    val lastBrowseAyahId: AyahId? = null,
    val onboardingDone: Boolean = false,
    val legacyMigrationState: LegacyMigrationState = LegacyMigrationState.PENDING,
) {
    init {
        require(dayStartMinutes in 0..MAX_DAY_START_MINUTES) { "dayStartMinutes out of range: $dayStartMinutes" }
        require(quranFontSp in MIN_FONT_SP..MAX_FONT_SP) { "quranFontSp out of range: $quranFontSp" }
        require(goalRank != Rank.NONE) { "NONE is not a goal; use null" }
    }

    companion object {
        const val MAX_DAY_START_MINUTES = 12 * 60
        const val MIN_FONT_SP = 20
        const val MAX_FONT_SP = 48
        const val DEFAULT_FONT_SP = 28
        const val DEFAULT_RECITER = "ar.alafasy"
        const val DEFAULT_AUDIO_QUALITY = 64
    }
}
