package io.github.mehrdad_abdi.qintar.data.quran

import io.github.mehrdad_abdi.qintar.domain.model.AyahId
import io.github.mehrdad_abdi.qintar.domain.model.AyahRange
import io.github.mehrdad_abdi.qintar.domain.model.Revelation
import io.github.mehrdad_abdi.qintar.domain.model.SurahInfo
import io.github.mehrdad_abdi.qintar.domain.port.QuranTextSource
import io.github.mehrdad_abdi.qintar.domain.quran.ArrayQuranIndex
import io.github.mehrdad_abdi.qintar.domain.quran.AyahRow
import io.github.mehrdad_abdi.qintar.domain.quran.QuranIndex
import javax.inject.Inject
import javax.inject.Singleton
import io.github.mehrdad_abdi.qintar.domain.port.AyahText as DomainAyahText

/** Verbatim text from the bundled `quran.db` asset, read on demand (§7.4). */
@Singleton
class AssetQuranTextSource @Inject constructor(private val dao: QuranDao) : QuranTextSource {
    override suspend fun ayahs(range: AyahRange): List<DomainAyahText> =
        dao.ayahTexts(range.start.value, range.end.value).map { DomainAyahText(AyahId(it.id), it.text) }

    /** Empty for a page outside 1…604. */
    override suspend fun page(page: Int): List<DomainAyahText> =
        dao.ayahTextsOnPage(page).map { DomainAyahText(AyahId(it.id), it.text) }
}

/** Builds the in-memory [QuranIndex] from the structural columns of `quran.db`; the text is not loaded. */
object QuranIndexLoader {
    /** Throws `IllegalArgumentException` if the asset is not a complete, consistent mushaf (see [ArrayQuranIndex]). */
    suspend fun load(dao: QuranDao): QuranIndex {
        val surahs = dao.surahs().map {
            SurahInfo(
                number = it.number,
                nameAr = it.nameAr,
                nameLatin = it.nameLatin,
                nameEn = it.nameEn,
                revelation = Revelation.valueOf(it.revelation),
                ayahCount = it.ayahCount,
                firstAyahId = AyahId(it.firstAyahId),
                hasBasmala = it.hasBasmala,
            )
        }
        val ayahs = dao.ayahStructures().map { AyahRow(it.id, it.surah, it.number, it.page, it.juz, it.hizbQuarter, it.sajda) }
        return ArrayQuranIndex(ayahs, surahs)
    }
}
