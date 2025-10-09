package io.github.mehrdad_abdi.quranbookmarks.presentation.ui.reading

import app.cash.turbine.test
import io.github.mehrdad_abdi.quranbookmarks.data.remote.dto.ReciterData
import io.github.mehrdad_abdi.quranbookmarks.domain.model.Bookmark
import io.github.mehrdad_abdi.quranbookmarks.domain.model.BookmarkGroup
import io.github.mehrdad_abdi.quranbookmarks.domain.model.CachedContent
import io.github.mehrdad_abdi.quranbookmarks.domain.model.VerseMetadata
import io.github.mehrdad_abdi.quranbookmarks.domain.repository.QuranRepository
import io.github.mehrdad_abdi.quranbookmarks.domain.repository.SettingsRepository
import io.github.mehrdad_abdi.quranbookmarks.domain.service.AudioService
import io.github.mehrdad_abdi.quranbookmarks.domain.service.PlaybackState
import io.github.mehrdad_abdi.quranbookmarks.domain.usecase.bookmark.GetBookmarkByIdUseCase
import io.github.mehrdad_abdi.quranbookmarks.domain.usecase.bookmark.GetBookmarkDisplayTextUseCase
import io.github.mehrdad_abdi.quranbookmarks.domain.usecase.reading.GetBookmarkContentUseCase
import io.github.mehrdad_abdi.quranbookmarks.domain.usecase.reading.GetTodayStatsUseCase
import io.github.mehrdad_abdi.quranbookmarks.domain.usecase.reading.ToggleAyahTrackingUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when` as whenever
import org.mockito.kotlin.verify

/**
 * Regression tests for bismillah playback functionality
 *
 * Requirements:
 * - When playing first ayah of any surah (2-114, excluding 9), bismillah should play first
 * - Bismillah audio is from Surah 1, Ayah 1 of the selected reciter
 * - Exceptions: Surah 1 (already has bismillah) and Surah 9 (no bismillah)
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BookmarkReadingViewModelBismillahTest {

    private lateinit var viewModel: BookmarkReadingViewModel
    private lateinit var getBookmarkByIdUseCase: GetBookmarkByIdUseCase
    private lateinit var getBookmarkDisplayTextUseCase: GetBookmarkDisplayTextUseCase
    private lateinit var getBookmarkContentUseCase: GetBookmarkContentUseCase
    private lateinit var quranRepository: QuranRepository
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var audioService: AudioService
    private lateinit var getTodayStatsUseCase: GetTodayStatsUseCase
    private lateinit var toggleAyahTrackingUseCase: ToggleAyahTrackingUseCase

    private val testDispatcher = UnconfinedTestDispatcher()

    private val testReciter = ReciterData(
        identifier = "ar.alafasy",
        name = "Mishary Alafasy",
        englishName = "Mishary Alafasy",
        format = "audio",
        language = "ar",
        type = "versebyverse"
    )

    private val testGroup = BookmarkGroup(
        id = 1L,
        name = "Test Group",
        reciterEdition = "ar.alafasy"
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        getBookmarkByIdUseCase = mock(GetBookmarkByIdUseCase::class.java)
        getBookmarkDisplayTextUseCase = mock(GetBookmarkDisplayTextUseCase::class.java)
        getBookmarkContentUseCase = mock(GetBookmarkContentUseCase::class.java)
        quranRepository = mock(QuranRepository::class.java)
        settingsRepository = mock(SettingsRepository::class.java)
        audioService = mock(AudioService::class.java)
        getTodayStatsUseCase = mock(GetTodayStatsUseCase::class.java)
        toggleAyahTrackingUseCase = mock(ToggleAyahTrackingUseCase::class.java)

        // Setup default mocks
        whenever(audioService.playbackState).thenReturn(
            MutableStateFlow(PlaybackState())
        )
        whenever(getTodayStatsUseCase.invoke()).thenReturn(
            MutableStateFlow(io.github.mehrdad_abdi.quranbookmarks.domain.model.TodayStats(
                totalAyahsRead = 0,
                trackedAyahIds = emptySet()
            ))
        )
        whenever(settingsRepository.getSettings()).thenReturn(
            MutableStateFlow(io.github.mehrdad_abdi.quranbookmarks.domain.model.Settings(
                reciterBitrate = "64"
            ))
        )

        viewModel = BookmarkReadingViewModel(
            getBookmarkByIdUseCase,
            getBookmarkDisplayTextUseCase,
            getBookmarkContentUseCase,
            quranRepository,
            settingsRepository,
            audioService,
            getTodayStatsUseCase,
            toggleAyahTrackingUseCase
        )
    }

    @Test
    fun `bismillah should play before first ayah of surah 2`() = runTest {
        // Given: First ayah of Surah Al-Baqarah (2:1)
        val bookmark = createBookmark(1L, surah = 2, startAyah = 1, endAyah = 1)
        val verses = listOf(createVerse(surah = 2, ayah = 1, globalAyahNumber = 8))

        setupBookmarkMocks(bookmark, verses)
        whenever(quranRepository.getAvailableReciters()).thenReturn(Result.success(listOf(testReciter)))
        whenever(quranRepository.getAudioUrl("ar.alafasy", 1, "64")).thenReturn("https://audio/bismillah.mp3")
        whenever(quranRepository.getAudioUrl("ar.alafasy", 8, "64")).thenReturn("https://audio/2_1.mp3")

        // When: Load bookmark and play first ayah
        viewModel.loadBookmark(1L)
        viewModel.playAudioAtIndex(0)

        // Then: Bismillah should be played first
        verify(audioService).playAudio("https://audio/bismillah.mp3")
        viewModel.uiState.test {
            val state = awaitItem()
            assertTrue(state.isPlayingBismillah)
            assertEquals(0, state.currentPlayingIndex)
            assertEquals(0, state.pendingVerseAfterBismillah)
        }
    }

    @Test
    fun `bismillah should NOT play for surah 1 (Al-Fatiha)`() = runTest {
        // Given: First ayah of Surah Al-Fatiha (1:1)
        val bookmark = createBookmark(1L, surah = 1, startAyah = 1, endAyah = 1)
        val verses = listOf(createVerse(surah = 1, ayah = 1, globalAyahNumber = 1))

        setupBookmarkMocks(bookmark, verses)
        whenever(quranRepository.getAvailableReciters()).thenReturn(Result.success(listOf(testReciter)))
        whenever(quranRepository.getAudioUrl("ar.alafasy", 1, "64")).thenReturn("https://audio/1_1.mp3")

        // When: Load bookmark and play first ayah
        viewModel.loadBookmark(1L)
        viewModel.playAudioAtIndex(0)

        // Then: Should play ayah directly without bismillah
        verify(audioService).playAudio("https://audio/1_1.mp3")
        viewModel.uiState.test {
            val state = awaitItem()
            assertFalse(state.isPlayingBismillah)
            assertNull(state.pendingVerseAfterBismillah)
        }
    }

    @Test
    fun `bismillah should NOT play for surah 9 (At-Tawbah)`() = runTest {
        // Given: First ayah of Surah At-Tawbah (9:1)
        val bookmark = createBookmark(1L, surah = 9, startAyah = 1, endAyah = 1)
        val verses = listOf(createVerse(surah = 9, ayah = 1, globalAyahNumber = 1239))

        setupBookmarkMocks(bookmark, verses)
        whenever(quranRepository.getAvailableReciters()).thenReturn(Result.success(listOf(testReciter)))
        whenever(quranRepository.getAudioUrl("ar.alafasy", 1239, "64")).thenReturn("https://audio/9_1.mp3")

        // When: Load bookmark and play first ayah
        viewModel.loadBookmark(1L)
        viewModel.playAudioAtIndex(0)

        // Then: Should play ayah directly without bismillah
        verify(audioService).playAudio("https://audio/9_1.mp3")
        viewModel.uiState.test {
            val state = awaitItem()
            assertFalse(state.isPlayingBismillah)
            assertNull(state.pendingVerseAfterBismillah)
        }
    }

    @Test
    fun `bismillah should NOT play for second ayah of any surah`() = runTest {
        // Given: Second ayah of Surah Al-Baqarah (2:2)
        val bookmark = createBookmark(1L, surah = 2, startAyah = 2, endAyah = 2)
        val verses = listOf(createVerse(surah = 2, ayah = 2, globalAyahNumber = 9))

        setupBookmarkMocks(bookmark, verses)
        whenever(quranRepository.getAvailableReciters()).thenReturn(Result.success(listOf(testReciter)))
        whenever(quranRepository.getAudioUrl("ar.alafasy", 9, "64")).thenReturn("https://audio/2_2.mp3")

        // When: Load bookmark and play second ayah
        viewModel.loadBookmark(1L)
        viewModel.playAudioAtIndex(0)

        // Then: Should play ayah directly without bismillah
        verify(audioService).playAudio("https://audio/2_2.mp3")
        viewModel.uiState.test {
            val state = awaitItem()
            assertFalse(state.isPlayingBismillah)
            assertNull(state.pendingVerseAfterBismillah)
        }
    }

    @Test
    fun `bismillah should use cached audio when available`() = runTest {
        // Given: First ayah of Surah 3 with cached bismillah
        val bookmark = createBookmark(1L, surah = 3, startAyah = 1, endAyah = 1)
        val verses = listOf(createVerse(surah = 3, ayah = 1, globalAyahNumber = 293))

        val cachedBismillah = CachedContent(
            id = "1:1",
            surah = 1,
            ayah = 1,
            audioPath = "/cache/bismillah.mp3"
        )

        setupBookmarkMocks(bookmark, verses)
        whenever(quranRepository.getAvailableReciters()).thenReturn(Result.success(listOf(testReciter)))
        whenever(quranRepository.getCachedContent(1, 1)).thenReturn(cachedBismillah)
        whenever(quranRepository.getAudioUrl("ar.alafasy", 293, "64")).thenReturn("https://audio/3_1.mp3")

        // When: Load bookmark and play first ayah
        viewModel.loadBookmark(1L)
        viewModel.playAudioAtIndex(0)

        // Then: Should use cached bismillah
        verify(audioService).playAudio("/cache/bismillah.mp3")
        viewModel.uiState.test {
            val state = awaitItem()
            assertTrue(state.isPlayingBismillah)
        }
    }

    @Test
    fun `bismillah should work for all surahs 2-114 except 9`() = runTest {
        // Test a few random surahs to ensure logic is correct
        val testCases = listOf(
            2 to true,    // Al-Baqarah - should have bismillah
            9 to false,   // At-Tawbah - should NOT have bismillah
            50 to true,   // Qaf - should have bismillah
            114 to true,  // An-Nas - should have bismillah
            1 to false    // Al-Fatiha - should NOT have bismillah
        )

        testCases.forEach { (surahNumber, shouldPlayBismillah) ->
            val bookmark = createBookmark(1L, surah = surahNumber, startAyah = 1, endAyah = 1)
            val verses = listOf(createVerse(surah = surahNumber, ayah = 1, globalAyahNumber = 100))

            setupBookmarkMocks(bookmark, verses)
            whenever(quranRepository.getAvailableReciters()).thenReturn(Result.success(listOf(testReciter)))

            if (shouldPlayBismillah) {
                whenever(quranRepository.getAudioUrl("ar.alafasy", 1, "64")).thenReturn("https://audio/bismillah.mp3")
            }
            whenever(quranRepository.getAudioUrl("ar.alafasy", 100, "64")).thenReturn("https://audio/ayah.mp3")

            viewModel.loadBookmark(1L)
            viewModel.playAudioAtIndex(0)

            viewModel.uiState.test {
                val state = awaitItem()
                assertEquals(
                    "Surah $surahNumber bismillah state incorrect",
                    shouldPlayBismillah,
                    state.isPlayingBismillah
                )
            }
        }
    }

    // Helper functions

    private fun createBookmark(
        id: Long,
        surah: Int,
        startAyah: Int,
        endAyah: Int
    ): Bookmark {
        return Bookmark(
            id = id,
            groupId = 1L,
            surahNumber = surah,
            startAyah = startAyah,
            endAyah = endAyah,
            group = testGroup
        )
    }

    private fun createVerse(
        surah: Int,
        ayah: Int,
        globalAyahNumber: Int
    ): VerseMetadata {
        return VerseMetadata(
            text = "Test verse text",
            surahName = "Test Surah",
            surahNameEn = "Test Surah",
            surahNumber = surah,
            revelationType = "Meccan",
            numberOfAyahs = 100,
            hizbQuarter = 1,
            rukuNumber = 1,
            page = 1,
            manzil = 1,
            sajda = false,
            globalAyahNumber = globalAyahNumber,
            ayahInSurah = ayah
        )
    }

    private suspend fun setupBookmarkMocks(bookmark: Bookmark, verses: List<VerseMetadata>) {
        whenever(getBookmarkByIdUseCase.invoke(bookmark.id)).thenReturn(Result.success(bookmark))
        whenever(getBookmarkDisplayTextUseCase.invoke(bookmark)).thenReturn("${bookmark.surahNumber}:${bookmark.startAyah}-${bookmark.endAyah}")
        whenever(getBookmarkContentUseCase.invoke(bookmark)).thenReturn(Result.success(verses))
    }
}
