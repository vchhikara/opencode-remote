package com.opencode.remote.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * The redesign's token set, transcribed from the DARK / LIGHT tables in
 * "OpenCode Remote - Redesign.dc.html" (alpha values converted to ARGB hex).
 *
 * Semantics matter more than hue here:
 *  - [gold] / [goldInk] / [goldWash] — a moment that needs the user: a decision, the
 *    one primary action on a screen. Never a general brand tint.
 *  - [posInk] / [posWash] — the agent is running and fine.
 *  - [negInk] / [negWash] / [burgundy] — negative or destructive.
 *  - everything else is quiet neutral surface, ink and hairline rules.
 */
@Immutable
data class OcColors(
    val isDark: Boolean,
    val bg: Color,
    val panel: Color,
    val ink: Color,
    val ink2: Color,
    val ink3: Color,
    val rule: Color,
    val ruleStrong: Color,
    /** Gold fill for primary/decision buttons (the design-system --gold, same in both modes). */
    val gold: Color,
    val onGold: Color,
    /** Gold used as text/line colour; darkened in light mode for contrast. */
    val goldInk: Color,
    val goldWash: Color,
    val goldLine: Color,
    val codeBg: Color,
    val posInk: Color,
    val negInk: Color,
    val posWash: Color,
    val negWash: Color,
    val burgundy: Color,
    val onBurgundy: Color,
    val scrim: Color,
    val sheetScrim: Color
)

val DarkOcColors = OcColors(
    isDark = true,
    bg = Color(0xFF101214),
    panel = Color(0xFF191B1E),
    ink = Color(0xFFE8E3DB),
    ink2 = Color(0xB8E8E3DB),       // rgba(232,227,219,.72)
    ink3 = Color(0x66E8E3DB),       // rgba(232,227,219,.40)
    rule = Color(0x14FFFFFF),       // rgba(255,255,255,.08)
    ruleStrong = Color(0x29FFFFFF), // rgba(255,255,255,.16)
    gold = Color(0xFFE0A53E),
    onGold = Color(0xFF101214),
    goldInk = Color(0xFFE0A53E),
    goldWash = Color(0x1AE0A53E),   // rgba(224,165,62,.10)
    goldLine = Color(0x59E0A53E),   // rgba(224,165,62,.35)
    codeBg = Color(0xFF0B0D0E),
    posInk = Color(0xFF6FCF97),
    negInk = Color(0xFFE88B94),
    posWash = Color(0x382D7B48),    // rgba(45,123,72,.22)
    negWash = Color(0x3D9A2737),    // rgba(154,39,55,.24)
    burgundy = Color(0xFF9A2737),
    onBurgundy = Color(0xFFF2E9E6),
    scrim = Color(0x8C000000),      // rgba(0,0,0,.55) — drawer
    sheetScrim = Color(0x9E000000)  // rgba(0,0,0,.62) — interrupt sheet
)

val LightOcColors = OcColors(
    isDark = false,
    bg = Color(0xFFF2EFE9),
    panel = Color(0xFFFFFFFF),
    ink = Color(0xFF15181B),
    ink2 = Color(0xB315181B),       // rgba(21,24,27,.70)
    ink3 = Color(0x6115181B),       // rgba(21,24,27,.38)
    rule = Color(0x1A000000),       // rgba(0,0,0,.10)
    ruleStrong = Color(0x2E000000), // rgba(0,0,0,.18)
    gold = Color(0xFFE0A53E),
    onGold = Color(0xFF101214),
    goldInk = Color(0xFF8A5B0C),
    goldWash = Color(0x29E0A53E),   // rgba(224,165,62,.16)
    goldLine = Color(0x598A5B0C),   // rgba(138,91,12,.35)
    codeBg = Color(0xFFF7F4EE),
    posInk = Color(0xFF1E6B3C),
    negInk = Color(0xFF8E2231),
    posWash = Color(0x242D7B48),    // rgba(45,123,72,.14)
    negWash = Color(0x1F9A2737),    // rgba(154,39,55,.12)
    burgundy = Color(0xFF9A2737),
    onBurgundy = Color(0xFFF2E9E6),
    scrim = Color(0x8C000000),
    sheetScrim = Color(0x9E000000)
)
