package io.github.mehrdad_abdi.quranbookmarks.domain.model

data class Bookmark(
    val id: Long = 0,
    val groupId: Long,
    val type: BookmarkType,
    val startSurah: Int,
    val startAyah: Int = 1,
    val endSurah: Int = startSurah,
    val endAyah: Int = startAyah,
    val description: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun getDisplayText(): String {
        return when (type) {
            BookmarkType.AYAH -> "Surah $startSurah:$startAyah"
            BookmarkType.RANGE -> "Surah $startSurah:$startAyah-$endAyah"
            BookmarkType.SURAH -> "Surah $startSurah"
            BookmarkType.PAGE -> "Page $startAyah"
        }
    }
}