package io.github.mehrdad_abdi.qintar.feature.bookmarks

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.mehrdad_abdi.qintar.R
import io.github.mehrdad_abdi.qintar.di.IoDispatcher
import io.github.mehrdad_abdi.qintar.domain.model.AyahId
import io.github.mehrdad_abdi.qintar.domain.model.AyahRange
import io.github.mehrdad_abdi.qintar.domain.model.AyahRef
import io.github.mehrdad_abdi.qintar.domain.model.Bookmark
import io.github.mehrdad_abdi.qintar.domain.model.BookmarkDraft
import io.github.mehrdad_abdi.qintar.domain.model.BookmarkKind
import io.github.mehrdad_abdi.qintar.domain.model.DomainFailure
import io.github.mehrdad_abdi.qintar.domain.model.DomainFailure.InvalidInput.Field
import io.github.mehrdad_abdi.qintar.domain.model.Quran
import io.github.mehrdad_abdi.qintar.domain.model.Schedule
import io.github.mehrdad_abdi.qintar.domain.model.SurahInfo
import io.github.mehrdad_abdi.qintar.domain.port.PrefsStore
import io.github.mehrdad_abdi.qintar.domain.port.QuranTextSource
import io.github.mehrdad_abdi.qintar.domain.quran.QuranIndex
import io.github.mehrdad_abdi.qintar.domain.quran.PassageSummary
import io.github.mehrdad_abdi.qintar.domain.quran.ayahId
import io.github.mehrdad_abdi.qintar.domain.quran.summary
import io.github.mehrdad_abdi.qintar.domain.service.Availability
import io.github.mehrdad_abdi.qintar.domain.service.AyahTextFormatter
import io.github.mehrdad_abdi.qintar.domain.service.BookmarkLibrary
import io.github.mehrdad_abdi.qintar.domain.service.ReadingTracker
import io.github.mehrdad_abdi.qintar.ui.common.ScheduleForm
import io.github.mehrdad_abdi.qintar.ui.common.parseWholeNumber
import io.github.mehrdad_abdi.qintar.ui.common.weekOrder
import io.github.mehrdad_abdi.qintar.ui.navigation.BookmarkEditorRoute
import java.time.DayOfWeek
import java.time.temporal.WeekFields
import java.util.Locale
import javax.inject.Provider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class EditorPhase { LOADING, READY, NOT_FOUND }

/** The fields of the editor an error can belong to. */
enum class EditorField { FROM_AYAH, TO_AYAH, PAGE, JUZ, DESCRIPTION, REPEAT, SCHEDULE }

/** Why a field was refused; the screen shows [message] under it. */
enum class EditorProblem(@StringRes val message: Int) {
    NUMBER_MISSING(R.string.bm_err_number),
    AYAH_OUT_OF_RANGE(R.string.bm_err_ayah),
    END_BEFORE_START(R.string.bm_err_end_before_start),
    PAGE_OUT_OF_RANGE(R.string.bm_err_page),
    JUZ_OUT_OF_RANGE(R.string.bm_err_juz),
    DESCRIPTION_TOO_LONG(R.string.bm_err_description),
    REPEAT_INVALID(R.string.bm_err_repeat),
    SCHEDULE_INVALID(R.string.bm_err_schedule),
}

/** A group in the editor's group choice, with the schedule a grouped bookmark would inherit. */
data class GroupOption(val id: Long, val name: String, val schedule: Schedule?)

/** One ayah of the preview: [text] is formatted exactly as the reader formats it. */
data class PreviewAyah(
    val id: AyahId,
    val surah: Int,
    val number: Int,
    val text: String,
    /** The Bismillah stands before it: ayah 1 of every surah but al-Fatiha (where it is ayah 1) and at-Tawba. */
    val basmala: Boolean = false,
)

/**
 * The first ayahs of the passage; for a long one, how many are left out and then its [last] ayah, so
 * the preview shows from where to where.
 */
data class PassagePreview(val ayahs: List<PreviewAyah> = emptyList(), val more: Int = 0, val last: PreviewAyah? = null)

data class BookmarkEditorUiState(
    val phase: EditorPhase = EditorPhase.LOADING,
    val isNew: Boolean = true,
    val surahs: List<SurahInfo> = emptyList(),
    val kind: BookmarkKind = BookmarkKind.AYAH,
    // The position as typed. Surah and ayah: ayah / range start; toSurah and toAyahText: range end.
    val surah: Int = 1,
    val ayahText: String = "1",
    val toSurah: Int = 1,
    val toAyahText: String = "1",
    val pageText: String = "1",
    val juzText: String = "1",
    /** The passage the position stands for; null while a number is invalid. */
    val range: AyahRange? = null,
    val summary: PassageSummary? = null,
    val preview: PassagePreview = PassagePreview(),
    val fontSp: Int = 28,
    val label: String = "",
    val description: String = "",
    val repeatTarget: Int = 1,
    val groupId: Long? = null,
    val groups: List<GroupOption> = emptyList(),
    val schedule: ScheduleForm = ScheduleForm(),
    val weekDays: List<DayOfWeek> = weekOrder(DayOfWeek.MONDAY),
    val errors: Map<EditorField, EditorProblem> = emptyMap(),
    /** Set once the bookmark is saved: the screen leaves. */
    val savedId: Long? = null,
) {
    /** The kinds offered. A juz is not (owner, 2026-11-22), except to show an existing juz bookmark. */
    val kinds: List<BookmarkKind>
        get() = BookmarkKind.entries.filter { it != BookmarkKind.JUZ || kind == BookmarkKind.JUZ }

    /** The schedule a grouped bookmark falls back to when it has none of its own. */
    val inheritedSchedule: Schedule? get() = groups.firstOrNull { it.id == groupId }?.schedule
}

/**
 * Add or edit a bookmark (§5.6): kind and passage, a live preview of the actual text, label,
 * description, repeat target, group and schedule. [route] may carry a passage to start from.
 */
@HiltViewModel(assistedFactory = BookmarkEditorViewModel.Factory::class)
class BookmarkEditorViewModel(
    private val route: BookmarkEditorRoute,
    private val index: Provider<QuranIndex>,
    private val text: QuranTextSource,
    private val library: BookmarkLibrary,
    private val availability: Availability,
    private val prefs: PrefsStore,
    private val io: CoroutineDispatcher,
    /** The device's first weekday, for the "System" week-start setting. */
    private val systemFirstDay: () -> DayOfWeek,
) : ViewModel() {

    @AssistedInject
    constructor(
        @Assisted route: BookmarkEditorRoute,
        index: Provider<QuranIndex>,
        text: QuranTextSource,
        library: BookmarkLibrary,
        availability: Availability,
        prefs: PrefsStore,
        @IoDispatcher io: CoroutineDispatcher,
    ) : this(route, index, text, library, availability, prefs, io, { WeekFields.of(Locale.getDefault()).firstDayOfWeek })

    @AssistedFactory
    interface Factory {
        fun create(route: BookmarkEditorRoute): BookmarkEditorViewModel
    }

    private val _state = MutableStateFlow(BookmarkEditorUiState(isNew = route.bookmarkId == BookmarkEditorRoute.NEW))
    val state: StateFlow<BookmarkEditorUiState> = _state.asStateFlow()

    private lateinit var quran: QuranIndex
    private var breakAtPauseMarks = true
    private var previewJob: Job? = null

    init {
        viewModelScope.launch { open() }
    }

    private suspend fun open() {
        quran = withContext(io) { index.get() }
        val userPrefs = prefs.prefs.first()
        breakAtPauseMarks = userPrefs.breakAtPauseMarks
        val board = availability.board()
        val groups = board.groups.map { GroupOption(it.group.id, it.group.name, it.group.schedule) }
        val base = _state.value.copy(
            surahs = quran.surahs,
            groups = groups,
            fontSp = userPrefs.quranFontSp,
            weekDays = weekOrder(userPrefs.weekStart.firstDay(systemFirstDay())),
        )
        if (route.bookmarkId != BookmarkEditorRoute.NEW) {
            val existing = board.state(route.bookmarkId)?.bookmark
            if (existing == null) {
                _state.value = base.copy(phase = EditorPhase.NOT_FOUND)
                return
            }
            _state.value = base.copy(phase = EditorPhase.READY, isNew = false).fromBookmark(existing).seeded(existing.range)
        } else {
            val draft = draftRange()
            val kind = draftKind(draft)
            _state.value = base.copy(
                phase = EditorPhase.READY,
                kind = kind,
                groupId = route.draftGroupId.takeIf { id -> id != BookmarkEditorRoute.NEW && groups.any { it.id == id } },
            ).seeded(draft)
        }
        refresh()
    }

    private fun draftRange(): AyahRange {
        val first = route.draftFirstAyahId
        val last = route.draftLastAyahId
        if (first !in AyahId.FIRST.value..AyahId.LAST.value) return AyahRange.single(AyahId.FIRST)
        val end = if (last in first..AyahId.LAST.value) last else first
        return AyahRange(AyahId(first), AyahId(end))
    }

    private fun draftKind(range: AyahRange): BookmarkKind =
        BookmarkKind.entries.firstOrNull { it.name == route.draftKind }
            ?: if (range.size == 1) BookmarkKind.AYAH else BookmarkKind.RANGE

    private fun BookmarkEditorUiState.fromBookmark(bookmark: Bookmark) = copy(
        kind = bookmark.kind,
        label = bookmark.label.orEmpty(),
        description = bookmark.description.orEmpty(),
        repeatTarget = bookmark.repeatTarget,
        groupId = bookmark.groupId,
        schedule = ScheduleForm.of(bookmark.schedule),
    )

    /** Fills every position field from [range], so that switching kind keeps the place. */
    private fun BookmarkEditorUiState.seeded(range: AyahRange): BookmarkEditorUiState {
        val start = quran.ref(range.start)
        val end = quran.ref(range.end)
        return copy(
            surah = start.surah,
            ayahText = start.ayah.toString(),
            toSurah = end.surah,
            toAyahText = end.ayah.toString(),
            pageText = quran.pageOf(range.start).toString(),
            juzText = quran.juzOf(range.start).toString(),
        ).resolved()
    }

    // ---- passage ---------------------------------------------------------------------------------

    fun onKind(kind: BookmarkKind) {
        val current = _state.value
        // Going to a range from one ayah (or back) keeps the place; a surah, page or juz is whole.
        val from = current.range
        val next = current.copy(kind = kind)
        _state.value = (if (from != null) next.seeded(from) else next.resolved())
        refresh()
    }

    /** A range stays inside one surah (owner, 2026-11-22): choosing the surah sets both ends. */
    fun onSurah(number: Int) = change {
        val moved = copy(surah = number, ayahText = clampAyah(number, ayahText))
        if (kind == BookmarkKind.RANGE) moved.copy(toSurah = number, toAyahText = clampAyah(number, toAyahText)) else moved
    }

    fun onAyahText(value: String) = change { copy(ayahText = value) }

    fun onToSurah(number: Int) = change { copy(toSurah = number, toAyahText = clampAyah(number, toAyahText)) }

    fun onToAyahText(value: String) = change { copy(toAyahText = value) }

    fun onPageText(value: String) = change { copy(pageText = value) }

    fun onJuzText(value: String) = change { copy(juzText = value) }

    private fun clampAyah(surah: Int, text: String): String {
        val n = parseWholeNumber(text)
        return if (n != null && n in 1..quran.surah(surah).ayahCount) text else "1"
    }

    private inline fun change(block: BookmarkEditorUiState.() -> BookmarkEditorUiState) {
        _state.update { it.block().resolved() }
        refresh()
    }

    /** The passage and the position errors that the typed numbers give. */
    private fun BookmarkEditorUiState.resolved(): BookmarkEditorUiState {
        val problems = HashMap<EditorField, EditorProblem>()
        // Keep the errors of the other fields: description, repeat and schedule are checked on their own.
        errors.filterKeys { it in OTHER_FIELDS }.forEach { (field, problem) -> problems[field] = problem }

        fun ayah(surah: Int, text: String, field: EditorField): AyahId? {
            val number = parseWholeNumber(text)
            if (number == null) {
                problems[field] = EditorProblem.NUMBER_MISSING
                return null
            }
            val id = quran.ayahId(surah, number)
            if (id == null) problems[field] = EditorProblem.AYAH_OUT_OF_RANGE
            return id
        }

        val range: AyahRange? = when (kind) {
            BookmarkKind.AYAH -> ayah(surah, ayahText, EditorField.FROM_AYAH)?.let(AyahRange::single)
            BookmarkKind.RANGE -> {
                val from = ayah(surah, ayahText, EditorField.FROM_AYAH)
                val to = ayah(toSurah, toAyahText, EditorField.TO_AYAH)
                if (from != null && to != null && to < from) {
                    problems[EditorField.TO_AYAH] = EditorProblem.END_BEFORE_START
                    null
                } else if (from != null && to != null) {
                    AyahRange(from, to)
                } else {
                    null
                }
            }
            BookmarkKind.SURAH -> quran.surahRange(surah)
            BookmarkKind.PAGE -> {
                val page = parseWholeNumber(pageText)
                when {
                    page == null -> null.also { problems[EditorField.PAGE] = EditorProblem.NUMBER_MISSING }
                    page !in 1..Quran.PAGE_COUNT -> null.also { problems[EditorField.PAGE] = EditorProblem.PAGE_OUT_OF_RANGE }
                    else -> quran.pageRange(page)
                }
            }
            BookmarkKind.JUZ -> {
                val juz = parseWholeNumber(juzText)
                when {
                    juz == null -> null.also { problems[EditorField.JUZ] = EditorProblem.NUMBER_MISSING }
                    juz !in 1..Quran.JUZ_COUNT -> null.also { problems[EditorField.JUZ] = EditorProblem.JUZ_OUT_OF_RANGE }
                    else -> quran.juzRange(juz)
                }
            }
        }
        return copy(range = range, summary = range?.let(quran::summary), errors = problems)
    }

    /** Loads the first ayahs of the passage; a long passage is never loaded whole. */
    private fun refresh() {
        previewJob?.cancel()
        val range = _state.value.range
        if (range == null) {
            _state.update { it.copy(preview = PassagePreview()) }
            return
        }
        previewJob = viewModelScope.launch {
            val shown = AyahRange(range.start, AyahId(minOf(range.end.value, range.start.value + PREVIEW_AYAHS - 1)))
            val hasLast = range.end != shown.end
            val ayahs = withContext(io) { text.ayahs(shown) + if (hasLast) text.ayahs(AyahRange.single(range.end)) else emptyList() }
            val formatted = ayahs.map { ayah ->
                val ref: AyahRef = quran.ref(ayah.id)
                PreviewAyah(
                    ayah.id, ref.surah, ref.ayah, AyahTextFormatter.format(ayah.text, ref.ayah, breakAtPauseMarks),
                    basmala = ref.ayah == 1 && quran.surah(ref.surah).hasBasmala,
                )
            }
            val preview = PassagePreview(
                ayahs = if (hasLast) formatted.dropLast(1) else formatted,
                more = range.size - shown.size - (if (hasLast) 1 else 0),
                last = if (hasLast) formatted.lastOrNull() else null,
            )
            _state.update { if (it.range == range) it.copy(preview = preview) else it }
        }
    }

    // ---- the rest of the form ----------------------------------------------------------------------

    fun onLabel(value: String) = _state.update { it.copy(label = value) }

    fun onDescription(value: String) = _state.update {
        it.copy(description = value).withProblem(EditorField.DESCRIPTION, EditorProblem.DESCRIPTION_TOO_LONG.takeIf { value.trim().length > Bookmark.MAX_DESCRIPTION })
    }

    fun onRepeatTarget(value: Int) = _state.update {
        it.copy(repeatTarget = value).withProblem(EditorField.REPEAT, EditorProblem.REPEAT_INVALID.takeIf { value !in 1..ReadingTracker.MAX_COUNT })
    }

    /** [groupId] null = Ungrouped. */
    fun onGroup(groupId: Long?) = _state.update { it.copy(groupId = groupId) }

    fun onSchedule(form: ScheduleForm) = _state.update {
        it.copy(schedule = form).withProblem(EditorField.SCHEDULE, EditorProblem.SCHEDULE_INVALID.takeIf { form.problem != null })
    }

    private fun BookmarkEditorUiState.withProblem(field: EditorField, problem: EditorProblem?): BookmarkEditorUiState =
        copy(errors = if (problem == null) errors - field else errors + (field to problem))

    // ---- saving --------------------------------------------------------------------------------------

    fun onSave() {
        var current = _state.value
        if (current.phase != EditorPhase.READY) return
        if (current.description.trim().length > Bookmark.MAX_DESCRIPTION) {
            current = current.withProblem(EditorField.DESCRIPTION, EditorProblem.DESCRIPTION_TOO_LONG)
        }
        if (current.repeatTarget !in 1..ReadingTracker.MAX_COUNT) {
            current = current.withProblem(EditorField.REPEAT, EditorProblem.REPEAT_INVALID)
        }
        if (current.schedule.problem != null) current = current.withProblem(EditorField.SCHEDULE, EditorProblem.SCHEDULE_INVALID)
        val range = current.range
        if (range == null || current.errors.isNotEmpty()) {
            _state.value = current
            return
        }
        val draft = BookmarkDraft(
            range = range,
            kind = current.kind,
            label = current.label,
            description = current.description,
            repeatTarget = current.repeatTarget,
            groupId = current.groupId,
            schedule = current.schedule.toSchedule(),
        )
        viewModelScope.launch {
            try {
                val saved = if (current.isNew) library.create(draft) else library.update(route.bookmarkId, draft)
                _state.update { it.copy(savedId = saved.id) }
            } catch (failure: DomainFailure.InvalidInput) {
                val field = when (failure.field) {
                    Field.DESCRIPTION -> EditorField.DESCRIPTION to EditorProblem.DESCRIPTION_TOO_LONG
                    Field.REPEAT_TARGET -> EditorField.REPEAT to EditorProblem.REPEAT_INVALID
                    else -> EditorField.SCHEDULE to EditorProblem.SCHEDULE_INVALID
                }
                _state.update { it.withProblem(field.first, field.second) }
            } catch (_: DomainFailure.BookmarkNotFound) {
                _state.update { it.copy(phase = EditorPhase.NOT_FOUND) }
            } catch (_: DomainFailure.GroupNotFound) {
                // The group was deleted while the editor was open.
                _state.update { it.copy(groupId = null, groups = it.groups.filter { g -> g.id != current.groupId }) }
            }
        }
    }

    companion object {
        /** How many ayahs the preview shows before "… n more". */
        const val PREVIEW_AYAHS = 6

        private val OTHER_FIELDS = setOf(EditorField.DESCRIPTION, EditorField.REPEAT, EditorField.SCHEDULE)
    }
}
