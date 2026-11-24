package io.github.mehrdad_abdi.qintar.feature.player

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import io.github.mehrdad_abdi.qintar.domain.testing.QuranFixture
import io.github.mehrdad_abdi.qintar.ui.designsystem.QintarTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The mini player's title. */
@RunWith(RobolectricTestRunner::class)
class PlayerBarTest {
    @get:Rule
    val compose = createComposeRule()

    private fun show(state: PlayerBarState) = compose.setContent {
        QintarTheme {
            ReaderPlayerBar(state, {}, {}, {}, {}, {}, {}, {}, {})
        }
    }

    @Test
    fun `the Bismillah is titled as the surah's first ayah`() {
        val surah = QuranFixture.index.surah(36)
        show(PlayerBarState(visible = true, surah = surah, ayahNumber = 1, basmala = true))
        compose.onNodeWithText("${surah.nameLatin} 1", substring = true).assertExists()
        compose.onNodeWithText("Basmala", substring = true).assertDoesNotExist()
    }
}
