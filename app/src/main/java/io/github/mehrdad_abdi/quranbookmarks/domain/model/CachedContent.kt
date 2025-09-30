package io.github.mehrdad_abdi.quranbookmarks.domain.model

data class CachedContent(
    val id: String,
    val surah: Int,
    val ayah: Int,
    val imagePath: String? = null,
    val audioPath: String? = null,
    val metadata: VerseMetadata? = null,
    val downloadedAt: Long = System.currentTimeMillis()
)