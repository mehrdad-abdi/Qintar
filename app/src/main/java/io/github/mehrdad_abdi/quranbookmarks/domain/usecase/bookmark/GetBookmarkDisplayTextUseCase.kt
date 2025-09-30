package io.github.mehrdad_abdi.quranbookmarks.domain.usecase.bookmark

import io.github.mehrdad_abdi.quranbookmarks.domain.model.Bookmark
import io.github.mehrdad_abdi.quranbookmarks.domain.model.BookmarkType
import io.github.mehrdad_abdi.quranbookmarks.domain.repository.SurahRepository
import javax.inject.Inject

class GetBookmarkDisplayTextUseCase @Inject constructor(
    private val surahRepository: SurahRepository
) {
    suspend operator fun invoke(bookmark: Bookmark): String {
        return when (bookmark.type) {
            BookmarkType.AYAH -> {
                val surahResult = surahRepository.getSurahByNumber(bookmark.startSurah)
                val surah = surahResult.getOrNull()
                if (surah != null) {
                    "${surah.name} ${bookmark.startAyah}"
                } else {
                    "Surah ${bookmark.startSurah}:${bookmark.startAyah}"
                }
            }
            BookmarkType.RANGE -> {
                val surahResult = surahRepository.getSurahByNumber(bookmark.startSurah)
                val surah = surahResult.getOrNull()
                if (surah != null) {
                    "${surah.name} ${bookmark.startAyah}-${bookmark.endAyah}"
                } else {
                    "Surah ${bookmark.startSurah}:${bookmark.startAyah}-${bookmark.endAyah}"
                }
            }
            BookmarkType.SURAH -> {
                val surahResult = surahRepository.getSurahByNumber(bookmark.startSurah)
                val surah = surahResult.getOrNull()
                if (surah != null) {
                    surah.name
                } else {
                    "Surah ${bookmark.startSurah}"
                }
            }
            BookmarkType.PAGE -> "Page ${bookmark.startAyah}"
        }
    }
}