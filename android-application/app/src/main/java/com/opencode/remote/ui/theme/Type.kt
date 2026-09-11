package com.opencode.remote.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * Font roles. The prototype references its design system's --font-display /
 * --font-body faces, whose files are not part of the handoff, so both roles map to the
 * platform sans here; swap these two lines to bundle a specific face later.
 */
object OcFonts {
    val Display: FontFamily = FontFamily.SansSerif
    val Body: FontFamily = FontFamily.SansSerif
    val Mono: FontFamily = FontFamily.Monospace
}

private const val TABULAR = "tnum"

/**
 * Editorial type scale, lifted from the prototype's inline `font:` declarations.
 * Uppercase labels must be upper-cased at the call site (see [Eyebrow]) — Compose has
 * no text-transform.
 */
object OcType {
    // Small uppercase, tracking-heavy labels
    val eyebrow = TextStyle(fontFamily = OcFonts.Body, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.14.em)
    val tag = TextStyle(fontFamily = OcFonts.Body, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.1.em)
    val speaker = TextStyle(fontFamily = OcFonts.Body, fontWeight = FontWeight.SemiBold, fontSize = 10.sp, lineHeight = 12.sp, letterSpacing = 0.16.em)
    val statLabel = TextStyle(fontFamily = OcFonts.Body, fontWeight = FontWeight.Medium, fontSize = 10.sp, lineHeight = 14.sp, letterSpacing = 0.12.em)

    // Display
    val runTitle = TextStyle(fontFamily = OcFonts.Display, fontWeight = FontWeight.SemiBold, fontSize = 26.sp, lineHeight = 30.sp, letterSpacing = (-0.03).em)
    val sheetTitle = TextStyle(fontFamily = OcFonts.Display, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 29.sp, letterSpacing = (-0.03).em)
    val pageTitle = TextStyle(fontFamily = OcFonts.Display, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 25.sp, letterSpacing = (-0.03).em)
    val statusTitle = TextStyle(fontFamily = OcFonts.Display, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 25.sp, letterSpacing = (-0.03).em)
    val drawerTitle = TextStyle(fontFamily = OcFonts.Display, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 22.sp, letterSpacing = (-0.03).em)
    val stat = TextStyle(fontFamily = OcFonts.Display, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 20.sp, letterSpacing = (-0.03).em, fontFeatureSettings = TABULAR)
    val screenTitle = TextStyle(fontFamily = OcFonts.Display, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 18.sp, letterSpacing = (-0.03).em)
    val rowTitle = TextStyle(fontFamily = OcFonts.Display, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 19.5.sp, letterSpacing = (-0.02).em)
    val elapsed = TextStyle(fontFamily = OcFonts.Display, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 13.sp, letterSpacing = (-0.02).em, fontFeatureSettings = TABULAR)
    val bigElapsed = TextStyle(fontFamily = OcFonts.Display, fontWeight = FontWeight.SemiBold, fontSize = 34.sp, lineHeight = 34.sp, letterSpacing = (-0.04).em, fontFeatureSettings = TABULAR)
    val trailTime = TextStyle(fontFamily = OcFonts.Display, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 18.sp, letterSpacing = (-0.02).em, fontFeatureSettings = TABULAR)

    // Body
    val message = TextStyle(fontFamily = OcFonts.Body, fontWeight = FontWeight.Normal, fontSize = 14.5.sp, lineHeight = 22.5.sp)
    val bodyLarge = TextStyle(fontFamily = OcFonts.Body, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 22.4.sp)
    val body = TextStyle(fontFamily = OcFonts.Body, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 19.5.sp)
    val bodySmall = TextStyle(fontFamily = OcFonts.Body, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 18.sp)
    val trail = TextStyle(fontFamily = OcFonts.Body, fontWeight = FontWeight.Normal, fontSize = 13.5.sp, lineHeight = 20.sp)
    val rowStrong = TextStyle(fontFamily = OcFonts.Body, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 18.sp)
    val drawerItem = TextStyle(fontFamily = OcFonts.Body, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 18.sp)
    val dirName = TextStyle(fontFamily = OcFonts.Body, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp, lineHeight = 19.sp)
    val input = TextStyle(fontFamily = OcFonts.Body, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 20.sp)

    // Buttons
    val button = TextStyle(fontFamily = OcFonts.Body, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 13.sp)
    val buttonStrong = TextStyle(fontFamily = OcFonts.Body, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 13.sp)
    val buttonLarge = TextStyle(fontFamily = OcFonts.Body, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 14.sp)
    val buttonLargeStrong = TextStyle(fontFamily = OcFonts.Body, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 14.sp)
    val buttonHero = TextStyle(fontFamily = OcFonts.Body, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 15.sp)
    val chip = TextStyle(fontFamily = OcFonts.Body, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 12.sp)

    // Code / paths / terminal
    val mono = TextStyle(fontFamily = OcFonts.Mono, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp)
    val monoSmall = TextStyle(fontFamily = OcFonts.Mono, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 17.sp)
    val monoList = TextStyle(fontFamily = OcFonts.Mono, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 20.4.sp)
    val code = TextStyle(fontFamily = OcFonts.Mono, fontWeight = FontWeight.Normal, fontSize = 11.5.sp, lineHeight = 18.4.sp)
    val terminal = TextStyle(fontFamily = OcFonts.Mono, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 19.8.sp)
    val terminalInput = TextStyle(fontFamily = OcFonts.Mono, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 18.sp)
    val key = TextStyle(fontFamily = OcFonts.Mono, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 12.sp)
}

/** Material type scale mapped onto the same faces, for the few stock components still
 *  in use (sheets, text selection, dialogs). */
val AppTypography = Typography(
    headlineMedium = OcType.pageTitle,
    titleLarge = OcType.statusTitle,
    titleMedium = OcType.rowTitle,
    bodyLarge = OcType.bodyLarge,
    bodyMedium = OcType.body,
    bodySmall = OcType.bodySmall,
    labelLarge = OcType.buttonStrong,
    labelMedium = OcType.tag,
    labelSmall = OcType.eyebrow
)
