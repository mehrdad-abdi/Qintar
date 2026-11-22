package io.github.mehrdad_abdi.qintar.domain.quran

import io.github.mehrdad_abdi.qintar.domain.model.AyahId
import io.github.mehrdad_abdi.qintar.domain.model.AyahRange
import io.github.mehrdad_abdi.qintar.domain.model.AyahRef
import io.github.mehrdad_abdi.qintar.domain.model.SajdaType
import io.github.mehrdad_abdi.qintar.domain.model.SurahInfo

/**
 * Every structural question about the mushaf, answered synchronously from memory (§7.4). It never
 * holds text. Numbers out of range (surah 115, page 0) throw [IllegalArgumentException]; only
 * [ayahId] returns null, because it is the one lookup fed by user input.
 */
interface QuranIndex {
    /** All 114 surahs in order. */
    val surahs: List<SurahInfo>

    /** The ayah a reference points to, or null when that surah has no such ayah. */
    fun ayahId(ref: AyahRef): AyahId?

    fun ref(id: AyahId): AyahRef

    fun pageOf(id: AyahId): Int

    fun juzOf(id: AyahId): Int

    fun hizbQuarterOf(id: AyahId): Int

    fun sajdaOf(id: AyahId): SajdaType

    fun surah(number: Int): SurahInfo

    fun pageRange(page: Int): AyahRange

    fun juzRange(juz: Int): AyahRange

    fun hizbQuarterRange(quarter: Int): AyahRange

    fun surahRange(number: Int): AyahRange

    fun ayahsOnPage(page: Int): Int
}

fun QuranIndex.ayahId(surah: Int, ayah: Int): AyahId? =
    if (surah in 1..surahs.size && ayah >= 1) ayahId(AyahRef(surah, ayah)) else null

fun QuranIndex.surahOf(id: AyahId): SurahInfo = surah(ref(id).surah)

/** "110 ayahs · pages 293–304" for the bookmark editor and the manual log (§5.6, §5.8). */
data class PassageSummary(val ayahCount: Int, val firstPage: Int, val lastPage: Int)

fun QuranIndex.summary(range: AyahRange): PassageSummary =
    PassageSummary(range.size, pageOf(range.start), pageOf(range.end))

/** Contiguous surahs `from..to` as one passage (manual log "whole surah(s)"). */
fun QuranIndex.surahsRange(from: Int, to: Int): AyahRange = AyahRange(surahRange(from).start, surahRange(to).end)

/** Contiguous pages `from..to` as one passage. */
fun QuranIndex.pagesRange(from: Int, to: Int): AyahRange = AyahRange(pageRange(from).start, pageRange(to).end)
