package io.github.mehrdad_abdi.qintar.ui.designsystem

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** 4dp grid (§6.3). */
object Spacing {
    val xxs = 2.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val xxl = 28.dp

    /** Horizontal screen margin. */
    val screen = 20.dp

    /** Vertical rhythm between cards. */
    val betweenCards = 16.dp

    /** Vertical rhythm between sections. */
    val betweenSections = 28.dp

    /** Inner padding of a card. */
    val card = 16.dp
}

object Sizes {
    /** Minimum touch target (§6.7). */
    val touchTarget = 48.dp

    /** Content max width on tablets and landscape (§6.3). */
    val contentMaxWidth = 640.dp
    val hairline = 1.dp
    val icon = 24.dp
    val iconSmall = 20.dp

    /** The tinted circle of a small play button inside its touch target (Bookmarks list). */
    val playCircle = 34.dp

    /** Rank mark in calendar cells: the smallest size at which ranks must stay distinguishable. */
    val rankMarkSmall = 16.dp
    val rankMark = 24.dp
}

object Radii {
    val card = 20.dp
    val inner = 12.dp
}

object QintarShapes {
    val Card = RoundedCornerShape(Radii.card)
    val Inner = RoundedCornerShape(Radii.inner)
    val Pill = CircleShape
}

/** Tonal elevation only; there are no shadows (§6.3). Level 2 is for the today pill and mini player. */
object Elevation {
    val none = 0.dp
    val floating = 3.dp
}

internal val MaterialShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = QintarShapes.Inner,
    medium = QintarShapes.Inner,
    large = QintarShapes.Card,
    extraLarge = QintarShapes.Card,
)
