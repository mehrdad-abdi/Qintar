package io.github.mehrdad_abdi.qintar.ui.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.clipRect

/** Where a row stands in a card that is split over several rows of a lazy list. */
enum class CardSegment {
    /** The whole card in one row. */
    SINGLE,
    TOP,
    MIDDLE,
    BOTTOM,
}

private val CardSegment.shape: Shape
    get() = when (this) {
        CardSegment.SINGLE -> QintarShapes.Card
        CardSegment.TOP -> RoundedCornerShape(topStart = Radii.card, topEnd = Radii.card)
        CardSegment.MIDDLE -> RectangleShape
        CardSegment.BOTTOM -> RoundedCornerShape(bottomStart = Radii.card, bottomEnd = Radii.card)
    }

/**
 * Draws this row as its [segment] of one [QCard]-like card (surface tone, hairline border, card
 * corners): the rows of a lazy list then read as one card, e.g. a bookmark with its ayahs. Only the
 * outer edges get corners and border; where two segments meet there is none.
 */
fun Modifier.cardSegment(segment: CardSegment, color: Color, borderColor: Color): Modifier =
    this
        .background(color, segment.shape)
        .drawWithContent {
            drawContent()
            val stroke = Sizes.hairline.toPx()
            val outline = segment.shape.createOutline(size, layoutDirection, this)
            when (segment) {
                CardSegment.SINGLE -> drawOutline(outline, borderColor, style = Stroke(stroke))
                CardSegment.TOP -> clipRect(bottom = size.height - stroke) { drawOutline(outline, borderColor, style = Stroke(stroke)) }
                CardSegment.BOTTOM -> clipRect(top = stroke) { drawOutline(outline, borderColor, style = Stroke(stroke)) }
                CardSegment.MIDDLE -> {
                    drawLine(borderColor, Offset(stroke / 2, 0f), Offset(stroke / 2, size.height), stroke)
                    drawLine(borderColor, Offset(size.width - stroke / 2, 0f), Offset(size.width - stroke / 2, size.height), stroke)
                }
            }
        }
