package io.github.mehrdad_abdi.quranbookmarks.presentation.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * RTL-aware icons that automatically mirror based on layout direction
 */
object RtlIcons {
    /**
     * Back arrow that points in the correct direction based on RTL/LTR
     */
    val ArrowBack: ImageVector
        @Composable
        get() = Icons.AutoMirrored.Filled.ArrowBack

    /**
     * Forward/Next arrow that points in the correct direction based on RTL/LTR
     */
    val ArrowForward: ImageVector
        @Composable
        get() = Icons.AutoMirrored.Filled.ArrowForward

    /**
     * Expand/Forward arrow for "show more" or navigation actions
     * Uses AutoMirrored icon that automatically flips for RTL
     */
    val ExpandForward: ImageVector
        @Composable
        get() = Icons.AutoMirrored.Filled.ArrowForward

    /**
     * Expand down icon (not RTL-sensitive, always points down)
     */
    val ExpandDown: ImageVector
        @Composable
        get() = Icons.Default.ExpandMore
}
