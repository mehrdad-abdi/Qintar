package io.github.mehrdad_abdi.qintar.domain.testing

import io.github.mehrdad_abdi.qintar.domain.model.AyahId
import io.github.mehrdad_abdi.qintar.domain.model.Quran
import io.github.mehrdad_abdi.qintar.domain.model.Revelation
import io.github.mehrdad_abdi.qintar.domain.model.SurahInfo
import io.github.mehrdad_abdi.qintar.domain.quran.ArrayQuranIndex
import io.github.mehrdad_abdi.qintar.domain.quran.AyahRow
import io.github.mehrdad_abdi.qintar.domain.quran.QuranIndex

/**
 * A [QuranIndex] for tests.
 *
 * Real: the 114 surah ayah counts (6,236 in total), which surahs have a basmala, and the 15 sajda ayahs.
 * Synthetic: names ("Surah 2"), revelation, and the page / juz / hizb layout, which is regular so that
 * tests can reason about it:
 *  - pages: ayahs are spread evenly, 10 or 11 per page, over 604 pages ([pageOf]);
 *  - juz: 20 pages each, the 30th takes the last 24 pages;
 *  - hizb quarters: each juz's ayahs split into 8 near-equal runs.
 * Tests that depend on the real mushaf layout belong in `:data`, against the generated `quran.db`.
 */
object QuranFixture {
    val AYAH_COUNTS: List<Int> = listOf(
        7, 286, 200, 176, 120, 165, 206, 75, 129, 109, 123, 111, 43, 52, 99, 128, 111, 110, 98, 135,
        112, 78, 118, 64, 77, 227, 93, 88, 69, 60, 34, 30, 73, 54, 45, 83, 182, 88, 75, 85,
        54, 53, 89, 59, 37, 35, 38, 29, 18, 45, 60, 49, 62, 55, 78, 96, 29, 22, 24, 13,
        14, 11, 11, 18, 12, 12, 30, 52, 52, 44, 28, 28, 20, 56, 40, 31, 50, 40, 46, 42,
        29, 19, 36, 25, 22, 17, 19, 26, 30, 20, 15, 21, 11, 8, 8, 19, 5, 8, 8, 11,
        11, 8, 3, 9, 5, 4, 7, 3, 6, 3, 5, 4, 5, 6,
    )

    /** (surah, ayah) → sajda code: 1 recommended, 2 obligatory. */
    private val SAJDAS: Map<Pair<Int, Int>, Int> = mapOf(
        (7 to 206) to 1, (13 to 15) to 1, (16 to 50) to 1, (17 to 109) to 1, (19 to 58) to 1,
        (22 to 18) to 1, (22 to 77) to 1, (25 to 60) to 1, (27 to 26) to 1, (32 to 15) to 2,
        (38 to 24) to 1, (41 to 38) to 2, (53 to 62) to 2, (84 to 21) to 1, (96 to 19) to 2,
    )

    private const val PAGES_PER_JUZ = 20

    /** The synthetic page of a global ayah number. */
    fun pageOf(ayahId: Int): Int = 1 + (ayahId - 1) * Quran.PAGE_COUNT / Quran.AYAH_COUNT

    fun juzOfPage(page: Int): Int = minOf(Quran.JUZ_COUNT, 1 + (page - 1) / PAGES_PER_JUZ)

    val surahs: List<SurahInfo> = buildList {
        var first = 1
        AYAH_COUNTS.forEachIndexed { i, count ->
            val number = i + 1
            add(
                SurahInfo(
                    number = number,
                    nameAr = "سورة $number",
                    nameLatin = "Surah $number",
                    nameEn = "Chapter $number",
                    revelation = if (number % 3 == 2) Revelation.MEDINAN else Revelation.MECCAN,
                    ayahCount = count,
                    firstAyahId = AyahId(first),
                    hasBasmala = number != 1 && number != 9,
                ),
            )
            first += count
        }
    }

    val ayahRows: List<AyahRow> = buildList {
        val juzOfAyah = IntArray(Quran.AYAH_COUNT + 1) { if (it == 0) 0 else juzOfPage(pageOf(it)) }
        val juzFirst = IntArray(Quran.JUZ_COUNT + 2)
        for (id in Quran.AYAH_COUNT downTo 1) juzFirst[juzOfAyah[id]] = id
        juzFirst[Quran.JUZ_COUNT + 1] = Quran.AYAH_COUNT + 1
        var id = 1
        for (surah in surahs) {
            for (number in 1..surah.ayahCount) {
                val juz = juzOfAyah[id]
                val juzSize = juzFirst[juz + 1] - juzFirst[juz]
                val quarterInJuz = (id - juzFirst[juz]) * 8 / juzSize
                add(
                    AyahRow(
                        id = id,
                        surah = surah.number,
                        number = number,
                        page = pageOf(id),
                        juz = juz,
                        hizbQuarter = (juz - 1) * 8 + quarterInJuz + 1,
                        sajda = SAJDAS[surah.number to number] ?: 0,
                    ),
                )
                id++
            }
        }
    }

    /** Shared instance; the index is immutable. */
    val index: QuranIndex by lazy { ArrayQuranIndex(ayahRows, surahs) }

    /** `ayah(2, 255)` → the AyahId of 2:255. Fails the test on a reference that does not exist. */
    fun ayah(surah: Int, number: Int): AyahId {
        val info = surahs[surah - 1]
        require(number in 1..info.ayahCount) { "No ayah $surah:$number" }
        return AyahId(info.firstAyahId.value + number - 1)
    }
}
