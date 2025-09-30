package io.github.mehrdad_abdi.quranbookmarks.domain.usecase.reading

import android.util.Log
import io.github.mehrdad_abdi.quranbookmarks.domain.model.Bookmark
import io.github.mehrdad_abdi.quranbookmarks.domain.model.VerseMetadata
import io.github.mehrdad_abdi.quranbookmarks.domain.repository.BookmarkRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Data class to represent a verse in the playlist with its source bookmark
 */
data class PlaylistVerse(
    val verse: VerseMetadata,
    val bookmark: Bookmark,
    val verseIndexInBookmark: Int
)

/**
 * Use case to get all verses from all bookmarks in a profile for continuous playback
 */
class GetProfilePlaylistUseCase @Inject constructor(
    private val bookmarkRepository: BookmarkRepository,
    private val getBookmarkContentUseCase: GetBookmarkContentUseCase
) {
    companion object {
        private const val TAG = "GetProfilePlaylist"
    }

    suspend operator fun invoke(profileId: Long): Result<List<PlaylistVerse>> {
        return try {
            Log.d(TAG, "Getting playlist for profile $profileId")

            // Get all bookmarks for this profile
            val bookmarks = bookmarkRepository.getBookmarksByGroupId(profileId).first()

            if (bookmarks.isEmpty()) {
                Log.d(TAG, "No bookmarks found for profile $profileId")
                return Result.success(emptyList())
            }

            Log.d(TAG, "Found ${bookmarks.size} bookmarks for profile $profileId")

            // Build playlist by fetching verses for each bookmark
            val playlist = mutableListOf<PlaylistVerse>()

            for (bookmark in bookmarks) {
                val contentResult = getBookmarkContentUseCase(bookmark)

                if (contentResult.isSuccess) {
                    val verses = contentResult.getOrThrow()
                    Log.d(TAG, "Loaded ${verses.size} verses for bookmark ${bookmark.id}")

                    verses.forEachIndexed { index, verse ->
                        playlist.add(
                            PlaylistVerse(
                                verse = verse,
                                bookmark = bookmark,
                                verseIndexInBookmark = index
                            )
                        )
                    }
                } else {
                    Log.e(TAG, "Failed to load content for bookmark ${bookmark.id}: ${contentResult.exceptionOrNull()?.message}")
                    // Continue with other bookmarks even if one fails
                }
            }

            Log.d(TAG, "Total playlist size: ${playlist.size} verses")
            Result.success(playlist)

        } catch (e: Exception) {
            Log.e(TAG, "Error getting profile playlist", e)
            Result.failure(e)
        }
    }
}