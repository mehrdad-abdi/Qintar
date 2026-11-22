package io.github.mehrdad_abdi.qintar.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import io.github.mehrdad_abdi.qintar.R
import io.github.mehrdad_abdi.qintar.domain.model.Revelation
import io.github.mehrdad_abdi.qintar.domain.model.SurahInfo
import io.github.mehrdad_abdi.qintar.domain.service.BookmarkTitle
import io.github.mehrdad_abdi.qintar.ui.designsystem.formatNumber

/*
 * How Quran positions are named on screen. Every feature uses these, so that "Al-Anfal 12" reads the
 * same in the reader, on Today and in the bookmark lists.
 */

/** Arabic name in Persian and Arabic, the transliterated name elsewhere. */
fun SurahInfo.displayName(language: String): String = if (language == "fa" || language == "ar") nameAr else nameLatin

@Composable
@ReadOnlyComposable
fun surahName(surah: SurahInfo): String = surah.displayName(LocalConfiguration.current.locales[0].language)

/** "Al-Anfal 12". */
@Composable
@ReadOnlyComposable
fun ayahName(surah: SurahInfo, ayah: Int): String =
    stringResource(R.string.ayah_name, surahName(surah), formatNumber(ayah))

/** "75 ayahs · Medinan". */
@Composable
@ReadOnlyComposable
fun surahFacts(surah: SurahInfo): String = stringResource(
    R.string.surah_facts,
    formatNumber(surah.ayahCount),
    stringResource(if (surah.revelation == Revelation.MECCAN) R.string.revelation_meccan else R.string.revelation_medinan),
)

/** The title of a bookmark: its label, or a name derived from its passage (§4.7). */
@Composable
@ReadOnlyComposable
fun bookmarkTitleText(title: BookmarkTitle): String = when (title) {
    is BookmarkTitle.Label -> title.text
    is BookmarkTitle.Ayah -> ayahName(title.surah, title.ayah)
    is BookmarkTitle.RangeInSurah -> stringResource(
        R.string.bookmark_title_range,
        surahName(title.surah),
        formatNumber(title.fromAyah),
        formatNumber(title.toAyah),
    )
    is BookmarkTitle.RangeAcrossSurahs -> stringResource(
        R.string.bookmark_title_across,
        ayahName(title.fromSurah, title.fromAyah),
        ayahName(title.toSurah, title.toAyah),
    )
    is BookmarkTitle.Surah -> surahName(title.surah)
    is BookmarkTitle.Page -> stringResource(R.string.page_n, formatNumber(title.page))
    is BookmarkTitle.Juz -> stringResource(R.string.juz_n, formatNumber(title.juz))
}
