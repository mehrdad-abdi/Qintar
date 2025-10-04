package io.github.mehrdad_abdi.quranbookmarks.domain.model

data class Bookmark(
    val id: Long = 0,
    val type: BookmarkType,
    val startSurah: Int,
    val startAyah: Int = 1,
    val endSurah: Int = startSurah,
    val endAyah: Int = startAyah,
    val description: String = "",
    val tags: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun getDisplayText(): String {
        return when (type) {
            BookmarkType.AYAH -> "${SurahNames.getName(startSurah)} $startAyah"
            BookmarkType.RANGE -> "${SurahNames.getName(startSurah)} $startAyah-$endAyah"
            BookmarkType.SURAH -> SurahNames.getName(startSurah)
            BookmarkType.PAGE -> "Page $startAyah"
        }
    }
}