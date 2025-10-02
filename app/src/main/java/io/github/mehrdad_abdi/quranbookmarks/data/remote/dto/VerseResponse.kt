package io.github.mehrdad_abdi.quranbookmarks.data.remote.dto

import com.google.gson.annotations.SerializedName

data class VerseResponse(
    @SerializedName("code")
    val code: Int,
    @SerializedName("status")
    val status: String,
    @SerializedName("data")
    val data: VerseData
)

data class VerseData(
    @SerializedName("number")
    val number: Int,
    @SerializedName("text")
    val text: String,
    @SerializedName("surah")
    val surah: SurahData,
    @SerializedName("numberInSurah")
    val numberInSurah: Int,
    @SerializedName("juz")
    val juz: Int,
    @SerializedName("manzil")
    val manzil: Int,
    @SerializedName("page")
    val page: Int,
    @SerializedName("ruku")
    val ruku: Int,
    @SerializedName("hizbQuarter")
    val hizbQuarter: Int,
    @SerializedName("sajda")
    val sajda: Boolean,
    @SerializedName("audio")
    val audio: String? = null
)

data class SurahData(
    @SerializedName("number")
    val number: Int,
    @SerializedName("name")
    val name: String,
    @SerializedName("englishName")
    val englishName: String,
    @SerializedName("englishNameTranslation")
    val englishNameTranslation: String,
    @SerializedName("revelationType")
    val revelationType: String,
    @SerializedName("numberOfAyahs")
    val numberOfAyahs: Int
)