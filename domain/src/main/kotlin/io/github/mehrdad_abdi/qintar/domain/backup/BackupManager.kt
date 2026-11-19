package io.github.mehrdad_abdi.qintar.domain.backup

import io.github.mehrdad_abdi.qintar.domain.model.ContextKind
import io.github.mehrdad_abdi.qintar.domain.model.DomainFailure
import io.github.mehrdad_abdi.qintar.domain.model.NewReadEvent
import io.github.mehrdad_abdi.qintar.domain.model.NewTally
import io.github.mehrdad_abdi.qintar.domain.model.ReadContext
import io.github.mehrdad_abdi.qintar.domain.model.ReadMethod
import io.github.mehrdad_abdi.qintar.domain.model.AyahId
import io.github.mehrdad_abdi.qintar.domain.model.UserPrefs
import io.github.mehrdad_abdi.qintar.domain.port.BookmarkStore
import io.github.mehrdad_abdi.qintar.domain.port.KhatmStore
import io.github.mehrdad_abdi.qintar.domain.port.PrefsStore
import io.github.mehrdad_abdi.qintar.domain.port.ReadLogStore
import io.github.mehrdad_abdi.qintar.domain.port.TransactionRunner
import io.github.mehrdad_abdi.qintar.domain.service.ReadingLogImporter
import io.github.mehrdad_abdi.qintar.domain.time.DayClock
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/**
 * Export, validation and import of backups (§7.10). A v1 file or database reaches [import] as the
 * [BackupData] produced by [LegacyV1Converter], so there is one import path.
 */
class BackupManager @Inject constructor(
    private val log: ReadLogStore,
    private val khatms: KhatmStore,
    private val bookmarks: BookmarkStore,
    private val prefs: PrefsStore,
    private val importer: ReadingLogImporter,
    private val clock: DayClock,
    private val tx: TransactionRunner,
) {
    suspend fun export(appVersionName: String? = null, appVersionCode: Int? = null): BackupData = tx.inTransaction {
        BackupData(
            exportedAt = clock.now(),
            appVersionName = appVersionName,
            appVersionCode = appVersionCode,
            prefs = prefs.prefs.first(),
            khatms = khatms.khatms(),
            groups = bookmarks.groups(),
            bookmarks = bookmarks.bookmarks(),
            events = log.allEvents().map { NewReadEvent(it.ayahId, it.day, it.readAt, it.context, it.method, it.batchId) },
            tallies = log.allTallies().map { NewTally(it.day, it.count, it.note, it.createdAt) },
        )
    }

    /** Validates a decoded file and summarises it. Reads nothing from the device and changes nothing. */
    fun preview(data: BackupData): BackupPreview {
        val today = clock.today()
        val problems = ArrayList<BackupProblem>()

        if (data.version > BackupData.CURRENT_VERSION) problems += BackupProblem.UnsupportedVersion(data.version)

        val days = data.events.map { it.day } + data.tallies.map { it.day }
        val future = days.filter { it > today }
        if (future.isNotEmpty()) problems += BackupProblem.FutureDays(future.size, future.max())

        fun duplicates(ids: List<Long>, kind: BackupProblem.DuplicateId.Kind) {
            ids.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
                .forEach { problems += BackupProblem.DuplicateId(kind, it) }
        }
        duplicates(data.khatms.map { it.id }, BackupProblem.DuplicateId.Kind.KHATM)
        duplicates(data.groups.map { it.id }, BackupProblem.DuplicateId.Kind.GROUP)
        duplicates(data.bookmarks.map { it.id }, BackupProblem.DuplicateId.Kind.BOOKMARK)

        val groupIds = data.groups.mapTo(HashSet()) { it.id }
        for (bookmark in data.bookmarks) {
            val groupId = bookmark.groupId ?: continue
            if (groupId !in groupIds) problems += BackupProblem.UnknownGroup(bookmark.id, groupId)
        }
        data.tallies.filter { it.count <= 0 }.forEach { problems += BackupProblem.InvalidTally(it.day, it.count) }

        val summary = BackupSummary(
            firstDay = days.minOrNull(),
            lastDay = days.maxOrNull(),
            events = data.events.size,
            tallies = data.tallies.size,
            totalAyahs = data.events.size + data.tallies.sumOf { it.count },
            bookmarks = data.bookmarks.size,
            groups = data.groups.size,
            khatms = data.khatms.size,
        )
        return BackupPreview(summary, problems)
    }

    /**
     * Applies a backup in one transaction: either all of it is written or nothing changes.
     * Throws [DomainFailure.InvalidBackup] without writing when [preview] reports a problem.
     */
    suspend fun import(data: BackupData, mode: ImportMode): ImportResult {
        val problems = preview(data).problems
        if (problems.isNotEmpty()) throw DomainFailure.InvalidBackup(problems)
        return tx.inTransaction {
            when (mode) {
                ImportMode.REPLACE -> replace(data)
                ImportMode.MERGE -> merge(data)
            }
        }
    }

    private suspend fun replace(data: BackupData): ImportResult {
        importer.clearLog()
        bookmarks.deleteAll()
        khatms.deleteAll()

        // Ids are kept, so event contexts need no translation.
        data.khatms.forEach { khatms.insert(it, data.exportedAt) }
        data.groups.forEach { bookmarks.insertGroup(it) }
        data.bookmarks.forEach { bookmarks.insertBookmark(it) }
        importer.importRecords(data.events, data.tallies)

        data.prefs?.let { imported -> prefs.update { current -> imported.keepingDeviceState(current) } }

        return ImportResult(
            eventsAdded = data.events.size,
            eventsSkipped = 0,
            talliesAdded = data.tallies.size,
            talliesSkipped = 0,
            bookmarksAdded = data.bookmarks.size,
            groupsAdded = data.groups.size,
            khatmsAdded = data.khatms.size,
        )
    }

    /**
     * Adds the file to what is on the device. A khatm, group or bookmark of the file that the device
     * already holds with the same content (a second merge of the same file, a device's own export, a
     * carry-over from v1 that is repeated) is not added again: its readings attach to the one that is
     * there. Each row on the device stands in for at most one row of the file.
     */
    private suspend fun merge(data: BackupData): ImportResult {
        var reused = 0

        val freeKhatms = khatms.khatms().toMutableList()
        val khatmIds = data.khatms.associate { khatm ->
            // The start day is left out: a carry-over from v1 that is repeated on a later day dates a
            // khatm without readings "today", and it is still the same khatm.
            val same = freeKhatms.firstOrNull { it.copy(id = khatm.id, startedDay = khatm.startedDay) == khatm }
            khatm.id to if (same != null) {
                freeKhatms.remove(same)
                reused++
                same.id
            } else {
                khatms.insert(khatm.copy(id = 0), data.exportedAt)
            }
        }
        val khatmsAdded = data.khatms.size - reused

        val deviceGroups = bookmarks.groups()
        val freeGroups = deviceGroups.toMutableList()
        var groupSort = (deviceGroups.maxOfOrNull { it.sortIndex } ?: -1) + 1
        var groupsAdded = 0
        val groupIds = data.groups.sortedBy { it.sortIndex }.associate { group ->
            val same = freeGroups.firstOrNull { it.name == group.name && it.description == group.description && it.schedule == group.schedule }
            group.id to if (same != null) {
                freeGroups.remove(same)
                reused++
                same.id
            } else {
                groupsAdded++
                bookmarks.insertGroup(group.copy(id = 0, sortIndex = groupSort++))
            }
        }

        // Imported bookmarks go after the device's own in the custom order, keeping their relative order.
        val deviceBookmarks = bookmarks.bookmarks()
        val freeBookmarks = deviceBookmarks.toMutableList()
        var bookmarkSort = (deviceBookmarks.maxOfOrNull { it.sortIndex } ?: -1) + 1
        var bookmarksAdded = 0
        val bookmarkIds = data.bookmarks.sortedBy { it.sortIndex }.associate { bookmark ->
            val groupId = bookmark.groupId?.let(groupIds::getValue)
            val same = freeBookmarks.firstOrNull {
                it.range == bookmark.range && it.kind == bookmark.kind && it.label == bookmark.label &&
                    it.description == bookmark.description && it.repeatTarget == bookmark.repeatTarget &&
                    it.groupId == groupId && it.schedule == bookmark.schedule
            }
            bookmark.id to if (same != null) {
                freeBookmarks.remove(same)
                reused++
                same.id
            } else {
                bookmarksAdded++
                bookmarks.insertBookmark(bookmark.copy(id = 0, groupId = groupId, sortIndex = bookmarkSort++))
            }
        }

        fun remap(context: ReadContext): ReadContext = when (context) {
            is ReadContext.Khatm -> ReadContext.Khatm(khatmIds[context.id] ?: orphan(context.id))
            is ReadContext.Bookmark -> ReadContext.Bookmark(bookmarkIds[context.id] ?: orphan(context.id))
            else -> context
        }

        // "Identical" for a merge is (ayah, moment, context type, method) (§7.10). It is compared as a
        // multiset, not a set: one bulk action legitimately writes several events that agree on all
        // four (three full readings in one tap), and a merge must neither collapse them nor, when the
        // same file is imported twice, double them.
        val existing = log.allEvents().groupingBy { EventKey(it.ayahId, it.readAt, it.context.kind, it.method) }
            .eachCountTo(HashMap())
        val newEvents = ArrayList<NewReadEvent>()
        for (event in data.events) {
            val key = EventKey(event.ayahId, event.readAt, event.context.kind, event.method)
            val alreadyThere = existing[key] ?: 0
            if (alreadyThere > 0) {
                existing[key] = alreadyThere - 1
            } else {
                newEvents += event.copy(context = remap(event.context))
            }
        }

        // The file format gives tallies no identity beyond day, count and note; the same multiset rule
        // keeps a re-import from doubling them.
        val existingTallies = log.allTallies().groupingBy { TallyKey(it.day, it.count, it.note) }.eachCountTo(HashMap())
        val newTallies = ArrayList<NewTally>()
        for (tally in data.tallies) {
            val key = TallyKey(tally.day, tally.count, tally.note)
            val alreadyThere = existingTallies[key] ?: 0
            if (alreadyThere > 0) existingTallies[key] = alreadyThere - 1 else newTallies += tally
        }

        importer.importRecords(newEvents, newTallies)

        return ImportResult(
            eventsAdded = newEvents.size,
            eventsSkipped = data.events.size - newEvents.size,
            talliesAdded = newTallies.size,
            talliesSkipped = data.tallies.size - newTallies.size,
            bookmarksAdded = bookmarksAdded,
            groupsAdded = groupsAdded,
            khatmsAdded = khatmsAdded,
            itemsSkipped = reused,
        )
    }

    /**
     * The id given to a context whose bookmark or khatm is not in the file (it was deleted before the
     * export). It must not collide with any row on this device, now or later, or old readings would
     * show up inside an unrelated bookmark; generated ids are positive, so a negative id never matches.
     */
    private fun orphan(id: Long): Long = if (id > 0) -id else id

    /** Preferences describe the user; these three describe this installation and survive a restore. */
    private fun UserPrefs.keepingDeviceState(current: UserPrefs): UserPrefs = copy(
        onboardingDone = current.onboardingDone,
        legacyMigrationState = current.legacyMigrationState,
        lastBrowseAyahId = current.lastBrowseAyahId,
    )

    private data class EventKey(val ayahId: AyahId, val readAt: Instant, val kind: ContextKind, val method: ReadMethod)

    private data class TallyKey(val day: LocalDate, val count: Int, val note: String?)
}
