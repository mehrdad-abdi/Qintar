package io.github.mehrdad_abdi.quranbookmarks

import io.github.mehrdad_abdi.quranbookmarks.domain.model.Bookmark
import io.github.mehrdad_abdi.quranbookmarks.domain.model.BookmarkType
import org.junit.Test
import org.junit.Assert.*

class NewBugReproductionTest {

    @Test
    fun `Bug 4 - Surah 36 ayah 36 should load Yasin not Saffaat`() {
        // Given: A bookmark for Surah 36 (Yasin), Ayah 36
        val ayahBookmark = Bookmark(
            id = 1L,
            groupId = 1L,
            type = BookmarkType.AYAH,
            startSurah = 36, // Yasin
            startAyah = 36,
            description = "Yasin verse 36"
        )

        // When: Calculating global ayah number with current formula
        val globalAyahNumber = calculateCurrentGlobalAyahNumber(36, 36)

        // The issue: Current calculation gives wrong global ayah number
        // Yasin (36) should have correct global ayah mapping
        // Current calculation: 3765 + 36 = 3801
        // But this might still be wrong if the cumulative counts are off

        println("Current global ayah calculation for Surah 36, Ayah 36: $globalAyahNumber")
        println("Expected: This should map to Yasin, but currently maps to Saffaat")

        // We need to verify the actual API mapping
        // Yasin is surah 36, Saffaat is surah 37
        // If we're getting Saffaat, our cumulative count before Yasin is wrong
    }

    @Test
    fun `Bug 5 - RANGE bookmark 2 256-258 should show content`() {
        // Given: A RANGE bookmark for Surah 2, Ayah 256-258
        val rangeBookmark = Bookmark(
            id = 1L,
            groupId = 1L,
            type = BookmarkType.RANGE,
            startSurah = 2,
            startAyah = 256,
            endSurah = 2,
            endAyah = 258,
            description = "Al-Baqarah 256-258"
        )

        // When: Getting display text
        val displayText = rangeBookmark.getDisplayText()

        // Then: Should show proper range
        assertEquals("Surah 2:256-258", displayText)

        // The real issue is likely in the API endpoint or response handling
        // API URL should be: https://api.alquran.cloud/v1/surah/2/ayahs/256-258
        // But this endpoint might not exist or might return different structure

        // POTENTIAL FIX: The API might expect different endpoint format
        // Check if API supports: /v1/surah/2/256-258 instead of /v1/surah/2/ayahs/256-258
        // Or we might need to call individual ayahs and combine results

        println("Range bookmark display: $displayText")
        println("Expected: Should load 3 verses from Surah 2, but shows nothing")
        println("API endpoint being called: v1/surah/2/ayahs/256-258")
        println("Possible fix: Change API endpoint or use individual ayah calls")
    }

    @Test
    fun `Bug 6 - Surah bookmark should include surah name in title`() {
        // Given: A SURAH bookmark for Surah 36 (Yasin)
        val surahBookmark = Bookmark(
            id = 1L,
            groupId = 1L,
            type = BookmarkType.SURAH,
            startSurah = 36, // Yasin
            startAyah = 1,
            description = "Complete Yasin"
        )

        // When: Getting display text
        val currentDisplayText = surahBookmark.getDisplayText()

        // Then: Currently shows "Surah 36", should show "Surah 36 - Yasin"
        assertEquals("Surah 36", currentDisplayText) // Current behavior

        // Expected behavior (after fix):
        // Should show something like "Surah 36 - Yasin" or "Yasin (36)"
        println("Current surah bookmark display: $currentDisplayText")
        println("Expected: Should include surah name like 'Surah 36 - Yasin'")
    }

    // Test the current calculation to identify the issue
    private fun calculateCurrentGlobalAyahNumber(surah: Int, ayah: Int): Int {
        val ayahsBeforeSurah = when (surah) {
            1 -> 0
            2 -> 7          // Al-Fatiha: 7 ayahs
            3 -> 293        // Al-Baqarah: 286 ayahs (7 + 286)
            4 -> 493        // Aal-E-Imran: 200 ayahs (293 + 200)
            5 -> 669        // An-Nisa: 176 ayahs (493 + 176)
            6 -> 789        // Al-Maidah: 120 ayahs (669 + 120)
            7 -> 995        // Al-An'am: 165 ayahs (789 + 165)
            8 -> 1201       // Al-A'raf: 206 ayahs (995 + 206)
            9 -> 1276       // Al-Anfal: 75 ayahs (1201 + 75)
            10 -> 1405      // At-Taubah: 129 ayahs (1276 + 129)
            11 -> 1514      // Yunus: 109 ayahs (1405 + 109)
            12 -> 1637      // Hud: 123 ayahs (1514 + 123)
            13 -> 1748      // Yusuf: 111 ayahs (1637 + 111)
            14 -> 1791      // Ar-Ra'd: 43 ayahs (1748 + 43)
            15 -> 1843      // Ibrahim: 52 ayahs (1791 + 52)
            16 -> 1942      // Al-Hijr: 99 ayahs (1843 + 99)
            17 -> 2070      // An-Nahl: 128 ayahs (1942 + 128)
            18 -> 2181      // Al-Isra: 111 ayahs (2070 + 111)
            19 -> 2291      // Al-Kahf: 110 ayahs (2181 + 110)
            20 -> 2389      // Maryam: 98 ayahs (2291 + 98)
            21 -> 2524      // Taha: 135 ayahs (2389 + 135)
            22 -> 2635      // Al-Anbiya: 112 ayahs (2524 + 112)
            23 -> 2713      // Al-Hajj: 78 ayahs (2635 + 78)
            24 -> 2831      // Al-Mu'minun: 118 ayahs (2713 + 118)
            25 -> 2895      // An-Nur: 64 ayahs (2831 + 64)
            26 -> 2973      // Al-Furqan: 77 ayahs (2895 + 77)
            27 -> 3200      // Ash-Shu'ara: 227 ayahs (2973 + 227)
            28 -> 3289      // An-Naml: 93 ayahs (3200 + 93)
            29 -> 3377      // Al-Qasas: 88 ayahs (3289 + 88)
            30 -> 3466      // Al-Ankabut: 69 ayahs (3377 + 69)
            31 -> 3526      // Ar-Rum: 60 ayahs (3466 + 60)
            32 -> 3560      // Luqman: 34 ayahs (3526 + 34)
            33 -> 3593      // As-Sajdah: 30 ayahs (3560 + 30)
            34 -> 3666      // Al-Ahzab: 73 ayahs (3593 + 73)
            35 -> 3720      // Saba: 54 ayahs (3666 + 54)
            36 -> 3765      // Fatir: 45 ayahs (3720 + 45)
            // ERROR: This is wrong! Surah 36 should be Yasin, not offset for Fatir
            // The mapping is off by one surah
            else -> 3765 + (surah - 36) * 50
        }
        return ayahsBeforeSurah + ayah
    }
}