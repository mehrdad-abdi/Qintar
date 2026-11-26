package io.github.mehrdad_abdi.qintar.ui.designsystem

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** What a user can observe of the components: what is shown for given inputs, what a tap does. */
@RunWith(RobolectricTestRunner::class)
class ComponentBehaviourTest {

    @get:Rule
    val compose = createComposeRule()

    private fun stateDescription(text: String) =
        SemanticsMatcher("stateDescription = $text") {
            it.config.getOrNull(SemanticsProperties.StateDescription) == text
        }

    // ---- AyahRow ----------------------------------------------------------------------------

    @Test
    fun `tapping the check of an ayah invokes onCheck once per tap, read or not`() {
        var checks = 0
        compose.setContent {
            QintarTheme { AyahRow("نص", "8:1", AyahCheckState.ReadToday, onCheck = { checks++ }) }
        }
        compose.onNodeWithContentDescription("I read this ayah").performClick().performClick()
        assertEquals(2, checks)
    }

    @Test
    fun `the check is at least 48dp in both directions`() {
        compose.setContent { QintarTheme { AyahRow("نص", "8:1", AyahCheckState.Unread, onCheck = {}) } }
        compose.onNodeWithContentDescription("I read this ayah").assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun `an ayah read twice today shows x2 and one read once shows no multiplier`() {
        var count by mutableIntStateOf(2)
        compose.setContent { QintarTheme { AyahRow("نص", "8:2", AyahCheckState.ReadToday, onCheck = {}, readCount = count) } }
        compose.onNodeWithText("×2", useUnmergedTree = true).assertIsDisplayed()
        compose.onNode(stateDescription("read 2 times today")).assertExists()

        count = 1
        compose.onNodeWithText("×1", useUnmergedTree = true).assertDoesNotExist()
        compose.onNode(stateDescription("read today")).assertExists()
    }

    @Test
    fun `ayah states are announced with the ayah's label`() {
        compose.setContent {
            QintarTheme {
                AyahRow("نص", "2:255", AyahCheckState.CoveredEarlier, onCheck = {}, accessibilityLabel = "Al-Baqarah, ayah 255")
            }
        }
        compose.onNodeWithContentDescription("Al-Baqarah, ayah 255, read on an earlier day").assertExists()
    }

    @Test
    fun `star, play, menu and the text each invoke their own callback`() {
        val calls = mutableListOf<String>()
        compose.setContent {
            QintarTheme {
                AyahRow(
                    "نص", "8:1", AyahCheckState.Unread,
                    onCheck = { calls += "check" },
                    accessibilityLabel = "Al-Anfal, ayah 1",
                    onTextClick = { calls += "text" },
                    onPlay = { calls += "play" },
                    onStar = { calls += "star" },
                    onMenu = { calls += "menu" },
                )
            }
        }
        compose.onNodeWithContentDescription("Bookmark").performClick()
        compose.onNodeWithContentDescription("Play").performClick()
        compose.onNodeWithContentDescription("More actions").performClick()
        compose.onNodeWithContentDescription("Al-Anfal, ayah 1, not read today").performClick()
        assertEquals(listOf("star", "play", "menu", "text"), calls)
    }

    // ---- Bookmarks --------------------------------------------------------------------------

    @Test
    fun `a bookmark done today says Read today and one not read does not`() {
        var state by mutableStateOf(ReadState.Done)
        compose.setContent { QintarTheme { BookmarkCard("Al-Mulk", state, onClick = {}) } }
        compose.onNodeWithText("Read today").assertIsDisplayed()

        state = ReadState.NotRead
        compose.onNodeWithText("Read today").assertDoesNotExist()
        compose.onNode(stateDescription("Not read today")).assertExists()
    }

    @Test
    fun `tapping a bookmark card opens it and the group's Read button opens the group`() {
        val calls = mutableListOf<String>()
        compose.setContent {
            QintarTheme {
                androidx.compose.foundation.layout.Column {
                    GroupHeader("Morning", ReadState.NotRead, window = "08:00–12:00", onRead = { calls += "group" })
                    BookmarkCard("Al-Kahf", ReadState.Partial, onClick = { calls += "bookmark" }, progressText = "42 / 110", progress = 0.4f)
                }
            }
        }
        compose.onNodeWithText("Read").performClick()
        compose.onNodeWithText("Al-Kahf").performClick()
        assertEquals(listOf("group", "bookmark"), calls)
        compose.onNodeWithText("42 / 110").assertIsDisplayed()
    }

    @Test
    fun `a group that is done shows no Read button`() {
        compose.setContent { QintarTheme { GroupHeader("Morning", ReadState.Done, onRead = {}) } }
        compose.onNodeWithText("Read").assertDoesNotExist()
        compose.onNode(stateDescription("Read today")).assertExists()
    }

    // ---- Today pill -------------------------------------------------------------------------

    @Test
    fun `the today pill shows the count and the distance, and the reached message when a rank is reached`() {
        var reached by mutableStateOf(false)
        compose.setContent {
            QintarTheme {
                TodayPill(rank = 1, count = 47, caption = "3 to Dhakir", progress = 0.94f, rankReached = reached, reachedText = "Dhakir reached")
            }
        }
        compose.onNodeWithContentDescription("47 today, 3 to Dhakir").assertIsDisplayed()

        reached = true
        compose.onNodeWithContentDescription("Dhakir reached, 47 today").assertIsDisplayed()
    }

    @Test
    fun `tapping the today pill invokes its callback`() {
        var taps = 0
        compose.setContent { QintarTheme { TodayPill(1, 47, "3 to Dhakir", 0.94f, onClick = { taps++ }) } }
        compose.onNodeWithContentDescription("47 today, 3 to Dhakir").performClick()
        assertEquals(1, taps)
    }

    @Test
    @Config(qualifiers = "fa")
    fun `counts are shown in Persian digits in Persian`() {
        compose.setContent { QintarTheme { TodayPill(1, 47, "۳ تا ذاکر", 0.94f) } }
        compose.onNodeWithContentDescription("۴۷ امروز, ۳ تا ذاکر").assertIsDisplayed()
    }

    // ---- Rank marks -------------------------------------------------------------------------

    @Test
    fun `each rank mark is described by its rank name`() {
        compose.setContent {
            QintarTheme { androidx.compose.foundation.layout.Row { for (rank in 1..7) RankMark(rank) } }
        }
        listOf(
            "Not among the heedless", "Dhakir · the remembering", "Qanit · the devout", "Khashi‘ · the humble",
            "Fa’iz · the victorious", "Mujtahid · the striver", "Qintar",
        ).forEach { compose.onNodeWithContentDescription(it).assertExists() }
    }

    @Test
    fun `the ladder announces the current rank`() {
        compose.setContent { QintarTheme { RankLadder(currentRank = 2) } }
        compose.onNodeWithContentDescription("Rank 2 of 7: Dhakir · the remembering").assertExists()
    }

    // ---- Inputs -----------------------------------------------------------------------------

    @Test
    fun `the count field steps up and down, accepts typed digits and stays inside its bounds`() {
        var value by mutableIntStateOf(1)
        compose.setContent { QintarTheme { CountField(value, { value = it }, min = 0, max = 100) } }

        compose.onNodeWithContentDescription("Increase").performClick()
        assertEquals(2, value)
        compose.onNodeWithContentDescription("Decrease").performClick().performClick()
        assertEquals(0, value)
        compose.onNodeWithContentDescription("Decrease").assertIsNotEnabled()

        compose.onNodeWithContentDescription("Count").performTextReplacement("37")
        assertEquals(37, value)
        compose.onNodeWithContentDescription("Count").performTextReplacement("۴۲")
        assertEquals(42, value)
        compose.onNodeWithContentDescription("Count").performTextReplacement("5000")
        assertEquals(100, value)
    }

    @Test
    fun `tapping a weekday chip reports its index`() {
        val toggled = mutableListOf<Int>()
        val names = listOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")
        compose.setContent {
            QintarTheme {
                WeekdayChips(listOf("S", "M", "T", "W", "T", "F", "S"), setOf(1), { toggled += it }, contentDescriptions = names)
            }
        }
        compose.onNodeWithContentDescription("Friday").performClick()
        compose.onNodeWithContentDescription("Monday").performClick()
        assertEquals(listOf(5, 1), toggled)
    }

    // ---- Insights ---------------------------------------------------------------------------

    @Test
    fun `every bar and every calendar day has a text alternative and reports taps`() {
        val tapped = mutableListOf<String>()
        val bars = listOf(
            DayBarUi("Mon", 120, 3, "Monday, 120 ayahs, Qanit"),
            DayBarUi("Tue", 0, 0, "Tuesday, nothing read"),
        )
        val days = listOf(
            MonthDayUi("1", rank = 3, count = 120, contentDescription = "1 November, 120 ayahs, Qanit"),
            MonthDayUi("2", rank = 0, isFuture = true, contentDescription = "2 November"),
        )
        compose.setContent {
            QintarTheme {
                androidx.compose.foundation.layout.Column {
                    DayBarChart(bars, onBarClick = { tapped += "bar$it" })
                    MonthGrid(listOf("S", "M", "T", "W", "T", "F", "S"), leadingBlanks = 3, days = days, onDayClick = { tapped += "day$it" })
                }
            }
        }
        compose.onNodeWithContentDescription("Tuesday, nothing read").assertExists()
        compose.onNodeWithContentDescription("Monday, 120 ayahs, Qanit").performClick()
        compose.onNodeWithContentDescription("1 November, 120 ayahs, Qanit").performClick()
        assertEquals(listOf("bar0", "day0"), tapped)
    }

    // ---- Player -----------------------------------------------------------------------------

    @Test
    fun `the mini player offers pause while playing and play while paused`() {
        var playing by mutableStateOf(true)
        val calls = mutableListOf<String>()
        compose.setContent {
            QintarTheme {
                MiniPlayer("Al-Anfal 2 · Alafasy", playing, "1×", onPlayPause = { playing = !playing }, onSpeed = { calls += "speed" }, onClose = { calls += "close" })
            }
        }
        compose.onNodeWithContentDescription("Pause audio").performClick()
        compose.onNodeWithContentDescription("Play audio").assertIsDisplayed()
        compose.onNodeWithContentDescription("Playback speed 1×").performClick()
        compose.onNodeWithContentDescription("Close player").performClick()
        assertTrue(calls == listOf("speed", "close"))
    }
}
