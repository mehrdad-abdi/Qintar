package io.github.mehrdad_abdi.qintar.feature.read

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.mehrdad_abdi.qintar.di.IoDispatcher
import io.github.mehrdad_abdi.qintar.domain.model.AyahId
import io.github.mehrdad_abdi.qintar.domain.model.AyahRange
import io.github.mehrdad_abdi.qintar.domain.model.AyahRef
import io.github.mehrdad_abdi.qintar.domain.model.Quran
import io.github.mehrdad_abdi.qintar.domain.model.SurahInfo
import io.github.mehrdad_abdi.qintar.domain.port.PrefsStore
import io.github.mehrdad_abdi.qintar.domain.quran.QuranIndex
import io.github.mehrdad_abdi.qintar.domain.service.GoToParser
import io.github.mehrdad_abdi.qintar.domain.time.DayClock
import io.github.mehrdad_abdi.qintar.domain.service.GoToResult
import io.github.mehrdad_abdi.qintar.domain.service.KhatmProgress
import io.github.mehrdad_abdi.qintar.feature.khatm.KhatmListItem
import io.github.mehrdad_abdi.qintar.feature.khatm.toListItem
import io.github.mehrdad_abdi.qintar.ui.common.GoToProblem
import io.github.mehrdad_abdi.qintar.ui.navigation.ReaderRoute
import javax.inject.Inject
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flatMapLatest
import io.github.mehrdad_abdi.qintar.domain.port.ReadLogStore
import javax.inject.Provider
import kotlin.random.Random
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ReadTab { SURAH, JUZ, PAGE }

/** A surah in the index with the share of its ayahs read today; null = none read today. */
data class SurahRowState(val surah: SurahInfo, val coverage: Float? = null)

/** A juz in the index with the ayah it starts at. */
data class JuzRowState(val juz: Int, val startSurah: SurahInfo, val start: AyahRef)

/** "Continue browsing": where the last Browse reading was. */
data class ContinueBrowsing(val ayahId: AyahId, val surah: SurahInfo, val ayah: Int, val page: Int)

data class ReadUiState(
    val loading: Boolean = true,
    val continueBrowsing: ContinueBrowsing? = null,
    val tab: ReadTab = ReadTab.SURAH,
    val filter: String = "",
    val surahs: List<SurahRowState> = emptyList(),
    val juz: List<JuzRowState> = emptyList(),
    val goToInput: String = "",
    /** The surahs a name typed into Go to may be (owner, 2026-11-25). */
    val goToSuggestions: List<SurahInfo> = emptyList(),
    val goToProblem: GoToProblem? = null,
    /** Khatms in progress, most recent activity first. */
    val khatms: List<KhatmListItem> = emptyList(),
) {
    /** Surahs matching [filter] by number, Arabic name or transliteration. */
    val visibleSurahs: List<SurahRowState>
        get() {
            val query = normalize(filter)
            if (query.isEmpty()) return surahs
            return surahs.filter { row ->
                val s = row.surah
                s.number.toString() == query ||
                    s.nameLatin.lowercase().contains(query) ||
                    s.nameEn.lowercase().contains(query) ||
                    s.nameAr.contains(query)
            }
        }

    private fun normalize(text: String): String = buildString {
        for (ch in text.trim()) {
            val digit = Character.digit(ch, 10)
            append(if (digit >= 0) ('0' + digit) else ch.lowercaseChar())
        }
    }
}

/**
 * The Read tab (§5.3): where to continue, the surah / juz / page indexes, "Go to…", random picks, and
 * the khatms in progress. Everything it opens is a [ReaderRoute] the screen hands to `onNavigate`.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class ReadViewModel(
    private val index: Provider<QuranIndex>,
    private val prefs: PrefsStore,
    private val log: ReadLogStore,
    private val khatmProgress: Provider<KhatmProgress>,
    private val clock: DayClock,
    private val io: CoroutineDispatcher,
    private val random: Random,
) : ViewModel() {

    @Inject
    constructor(
        index: Provider<QuranIndex>,
        prefs: PrefsStore,
        log: ReadLogStore,
        khatmProgress: Provider<KhatmProgress>,
        clock: DayClock,
        @IoDispatcher io: CoroutineDispatcher,
    ) : this(index, prefs, log, khatmProgress, clock, io, Random.Default)

    private val _state = MutableStateFlow(ReadUiState())
    val state: StateFlow<ReadUiState> = _state.asStateFlow()

    private var quran: QuranIndex? = null

    init {
        viewModelScope.launch {
            val (idx, progress) = withContext(io) { index.get() to khatmProgress.get() }
            quran = idx
            val juz = (1..Quran.JUZ_COUNT).map { n ->
                val start = idx.juzRange(n).start
                val ref = idx.ref(start)
                JuzRowState(n, idx.surah(ref.surah), ref)
            }
            val active = progress.observeActive()
            // The bars show today's reading, from anywhere (owner, 2026-11-25): the khatm has its own view.
            val coverage = clock.todayFlow.flatMapLatest { day -> log.observeEventsOfDay(day) }.map { events ->
                val counts = IntArray(Quran.SURAH_COUNT + 1)
                for (id in events.mapTo(HashSet()) { it.ayahId }) counts[idx.ref(id).surah]++
                idx.surahs.associate { it.number to counts[it.number].toFloat() / it.ayahCount }.filterValues { it > 0f }
            }
            combine(coverage, prefs.prefs, active) { cover, p, khatms ->
                Triple(cover, p.lastBrowseAyahId, khatms)
            }.collect { (coverage, last, active) ->
                _state.update {
                    it.copy(
                        loading = false,
                        juz = juz,
                        surahs = idx.surahs.map { s -> SurahRowState(s, coverage?.get(s.number)) },
                        continueBrowsing = last?.let { id ->
                            val ref = idx.ref(id)
                            ContinueBrowsing(id, idx.surah(ref.surah), ref.ayah, idx.pageOf(id))
                        },
                        khatms = active.map { snapshot -> snapshot.toListItem(idx) },
                    )
                }
            }
        }
    }

    fun onTab(tab: ReadTab) = _state.update { it.copy(tab = tab) }

    fun onFilter(text: String) = _state.update { it.copy(filter = text) }

    fun onGoToInput(text: String) = _state.update {
        it.copy(goToInput = text, goToProblem = null, goToSuggestions = quran?.let { idx -> GoToParser.suggestions(text, idx) }.orEmpty())
    }

    /** Parses the field. Returns the reader to open, or null with [ReadUiState.goToProblem] set. */
    fun submitGoTo(): ReaderRoute? {
        val idx = quran ?: return null
        val current = _state.value
        return when (val result = GoToParser.parse(current.goToInput, idx)) {
            is GoToResult.Invalid -> {
                _state.update { it.copy(goToProblem = GoToProblem.of(result.reason)) }
                null
            }
            else -> ReaderRoute.browse(checkNotNull(result.target).value)
        }
    }

    /** A random ayah, uniform over all 6,236. */
    fun randomAyah(): ReaderRoute? {
        if (quran == null) return null
        return ReaderRoute.random(random.nextInt(AyahRange.ALL.start.value, AyahRange.ALL.end.value + 1))
    }

    /** A random page, uniform over the 604; the reader's scope is that page. */
    fun randomPage(): ReaderRoute? {
        val idx = quran ?: return null
        val range = idx.pageRange(random.nextInt(1, Quran.PAGE_COUNT + 1))
        return ReaderRoute.random(range.start.value, range.end.value)
    }

    fun surahRoute(number: Int): ReaderRoute? = quran?.let { ReaderRoute.browse(it.surahRange(number).start.value) }

    fun juzRoute(number: Int): ReaderRoute? = quran?.let { ReaderRoute.browse(it.juzRange(number).start.value) }

    fun pageRoute(number: Int): ReaderRoute? = quran?.let { ReaderRoute.browse(it.pageRange(number).start.value) }

    fun continueRoute(): ReaderRoute? = _state.value.continueBrowsing?.let { ReaderRoute.browse(it.ayahId.value) }
}
