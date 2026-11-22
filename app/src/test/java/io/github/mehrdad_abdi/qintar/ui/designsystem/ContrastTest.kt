package io.github.mehrdad_abdi.qintar.ui.designsystem

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * DESIGN.md §6.1: contrast is enforced by test. Every text role is checked against every
 * background it is used on, in light, dark and pure-black; text needs 4.5:1, the graphic-only
 * gold needs 3:1.
 */
class ContrastTest {

    private val palettes = mapOf(
        "light" to QintarPalette.Light,
        "dark" to QintarPalette.Dark,
        "pure black" to QintarPalette.PureBlack,
    )

    private fun luminance(argb: Long): Double {
        fun channel(shift: Int): Double {
            val c = ((argb shr shift) and 0xFF) / 255.0
            return if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    }

    private fun ratio(foreground: Long, background: Long): Double {
        val a = luminance(foreground)
        val b = luminance(background)
        return (max(a, b) + 0.05) / (min(a, b) + 0.05)
    }

    private data class Pair(val fgName: String, val fg: Long, val bgName: String, val bg: Long, val minimum: Double)

    private fun pairs(p: QintarPalette): List<Pair> {
        val papers = listOf("background" to p.background, "surface" to p.surface, "surfaceContainer" to p.surfaceContainer)
        val inks = listOf(
            "onSurface" to p.onSurface,
            "onSurfaceVariant" to p.onSurfaceVariant,
            "primary" to p.primary,
            "goldInk" to p.goldInk,
            "error" to p.error,
        )
        val onPaper = papers.flatMap { (bgName, bg) ->
            inks.map { (fgName, fg) -> Pair(fgName, fg, bgName, bg, TEXT) } +
                Pair("gold", p.gold, bgName, bg, GRAPHIC)
        }
        return onPaper + listOf(
            Pair("onPrimary", p.onPrimary, "primary", p.primary, TEXT),
            Pair("onPrimaryContainer", p.onPrimaryContainer, "primaryContainer", p.primaryContainer, TEXT),
            Pair("goldInk", p.goldInk, "goldContainer", p.goldContainer, TEXT),
            Pair("onError", p.onError, "error", p.error, TEXT),
            Pair("onErrorContainer", p.onErrorContainer, "errorContainer", p.errorContainer, TEXT),
        )
    }

    @Test
    fun `every text and graphic role meets its WCAG minimum on every background it is used on`() {
        val failures = palettes.flatMap { (theme, palette) ->
            pairs(palette).mapNotNull { pair ->
                val r = ratio(pair.fg, pair.bg)
                if (r < pair.minimum) "$theme: ${pair.fgName} on ${pair.bgName} is %.2f:1, needs %.1f:1".format(r, pair.minimum) else null
            }
        }
        assertTrue("Contrast failures:\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun `the ratio function agrees with the WCAG reference values`() {
        assertTrue(ratio(0xFF000000, 0xFFFFFFFF) in 20.99..21.01)
        assertTrue(ratio(0xFF777777, 0xFFFFFFFF) in 4.47..4.49)
    }

    private companion object {
        const val TEXT = 4.5
        const val GRAPHIC = 3.0
    }
}
