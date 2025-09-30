package io.github.mehrdad_abdi.quranbookmarks.data.remote.dto

import com.google.gson.annotations.SerializedName

data class PageResponse(
    @SerializedName("code")
    val code: Int,
    @SerializedName("status")
    val status: String,
    @SerializedName("data")
    val data: PageData
)

data class PageData(
    @SerializedName("number")
    val number: Int,
    @SerializedName("ayahs")
    val ayahs: List<AyahData>
)