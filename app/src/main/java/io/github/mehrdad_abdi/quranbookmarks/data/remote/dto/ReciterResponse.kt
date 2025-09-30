package io.github.mehrdad_abdi.quranbookmarks.data.remote.dto

import com.google.gson.annotations.SerializedName

data class ReciterResponse(
    @SerializedName("code")
    val code: Int,
    @SerializedName("status")
    val status: String,
    @SerializedName("data")
    val data: List<ReciterData>
)

data class ReciterData(
    @SerializedName("identifier")
    val identifier: String,
    @SerializedName("language")
    val language: String,
    @SerializedName("name")
    val name: String,
    @SerializedName("englishName")
    val englishName: String,
    @SerializedName("format")
    val format: String,
    @SerializedName("type")
    val type: String,
    @SerializedName("direction")
    val direction: String?
)