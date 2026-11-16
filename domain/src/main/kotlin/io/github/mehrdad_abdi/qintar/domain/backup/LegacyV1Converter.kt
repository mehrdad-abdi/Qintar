package io.github.mehrdad_abdi.qintar.domain.backup

import io.github.mehrdad_abdi.qintar.domain.model.AppLanguage
import io.github.mehrdad_abdi.qintar.domain.model.AyahId
import io.github.mehrdad_abdi.qintar.domain.model.AyahRange
import io.github.mehrdad_abdi.qintar.domain.model.Bookmark
import io.github.mehrdad_abdi.qintar.domain.model.BookmarkGroup
import io.github.mehrdad_abdi.qintar.domain.model.BookmarkKind
import io.github.mehrdad_abdi.qintar.domain.model.Khatm
import io.github.mehrdad_abdi.qintar.domain.model.NewReadEvent
import io.github.mehrdad_abdi.qintar.domain.model.NewTally
import io.github.mehrdad_abdi.qintar.domain.model.Quran
import io.github.mehrdad_abdi.qintar.domain.model.ReadContext
import io.github.mehrdad_abdi.qintar.domain.model.ReadMethod
import io.github.mehrdad_abdi.qintar.domain.model.ThemeMode
import io.github.mehrdad_abdi.qintar.domain.model.UserPrefs
import io.github.mehrdad_abdi.qintar.domain.quran.QuranIndex
import io.github.mehrdad_abdi.qintar.domain.quran.ayahId
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeParseException

/** A row of v1's `bookmarks` table (Room version 3), or a bookmark of a v1 backup file. */
data class V1Bookmark(
    val id: Long,
    /** `AYAH`, `RANGE`, `SURAH` or `PAGE`. */
    val type: String,
    val startSurah: Int,
    /** For type `PAGE` v1 keeps the page number here. */
    val startAyah: Int,
    val endSurah: Int,
    val endAyah: Int,
    val description: String?,
    val tags: List<String>,
    /** Epoch milliseconds. */
    val createdAt: Long,
    val updatedAt: Long,
)

/** A row of v1's `reading_activity` table. `badgeLevel` is derived and not read. */
data class V1ReadingActivity(
    /** `YYYY-MM-DD`. */
    val date: String,
    val totalAyahsRead: Int,
    /** The decoded JSON array; each id is `"bookmarkId:surah:ayah"`. */
    val trackedAyahIds: List<String>,
)

/** The values v1 kept in SharedPreferences; null = not found. */
data class V1Settings(
    val reciterId: String? = null,
    val bitrate: Int? = null,
    val reminderEnabled: Boolean? = null,
    val reminderTime: LocalTime? = null,
    /** `light`, `dark` or anything else for "follow the system"; case-insensitive. */
    val theme: String? = null,
    val playbackSpeed: Float? = null,
    /** A language tag: `en`, `ar`, `fa`. */
    val language: String? = null,
)

data class V1Data(
    val bookmarks: List<V1Bookmark> = emptyList(),
    val activities: List<V1ReadingActivity> = emptyList(),
    /** `khatm_progress.lastPageRead`; null when the row does not exist. */
    val khatmLastPageRead: Int? = null,
    val settings: V1Settings? = null,
)

/** What the "carried over" card reports. */
data class LegacyReport(
    val bookmarks: Int,
    val groups: Int,
    val events: Int,
    /** Ayahs carried as tallies because v1 kept only their number. */
    val talliedAyahs: Int,
    val days: Int,
    val khatmCarried: Boolean,
    /** Tracked ids that were malformed or named an ayah that does not exist. */
    val skippedAyahIds: Int,
    /** Bookmarks whose type or position could not be understood. */
    val skippedBookmarks: Int,
    /** Activity rows with an unreadable date or a date after today. */
    val skippedDays: Int,
)

data class LegacyConversion(val data: BackupData, val report: LegacyReport)

/** Maps v1 rows to v2 models (§7.11). Pure: both entry points (in-place upgrade and v1 backup file) use it. */
object LegacyV1Converter {
    /** The note on tallies that stand in for v1 readings without an ayah identity. */
    const val IMPORTED_NOTE = "imported"

    private const val TAGS_PREFIX = "Tags: "

    /**
     * @param zone the device zone, used to place each reading at noon of its day.
     * @param today the current reading day: the upper bound for imported days and the khatm's fallback start.
     * @param khatmName the (localized) name to give the carried-over khatm.
     */
    fun convert(
        v1: V1Data,
        index: QuranIndex,
        zone: ZoneId,
        today: LocalDate,
        now: Instant,
        khatmName: String,
    ): LegacyConversion {
        var skippedBookmarks = 0
        var skippedAyahIds = 0
        var skippedDays = 0

        // ---- bookmarks and groups ----
        val groups = ArrayList<BookmarkGroup>()
        val groupIdByName = HashMap<String, Long>()
        val bookmarks = ArrayList<Bookmark>()
        // sortIndex follows creation order; v1 ids break ties so the result is deterministic.
        for (row in v1.bookmarks.sortedWith(compareBy<V1Bookmark> { it.createdAt }.thenBy { it.id })) {
            val kind = kindOf(row.type)
            val range = kind?.let { rangeOf(row, it, index) }
            if (kind == null || range == null) {
                skippedBookmarks++
                continue
            }
            val created = Instant.ofEpochMilli(row.createdAt)
            val tags = row.tags.map { it.trim() }.filter { it.isNotEmpty() }
            val groupId = tags.firstOrNull()?.let { name ->
                groupIdByName.getOrPut(name) {
                    val id = groups.size + 1L
                    groups += BookmarkGroup(id, name, description = null, schedule = null, sortIndex = groups.size, createdAt = created)
                    id
                }
            }
            val description = listOfNotNull(
                row.description?.trim()?.takeIf { it.isNotEmpty() },
                // Only the first tag can become the group; the rest are kept in words so nothing is lost.
                tags.drop(1).takeIf { it.isNotEmpty() }?.joinToString(", ", prefix = TAGS_PREFIX),
            ).joinToString("\n").takeIf { it.isNotEmpty() }?.take(Bookmark.MAX_DESCRIPTION)
            bookmarks += Bookmark(
                id = row.id,
                range = range,
                kind = kind,
                label = null,
                description = description,
                repeatTarget = 1,
                groupId = groupId,
                schedule = null,
                sortIndex = bookmarks.size,
                createdAt = created,
                updatedAt = Instant.ofEpochMilli(row.updatedAt),
            )
        }
        val bookmarkIds = bookmarks.mapTo(HashSet()) { it.id }

        // ---- reading activity ----
        val events = ArrayList<NewReadEvent>()
        val tallies = ArrayList<NewTally>()
        val days = HashSet<LocalDate>()
        for (row in v1.activities) {
            val day = parseDay(row.date)
            if (day == null || day > today) {
                skippedDays++
                continue
            }
            val readAt = day.atTime(LocalTime.NOON).atZone(zone).toInstant()
            var converted = 0
            for (tracked in row.trackedAyahIds) {
                val parsed = parseTrackedId(tracked, index)
                if (parsed == null) {
                    skippedAyahIds++
                    continue
                }
                val (bookmarkId, ayah) = parsed
                // v1 used -1 for both khatm and random, so anything that is not a known bookmark is Browse.
                val context = if (bookmarkId in bookmarkIds) ReadContext.Bookmark(bookmarkId) else ReadContext.Browse
                events += NewReadEvent(ayah, day, readAt, context, ReadMethod.IMPORT, batchId = null)
                converted++
            }
            // The day's total is what the user saw in v1 and must survive: whatever part of it did not
            // become an event (more counted than tracked, or an id that could not be read) becomes a tally.
            val remainder = row.totalAyahsRead - converted
            if (remainder > 0) tallies += NewTally(day, remainder, IMPORTED_NOTE, readAt)
            if (converted > 0 || remainder > 0) days += day
        }

        // ---- khatm ----
        val khatms = v1.khatmLastPageRead?.takeIf { it > 1 }?.let { page ->
            val lastFinishedPage = (page - 1).coerceAtMost(Quran.PAGE_COUNT)
            listOf(
                Khatm(
                    id = 1,
                    name = khatmName,
                    startedDay = days.minOrNull() ?: today,
                    dailyPages = null,
                    // v1 knew only the page being read, so everything before it is assumed covered.
                    assumedCoveredUpTo = index.pageRange(lastFinishedPage).end.value,
                    completedAt = null,
                ),
            )
        }.orEmpty()

        val data = BackupData(
            exportedAt = now,
            prefs = v1.settings?.let(::prefsOf),
            khatms = khatms,
            groups = groups,
            bookmarks = bookmarks,
            events = events,
            tallies = tallies,
        )
        val report = LegacyReport(
            bookmarks = bookmarks.size,
            groups = groups.size,
            events = events.size,
            talliedAyahs = tallies.sumOf { it.count },
            days = days.size,
            khatmCarried = khatms.isNotEmpty(),
            skippedAyahIds = skippedAyahIds,
            skippedBookmarks = skippedBookmarks,
            skippedDays = skippedDays,
        )
        return LegacyConversion(data, report)
    }

    private fun kindOf(type: String): BookmarkKind? = when (type.trim().uppercase()) {
        "AYAH" -> BookmarkKind.AYAH
        "RANGE" -> BookmarkKind.RANGE
        "SURAH" -> BookmarkKind.SURAH
        "PAGE" -> BookmarkKind.PAGE
        else -> null
    }

    private fun rangeOf(row: V1Bookmark, kind: BookmarkKind, index: QuranIndex): AyahRange? = when (kind) {
        BookmarkKind.AYAH -> index.ayahId(row.startSurah, row.startAyah)?.let { AyahRange.single(it) }
        BookmarkKind.RANGE -> {
            val start = index.ayahId(row.startSurah, row.startAyah)
            val end = index.ayahId(row.endSurah, row.endAyah)
            if (start != null && end != null && start <= end) AyahRange(start, end) else null
        }
        BookmarkKind.SURAH -> row.startSurah.takeIf { it in 1..Quran.SURAH_COUNT }?.let(index::surahRange)
        BookmarkKind.PAGE -> row.startAyah.takeIf { it in 1..Quran.PAGE_COUNT }?.let(index::pageRange)
        BookmarkKind.JUZ -> null
    }

    private fun parseDay(text: String): LocalDate? = try {
        LocalDate.parse(text.trim())
    } catch (_: DateTimeParseException) {
        null
    }

    /** `"bookmarkId:surah:ayah"` → (bookmark id, ayah); null when malformed or no such ayah. */
    private fun parseTrackedId(text: String, index: QuranIndex): Pair<Long, AyahId>? {
        val parts = text.trim().split(':')
        if (parts.size != 3) return null
        val bookmarkId = parts[0].toLongOrNull() ?: return null
        val surah = parts[1].toIntOrNull() ?: return null
        val ayah = parts[2].toIntOrNull() ?: return null
        return index.ayahId(surah, ayah)?.let { bookmarkId to it }
    }

    private fun prefsOf(settings: V1Settings): UserPrefs {
        val defaults = UserPrefs()
        return defaults.copy(
            reciterId = settings.reciterId?.takeIf { it.isNotBlank() } ?: defaults.reciterId,
            audioQuality = settings.bitrate?.takeIf { it > 0 } ?: defaults.audioQuality,
            reminderEnabled = settings.reminderEnabled ?: defaults.reminderEnabled,
            reminderTime = settings.reminderTime ?: defaults.reminderTime,
            theme = when (settings.theme?.trim()?.lowercase()) {
                "light" -> ThemeMode.LIGHT
                "dark" -> ThemeMode.DARK
                else -> ThemeMode.SYSTEM
            },
            playbackSpeed = settings.playbackSpeed?.takeIf { it in 0.5f..2f } ?: defaults.playbackSpeed,
            language = when (settings.language?.trim()?.lowercase()?.take(2)) {
                "en" -> AppLanguage.ENGLISH
                "ar" -> AppLanguage.ARABIC
                "fa" -> AppLanguage.PERSIAN
                else -> AppLanguage.SYSTEM
            },
        )
    }
}
