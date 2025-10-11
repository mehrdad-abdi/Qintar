package io.github.mehrdad_abdi.quranbookmarks.domain.usecase.backup

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/**
 * Use case for file operations using Storage Access Framework.
 */
class FileOperationsUseCase @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /**
     * Generate default backup filename with timestamp.
     */
    fun generateBackupFilename(): String {
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"))
        return "quran_bookmarks_backup_$timestamp.json"
    }

    /**
     * Write backup JSON to URI (from SAF).
     */
    suspend fun writeBackupToUri(uri: Uri, jsonContent: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(jsonContent.toByteArray())
                }
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    /**
     * Read backup JSON from URI (from SAF).
     */
    suspend fun readBackupFromUri(uri: Uri): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val stringBuilder = StringBuilder()
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    BufferedReader(InputStreamReader(inputStream)).use { reader ->
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            stringBuilder.append(line)
                        }
                    }
                }
                Result.success(stringBuilder.toString())
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    /**
     * Get file size from URI.
     */
    fun getFileSize(uri: Uri): Long? {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                cursor.moveToFirst()
                cursor.getLong(sizeIndex)
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Get filename from URI.
     */
    fun getFileName(uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                cursor.moveToFirst()
                cursor.getString(nameIndex)
            }
        } catch (e: Exception) {
            null
        }
    }
}
