package io.github.mehrdad_abdi.qintar.data.backup

import io.github.mehrdad_abdi.qintar.domain.backup.BackupData
import io.github.mehrdad_abdi.qintar.domain.backup.V1Bookmark
import io.github.mehrdad_abdi.qintar.domain.backup.V1Data
import io.github.mehrdad_abdi.qintar.domain.backup.V1ReadingActivity
import io.github.mehrdad_abdi.qintar.domain.backup.V1Settings
import io.github.mehrdad_abdi.qintar.domain.model.AppLanguage
import io.github.mehrdad_abdi.qintar.domain.model.AyahId
import io.github.mehrdad_abdi.qintar.domain.model.AyahRange
import io.github.mehrdad_abdi.qintar.domain.model.Bookmark
import io.github.mehrdad_abdi.qintar.domain.model.BookmarkGroup
import io.github.mehrdad_abdi.qintar.domain.model.BookmarkKind
import io.github.mehrdad_abdi.qintar.domain.model.BookmarkSort
import io.github.mehrdad_abdi.qintar.domain.model.CalendarSystem
import io.github.mehrdad_abdi.qintar.domain.model.ContextKind
import io.github.mehrdad_abdi.qintar.domain.model.Khatm
import io.github.mehrdad_abdi.qintar.domain.model.NewReadEvent
import io.github.mehrdad_abdi.qintar.domain.model.NewTally
import io.github.mehrdad_abdi.qintar.domain.model.Rank
import io.github.mehrdad_abdi.qintar.domain.model.ReadContext
import io.github.mehrdad_abdi.qintar.domain.model.ReadMethod
import io.github.mehrdad_abdi.qintar.domain.model.Schedule
import io.github.mehrdad_abdi.qintar.domain.model.ThemeMode
import io.github.mehrdad_abdi.qintar.domain.model.TimeWindow
import io.github.mehrdad_abdi.qintar.domain.model.UserPrefs
import io.github.mehrdad_abdi.qintar.domain.model.WeekStart
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/** What a file turned out to be. Decoding never throws; a file that cannot be used is [Invalid]. */
sealed interface BackupDecodeResult {
    /** A Qintar 2 backup. Still to be validated by `BackupManager.preview`. */
    data class Backup(val data: BackupData) : BackupDecodeResult

    /** A v1 backup (`metadata.version` "1.x"); goes through `LegacyV1Converter` (§7.11). */
    data class LegacyV1(val data: V1Data) : BackupDecodeResult

    data class Invalid(val reason: Reason, val detail: String) : BackupDecodeResult {
        enum class Reason {
            /** Not JSON at all (truncated, another file type). */
            NOT_JSON,

            /** JSON, but neither a Qintar backup nor a v1 backup. */
            NOT_A_BACKUP,

            /** Written by a newer app than this one understands; [detail] is the file's version. */
            NEWER_VERSION,

            /** A backup whose content is missing a required field or holds an impossible value. */
            MALFORMED,
        }
    }
}

/**
 * The backup file format (§7.10): JSON text ↔ [BackupData], and v1 backup JSON → [V1Data].
 *
 * Beyond the documented fields the encoder writes `createdAt` / `updatedAt` (epoch milliseconds) for
 * groups, bookmarks and tallies, and `once` inside a bookmark's schedule, so that a restore is
 * lossless. They are optional when decoding: absent, they become `exportedAt`. Unknown fields are ignored.
 *
 * Preferences: only user-facing ones are written. Device state (`onboardingDone`,
 * `legacyMigrationState`, `lastBrowseAyahId`) is not, and decodes to its default.
 */
class BackupCodec @Inject constructor() {

    // ---------------------------------------------------------------- encode

    fun encode(data: BackupData): String = buildJsonObject {
        put("format", BackupData.FORMAT)
        put("version", data.version)
        put("exportedAt", data.exportedAt.toString())
        putJsonObject("app") {
            put("versionName", data.appVersionName)
            put("versionCode", data.appVersionCode)
        }
        data.prefs?.let { put("prefs", encodePrefs(it)) }
        putJsonArray("khatms") {
            for (k in data.khatms) add(
                buildJsonObject {
                    put("id", k.id)
                    put("name", k.name)
                    put("startedDay", k.startedDay.toString())
                    put("dailyPages", k.dailyPages)
                    put("assumedCoveredUpTo", k.assumedCoveredUpTo)
                    put("completedAt", k.completedAt?.toString())
                },
            )
        }
        putJsonArray("groups") {
            for (g in data.groups) add(
                buildJsonObject {
                    put("id", g.id)
                    put("name", g.name)
                    put("description", g.description)
                    putSchedule(g.schedule)
                    put("sortIndex", g.sortIndex)
                    put("createdAt", g.createdAt.toEpochMilli())
                },
            )
        }
        putJsonArray("bookmarks") {
            for (b in data.bookmarks) add(
                buildJsonObject {
                    put("id", b.id)
                    put("start", b.range.start.value)
                    put("end", b.range.end.value)
                    put("kind", b.kind.name)
                    put("label", b.label)
                    put("description", b.description)
                    put("repeatTarget", b.repeatTarget)
                    put("groupId", b.groupId)
                    putSchedule(b.schedule)
                    put("sortIndex", b.sortIndex)
                    put("createdAt", b.createdAt.toEpochMilli())
                    put("updatedAt", b.updatedAt.toEpochMilli())
                },
            )
        }
        putJsonObject("events") {
            putJsonArray("columns") { EVENT_COLUMNS.forEach { add(it) } }
            putJsonArray("rows") {
                for (e in data.events) add(
                    buildJsonArray {
                        add(e.ayahId.value)
                        add(e.day.toString())
                        add(e.readAt.toEpochMilli())
                        add(e.context.kind.name)
                        add(e.context.id)
                        add(e.method.name)
                        add(e.batchId)
                    },
                )
            }
        }
        putJsonArray("tallies") {
            for (t in data.tallies) add(
                buildJsonObject {
                    put("day", t.day.toString())
                    put("count", t.count)
                    put("note", t.note)
                    put("createdAt", t.createdAt.toEpochMilli())
                },
            )
        }
    }.toString()

    private fun JsonObjectBuilder.putSchedule(schedule: Schedule?) {
        if (schedule == null) {
            put("schedule", JsonNull)
            return
        }
        putJsonObject("schedule") {
            putJsonArray("days") { DayOfWeek.entries.filter { it in schedule.days }.forEach { add(it.name) } }
            val window = schedule.window
            if (window == null) {
                put("window", JsonNull)
            } else {
                putJsonObject("window") {
                    put("from", window.from.format(TIME))
                    put("to", window.to.format(TIME))
                }
            }
            if (schedule.once) put("once", true)
        }
    }

    private fun encodePrefs(p: UserPrefs): JsonObject = buildJsonObject {
        put("language", p.language.name)
        put("theme", p.theme.name)
        put("pureBlack", p.pureBlack)
        put("quranFontSp", p.quranFontSp)
        put("breakAtPauseMarks", p.breakAtPauseMarks)
        put("keepScreenOn", p.keepScreenOn)
        put("goalRank", p.goalRank?.name)
        put("dayStartMinutes", p.dayStartMinutes)
        put("weekStart", p.weekStart.name)
        put("calendarSystem", p.calendarSystem.name)
        put("latinNumerals", p.latinNumerals)
        put("reciterId", p.reciterId)
        put("audioQuality", p.audioQuality)
        put("playbackSpeed", p.playbackSpeed)
        put("countListened", p.countListened)
        put("stopBeforeSajda", p.stopBeforeSajda)
        put("wifiOnlyDownloads", p.wifiOnlyDownloads)
        put("reminderEnabled", p.reminderEnabled)
        put("reminderTime", p.reminderTime.format(TIME))
        put("streakReminderEnabled", p.streakReminderEnabled)
        put("streakReminderTime", p.streakReminderTime.format(TIME))
        put("bookmarkSort", p.bookmarkSort.name)
    }

    // ---------------------------------------------------------------- decode

    /** Never throws: anything that is not a usable backup comes back as [BackupDecodeResult.Invalid]. */
    fun decode(text: String): BackupDecodeResult {
        val root = try {
            JSON.parseToJsonElement(text.trimStart('﻿'))
        } catch (failure: Exception) {
            return invalid(BackupDecodeResult.Invalid.Reason.NOT_JSON, failure.message ?: "not JSON")
        } catch (_: StackOverflowError) {
            return invalid(BackupDecodeResult.Invalid.Reason.NOT_JSON, "nested too deeply")
        }
        if (root !is JsonObject) return invalid(BackupDecodeResult.Invalid.Reason.NOT_A_BACKUP, "not a JSON object")
        return try {
            when {
                root.text("format") == BackupData.FORMAT -> decodeV2(root)
                isV1(root) -> BackupDecodeResult.LegacyV1(decodeV1(root))
                else -> invalid(BackupDecodeResult.Invalid.Reason.NOT_A_BACKUP, "no \"format\": \"${BackupData.FORMAT}\"")
            }
        } catch (failure: Malformed) {
            invalid(BackupDecodeResult.Invalid.Reason.MALFORMED, failure.message ?: "malformed")
        } catch (failure: Exception) {
            // IllegalArgumentException from a model constructor (ayah id out of range, range reversed,
            // repeat target 0 …), DateTimeParseException, and anything else a hostile file can provoke.
            invalid(BackupDecodeResult.Invalid.Reason.MALFORMED, failure.message ?: failure.javaClass.simpleName)
        }
    }

    private fun invalid(reason: BackupDecodeResult.Invalid.Reason, detail: String) = BackupDecodeResult.Invalid(reason, detail)

    private fun decodeV2(root: JsonObject): BackupDecodeResult {
        val version = root.primitive("version")?.intOrNull ?: throw Malformed("version is not a number")
        if (version > BackupData.CURRENT_VERSION) {
            return invalid(BackupDecodeResult.Invalid.Reason.NEWER_VERSION, version.toString())
        }
        if (version < BackupData.CURRENT_VERSION) throw Malformed("unknown version $version")

        val exportedAt = Instant.parse(root.requireText("exportedAt"))
        val app = root.obj("app")

        val khatms = root.objects("khatms").map { k ->
            Khatm(
                id = k.requireLong("id"),
                name = k.requireText("name"),
                startedDay = LocalDate.parse(k.requireText("startedDay")),
                dailyPages = k.primitive("dailyPages")?.let { it.floatOrNull ?: throw Malformed("khatm dailyPages") },
                assumedCoveredUpTo = k.primitive("assumedCoveredUpTo")?.let { it.intOrNull ?: throw Malformed("khatm assumedCoveredUpTo") } ?: 0,
                completedAt = k.text("completedAt")?.let(Instant::parse),
            )
        }
        val groups = root.objects("groups").map { g ->
            BookmarkGroup(
                id = g.requireLong("id"),
                name = g.requireText("name"),
                description = g.text("description"),
                schedule = decodeSchedule(g.obj("schedule"))?.copy(once = false),
                sortIndex = g.requireInt("sortIndex"),
                createdAt = g.instant("createdAt") ?: exportedAt,
            )
        }
        val bookmarks = root.objects("bookmarks").map { b ->
            val createdAt = b.instant("createdAt") ?: exportedAt
            Bookmark(
                id = b.requireLong("id"),
                range = AyahRange.of(b.requireInt("start"), b.requireInt("end")),
                kind = enumOf<BookmarkKind>(b.requireText("kind")),
                label = b.text("label"),
                description = b.text("description"),
                repeatTarget = b.primitive("repeatTarget")?.let { it.intOrNull ?: throw Malformed("bookmark repeatTarget") } ?: 1,
                groupId = b.primitive("groupId")?.let { it.longOrNull ?: throw Malformed("bookmark groupId") },
                schedule = decodeSchedule(b.obj("schedule")),
                sortIndex = b.requireInt("sortIndex"),
                createdAt = createdAt,
                updatedAt = b.instant("updatedAt") ?: createdAt,
            )
        }
        val tallies = root.objects("tallies").map { t ->
            NewTally(
                day = LocalDate.parse(t.requireText("day")),
                count = t.requireInt("count"),
                note = t.text("note"),
                createdAt = t.instant("createdAt") ?: exportedAt,
            )
        }

        return BackupDecodeResult.Backup(
            BackupData(
                version = version,
                exportedAt = exportedAt,
                appVersionName = app?.text("versionName"),
                appVersionCode = app?.primitive("versionCode")?.intOrNull,
                prefs = root.obj("prefs")?.let(::decodePrefs),
                khatms = khatms,
                groups = groups,
                bookmarks = bookmarks,
                events = decodeEvents(root.obj("events")),
                tallies = tallies,
            ),
        )
    }

    /** Columns are looked up by name, so a file may order them differently or carry extra ones. */
    private fun decodeEvents(events: JsonObject?): List<NewReadEvent> {
        if (events == null) return emptyList()
        val columns = (events["columns"] as? JsonArray ?: throw Malformed("events.columns missing"))
            .map { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content ?: throw Malformed("events.columns") }
        fun column(name: String, required: Boolean = true): Int =
            columns.indexOf(name).also { if (it < 0 && required) throw Malformed("events column \"$name\" missing") }

        val ayahId = column("ayahId")
        val day = column("day")
        val readAt = column("readAt")
        val contextType = column("contextType")
        val contextId = column("contextId")
        val method = column("method")
        val batchId = column("batchId", required = false)

        val rows = events["rows"] as? JsonArray ?: throw Malformed("events.rows missing")
        return rows.mapIndexed { i, row ->
            if (row !is JsonArray || row.size < columns.size) throw Malformed("event row $i is not a full row")
            fun cell(index: Int): JsonPrimitive? = when (val value = row[index]) {
                is JsonNull -> null
                is JsonPrimitive -> value
                else -> throw Malformed("event row $i")
            }
            val kind = enumOf<ContextKind>(cell(contextType)?.content ?: throw Malformed("event row $i: contextType"))
            val id = cell(contextId)?.let { it.longOrNull ?: throw Malformed("event row $i: contextId") }
            NewReadEvent(
                ayahId = AyahId(cell(ayahId)?.intOrNull ?: throw Malformed("event row $i: ayahId")),
                day = LocalDate.parse(cell(day)?.content ?: throw Malformed("event row $i: day")),
                readAt = Instant.ofEpochMilli(cell(readAt)?.longOrNull ?: throw Malformed("event row $i: readAt")),
                context = ReadContext.of(kind, id) ?: throw Malformed("event row $i: $kind needs a contextId"),
                method = enumOf<ReadMethod>(cell(method)?.content ?: throw Malformed("event row $i: method")),
                batchId = if (batchId >= 0) cell(batchId)?.content else null,
            )
        }
    }

    private fun decodeSchedule(json: JsonObject?): Schedule? {
        if (json == null) return null
        val days = (json["days"] as? JsonArray ?: throw Malformed("schedule.days missing"))
            .mapTo(LinkedHashSet()) { enumOf<DayOfWeek>((it as? JsonPrimitive)?.content ?: throw Malformed("schedule.days")) }
        val window = json.obj("window")?.let { TimeWindow(LocalTime.parse(it.requireText("from")), LocalTime.parse(it.requireText("to"))) }
        return Schedule(days, window, once = json.primitive("once")?.booleanOrNull ?: false)
    }

    /**
     * Preferences are a convenience, not history: a field that is missing or unusable falls back to
     * its default instead of refusing the whole file.
     */
    private fun decodePrefs(json: JsonObject): UserPrefs {
        val d = UserPrefs()
        fun bool(name: String, default: Boolean) = json.primitive(name)?.booleanOrNull ?: default
        fun time(name: String, default: LocalTime) = json.text(name)?.let { runCatching { LocalTime.parse(it) }.getOrNull() } ?: default
        return UserPrefs(
            language = json.enumOrNull<AppLanguage>("language") ?: d.language,
            theme = json.enumOrNull<ThemeMode>("theme") ?: d.theme,
            pureBlack = bool("pureBlack", d.pureBlack),
            quranFontSp = json.primitive("quranFontSp")?.intOrNull?.takeIf { it in UserPrefs.MIN_FONT_SP..UserPrefs.MAX_FONT_SP } ?: d.quranFontSp,
            breakAtPauseMarks = bool("breakAtPauseMarks", d.breakAtPauseMarks),
            keepScreenOn = bool("keepScreenOn", d.keepScreenOn),
            goalRank = json.enumOrNull<Rank>("goalRank")?.takeIf { it != Rank.NONE },
            dayStartMinutes = json.primitive("dayStartMinutes")?.intOrNull?.takeIf { it in 0..UserPrefs.MAX_DAY_START_MINUTES } ?: d.dayStartMinutes,
            weekStart = json.enumOrNull<WeekStart>("weekStart") ?: d.weekStart,
            calendarSystem = json.enumOrNull<CalendarSystem>("calendarSystem") ?: d.calendarSystem,
            latinNumerals = bool("latinNumerals", d.latinNumerals),
            reciterId = json.text("reciterId")?.takeIf { it.isNotBlank() } ?: d.reciterId,
            audioQuality = json.primitive("audioQuality")?.intOrNull?.takeIf { it > 0 } ?: d.audioQuality,
            playbackSpeed = json.primitive("playbackSpeed")?.floatOrNull?.takeIf { it.isFinite() && it > 0f } ?: d.playbackSpeed,
            countListened = bool("countListened", d.countListened),
            stopBeforeSajda = bool("stopBeforeSajda", d.stopBeforeSajda),
            wifiOnlyDownloads = bool("wifiOnlyDownloads", d.wifiOnlyDownloads),
            reminderEnabled = bool("reminderEnabled", d.reminderEnabled),
            reminderTime = time("reminderTime", d.reminderTime),
            streakReminderEnabled = bool("streakReminderEnabled", d.streakReminderEnabled),
            streakReminderTime = time("streakReminderTime", d.streakReminderTime),
            bookmarkSort = json.enumOrNull<BookmarkSort>("bookmarkSort") ?: d.bookmarkSort,
        )
    }

    // ---------------------------------------------------------------- v1

    /** v1 wrote Gson's rendering of its `BackupData`: `{ metadata: { version: "1.0", … }, bookmarks, readingActivity, settings }`. */
    private fun isV1(root: JsonObject): Boolean {
        val version = root.obj("metadata")?.text("version") ?: root.text("version") ?: return false
        return version == "1" || version.startsWith("1.")
    }

    private fun decodeV1(root: JsonObject): V1Data {
        val exported = root.obj("metadata")?.text("timestamp")?.let(::v1Timestamp)
        val bookmarks = root.objects("bookmarks").mapIndexed { i, b ->
            // Gson leaves out nothing v1 always set, but be lenient: only the position is essential.
            val startSurah = b.requireInt("startSurah")
            val startAyah = b.primitive("startAyah")?.intOrNull ?: 1
            val created = b.primitive("createdAt")?.longOrNull ?: exported ?: 0L
            V1Bookmark(
                id = b.primitive("id")?.longOrNull?.takeIf { it > 0 } ?: -(i + 1L),
                type = b.requireText("type"),
                startSurah = startSurah,
                startAyah = startAyah,
                endSurah = b.primitive("endSurah")?.intOrNull ?: startSurah,
                endAyah = b.primitive("endAyah")?.intOrNull ?: startAyah,
                description = b.text("description"),
                tags = b.strings("tags"),
                createdAt = created,
                updatedAt = b.primitive("updatedAt")?.longOrNull ?: created,
            )
        }
        val activities = root.objects("readingActivity").map { a ->
            val tracked = a.strings("trackedAyahIds")
            V1ReadingActivity(
                date = a.requireText("date"),
                totalAyahsRead = a.primitive("totalAyahsRead")?.intOrNull ?: tracked.size,
                trackedAyahIds = tracked,
            )
        }
        val settings = root.obj("settings")?.let { s ->
            val notification = s.obj("notificationSettings")
            V1Settings(
                reciterId = s.text("reciterEdition"),
                bitrate = s.primitive("reciterBitrate")?.content?.trim()?.toIntOrNull(),
                reminderEnabled = notification?.primitive("enabled")?.booleanOrNull,
                reminderTime = notification?.text("time")?.let(::v1Time),
                theme = s.text("theme"),
                playbackSpeed = s.primitive("playbackSpeed")?.floatOrNull,
                language = s.text("language")?.let(::v1Language),
            )
        }
        // A v1 backup never carried the khatm position.
        return V1Data(bookmarks = bookmarks, activities = activities, khatmLastPageRead = null, settings = settings)
    }

    private class Malformed(message: String) : Exception(message)

    companion object {
        val EVENT_COLUMNS = listOf("ayahId", "day", "readAt", "contextType", "contextId", "method", "batchId")

        private val JSON = Json { isLenient = false }
        private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

        /** v1's `HH:mm` reminder time; null when unreadable. */
        internal fun v1Time(text: String): LocalTime? = runCatching { LocalTime.parse(text.trim(), DateTimeFormatter.ofPattern("H:mm")) }.getOrNull()

        /** v1's `AppLanguage` enum name (as Gson wrote it) or a locale code → a language tag. */
        internal fun v1Language(value: String): String = when (value.trim().uppercase()) {
            "ENGLISH" -> "en"
            "ARABIC" -> "ar"
            "FARSI", "PERSIAN" -> "fa"
            else -> value.trim().lowercase()
        }

        /** v1 wrote a zone-less local date-time; good enough as a fallback creation time. */
        private fun v1Timestamp(text: String): Long? = runCatching {
            LocalDateTime.parse(text).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }.getOrNull()

        private fun JsonObject.primitive(name: String): JsonPrimitive? = (this[name] as? JsonPrimitive)?.takeIf { it !is JsonNull }

        private fun JsonObject.text(name: String): String? = primitive(name)?.takeIf { it.isString }?.content

        private fun JsonObject.obj(name: String): JsonObject? = when (val value = this[name]) {
            null, is JsonNull -> null
            is JsonObject -> value
            else -> throw Malformed("\"$name\" is not an object")
        }

        /** A missing or null list is empty; anything else that is not a list of objects is malformed. */
        private fun JsonObject.objects(name: String): List<JsonObject> = when (val value = this[name]) {
            null, is JsonNull -> emptyList()
            is JsonArray -> value.map { it as? JsonObject ?: throw Malformed("\"$name\" holds something that is not an object") }
            else -> throw Malformed("\"$name\" is not a list")
        }

        private fun JsonObject.strings(name: String): List<String> =
            (this[name] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p !is JsonNull }?.content }.orEmpty()

        private fun JsonObject.requireText(name: String): String = text(name) ?: throw Malformed("\"$name\" missing")

        private fun JsonObject.requireInt(name: String): Int = primitive(name)?.intOrNull ?: throw Malformed("\"$name\" missing or not a number")

        private fun JsonObject.requireLong(name: String): Long = primitive(name)?.longOrNull ?: throw Malformed("\"$name\" missing or not a number")

        private fun JsonObject.instant(name: String): Instant? = primitive(name)?.longOrNull?.let(Instant::ofEpochMilli)

        private inline fun <reified E : Enum<E>> enumOf(name: String): E =
            enumValues<E>().firstOrNull { it.name == name } ?: throw Malformed("unknown ${E::class.java.simpleName} \"$name\"")

        private inline fun <reified E : Enum<E>> JsonObject.enumOrNull(name: String): E? =
            text(name)?.let { value -> enumValues<E>().firstOrNull { it.name == value } }
    }
}
