package io.github.mehrdad_abdi.quranbookmarks.domain.model

object SurahNames {
    // Number of ayahs in each surah
    private val ayahCounts = intArrayOf(
        7, 286, 200, 176, 120, 165, 206, 75, 129, 109, // 1-10
        123, 111, 43, 52, 99, 128, 111, 110, 98, 135, // 11-20
        112, 78, 118, 64, 77, 227, 93, 88, 69, 60, // 21-30
        34, 30, 73, 54, 45, 83, 182, 88, 75, 85, // 31-40
        54, 53, 89, 59, 37, 35, 38, 29, 18, 45, // 41-50
        60, 49, 62, 55, 78, 96, 29, 22, 24, 13, // 51-60
        14, 11, 11, 18, 12, 12, 30, 52, 52, 44, // 61-70
        28, 28, 20, 56, 40, 31, 50, 40, 46, 42, // 71-80
        29, 19, 36, 25, 22, 17, 19, 26, 30, 20, // 81-90
        15, 21, 11, 8, 8, 19, 5, 8, 8, 11, // 91-100
        11, 8, 3, 9, 5, 4, 7, 3, 6, 3, // 101-110
        5, 4, 5, 6 // 111-114
    )

    // Cumulative ayah counts (starting position for each surah)
    // This is calculated once to optimize lookups
    private val cumulativeAyahCounts: IntArray by lazy {
        IntArray(115) { surahIndex ->
            if (surahIndex == 0) {
                0 // Before surah 1
            } else {
                ayahCounts.take(surahIndex).sum()
            }
        }
    }

    private val surahNames = mapOf(
        1 to "Al-Fatihah",
        2 to "Al-Baqarah",
        3 to "Ali 'Imran",
        4 to "An-Nisa",
        5 to "Al-Ma'idah",
        6 to "Al-An'am",
        7 to "Al-A'raf",
        8 to "Al-Anfal",
        9 to "At-Tawbah",
        10 to "Yunus",
        11 to "Hud",
        12 to "Yusuf",
        13 to "Ar-Ra'd",
        14 to "Ibrahim",
        15 to "Al-Hijr",
        16 to "An-Nahl",
        17 to "Al-Isra",
        18 to "Al-Kahf",
        19 to "Maryam",
        20 to "Taha",
        21 to "Al-Anbya",
        22 to "Al-Hajj",
        23 to "Al-Mu'minun",
        24 to "An-Nur",
        25 to "Al-Furqan",
        26 to "Ash-Shu'ara",
        27 to "An-Naml",
        28 to "Al-Qasas",
        29 to "Al-'Ankabut",
        30 to "Ar-Rum",
        31 to "Luqman",
        32 to "As-Sajdah",
        33 to "Al-Ahzab",
        34 to "Saba",
        35 to "Fatir",
        36 to "Ya-Sin",
        37 to "As-Saffat",
        38 to "Sad",
        39 to "Az-Zumar",
        40 to "Ghafir",
        41 to "Fussilat",
        42 to "Ash-Shuraa",
        43 to "Az-Zukhruf",
        44 to "Ad-Dukhan",
        45 to "Al-Jathiyah",
        46 to "Al-Ahqaf",
        47 to "Muhammad",
        48 to "Al-Fath",
        49 to "Al-Hujurat",
        50 to "Qaf",
        51 to "Adh-Dhariyat",
        52 to "At-Tur",
        53 to "An-Najm",
        54 to "Al-Qamar",
        55 to "Ar-Rahman",
        56 to "Al-Waqi'ah",
        57 to "Al-Hadid",
        58 to "Al-Mujadila",
        59 to "Al-Hashr",
        60 to "Al-Mumtahanah",
        61 to "As-Saf",
        62 to "Al-Jumu'ah",
        63 to "Al-Munafiqun",
        64 to "At-Taghabun",
        65 to "At-Talaq",
        66 to "At-Tahrim",
        67 to "Al-Mulk",
        68 to "Al-Qalam",
        69 to "Al-Haqqah",
        70 to "Al-Ma'arij",
        71 to "Nuh",
        72 to "Al-Jinn",
        73 to "Al-Muzzammil",
        74 to "Al-Muddaththir",
        75 to "Al-Qiyamah",
        76 to "Al-Insan",
        77 to "Al-Mursalat",
        78 to "An-Naba",
        79 to "An-Nazi'at",
        80 to "Abasa",
        81 to "At-Takwir",
        82 to "Al-Infitar",
        83 to "Al-Mutaffifin",
        84 to "Al-Inshiqaq",
        85 to "Al-Buruj",
        86 to "At-Tariq",
        87 to "Al-A'la",
        88 to "Al-Ghashiyah",
        89 to "Al-Fajr",
        90 to "Al-Balad",
        91 to "Ash-Shams",
        92 to "Al-Layl",
        93 to "Ad-Duhaa",
        94 to "Ash-Sharh",
        95 to "At-Tin",
        96 to "Al-'Alaq",
        97 to "Al-Qadr",
        98 to "Al-Bayyinah",
        99 to "Az-Zalzalah",
        100 to "Al-'Adiyat",
        101 to "Al-Qari'ah",
        102 to "At-Takathur",
        103 to "Al-'Asr",
        104 to "Al-Humazah",
        105 to "Al-Fil",
        106 to "Quraysh",
        107 to "Al-Ma'un",
        108 to "Al-Kawthar",
        109 to "Al-Kafirun",
        110 to "An-Nasr",
        111 to "Al-Masad",
        112 to "Al-Ikhlas",
        113 to "Al-Falaq",
        114 to "An-Nas"
    )

    fun getName(number: Int): String {
        return surahNames[number] ?: "Surah $number"
    }

    /**
     * Get the number of ayahs in a surah
     * @param surahNumber Surah number (1-114)
     * @return Number of ayahs in the surah, or 0 if invalid
     */
    fun getAyahCount(surahNumber: Int): Int {
        return if (surahNumber in 1..114) {
            ayahCounts[surahNumber - 1]
        } else {
            0
        }
    }

    /**
     * Calculate the absolute ayah number (1-6236) from surah and ayah numbers
     * @param surahNumber Surah number (1-114)
     * @param ayahNumber Ayah number within the surah (1-based)
     * @return Absolute ayah number (1-6236), or -1 if invalid
     */
    fun getAbsoluteAyahNumber(surahNumber: Int, ayahNumber: Int): Int {
        if (surahNumber !in 1..114) return -1
        if (ayahNumber < 1 || ayahNumber > getAyahCount(surahNumber)) return -1

        return cumulativeAyahCounts[surahNumber - 1] + ayahNumber
    }

    /**
     * Get the total number of ayahs in the Quran
     */
    fun getTotalAyahCount(): Int = 6236
}
