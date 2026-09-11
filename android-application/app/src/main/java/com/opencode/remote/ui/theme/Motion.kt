package com.opencode.remote.ui.theme

import androidx.compose.animation.core.CubicBezierEasing

/** Motion tokens from the prototype: `cubic-bezier(.22,1,.36,1)` for entries and state
 *  changes (.3–.35s), and a 1.6s ease-in-out pulse for live indicators. */
object OcMotion {
    val Easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
    val PulseEasing = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)
    const val ENTER_MS = 350
    const val STATE_MS = 300
    const val PULSE_HALF_MS = 800
}
