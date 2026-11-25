package io.github.mehrdad_abdi.qintar.domain.service

import io.github.mehrdad_abdi.qintar.domain.model.AyahId
import io.github.mehrdad_abdi.qintar.domain.model.Bookmark
import io.github.mehrdad_abdi.qintar.domain.model.BookmarkDraft
import io.github.mehrdad_abdi.qintar.domain.model.BookmarkGroup
import io.github.mehrdad_abdi.qintar.domain.model.BookmarkKind
import io.github.mehrdad_abdi.qintar.domain.model.DomainFailure
import io.github.mehrdad_abdi.qintar.domain.model.DomainFailure.InvalidInput.Field
import io.github.mehrdad_abdi.qintar.domain.model.Schedule
import io.github.mehrdad_abdi.qintar.domain.port.BookmarkStore
import io.github.mehrdad_abdi.qintar.domain.port.TransactionRunner
import io.github.mehrdad_abdi.qintar.domain.time.DayClock
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Outcome of tapping the star on an ayah (R-BMK-2). */
sealed interface StarResult {
    data class Added(val bookmark: Bookmark) : StarResult

    /** Removed; pass [bookmark] to [BookmarkLibrary.restore] if the user presses Undo. */
    data class Removed(val bookmark: Bookmark) : StarResult

    /**
     * Nothing was changed: the bookmark carries a label, description, group, schedule or a target
     * above 1, so the user must confirm. Call `toggleStar(ayah, confirmed = true)` to remove it.
     */
    data class NeedsConfirmation(val bookmark: Bookmark) : StarResult
}

/**
 * Creating, editing, ordering and deleting bookmarks and groups (§4.7). No operation here touches the
 * reading log: readings made in a bookmark outlive it.
 */
class BookmarkLibrary @Inject constructor(
    private val store: BookmarkStore,
    private val clock: DayClock,
    private val tx: TransactionRunner,
) {
    // ---- bookmarks ----

    suspend fun create(draft: BookmarkDraft): Bookmark = tx.inTransaction {
        val now = clock.now()
        val bookmark = Bookmark(
            id = 0,
            range = draft.range,
            kind = draft.kind,
            label = draft.label.clean(),
            description = validDescription(draft.description),
            repeatTarget = validTarget(draft.repeatTarget),
            groupId = validGroup(draft.groupId),
            schedule = draft.schedule,
            // New items go to the end of the custom order.
            sortIndex = (store.bookmarks().maxOfOrNull { it.sortIndex } ?: -1) + 1,
            createdAt = now,
            updatedAt = now,
        )
        bookmark.copy(id = store.insertBookmark(bookmark))
    }

    /** Applies the editor's draft to an existing bookmark; its place in the order and creation time stay. */
    suspend fun update(id: Long, draft: BookmarkDraft): Bookmark = tx.inTransaction {
        val current = store.bookmark(id) ?: throw DomainFailure.BookmarkNotFound(id)
        val updated = current.copy(
            range = draft.range,
            kind = draft.kind,
            label = draft.label.clean(),
            description = validDescription(draft.description),
            repeatTarget = validTarget(draft.repeatTarget),
            groupId = validGroup(draft.groupId),
            schedule = draft.schedule,
            updatedAt = clock.now(),
        )
        store.updateBookmark(updated)
        updated
    }

    /** Deletes and returns the bookmark so that an Undo can [restore] it. Its readings are kept. */
    suspend fun delete(id: Long): Bookmark = tx.inTransaction {
        val bookmark = store.bookmark(id) ?: throw DomainFailure.BookmarkNotFound(id)
        store.deleteBookmark(id)
        bookmark
    }

    /**
     * Puts a deleted bookmark back under its old id, so the readings made in it attach to it again.
     * If its group has been deleted meanwhile it comes back ungrouped.
     */
    suspend fun restore(bookmark: Bookmark) {
        tx.inTransaction {
            val groupId = bookmark.groupId?.takeIf { store.group(it) != null }
            store.insertBookmark(bookmark.copy(groupId = groupId))
        }
    }

    /** [groupId] null = Ungrouped. */
    suspend fun moveToGroup(id: Long, groupId: Long?) {
        tx.inTransaction {
            val bookmark = store.bookmark(id) ?: throw DomainFailure.BookmarkNotFound(id)
            store.updateBookmark(bookmark.copy(groupId = validGroup(groupId), updatedAt = clock.now()))
        }
    }

    /**
     * "Duplicate to another group": an independent bookmark with the same passage and settings. Counts
     * are per bookmark, so the copy starts unread — which is the point (§4.7, Decision).
     */
    suspend fun duplicateToGroup(id: Long, groupId: Long?): Bookmark {
        val source = store.bookmark(id) ?: throw DomainFailure.BookmarkNotFound(id)
        return create(
            BookmarkDraft(source.range, source.kind, source.label, source.description, source.repeatTarget, groupId, source.schedule),
        )
    }

    /** Custom order (drag): the given ids take sort indexes 0, 1, 2… Ids not listed keep theirs. */
    suspend fun reorder(orderedIds: List<Long>) {
        tx.inTransaction {
            orderedIds.forEachIndexed { position, id ->
                val bookmark = store.bookmark(id) ?: return@forEachIndexed
                if (bookmark.sortIndex != position) store.updateBookmark(bookmark.copy(sortIndex = position))
            }
        }
    }

    // ---- quick star ----

    /**
     * Every ayah that is in a bookmark, with the bookmark it opens: the first one created that
     * contains it. The reader's filled stars.
     */
    fun observeBookmarkedAyahs(): Flow<Map<AyahId, Long>> =
        store.observeBookmarks().map { list ->
            val map = HashMap<AyahId, Long>()
            list.sortedBy { it.id }.forEach { b -> b.range.forEach { ayah -> map.putIfAbsent(ayah, b.id) } }
            map as Map<AyahId, Long>
        }.distinctUntilChanged()

    /** Ayahs that have a single-ayah bookmark of kind AYAH: the filled stars. */
    fun observeStarred(): Flow<Set<AyahId>> =
        store.observeBookmarks().map { list -> list.filter(::isStar).mapTo(HashSet()) { it.range.start } }
            .distinctUntilChanged()

    suspend fun toggleStar(ayah: AyahId, confirmed: Boolean = false): StarResult = tx.inTransaction {
        val stars = store.bookmarks().filter { isStar(it) && it.range.start == ayah }
        // If the user has both a bare star and an elaborated bookmark on the same ayah, the star tap
        // removes the bare one, which is the one a star tap created.
        val existing = stars.firstOrNull(::isPlain) ?: stars.firstOrNull()
        when {
            existing == null -> StarResult.Added(create(BookmarkDrafts.ayah(ayah)))
            !isPlain(existing) && !confirmed -> StarResult.NeedsConfirmation(existing)
            else -> {
                store.deleteBookmark(existing.id)
                StarResult.Removed(existing)
            }
        }
    }

    private fun isStar(bookmark: Bookmark) = bookmark.kind == BookmarkKind.AYAH && bookmark.range.size == 1

    private fun isPlain(bookmark: Bookmark) = bookmark.label == null && bookmark.description == null &&
        bookmark.groupId == null && bookmark.schedule == null && bookmark.repeatTarget == 1

    // ---- groups ----

    suspend fun createGroup(name: String, description: String? = null, schedule: Schedule? = null): BookmarkGroup =
        tx.inTransaction {
            val group = BookmarkGroup(
                id = 0,
                name = validName(name),
                description = validDescription(description),
                schedule = validGroupSchedule(schedule),
                sortIndex = (store.groups().maxOfOrNull { it.sortIndex } ?: -1) + 1,
                createdAt = clock.now(),
            )
            group.copy(id = store.insertGroup(group))
        }

    suspend fun updateGroup(id: Long, name: String, description: String?, schedule: Schedule?): BookmarkGroup =
        tx.inTransaction {
            val current = store.group(id) ?: throw DomainFailure.GroupNotFound(id)
            val updated = current.copy(
                name = validName(name),
                description = validDescription(description),
                schedule = validGroupSchedule(schedule),
            )
            store.updateGroup(updated)
            updated
        }

    /**
     * @param deleteBookmarks true deletes the group's bookmarks with it; false moves them to Ungrouped.
     *   Readings are kept either way.
     */
    suspend fun deleteGroup(id: Long, deleteBookmarks: Boolean) {
        tx.inTransaction {
            if (deleteBookmarks) {
                store.bookmarks().filter { it.groupId == id }.forEach { store.deleteBookmark(it.id) }
            }
            store.deleteGroup(id)
        }
    }

    /**
     * "My order" of the Bookmarks list's top level (owner, 2026-11-25): groups and bookmarks without a
     * group take positions 0, 1, 2… in one sequence. A group's own bookmarks are ordered with [reorder].
     */
    suspend fun arrange(order: List<ArrangeKey>) {
        tx.inTransaction {
            order.forEachIndexed { position, key ->
                when (key) {
                    is ArrangeKey.Group -> store.group(key.id)?.takeIf { it.sortIndex != position }?.let { store.updateGroup(it.copy(sortIndex = position)) }
                    is ArrangeKey.Bookmark -> store.bookmark(key.id)?.takeIf { it.sortIndex != position }?.let { store.updateBookmark(it.copy(sortIndex = position)) }
                }
            }
        }
    }

    suspend fun reorderGroups(orderedIds: List<Long>) {
        tx.inTransaction {
            orderedIds.forEachIndexed { position, id ->
                val group = store.group(id) ?: return@forEachIndexed
                if (group.sortIndex != position) store.updateGroup(group.copy(sortIndex = position))
            }
        }
    }

    // ---- validation ----

    private fun String?.clean(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

    private fun validName(name: String): String =
        name.trim().ifEmpty { throw DomainFailure.InvalidInput(Field.NAME) }

    private fun validDescription(description: String?): String? {
        val text = description.clean() ?: return null
        if (text.length > Bookmark.MAX_DESCRIPTION) throw DomainFailure.InvalidInput(Field.DESCRIPTION)
        return text
    }

    private fun validTarget(target: Int): Int {
        if (target !in 1..ReadingTracker.MAX_COUNT) throw DomainFailure.InvalidInput(Field.REPEAT_TARGET)
        return target
    }

    private suspend fun validGroup(groupId: Long?): Long? {
        if (groupId != null && store.group(groupId) == null) throw DomainFailure.GroupNotFound(groupId)
        return groupId
    }

    /** `once` describes one bookmark being finished; a group has no such state. */
    private fun validGroupSchedule(schedule: Schedule?): Schedule? {
        if (schedule?.once == true) throw DomainFailure.InvalidInput(Field.SCHEDULE)
        return schedule
    }
}

/** An entry of the Bookmarks list's top level, for [BookmarkLibrary.arrange]. */
sealed interface ArrangeKey {
    val id: Long

    data class Group(override val id: Long) : ArrangeKey

    data class Bookmark(override val id: Long) : ArrangeKey
}
