package io.github.mehrdad_abdi.qintar.domain.service

import io.github.mehrdad_abdi.qintar.domain.model.AyahId
import io.github.mehrdad_abdi.qintar.domain.model.AyahRange
import io.github.mehrdad_abdi.qintar.domain.model.Bookmark
import io.github.mehrdad_abdi.qintar.domain.model.BookmarkDraft
import io.github.mehrdad_abdi.qintar.domain.model.BookmarkKind
import io.github.mehrdad_abdi.qintar.domain.model.SurahInfo
import io.github.mehrdad_abdi.qintar.domain.quran.QuranIndex
import io.github.mehrdad_abdi.qintar.domain.quran.surahOf

/**
 * What a bookmark is called, as data (§4.7). The UI turns it into text with the surah name of the app
 * language and localized numerals; the domain never builds a display string.
 */
sealed interface BookmarkTitle {
    /** The user's own name for it. */
    data class Label(val text: String) : BookmarkTitle

    /** "Al-Baqarah 255". */
    data class Ayah(val surah: SurahInfo, val ayah: Int) : BookmarkTitle

    /** "Al-Kahf 1–10". */
    data class RangeInSurah(val surah: SurahInfo, val fromAyah: Int, val toAyah: Int) : BookmarkTitle

    /** "Al-Baqarah 285 – Āl ‘Imrān 5". */
    data class RangeAcrossSurahs(
        val fromSurah: SurahInfo,
        val fromAyah: Int,
        val toSurah: SurahInfo,
        val toAyah: Int,
    ) : BookmarkTitle

    /** "Al-Kahf". */
    data class Surah(val surah: SurahInfo) : BookmarkTitle

    /** "Page 293". */
    data class Page(val page: Int) : BookmarkTitle

    /** "Juz 30". */
    data class Juz(val juz: Int) : BookmarkTitle
}

object BookmarkTitles {
    fun of(bookmark: Bookmark, index: QuranIndex): BookmarkTitle =
        of(bookmark.label, bookmark.kind, bookmark.range, index)

    fun of(draft: BookmarkDraft, index: QuranIndex): BookmarkTitle = of(draft.label, draft.kind, draft.range, index)

    fun of(label: String?, kind: BookmarkKind, range: AyahRange, index: QuranIndex): BookmarkTitle {
        label?.trim()?.takeIf { it.isNotEmpty() }?.let { return BookmarkTitle.Label(it) }
        val from = index.ref(range.start)
        val to = index.ref(range.end)
        return when (kind) {
            BookmarkKind.AYAH -> BookmarkTitle.Ayah(index.surah(from.surah), from.ayah)
            BookmarkKind.RANGE ->
                if (from.surah == to.surah) {
                    BookmarkTitle.RangeInSurah(index.surah(from.surah), from.ayah, to.ayah)
                } else {
                    BookmarkTitle.RangeAcrossSurahs(index.surah(from.surah), from.ayah, index.surah(to.surah), to.ayah)
                }
            BookmarkKind.SURAH -> BookmarkTitle.Surah(index.surah(from.surah))
            BookmarkKind.PAGE -> BookmarkTitle.Page(index.pageOf(range.start))
            BookmarkKind.JUZ -> BookmarkTitle.Juz(index.juzOf(range.start))
        }
    }
}

/** Drafts for each way a bookmark can be created, so kind and range always agree. */
object BookmarkDrafts {
    fun ayah(ayah: AyahId) = BookmarkDraft(AyahRange.single(ayah), BookmarkKind.AYAH)

    fun range(from: AyahId, to: AyahId) = BookmarkDraft(AyahRange(from, to), BookmarkKind.RANGE)

    fun surah(number: Int, index: QuranIndex) = BookmarkDraft(index.surahRange(number), BookmarkKind.SURAH)

    fun page(page: Int, index: QuranIndex) = BookmarkDraft(index.pageRange(page), BookmarkKind.PAGE)

    fun juz(juz: Int, index: QuranIndex) = BookmarkDraft(index.juzRange(juz), BookmarkKind.JUZ)

    /** The surah a draft's passage starts in, for editors that show a surah picker. */
    fun startSurah(draft: BookmarkDraft, index: QuranIndex): SurahInfo = index.surahOf(draft.range.start)
}
