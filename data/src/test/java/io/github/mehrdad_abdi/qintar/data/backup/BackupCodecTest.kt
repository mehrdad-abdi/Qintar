package io.github.mehrdad_abdi.qintar.data.backup

import io.github.mehrdad_abdi.qintar.data.backup.BackupDecodeResult.Invalid.Reason
import io.github.mehrdad_abdi.qintar.domain.backup.BackupData
import io.github.mehrdad_abdi.qintar.domain.backup.ImportMode
import io.github.mehrdad_abdi.qintar.domain.backup.LegacyV1Converter
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
import io.github.mehrdad_abdi.qintar.domain.model.Khatm
import io.github.mehrdad_abdi.qintar.domain.model.LegacyMigrationState
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
import io.github.mehrdad_abdi.qintar.domain.testing.DomainFixture
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class BackupCodecTest {

    private val codec = BackupCodec()

    private val exportedAt: Instant = Instant.parse("2026-11-15T20:10:00Z")
    private val day: LocalDate = LocalDate.of(2026, 11, 13)

    private val prefs = UserPrefs(
        language = AppLanguage.ARABIC,
        theme = ThemeMode.DARK,
        pureBlack = true,
        quranFontSp = 34,
        breakAtPauseMarks = false,
        keepScreenOn = false,
        goalRank = Rank.DHAKIR,
        dayStartMinutes = 180,
        weekStart = WeekStart.SUNDAY,
        calendarSystem = CalendarSystem.ISLAMIC,
        latinNumerals = true,
        reciterId = "ar.minshawi",
        audioQuality = 128,
        playbackSpeed = 1.5f,
        countListened = false,
        stopBeforeSajda = true,
        wifiOnlyDownloads = false,
        reminderEnabled = true,
        reminderTime = LocalTime.of(6, 5),
        streakReminderEnabled = true,
        streakReminderTime = LocalTime.of(22, 30),
        bookmarkSort = BookmarkSort.RECENT,
    )

    private val full = BackupData(
        exportedAt = exportedAt,
        appVersionName = "2.0.0",
        appVersionCode = 200,
        prefs = prefs,
        khatms = listOf(
            Khatm(1, "First", LocalDate.of(2026, 10, 1), dailyPages = 4.0f, assumedCoveredUpTo = 0, completedAt = null),
            Khatm(2, "ختم رمضان \"الثاني\"", LocalDate.of(2026, 3, 1), dailyPages = null, assumedCoveredUpTo = 293, completedAt = Instant.parse("2026-03-29T18:00:00Z")),
        ),
        groups = listOf(
            BookmarkGroup(2, "Friday", null, Schedule(setOf(DayOfWeek.FRIDAY), null), sortIndex = 1, createdAt = Instant.ofEpochMilli(1_700_000_000_000)),
            BookmarkGroup(3, "Night", "before sleep", Schedule(Schedule.EVERY_DAY, TimeWindow(LocalTime.of(22, 0), LocalTime.of(1, 30))), 2, Instant.ofEpochMilli(1_700_000_000_500)),
            BookmarkGroup(4, "Loose", null, null, 0, exportedAt),
        ),
        bookmarks = listOf(
            Bookmark(7, AyahRange.of(2158, 2267), BookmarkKind.SURAH, null, "line one\nline two\ttabbed \\ slashed", 1, 2, null, 3, Instant.ofEpochMilli(1_700_000_001_000), Instant.ofEpochMilli(1_700_000_002_000)),
            Bookmark(8, AyahRange.of(262, 262), BookmarkKind.AYAH, "آية الكرسي", null, 3, null, Schedule.once(TimeWindow(LocalTime.of(5, 0), LocalTime.of(7, 0))), 0, exportedAt, exportedAt),
            Bookmark(9, AyahRange.of(1, 7), BookmarkKind.PAGE, null, null, 1, 3, Schedule(setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY), null), 1, exportedAt, exportedAt),
        ),
        events = listOf(
            NewReadEvent(AyahId(2158), day, Instant.ofEpochMilli(1_794_686_400_000), ReadContext.Bookmark(7), ReadMethod.TAP, null),
            NewReadEvent(AyahId(1), day, Instant.ofEpochMilli(1_794_686_400_001), ReadContext.Khatm(1), ReadMethod.BULK, "batch-1"),
            NewReadEvent(AyahId(6236), day.plusDays(1), Instant.ofEpochMilli(1_794_686_400_002), ReadContext.Browse, ReadMethod.AUDIO, null),
            NewReadEvent(AyahId(255), day, Instant.ofEpochMilli(1_794_686_400_003), ReadContext.Random, ReadMethod.MANUAL, null),
            NewReadEvent(AyahId(255), day, Instant.ofEpochMilli(1_794_686_400_003), ReadContext.Log, ReadMethod.IMPORT, "b"),
            // A reading whose bookmark was deleted before the export.
            NewReadEvent(AyahId(300), day, Instant.ofEpochMilli(1_794_686_400_004), ReadContext.Bookmark(999), ReadMethod.TAP, null),
        ),
        tallies = listOf(NewTally(LocalDate.of(2026, 11, 14), 40, "mosque", Instant.ofEpochMilli(1_794_700_000_000)), NewTally(day, 3, null, exportedAt)),
    )

    private fun decoded(text: String): BackupData = when (val result = codec.decode(text)) {
        is BackupDecodeResult.Backup -> result.data
        else -> throw AssertionError("expected a backup, got $result")
    }

    private fun invalid(text: String): BackupDecodeResult.Invalid = when (val result = codec.decode(text)) {
        is BackupDecodeResult.Invalid -> result
        else -> throw AssertionError("expected an invalid file, got $result")
    }

    // ---- round trip ----

    @Test
    fun everythingInABackupSurvivesEncodingAndDecoding() {
        assertEquals(full, decoded(codec.encode(full)))
    }

    @Test
    fun anEmptyBackupRoundTrips() {
        val empty = BackupData(exportedAt = exportedAt)
        assertEquals(empty, decoded(codec.encode(empty)))
    }

    @Test
    fun deviceStateIsNotCarriedInTheFile() {
        val withDeviceState = full.copy(
            prefs = prefs.copy(onboardingDone = true, legacyMigrationState = LegacyMigrationState.ACKNOWLEDGED, lastBrowseAyahId = AyahId(77)),
        )

        val text = codec.encode(withDeviceState)

        assertEquals(prefs, decoded(text).prefs)
        for (key in listOf("onboardingDone", "legacyMigrationState", "lastBrowseAyahId")) assertTrue(key, !text.contains(key))
    }

    @Test
    fun theFileHasTheDocumentedShape() {
        val root = Json.parseToJsonElement(codec.encode(full)).jsonObject

        assertEquals("qintar-backup", root["format"]!!.jsonPrimitive.content)
        assertEquals(2, root["version"]!!.jsonPrimitive.content.toInt())
        assertEquals("2026-11-15T20:10:00Z", root["exportedAt"]!!.jsonPrimitive.content)
        assertEquals("2.0.0", root["app"]!!.jsonObject["versionName"]!!.jsonPrimitive.content)
        assertEquals("2026-10-01", root["khatms"]!!.jsonArray[0].jsonObject["startedDay"]!!.jsonPrimitive.content)
        assertEquals("[\"FRIDAY\"]", root["groups"]!!.jsonArray[0].jsonObject["schedule"]!!.jsonObject["days"].toString())
        val bookmark = root["bookmarks"]!!.jsonArray[0].jsonObject
        assertEquals(listOf("2158", "2267", "SURAH", "2"), listOf("start", "end", "kind", "groupId").map { bookmark[it]!!.jsonPrimitive.content })
        assertEquals("null", bookmark["schedule"].toString())
        val events = root["events"]!!.jsonObject
        assertEquals("""["ayahId","day","readAt","contextType","contextId","method","batchId"]""", events["columns"].toString())
        assertEquals("""[2158,"2026-11-13",1794686400000,"BOOKMARK",7,"TAP",null]""", events["rows"]!!.jsonArray[0].toString())
        assertEquals("""{"day":"2026-11-14","count":40,"note":"mosque","createdAt":1794700000000}""", root["tallies"]!!.jsonArray[0].toString())
    }

    @Test
    fun theExampleFileOfTheDesignDecodes() {
        val text = """
            {
              "format": "qintar-backup",
              "version": 2,
              "exportedAt": "2026-11-15T20:10:00Z",
              "app": { "versionName": "2.0.0", "versionCode": 200 },
              "prefs": { "theme": "DARK", "somethingNew": [1, 2, 3] },
              "khatms": [ { "id": 1, "name": "First", "startedDay": "2026-10-01", "dailyPages": 4.0,
                           "assumedCoveredUpTo": 0, "completedAt": null } ],
              "groups": [ { "id": 2, "name": "Friday", "description": null,
                            "schedule": { "days": ["FRIDAY"], "window": null }, "sortIndex": 1 } ],
              "bookmarks": [ { "id": 7, "start": 2158, "end": 2267, "kind": "SURAH", "label": null, "description": "Kahf",
                               "repeatTarget": 1, "groupId": 2, "schedule": null, "sortIndex": 3, "colour": "green" } ],
              "events": {
                "columns": ["ayahId", "day", "readAt", "contextType", "contextId", "method", "batchId"],
                "rows": [ [2158, "2026-11-13", 1794686400000, "BOOKMARK", 7, "TAP", null] ]
              },
              "tallies": [ { "day": "2026-11-14", "count": 40, "note": "mosque" } ],
              "addedInAFutureMinorVersion": { "ignored": true }
            }
        """.trimIndent()

        val data = decoded(text)

        assertEquals(UserPrefs(theme = ThemeMode.DARK), data.prefs)
        assertEquals(listOf(Khatm(1, "First", LocalDate.of(2026, 10, 1), 4.0f, 0, null)), data.khatms)
        assertEquals(listOf(BookmarkGroup(2, "Friday", null, Schedule(setOf(DayOfWeek.FRIDAY), null), 1, exportedAt)), data.groups)
        // The documented format has no timestamps for these; they become the export moment.
        assertEquals(listOf(Bookmark(7, AyahRange.of(2158, 2267), BookmarkKind.SURAH, null, "Kahf", 1, 2, null, 3, exportedAt, exportedAt)), data.bookmarks)
        assertEquals(listOf(NewReadEvent(AyahId(2158), day, Instant.ofEpochMilli(1794686400000), ReadContext.Bookmark(7), ReadMethod.TAP, null)), data.events)
        assertEquals(listOf(NewTally(LocalDate.of(2026, 11, 14), 40, "mosque", exportedAt)), data.tallies)
    }

    @Test
    fun eventColumnsMayComeInAnotherOrderOrWithExtras() {
        val text = """
            {"format":"qintar-backup","version":2,"exportedAt":"2026-11-15T20:10:00Z",
             "events":{"columns":["method","extra","contextId","contextType","readAt","day","ayahId"],
                       "rows":[["AUDIO",true,3,"KHATM",5,"2026-11-13",8]]}}
        """.trimIndent()

        assertEquals(
            listOf(NewReadEvent(AyahId(8), day, Instant.ofEpochMilli(5), ReadContext.Khatm(3), ReadMethod.AUDIO, null)),
            decoded(text).events,
        )
    }

    @Test
    fun aFileWithoutPreferencesDecodesWithNone() {
        assertNull(decoded("""{"format":"qintar-backup","version":2,"exportedAt":"2026-11-15T20:10:00Z"}""").prefs)
    }

    @Test
    fun unusablePreferenceValuesFallBackToDefaultsInsteadOfRefusingTheFile() {
        val text = """
            {"format":"qintar-backup","version":2,"exportedAt":"2026-11-15T20:10:00Z",
             "prefs":{"quranFontSp":500,"theme":"NEON","dayStartMinutes":"late","goalRank":"NONE","reminderTime":"25:99",
                      "playbackSpeed":-1,"reciterId":"","latinNumerals":true}}
        """.trimIndent()

        assertEquals(UserPrefs(latinNumerals = true), decoded(text).prefs)
    }

    @Test
    fun aBackupExportedFromTheDeviceCanBeRestoredOnAnEmptyOne() = runTest {
        val source = DomainFixture()
        val khatm = source.khatms.start("Khatm", dailyPages = 2f)
        val group = source.library.createGroup("Morning", schedule = Schedule.daily(TimeWindow(LocalTime.of(5, 0), LocalTime.of(8, 0))))
        val bookmark = source.library.create(
            io.github.mehrdad_abdi.qintar.domain.model.BookmarkDraft(AyahRange.of(1, 7), BookmarkKind.SURAH, groupId = group.id, repeatTarget = 2),
        )
        source.tracker.markRange(AyahRange.of(1, 20), ReadContext.Khatm(khatm.id), ReadMethod.BULK, io.github.mehrdad_abdi.qintar.domain.model.RangeMode.FILL)
        source.tracker.markRange(AyahRange.of(1, 7), ReadContext.Bookmark(bookmark.id), ReadMethod.TAP, io.github.mehrdad_abdi.qintar.domain.model.RangeMode.ADD)
        source.tracker.addTally(source.clock.today(), 40, "mosque")
        source.prefs.update { it.copy(theme = ThemeMode.DARK, dayStartMinutes = 0) }

        val file = codec.encode(source.backup.export("2.0.0", 200))

        val target = DomainFixture()
        target.backup.import(decoded(file), ImportMode.REPLACE)

        assertEquals(67, target.totals.today())
        assertEquals(source.totals.totals(null, null), target.totals.totals(null, null))
        assertEquals(source.bookmarkStore.bookmarks(), target.bookmarkStore.bookmarks())
        assertEquals(source.bookmarkStore.groups(), target.bookmarkStore.groups())
        assertEquals(source.khatmStore.khatms(), target.khatmStore.khatms())
        assertEquals(20, target.khatmProgress.snapshot(khatm.id)!!.coveredAyahs)
        assertEquals(ThemeMode.DARK, target.prefs.current.theme)

        // Importing the same file again as a merge adds no reading twice.
        val again = target.backup.import(decoded(file), ImportMode.MERGE)
        assertEquals(0, again.eventsAdded)
        assertEquals(67, target.totals.today())
    }

    // ---- files that must be refused ----

    @Test
    fun textThatIsNotJsonIsRefused() {
        for (text in listOf("", "   ", "PK\u0003\u0004 binary", "{\"format\":\"qintar-backup\",\"version\":2,", "<html><body>a page</body></html>", "[" .repeat(50_000))) {
            assertEquals(text.take(20), Reason.NOT_JSON, invalid(text).reason)
        }
    }

    @Test
    fun jsonThatIsNotABackupIsRefused() {
        // A bare word parses as a JSON literal, so it lands here rather than under NOT_JSON.
        for (text in listOf("[]", "42", "null", "\"qintar-backup\"", "backup", "{}", """{"format":"other-app","version":2}""", """{"version":2,"events":{}}""")) {
            assertEquals(text, Reason.NOT_A_BACKUP, invalid(text).reason)
        }
    }

    @Test
    fun aFileFromANewerAppIsRefusedWithItsVersion() {
        val result = invalid("""{"format":"qintar-backup","version":3,"exportedAt":"2027-01-01T00:00:00Z","shape":"unknown"}""")

        assertEquals(Reason.NEWER_VERSION, result.reason)
        assertEquals("3", result.detail)
    }

    @Test
    fun aBackupWithImpossibleContentIsRefusedNotThrown() {
        val head = """"format":"qintar-backup","version":2,"exportedAt":"2026-11-15T20:10:00Z""""
        fun events(row: String) = """{$head,"events":{"columns":["ayahId","day","readAt","contextType","contextId","method","batchId"],"rows":[$row]}}"""
        val broken = mapOf(
            "version missing" to """{"format":"qintar-backup","exportedAt":"2026-11-15T20:10:00Z"}""",
            "version not a number" to """{"format":"qintar-backup","version":"two","exportedAt":"2026-11-15T20:10:00Z"}""",
            "version zero" to """{"format":"qintar-backup","version":0,"exportedAt":"2026-11-15T20:10:00Z"}""",
            "exportedAt missing" to """{"format":"qintar-backup","version":2}""",
            "exportedAt unreadable" to """{"format":"qintar-backup","version":2,"exportedAt":"yesterday"}""",
            "ayah id out of range" to events("""[6237,"2026-11-13",1,"BROWSE",null,"TAP",null]"""),
            "ayah id zero" to events("""[0,"2026-11-13",1,"BROWSE",null,"TAP",null]"""),
            "ayah id not a number" to events("""["2:255","2026-11-13",1,"BROWSE",null,"TAP",null]"""),
            "impossible day" to events("""[1,"2026-02-30",1,"BROWSE",null,"TAP",null]"""),
            "unknown context" to events("""[1,"2026-11-13",1,"DREAM",null,"TAP",null]"""),
            "khatm without id" to events("""[1,"2026-11-13",1,"KHATM",null,"TAP",null]"""),
            "unknown method" to events("""[1,"2026-11-13",1,"BROWSE",null,"SHOUT",null]"""),
            "short row" to events("""[1,"2026-11-13"]"""),
            "row is an object" to events("""{"ayahId":1}"""),
            "nested cell" to events("""[[1],"2026-11-13",1,"BROWSE",null,"TAP",null]"""),
            "events without columns" to """{$head,"events":{"rows":[]}}""",
            "events missing a column" to """{$head,"events":{"columns":["ayahId","day"],"rows":[]}}""",
            "events not an object" to """{$head,"events":[1,2]}""",
            "bookmark range reversed" to """{$head,"bookmarks":[{"id":1,"start":9,"end":3,"kind":"RANGE","sortIndex":0}]}""",
            "bookmark ayah out of range" to """{$head,"bookmarks":[{"id":1,"start":1,"end":7000,"kind":"RANGE","sortIndex":0}]}""",
            "bookmark repeat target zero" to """{$head,"bookmarks":[{"id":1,"start":1,"end":7,"kind":"RANGE","repeatTarget":0,"sortIndex":0}]}""",
            "bookmark unknown kind" to """{$head,"bookmarks":[{"id":1,"start":1,"end":7,"kind":"HIZB","sortIndex":0}]}""",
            "bookmark without id" to """{$head,"bookmarks":[{"start":1,"end":7,"kind":"RANGE","sortIndex":0}]}""",
            "bookmarks not a list" to """{$head,"bookmarks":{"id":1}}""",
            "schedule without days" to """{$head,"groups":[{"id":1,"name":"g","schedule":{"days":[]},"sortIndex":0}]}""",
            "schedule unknown day" to """{$head,"groups":[{"id":1,"name":"g","schedule":{"days":["FUNDAY"]},"sortIndex":0}]}""",
            "window with equal ends" to """{$head,"groups":[{"id":1,"name":"g","schedule":{"days":["MONDAY"],"window":{"from":"05:00","to":"05:00"}},"sortIndex":0}]}""",
            "window unreadable" to """{$head,"groups":[{"id":1,"name":"g","schedule":{"days":["MONDAY"],"window":{"from":"dawn","to":"noon"}},"sortIndex":0}]}""",
            "group without name" to """{$head,"groups":[{"id":1,"sortIndex":0}]}""",
            "khatm assumed prefix too long" to """{$head,"khatms":[{"id":1,"name":"k","startedDay":"2026-01-01","assumedCoveredUpTo":7000}]}""",
            "khatm goal not positive" to """{$head,"khatms":[{"id":1,"name":"k","startedDay":"2026-01-01","dailyPages":0}]}""",
            "khatm day unreadable" to """{$head,"khatms":[{"id":1,"name":"k","startedDay":"01/01/2026"}]}""",
            "khatm entry is a number" to """{$head,"khatms":[5]}""",
            "tally without count" to """{$head,"tallies":[{"day":"2026-11-13"}]}""",
            "tally count too large" to """{$head,"tallies":[{"day":"2026-11-13","count":99999999999}]}""",
        )

        for ((what, text) in broken) {
            val result = try {
                codec.decode(text)
            } catch (failure: Throwable) {
                fail("$what: decode threw $failure")
                return
            }
            assertTrue("$what: accepted as $result", result is BackupDecodeResult.Invalid)
            assertEquals(what, Reason.MALFORMED, (result as BackupDecodeResult.Invalid).reason)
            assertTrue(what, result.detail.isNotBlank())
        }
    }

    // ---- v1 files ----

    /** As Gson pretty-printed v1's `BackupData` (BackupSerializer in v1). */
    private val v1File = """
        {
          "metadata": {
            "version": "1.0",
            "timestamp": "2025-10-05T21:14:09.123",
            "appVersion": "1.4.2",
            "deviceInfo": "Google Pixel 7 (Android 14)"
          },
          "bookmarks": [
            {
              "id": 15,
              "type": "RANGE",
              "startSurah": 2,
              "startAyah": 255,
              "endSurah": 2,
              "endAyah": 257,
              "description": "Ayat al-Kursi",
              "tags": ["Daily", "Protection"],
              "createdAt": 1727700000000,
              "updatedAt": 1727700500000
            },
            {
              "id": 16,
              "type": "PAGE",
              "startSurah": 1,
              "startAyah": 293,
              "endSurah": 1,
              "endAyah": 293,
              "description": "",
              "tags": [],
              "createdAt": 1727800000000,
              "updatedAt": 1727800000000
            }
          ],
          "readingActivity": [
            {
              "date": "2025-09-30",
              "totalAyahsRead": 3,
              "trackedAyahIds": ["15:2:255", "15:2:256", "-1:36:1"],
              "badgeLevel": "NONE"
            },
            {
              "date": "2025-10-01",
              "totalAyahsRead": 1,
              "trackedAyahIds": ["15:2:255"],
              "badgeLevel": "NONE"
            }
          ],
          "settings": {
            "reciterEdition": "ar.husary",
            "reciterBitrate": "128",
            "notificationSettings": { "enabled": true, "schedule": "DAILY", "time": "07:30", "days": [] },
            "theme": "DARK",
            "primaryColorHex": "#006B7D",
            "playbackSpeed": 1.25,
            "language": "FARSI"
          }
        }
    """.trimIndent()

    @Test
    fun aV1BackupFileDecodesToV1Rows() {
        val result = codec.decode(v1File)

        assertTrue("$result", result is BackupDecodeResult.LegacyV1)
        val v1 = (result as BackupDecodeResult.LegacyV1).data
        assertEquals(
            listOf(
                V1Bookmark(15, "RANGE", 2, 255, 2, 257, "Ayat al-Kursi", listOf("Daily", "Protection"), 1727700000000, 1727700500000),
                V1Bookmark(16, "PAGE", 1, 293, 1, 293, "", emptyList(), 1727800000000, 1727800000000),
            ),
            v1.bookmarks,
        )
        assertEquals(
            listOf(
                V1ReadingActivity("2025-09-30", 3, listOf("15:2:255", "15:2:256", "-1:36:1")),
                V1ReadingActivity("2025-10-01", 1, listOf("15:2:255")),
            ),
            v1.activities,
        )
        assertEquals(V1Settings("ar.husary", 128, true, LocalTime.of(7, 30), "DARK", 1.25f, "fa"), v1.settings)
        assertNull(v1.khatmLastPageRead)
    }

    @Test
    fun aV1BackupFileCanBeImportedThroughTheConverter() = runTest {
        val app = DomainFixture()
        val v1 = (codec.decode(v1File) as BackupDecodeResult.LegacyV1).data

        val conversion = LegacyV1Converter.convert(v1, app.index, app.clock.zone(), app.clock.today(), app.clock.now(), "Khatm")
        app.backup.import(conversion.data, ImportMode.MERGE)

        assertEquals(mapOf(LocalDate.of(2025, 9, 30) to 3, LocalDate.of(2025, 10, 1) to 1), app.totals.totals(null, null))
        assertEquals(listOf(BookmarkKind.RANGE, BookmarkKind.PAGE), app.bookmarkStore.bookmarks().map { it.kind })
        assertEquals(listOf("Daily"), app.bookmarkStore.groups().map { it.name })
        assertEquals(AppLanguage.PERSIAN, conversion.data.prefs!!.language)
        assertEquals(ThemeMode.DARK, conversion.data.prefs!!.theme)
    }

    @Test
    fun aSparseOrDamagedV1FileIsReadAsFarAsItGoesOrRefused() {
        val sparse = codec.decode("""{"metadata":{"version":"1.0"},"bookmarks":[{"type":"SURAH","startSurah":36}],"readingActivity":[{"date":"2025-09-30","trackedAyahIds":["1:1:1",null,7]}]}""")
        val v1 = (sparse as BackupDecodeResult.LegacyV1).data
        assertEquals("SURAH", v1.bookmarks.single().type)
        assertEquals(36, v1.bookmarks.single().endSurah)
        assertEquals(V1ReadingActivity("2025-09-30", 2, listOf("1:1:1", "7")), v1.activities.single())
        assertNull(v1.settings)

        assertEquals(V1Data(), (codec.decode("""{"version":"1.0"}""") as BackupDecodeResult.LegacyV1).data)

        for (text in listOf(
            """{"metadata":{"version":"1.0"},"bookmarks":[{"type":"AYAH"}]}""",
            """{"metadata":{"version":"1.0"},"bookmarks":"none"}""",
            """{"metadata":{"version":"1.0"},"readingActivity":[{"totalAyahsRead":4}]}""",
            """{"metadata":{"version":"1.0"},"settings":[]}""",
        )) {
            assertEquals(text, Reason.MALFORMED, invalid(text).reason)
        }
        assertEquals(Reason.NOT_A_BACKUP, invalid("""{"metadata":{"version":"7.0"},"bookmarks":[]}""").reason)
    }
}
