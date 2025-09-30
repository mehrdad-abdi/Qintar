package io.github.mehrdad_abdi.quranbookmarks.domain.model

data class Surah(
    val number: Int,
    val name: String,
    val englishName: String,
    val englishNameTranslation: String,
    val numberOfAyahs: Int,
    val revelationType: String
) {
    fun getDisplayName(): String = "$number $englishName $name"

    fun getSearchableText(): String = "$number $englishName $name $englishNameTranslation"
}