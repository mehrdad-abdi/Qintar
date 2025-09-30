package io.github.mehrdad_abdi.quranbookmarks.data.remote.dto

import com.google.gson.annotations.SerializedName

data class SurahListResponseDto(
    val code: Int,
    val status: String,
    val data: List<SurahDto>
)

data class SurahDto(
    val number: Int,
    val name: String,
    @SerializedName("englishName")
    val englishName: String,
    @SerializedName("englishNameTranslation")
    val englishNameTranslation: String,
    @SerializedName("numberOfAyahs")
    val numberOfAyahs: Int,
    @SerializedName("revelationType")
    val revelationType: String
)