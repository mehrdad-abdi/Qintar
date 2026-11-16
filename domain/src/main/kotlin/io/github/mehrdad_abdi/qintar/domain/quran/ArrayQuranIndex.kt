package io.github.mehrdad_abdi.qintar.domain.quran

import io.github.mehrdad_abdi.qintar.domain.model.AyahId
import io.github.mehrdad_abdi.qintar.domain.model.AyahRange
import io.github.mehrdad_abdi.qintar.domain.model.AyahRef
import io.github.mehrdad_abdi.qintar.domain.model.Quran
import io.github.mehrdad_abdi.qintar.domain.model.SajdaType
import io.github.mehrdad_abdi.qintar.domain.model.SurahInfo

/** One row of the `ayah` table without its text (§7.4). [sajda]: 0 none, 1 recommended, 2 obligatory. */
data class AyahRow(
    val id: Int,
    val surah: Int,
    val number: Int,
    val page: Int,
    val juz: Int,
    val hizbQuarter: Int,
    val sajda: Int,
)

/**
 * [QuranIndex] over primitive arrays. The data layer builds one at startup from the structural columns
 * of `quran.db`.
 *
 * The constructor refuses data that would make any later answer wrong (gaps in ids, a page that goes
 * backwards, a surah whose rows disagree with its metadata), so a bad asset fails at startup instead of
 * as a wrong count somewhere in the app.
 */
class ArrayQuranIndex(ayahs: List<AyahRow>, surahs: List<SurahInfo>) : QuranIndex {

    override val surahs: List<SurahInfo> = surahs.toList()

    // Index 0 is unused so that arrays are addressed by AyahId directly.
    private val surahOfAyah = IntArray(Quran.AYAH_COUNT + 1)
    private val numberOfAyah = IntArray(Quran.AYAH_COUNT + 1)
    private val pageOfAyah = IntArray(Quran.AYAH_COUNT + 1)
    private val juzOfAyah = IntArray(Quran.AYAH_COUNT + 1)
    private val quarterOfAyah = IntArray(Quran.AYAH_COUNT + 1)
    private val sajdaOfAyah = IntArray(Quran.AYAH_COUNT + 1)

    // firstOfX[n] = first AyahId of unit n; firstOfX[count + 1] = one past the last ayah.
    private val firstOfPage: IntArray
    private val firstOfJuz: IntArray
    private val firstOfQuarter: IntArray

    init {
        require(ayahs.size == Quran.AYAH_COUNT) { "Expected ${Quran.AYAH_COUNT} ayahs, got ${ayahs.size}" }
        require(this.surahs.size == Quran.SURAH_COUNT) { "Expected ${Quran.SURAH_COUNT} surahs, got ${surahs.size}" }

        var expectedFirst = 1
        this.surahs.forEachIndexed { i, s ->
            require(s.number == i + 1) { "Surah rows out of order at ${s.number}" }
            require(s.ayahCount >= 1) { "Surah ${s.number} has no ayahs" }
            require(s.firstAyahId.value == expectedFirst) { "Surah ${s.number} does not start at $expectedFirst" }
            expectedFirst += s.ayahCount
        }
        require(expectedFirst == Quran.AYAH_COUNT + 1) { "Surah ayah counts do not add up to ${Quran.AYAH_COUNT}" }

        ayahs.forEachIndexed { i, row ->
            val id = i + 1
            require(row.id == id) { "Ayah ids not contiguous at $id" }
            require(row.surah in 1..Quran.SURAH_COUNT) { "Ayah $id: surah ${row.surah}" }
            val surah = this.surahs[row.surah - 1]
            require(row.number == id - surah.firstAyahId.value + 1) { "Ayah $id is not ${row.surah}:${row.number}" }
            require(row.page in 1..Quran.PAGE_COUNT) { "Ayah $id: page ${row.page}" }
            require(row.juz in 1..Quran.JUZ_COUNT) { "Ayah $id: juz ${row.juz}" }
            require(row.hizbQuarter in 1..Quran.HIZB_QUARTER_COUNT) { "Ayah $id: hizb quarter ${row.hizbQuarter}" }
            require(row.sajda in 0..2) { "Ayah $id: sajda ${row.sajda}" }
            surahOfAyah[id] = row.surah
            numberOfAyah[id] = row.number
            pageOfAyah[id] = row.page
            juzOfAyah[id] = row.juz
            quarterOfAyah[id] = row.hizbQuarter
            sajdaOfAyah[id] = row.sajda
        }

        firstOfPage = boundaries(pageOfAyah, Quran.PAGE_COUNT, "page")
        firstOfJuz = boundaries(juzOfAyah, Quran.JUZ_COUNT, "juz")
        firstOfQuarter = boundaries(quarterOfAyah, Quran.HIZB_QUARTER_COUNT, "hizb quarter")
    }

    /** Requires the unit number to start at 1, never decrease, never skip, and reach [count]. */
    private fun boundaries(unitOfAyah: IntArray, count: Int, name: String): IntArray {
        val first = IntArray(count + 2)
        var current = 0
        for (id in 1..Quran.AYAH_COUNT) {
            val unit = unitOfAyah[id]
            if (unit != current) {
                require(unit == current + 1) { "Ayah $id: $name jumps from $current to $unit" }
                first[unit] = id
                current = unit
            }
        }
        require(current == count) { "Expected $count ${name}s, found $current" }
        first[count + 1] = Quran.AYAH_COUNT + 1
        return first
    }

    override fun ayahId(ref: AyahRef): AyahId? {
        val surah = surahs[ref.surah - 1]
        return if (ref.ayah <= surah.ayahCount) AyahId(surah.firstAyahId.value + ref.ayah - 1) else null
    }

    override fun ref(id: AyahId): AyahRef = AyahRef(surahOfAyah[id.value], numberOfAyah[id.value])

    override fun pageOf(id: AyahId): Int = pageOfAyah[id.value]

    override fun juzOf(id: AyahId): Int = juzOfAyah[id.value]

    override fun hizbQuarterOf(id: AyahId): Int = quarterOfAyah[id.value]

    override fun sajdaOf(id: AyahId): SajdaType = SajdaType.entries[sajdaOfAyah[id.value]]

    override fun surah(number: Int): SurahInfo {
        require(number in 1..Quran.SURAH_COUNT) { "Surah out of range: $number" }
        return surahs[number - 1]
    }

    override fun pageRange(page: Int): AyahRange = unitRange(firstOfPage, page, Quran.PAGE_COUNT, "Page")

    override fun juzRange(juz: Int): AyahRange = unitRange(firstOfJuz, juz, Quran.JUZ_COUNT, "Juz")

    override fun hizbQuarterRange(quarter: Int): AyahRange =
        unitRange(firstOfQuarter, quarter, Quran.HIZB_QUARTER_COUNT, "Hizb quarter")

    override fun surahRange(number: Int): AyahRange = surah(number).range

    override fun ayahsOnPage(page: Int): Int = pageRange(page).size

    private fun unitRange(first: IntArray, unit: Int, count: Int, name: String): AyahRange {
        require(unit in 1..count) { "$name out of range: $unit" }
        return AyahRange.of(first[unit], first[unit + 1] - 1)
    }
}
