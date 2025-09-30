package io.github.mehrdad_abdi.quranbookmarks.data.cache

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manager for downloading and caching files (audio and images) locally
 */
@Singleton
class FileDownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient
) {
    companion object {
        private const val TAG = "FileDownloadManager"
        private const val AUDIO_CACHE_DIR = "quran_audio"
        private const val IMAGE_CACHE_DIR = "quran_images"
    }

    /**
     * Download an audio file and return the local file path
     * Returns null if download fails
     */
    suspend fun downloadAudioFile(
        url: String,
        surah: Int,
        ayah: Int
    ): String? = withContext(Dispatchers.IO) {
        try {
            val fileName = "audio_${surah}_${ayah}.mp3"
            val cacheDir = File(context.cacheDir, AUDIO_CACHE_DIR)
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }

            val outputFile = File(cacheDir, fileName)

            // Check if file already exists and is valid
            if (outputFile.exists() && outputFile.length() > 0) {
                Log.d(TAG, "Audio file already cached: ${outputFile.absolutePath}")
                return@withContext outputFile.absolutePath
            }

            Log.d(TAG, "Downloading audio from: $url")

            val request = Request.Builder()
                .url(url)
                .build()

            val response = okHttpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                Log.e(TAG, "Failed to download audio: HTTP ${response.code}")
                return@withContext null
            }

            val inputStream = response.body?.byteStream()
            if (inputStream == null) {
                Log.e(TAG, "Response body is null for audio download")
                return@withContext null
            }

            // Download to temporary file first
            val tempFile = File(cacheDir, "$fileName.tmp")
            FileOutputStream(tempFile).use { output ->
                inputStream.use { input ->
                    input.copyTo(output)
                }
            }

            // Rename temp file to final file
            if (tempFile.renameTo(outputFile)) {
                Log.d(TAG, "Audio downloaded successfully: ${outputFile.absolutePath} (${outputFile.length()} bytes)")
                outputFile.absolutePath
            } else {
                Log.e(TAG, "Failed to rename temp file to output file")
                tempFile.delete()
                null
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error downloading audio file: ${e.message}", e)
            null
        }
    }

    /**
     * Download an image file and return the local file path
     * Returns null if download fails
     */
    suspend fun downloadImageFile(
        url: String,
        surah: Int,
        ayah: Int
    ): String? = withContext(Dispatchers.IO) {
        try {
            val fileName = "image_${surah}_${ayah}.png"
            val cacheDir = File(context.cacheDir, IMAGE_CACHE_DIR)
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }

            val outputFile = File(cacheDir, fileName)

            // Check if file already exists and is valid
            if (outputFile.exists() && outputFile.length() > 0) {
                Log.d(TAG, "Image file already cached: ${outputFile.absolutePath}")
                return@withContext outputFile.absolutePath
            }

            Log.d(TAG, "Downloading image from: $url")

            val request = Request.Builder()
                .url(url)
                .build()

            val response = okHttpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                Log.e(TAG, "Failed to download image: HTTP ${response.code}")
                return@withContext null
            }

            val inputStream = response.body?.byteStream()
            if (inputStream == null) {
                Log.e(TAG, "Response body is null for image download")
                return@withContext null
            }

            // Download to temporary file first
            val tempFile = File(cacheDir, "$fileName.tmp")
            FileOutputStream(tempFile).use { output ->
                inputStream.use { input ->
                    input.copyTo(output)
                }
            }

            // Rename temp file to final file
            if (tempFile.renameTo(outputFile)) {
                Log.d(TAG, "Image downloaded successfully: ${outputFile.absolutePath} (${outputFile.length()} bytes)")
                outputFile.absolutePath
            } else {
                Log.e(TAG, "Failed to rename temp file to output file")
                tempFile.delete()
                null
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error downloading image file: ${e.message}", e)
            null
        }
    }

    /**
     * Check if an audio file is cached locally
     */
    fun isAudioFileCached(surah: Int, ayah: Int): Boolean {
        val fileName = "audio_${surah}_${ayah}.mp3"
        val cacheDir = File(context.cacheDir, AUDIO_CACHE_DIR)
        val file = File(cacheDir, fileName)
        return file.exists() && file.length() > 0
    }

    /**
     * Check if an image file is cached locally
     */
    fun isImageFileCached(surah: Int, ayah: Int): Boolean {
        val fileName = "image_${surah}_${ayah}.png"
        val cacheDir = File(context.cacheDir, IMAGE_CACHE_DIR)
        val file = File(cacheDir, fileName)
        return file.exists() && file.length() > 0
    }

    /**
     * Get the local path for a cached audio file
     */
    fun getAudioFilePath(surah: Int, ayah: Int): String? {
        val fileName = "audio_${surah}_${ayah}.mp3"
        val cacheDir = File(context.cacheDir, AUDIO_CACHE_DIR)
        val file = File(cacheDir, fileName)
        return if (file.exists() && file.length() > 0) {
            file.absolutePath
        } else {
            null
        }
    }

    /**
     * Get the local path for a cached image file
     */
    fun getImageFilePath(surah: Int, ayah: Int): String? {
        val fileName = "image_${surah}_${ayah}.png"
        val cacheDir = File(context.cacheDir, IMAGE_CACHE_DIR)
        val file = File(cacheDir, fileName)
        return if (file.exists() && file.length() > 0) {
            file.absolutePath
        } else {
            null
        }
    }

    /**
     * Delete a cached audio file
     */
    fun deleteAudioFile(surah: Int, ayah: Int): Boolean {
        val fileName = "audio_${surah}_${ayah}.mp3"
        val cacheDir = File(context.cacheDir, AUDIO_CACHE_DIR)
        val file = File(cacheDir, fileName)
        return if (file.exists()) {
            file.delete()
        } else {
            false
        }
    }

    /**
     * Delete a cached image file
     */
    fun deleteImageFile(surah: Int, ayah: Int): Boolean {
        val fileName = "image_${surah}_${ayah}.png"
        val cacheDir = File(context.cacheDir, IMAGE_CACHE_DIR)
        val file = File(cacheDir, fileName)
        return if (file.exists()) {
            file.delete()
        } else {
            false
        }
    }

    /**
     * Get total cache size in bytes
     */
    fun getCacheSize(): Long {
        val audioDir = File(context.cacheDir, AUDIO_CACHE_DIR)
        val imageDir = File(context.cacheDir, IMAGE_CACHE_DIR)

        var totalSize = 0L

        if (audioDir.exists()) {
            audioDir.listFiles()?.forEach { file ->
                totalSize += file.length()
            }
        }

        if (imageDir.exists()) {
            imageDir.listFiles()?.forEach { file ->
                totalSize += file.length()
            }
        }

        return totalSize
    }

    /**
     * Clear all cached files
     */
    fun clearAllCache(): Boolean {
        val audioDir = File(context.cacheDir, AUDIO_CACHE_DIR)
        val imageDir = File(context.cacheDir, IMAGE_CACHE_DIR)

        var success = true

        if (audioDir.exists()) {
            success = success && audioDir.deleteRecursively()
        }

        if (imageDir.exists()) {
            success = success && imageDir.deleteRecursively()
        }

        return success
    }
}