package io.github.mehrdad_abdi.qintar.feature.bookmarks

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import io.github.mehrdad_abdi.qintar.ui.designsystem.QuranTextSizeMinSp
import io.github.mehrdad_abdi.qintar.ui.designsystem.quranDisplay
import io.github.mehrdad_abdi.qintar.ui.designsystem.quranTextStyle

/** Test tag of a bookmark card's glimpse. */
const val GLIMPSE_TAG = "bookmark-glimpse"

/** How many lines a passage may take on its card and still be shown whole. */
internal const val GLIMPSE_WHOLE_LINES = 3

/** The ellipsis between the start and the end of a long passage. */
private const val ELLIPSIS = "…"

/**
 * A bookmark's text on its card (owner, 2026-11-26): the whole passage when it fits in
 * [GLIMPSE_WHOLE_LINES] lines at this width, otherwise the first line of its start ending in "…",
 * then the last line of its end. Lines are cut where the text wraps, so never inside a word. The
 * text is the Quran text unchanged; only which part is drawn.
 */
@Composable
fun PassageGlimpse(glimpse: Glimpse, color: Color, modifier: Modifier = Modifier) {
    val style = quranTextStyle(QuranTextSizeMinSp)
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(modifier.fillMaxWidth().testTag(GLIMPSE_TAG)) {
        val width = constraints.maxWidth
        val lines = remember(glimpse, width, style) { glimpseLines(measurer, glimpse, style, width) }
        Column(Modifier.fillMaxWidth()) {
            lines.forEach { line -> Text(quranDisplay(line), style = style, color = color, modifier = Modifier.fillMaxWidth()) }
        }
    }
}

/** The lines to draw: one string when the passage is shown whole, else the start's first line and the end's last. */
internal fun glimpseLines(measurer: TextMeasurer, glimpse: Glimpse, style: TextStyle, width: Int): List<String> {
    if (width <= 0) return listOf(glimpse.head)
    val whole = measurer.measure(glimpse.head, style, constraints = Constraints(maxWidth = width))
    if (glimpse.tail == null && whole.lineCount <= GLIMPSE_WHOLE_LINES) return listOf(glimpse.head)
    // The first line is measured narrower by the width of " …", so the dots fit after its last word.
    val dots = measurer.measure(" $ELLIPSIS", style).size.width
    val narrow = measurer.measure(glimpse.head, style, constraints = Constraints(maxWidth = (width - dots).coerceAtLeast(1)))
    val first = glimpse.head.substring(0, narrow.getLineEnd(0, visibleEnd = true)).trimEnd() + " " + ELLIPSIS
    val end = glimpse.tail ?: glimpse.head
    val tail = if (glimpse.tail == null) whole else measurer.measure(end, style, constraints = Constraints(maxWidth = width))
    val last = end.substring(tail.getLineStart(tail.lineCount - 1)).trim()
    return listOf(first, last)
}
