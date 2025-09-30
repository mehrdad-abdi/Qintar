package io.github.mehrdad_abdi.quranbookmarks.domain.usecase.cache

import io.github.mehrdad_abdi.quranbookmarks.domain.model.Bookmark
import io.github.mehrdad_abdi.quranbookmarks.domain.model.BookmarkType
import javax.inject.Inject

/**
 * Data class representing a unique ayah in a bookmark
 */
data class AyahReference(
    val surah: Int,
    val ayah: Int,
    val globalAyahNumber: Int
) {
    fun getCacheId(): String = "$surah:$ayah"
}

/**
 * Use case to extract all individual ayah references from a bookmark
 * Returns a deduplicated list of ayahs that need to be cached
 */
class GetAyahsFromBookmarkUseCase @Inject constructor() {

    operator fun invoke(bookmark: Bookmark): List<AyahReference> {
        return when (bookmark.type) {
            BookmarkType.AYAH -> {
                // Single ayah
                listOf(
                    AyahReference(
                        surah = bookmark.startSurah,
                        ayah = bookmark.startAyah,
                        globalAyahNumber = calculateGlobalAyahNumber(bookmark.startSurah, bookmark.startAyah)
                    )
                )
            }
            BookmarkType.RANGE -> {
                // Range of ayahs in same surah
                (bookmark.startAyah..bookmark.endAyah).map { ayahNumber ->
                    AyahReference(
                        surah = bookmark.startSurah,
                        ayah = ayahNumber,
                        globalAyahNumber = calculateGlobalAyahNumber(bookmark.startSurah, ayahNumber)
                    )
                }
            }
            BookmarkType.SURAH -> {
                // Full surah - need to get all ayahs
                val ayahCount = getSurahAyahCount(bookmark.startSurah)
                (1..ayahCount).map { ayahNumber ->
                    AyahReference(
                        surah = bookmark.startSurah,
                        ayah = ayahNumber,
                        globalAyahNumber = calculateGlobalAyahNumber(bookmark.startSurah, ayahNumber)
                    )
                }
            }
            BookmarkType.PAGE -> {
                // Pages contain multiple ayahs from potentially multiple surahs
                // For now, we'll skip page-level caching as it's complex
                // TODO: Implement page-level ayah extraction
                emptyList()
            }
        }
    }

    private fun getSurahAyahCount(surah: Int): Int {
        return when (surah) {
            1 -> 7      // Al-Fatiha
            2 -> 286    // Al-Baqarah
            3 -> 200    // Aal-E-Imran
            4 -> 176    // An-Nisa
            5 -> 120    // Al-Maidah
            6 -> 165    // Al-An'am
            7 -> 206    // Al-A'raf
            8 -> 75     // Al-Anfal
            9 -> 129    // At-Taubah
            10 -> 109   // Yunus
            11 -> 123   // Hud
            12 -> 111   // Yusuf
            13 -> 43    // Ar-Ra'd
            14 -> 52    // Ibrahim
            15 -> 99    // Al-Hijr
            16 -> 128   // An-Nahl
            17 -> 111   // Al-Isra
            18 -> 110   // Al-Kahf
            19 -> 98    // Maryam
            20 -> 135   // Taha
            21 -> 112   // Al-Anbiya
            22 -> 78    // Al-Hajj
            23 -> 118   // Al-Mu'minun
            24 -> 64    // An-Nur
            25 -> 77    // Al-Furqan
            26 -> 227   // Ash-Shu'ara
            27 -> 93    // An-Naml
            28 -> 88    // Al-Qasas
            29 -> 69    // Al-Ankabut
            30 -> 60    // Ar-Rum
            31 -> 34    // Luqman
            32 -> 30    // As-Sajdah
            33 -> 73    // Al-Ahzab
            34 -> 54    // Saba
            35 -> 45    // Fatir
            36 -> 83    // Ya-Sin
            37 -> 182   // As-Saffat
            38 -> 88    // Sad
            39 -> 75    // Az-Zumar
            40 -> 85    // Ghafir
            41 -> 54    // Fussilat
            42 -> 53    // Ash-Shura
            43 -> 89    // Az-Zukhruf
            44 -> 59    // Ad-Dukhan
            45 -> 37    // Al-Jathiyah
            46 -> 35    // Al-Ahqaf
            47 -> 38    // Muhammad
            48 -> 29    // Al-Fath
            49 -> 18    // Al-Hujurat
            50 -> 45    // Qaf
            51 -> 60    // Adh-Dhariyat
            52 -> 49    // At-Tur
            53 -> 62    // An-Najm
            54 -> 55    // Al-Qamar
            55 -> 78    // Ar-Rahman
            56 -> 96    // Al-Waqi'ah
            57 -> 29    // Al-Hadid
            58 -> 22    // Al-Mujadila
            59 -> 24    // Al-Hashr
            60 -> 13    // Al-Mumtahanah
            61 -> 14    // As-Saff
            62 -> 11    // Al-Jumu'ah
            63 -> 11    // Al-Munafiqun
            64 -> 18    // At-Taghabun
            65 -> 12    // At-Talaq
            66 -> 12    // At-Tahrim
            67 -> 30    // Al-Mulk
            68 -> 52    // Al-Qalam
            69 -> 52    // Al-Haqqah
            70 -> 44    // Al-Ma'arij
            71 -> 28    // Nuh
            72 -> 28    // Al-Jinn
            73 -> 20    // Al-Muzzammil
            74 -> 56    // Al-Muddathir
            75 -> 40    // Al-Qiyamah
            76 -> 31    // Al-Insan
            77 -> 50    // Al-Mursalat
            78 -> 40    // An-Naba
            79 -> 46    // An-Nazi'at
            80 -> 42    // Abasa
            81 -> 29    // At-Takwir
            82 -> 19    // Al-Infitar
            83 -> 36    // Al-Mutaffifin
            84 -> 25    // Al-Inshiqaq
            85 -> 22    // Al-Buruj
            86 -> 17    // At-Tariq
            87 -> 19    // Al-A'la
            88 -> 26    // Al-Ghashiyah
            89 -> 30    // Al-Fajr
            90 -> 20    // Al-Balad
            91 -> 15    // Ash-Shams
            92 -> 21    // Al-Layl
            93 -> 11    // Ad-Duha
            94 -> 8     // Ash-Sharh
            95 -> 8     // At-Tin
            96 -> 19    // Al-Alaq
            97 -> 5     // Al-Qadr
            98 -> 8     // Al-Bayyinah
            99 -> 8     // Az-Zalzalah
            100 -> 11   // Al-Adiyat
            101 -> 11   // Al-Qari'ah
            102 -> 8    // At-Takathur
            103 -> 3    // Al-Asr
            104 -> 9    // Al-Humazah
            105 -> 5    // Al-Fil
            106 -> 4    // Quraysh
            107 -> 7    // Al-Ma'un
            108 -> 3    // Al-Kawthar
            109 -> 6    // Al-Kafirun
            110 -> 3    // An-Nasr
            111 -> 5    // Al-Masad
            112 -> 4    // Al-Ikhlas
            113 -> 5    // Al-Falaq
            114 -> 6    // An-Nas
            else -> 0
        }
    }

    private fun calculateGlobalAyahNumber(surah: Int, ayah: Int): Int {
        val ayahsBeforeSurah = when (surah) {
            1 -> 0
            2 -> 7
            3 -> 293
            4 -> 493
            5 -> 669
            6 -> 789
            7 -> 954
            8 -> 1160
            9 -> 1235
            10 -> 1364
            11 -> 1473
            12 -> 1596
            13 -> 1707
            14 -> 1750
            15 -> 1802
            16 -> 1901
            17 -> 2029
            18 -> 2140
            19 -> 2250
            20 -> 2348
            21 -> 2483
            22 -> 2595
            23 -> 2673
            24 -> 2791
            25 -> 2855
            26 -> 2932
            27 -> 3159
            28 -> 3252
            29 -> 3340
            30 -> 3409
            31 -> 3469
            32 -> 3503
            33 -> 3533
            34 -> 3606
            35 -> 3660
            36 -> 3705
            37 -> 3788
            38 -> 3970
            39 -> 4058
            40 -> 4133
            41 -> 4218
            42 -> 4272
            43 -> 4325
            44 -> 4414
            45 -> 4473
            46 -> 4510
            47 -> 4545
            48 -> 4583
            49 -> 4612
            50 -> 4630
            51 -> 4675
            52 -> 4735
            53 -> 4784
            54 -> 4846
            55 -> 4901
            56 -> 4979
            57 -> 5075
            58 -> 5104
            59 -> 5126
            60 -> 5150
            61 -> 5163
            62 -> 5177
            63 -> 5188
            64 -> 5199
            65 -> 5217
            66 -> 5229
            67 -> 5241
            68 -> 5271
            69 -> 5323
            70 -> 5375
            71 -> 5419
            72 -> 5447
            73 -> 5475
            74 -> 5495
            75 -> 5551
            76 -> 5591
            77 -> 5622
            78 -> 5672
            79 -> 5712
            80 -> 5758
            81 -> 5800
            82 -> 5829
            83 -> 5848
            84 -> 5884
            85 -> 5909
            86 -> 5931
            87 -> 5948
            88 -> 5967
            89 -> 5993
            90 -> 6023
            91 -> 6043
            92 -> 6058
            93 -> 6079
            94 -> 6090
            95 -> 6098
            96 -> 6106
            97 -> 6125
            98 -> 6130
            99 -> 6138
            100 -> 6146
            101 -> 6157
            102 -> 6168
            103 -> 6176
            104 -> 6179
            105 -> 6188
            106 -> 6193
            107 -> 6197
            108 -> 6204
            109 -> 6207
            110 -> 6213
            111 -> 6216
            112 -> 6221
            113 -> 6225
            114 -> 6230
            else -> 0
        }
        return ayahsBeforeSurah + ayah
    }
}