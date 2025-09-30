package io.github.mehrdad_abdi.quranbookmarks.data.remote.dto

import com.google.gson.annotations.SerializedName

data class SurahResponse(
    @SerializedName("code")
    val code: Int,
    @SerializedName("status")
    val status: String,
    @SerializedName("data")
    val data: SurahDetailData
)

data class SurahDetailData(
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
    val numberOfAyahs: Int,
    @SerializedName("ayahs")
    val ayahs: List<AyahData>
)

data class AyahData(
    @SerializedName("number")
    val number: Int,
    @SerializedName("text")
    val text: String,
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
    val sajda: Boolean
)