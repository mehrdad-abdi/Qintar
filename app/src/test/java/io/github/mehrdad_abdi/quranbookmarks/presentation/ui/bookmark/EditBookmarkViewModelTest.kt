package io.github.mehrdad_abdi.quranbookmarks.presentation.ui.bookmark

import io.github.mehrdad_abdi.quranbookmarks.domain.model.Bookmark
import io.github.mehrdad_abdi.quranbookmarks.domain.model.BookmarkType
import io.github.mehrdad_abdi.quranbookmarks.domain.usecase.bookmark.GetBookmarkByIdUseCase
import io.github.mehrdad_abdi.quranbookmarks.domain.model.Surah
import io.github.mehrdad_abdi.quranbookmarks.domain.usecase.bookmark.UpdateBookmarkUseCase
import io.github.mehrdad_abdi.quranbookmarks.domain.usecase.surah.GetAllSurahsUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.*

@ExperimentalCoroutinesApi
class EditBookmarkViewModelTest {

    @Mock
    private lateinit var getBookmarkByIdUseCase: GetBookmarkByIdUseCase

    @Mock
    private lateinit var updateBookmarkUseCase: UpdateBookmarkUseCase

    @Mock
    private lateinit var getAllSurahsUseCase: GetAllSurahsUseCase

    private lateinit var viewModel: EditBookmarkViewModel

    private val testDispatcher = UnconfinedTestDispatcher()

    // Test data
    private val testSurah1 = Surah(number = 1, name = "الفاتحة", englishName = "Al-Fatiha", englishNameTranslation = "The Opening", numberOfAyahs = 7, revelationType = "Meccan")
    private val testSurah2 = Surah(number = 2, name = "البقرة", englishName = "Al-Baqarah", englishNameTranslation = "The Cow", numberOfAyahs = 286, revelationType = "Medinan")
    private val testSurah3 = Surah(number = 3, name = "آل عمران", englishName = "Aal-E-Imran", englishNameTranslation = "The Family of Imran", numberOfAyahs = 200, revelationType = "Medinan")
    private val invalidSurah = Surah(number = 200, name = "Invalid", englishName = "Invalid", englishNameTranslation = "Invalid", numberOfAyahs = 1, revelationType = "Unknown")

    private val sampleBookmark = Bookmark(
        id = 1L,
        groupId = 1L,
        type = BookmarkType.AYAH,
        startSurah = 2,
        startAyah = 255,
        endSurah = 2,
        endAyah = 255,
        description = "Ayat al-Kursi"
    )

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        Dispatchers.setMain(testDispatcher)

        viewModel = EditBookmarkViewModel(getBookmarkByIdUseCase, updateBookmarkUseCase, getAllSurahsUseCase)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state should have default values`() {
        val state = viewModel.uiState.value
        assertEquals(0L, state.bookmarkId)
        assertEquals(0L, state.groupId)
        assertEquals("", state.selectedSurah?.number)
        assertEquals("", state.ayahNumber)
        assertEquals("", state.endAyahNumber)
        assertEquals("", state.description)
        assertEquals(BookmarkType.AYAH, state.bookmarkType)
        assertFalse(state.isLoading)
        assertFalse(state.isLoadingBookmark)
        assertNull(state.error)
    }

    @Test
    fun `loadBookmark should populate state with bookmark data`() = runTest {
        // Given
        whenever(getBookmarkByIdUseCase(1L)).thenReturn(Result.success(sampleBookmark))

        // When
        viewModel.loadBookmark(1L)

        // Then
        val state = viewModel.uiState.value
        assertFalse(state.isLoadingBookmark)
        assertEquals(1L, state.bookmarkId)
        assertEquals(1L, state.groupId)
        assertEquals("2", state.selectedSurah?.number)
        assertEquals("255", state.ayahNumber)
        assertEquals("", state.endAyahNumber) // Should be empty for AYAH type
        assertEquals("Ayat al-Kursi", state.description)
        assertEquals(BookmarkType.AYAH, state.bookmarkType)
    }

    @Test
    fun `loadBookmark should handle range bookmark correctly`() = runTest {
        // Given
        val rangeBookmark = sampleBookmark.copy(
            type = BookmarkType.RANGE,
            startAyah = 1,
            endAyah = 5
        )
        whenever(getBookmarkByIdUseCase(1L)).thenReturn(Result.success(rangeBookmark))

        // When
        viewModel.loadBookmark(1L)

        // Then
        val state = viewModel.uiState.value
        assertEquals("1", state.ayahNumber)
        assertEquals("5", state.endAyahNumber)
        assertEquals(BookmarkType.RANGE, state.bookmarkType)
    }

    @Test
    fun `loadBookmark should handle surah bookmark correctly`() = runTest {
        // Given
        val surahBookmark = sampleBookmark.copy(
            type = BookmarkType.SURAH,
            startAyah = 1,
            endAyah = 7
        )
        whenever(getBookmarkByIdUseCase(1L)).thenReturn(Result.success(surahBookmark))

        // When
        viewModel.loadBookmark(1L)

        // Then
        val state = viewModel.uiState.value
        assertEquals("", state.ayahNumber) // Should be empty for SURAH type
        assertEquals("", state.endAyahNumber)
        assertEquals(BookmarkType.SURAH, state.bookmarkType)
    }

    @Test
    fun `loadBookmark should handle failure`() = runTest {
        // Given
        whenever(getBookmarkByIdUseCase(1L)).thenReturn(Result.failure(Exception("Bookmark not found")))

        // When
        viewModel.loadBookmark(1L)

        // Then
        val state = viewModel.uiState.value
        assertFalse(state.isLoadingBookmark)
        assertEquals("Bookmark not found", state.error)
    }

    @Test
    fun `updateSelectedSurah should update surah number and clear error`() {
        // When
        viewModel.updateSelectedSurah(testSurah3)

        // Then
        val state = viewModel.uiState.value
        assertEquals("3", state.selectedSurah?.number)
        assertNull(state.surahError)
    }

    @Test
    fun `updateAyahNumber should update ayah number and clear error`() {
        // When
        viewModel.updateAyahNumber("100")

        // Then
        val state = viewModel.uiState.value
        assertEquals("100", state.ayahNumber)
        assertNull(state.ayahError)
    }

    @Test
    fun `updateEndAyahNumber should update end ayah number and clear error`() {
        // When
        viewModel.updateEndAyahNumber("150")

        // Then
        val state = viewModel.uiState.value
        assertEquals("150", state.endAyahNumber)
        assertNull(state.endAyahError)
    }

    @Test
    fun `updateDescription should update description`() {
        // When
        viewModel.updateDescription("Updated description")

        // Then
        assertEquals("Updated description", viewModel.uiState.value.description)
    }

    @Test
    fun `updateBookmarkType should update type and clear endAyah when not RANGE`() = runTest {
        // Given - load a range bookmark first
        val rangeBookmark = sampleBookmark.copy(
            type = BookmarkType.RANGE,
            endAyah = 10
        )
        whenever(getBookmarkByIdUseCase(1L)).thenReturn(Result.success(rangeBookmark))
        viewModel.loadBookmark(1L)

        // When - change to AYAH type
        viewModel.updateBookmarkType(BookmarkType.AYAH)

        // Then
        val state = viewModel.uiState.value
        assertEquals(BookmarkType.AYAH, state.bookmarkType)
        assertEquals("", state.endAyahNumber) // Should be cleared
    }

    @Test
    fun `updateBookmarkType to SURAH should clear ayah number`() = runTest {
        // Given - load an ayah bookmark first
        whenever(getBookmarkByIdUseCase(1L)).thenReturn(Result.success(sampleBookmark))
        viewModel.loadBookmark(1L)

        // When - change to SURAH type
        viewModel.updateBookmarkType(BookmarkType.SURAH)

        // Then
        val state = viewModel.uiState.value
        assertEquals(BookmarkType.SURAH, state.bookmarkType)
        assertEquals("", state.ayahNumber) // Should be cleared
    }

    @Test
    fun `updateBookmark should fail with invalid surah number`() = runTest {
        // Given - set up initial state
        whenever(getBookmarkByIdUseCase(1L)).thenReturn(Result.success(sampleBookmark))
        viewModel.loadBookmark(1L)
        viewModel.updateSelectedSurah(invalidSurah) // Invalid surah number

        var successCalled = false
        val onSuccess = { successCalled = true }

        // When
        viewModel.updateBookmark(onSuccess)

        // Then
        val state = viewModel.uiState.value
        assertNotNull(state.surahError)
        assertTrue(state.surahError!!.contains("between 1 and 114"))
        assertFalse(successCalled)
        verify(updateBookmarkUseCase, never()).invoke(any())
    }

    @Test
    fun `updateBookmark should succeed with valid AYAH bookmark`() = runTest {
        // Given
        whenever(getBookmarkByIdUseCase(1L)).thenReturn(Result.success(sampleBookmark))
        whenever(updateBookmarkUseCase(any())).thenReturn(Result.success(Unit))

        viewModel.loadBookmark(1L)
        viewModel.updateSelectedSurah(testSurah3)
        viewModel.updateAyahNumber("100")
        viewModel.updateDescription("Updated description")

        var successCalled = false
        val onSuccess = { successCalled = true }

        // When
        viewModel.updateBookmark(onSuccess)
        advanceUntilIdle()

        // Then
        assertTrue(successCalled)
        verify(updateBookmarkUseCase).invoke(argThat { bookmark ->
            bookmark.id == 1L &&
            bookmark.groupId == 1L &&
            bookmark.type == BookmarkType.AYAH &&
            bookmark.startSurah == 3 &&
            bookmark.startAyah == 100 &&
            bookmark.endSurah == 3 &&
            bookmark.endAyah == 100 &&
            bookmark.description == "Updated description"
        })
    }

    @Test
    fun `updateBookmark should succeed with valid RANGE bookmark`() = runTest {
        // Given
        whenever(getBookmarkByIdUseCase(1L)).thenReturn(Result.success(sampleBookmark))
        whenever(updateBookmarkUseCase(any())).thenReturn(Result.success(Unit))

        viewModel.loadBookmark(1L)
        viewModel.updateBookmarkType(BookmarkType.RANGE)
        viewModel.updateSelectedSurah(testSurah2)
        viewModel.updateAyahNumber("1")
        viewModel.updateEndAyahNumber("10")

        var successCalled = false
        val onSuccess = { successCalled = true }

        // When
        viewModel.updateBookmark(onSuccess)
        advanceUntilIdle()

        // Then
        assertTrue(successCalled)
        verify(updateBookmarkUseCase).invoke(argThat { bookmark ->
            bookmark.type == BookmarkType.RANGE &&
            bookmark.startSurah == 2 &&
            bookmark.startAyah == 1 &&
            bookmark.endAyah == 10
        })
    }

    @Test
    fun `updateBookmark should succeed with valid SURAH bookmark`() = runTest {
        // Given
        whenever(getBookmarkByIdUseCase(1L)).thenReturn(Result.success(sampleBookmark))
        whenever(updateBookmarkUseCase(any())).thenReturn(Result.success(Unit))

        viewModel.loadBookmark(1L)
        viewModel.updateBookmarkType(BookmarkType.SURAH)
        viewModel.updateSelectedSurah(testSurah1) // Al-Fatihah

        var successCalled = false
        val onSuccess = { successCalled = true }

        // When
        viewModel.updateBookmark(onSuccess)
        advanceUntilIdle()

        // Then
        assertTrue(successCalled)
        verify(updateBookmarkUseCase).invoke(argThat { bookmark ->
            bookmark.type == BookmarkType.SURAH &&
            bookmark.startSurah == 1 &&
            bookmark.startAyah == 1 &&
            bookmark.endAyah == 7 // Al-Fatihah has 7 ayahs
        })
    }

    @Test
    fun `updateBookmark should handle use case failure`() = runTest {
        // Given
        whenever(getBookmarkByIdUseCase(1L)).thenReturn(Result.success(sampleBookmark))
        whenever(updateBookmarkUseCase(any())).thenReturn(Result.failure(Exception("Database error")))

        viewModel.loadBookmark(1L)
        viewModel.updateSelectedSurah(testSurah2)
        viewModel.updateAyahNumber("255")

        var successCalled = false
        val onSuccess = { successCalled = true }

        // When
        viewModel.updateBookmark(onSuccess)
        advanceUntilIdle()

        // Then
        val state = viewModel.uiState.value
        assertEquals("Database error", state.error)
        assertFalse(state.isLoading)
        assertFalse(successCalled)
    }

    @Test
    fun `updateBookmark should trim description whitespace`() = runTest {
        // Given
        whenever(getBookmarkByIdUseCase(1L)).thenReturn(Result.success(sampleBookmark))
        whenever(updateBookmarkUseCase(any())).thenReturn(Result.success(Unit))

        viewModel.loadBookmark(1L)
        viewModel.updateDescription("  Updated description  ")

        var successCalled = false
        val onSuccess = { successCalled = true }

        // When
        viewModel.updateBookmark(onSuccess)
        advanceUntilIdle()

        // Then
        verify(updateBookmarkUseCase).invoke(argThat { bookmark ->
            bookmark.description == "Updated description"
        })
    }

    @Test
    fun `clearError should clear error state`() = runTest {
        // Given - set an error state
        whenever(getBookmarkByIdUseCase(1L)).thenReturn(Result.failure(Exception("Test error")))
        viewModel.loadBookmark(1L)
        assertNotNull(viewModel.uiState.value.error)

        // When
        viewModel.clearError()

        // Then
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun `constants should be correct`() {
        assertEquals(114, EditBookmarkViewModel.TOTAL_SURAHS)
        assertEquals(286, EditBookmarkViewModel.MAX_AYAH_IN_LONGEST_SURAH)
    }
}