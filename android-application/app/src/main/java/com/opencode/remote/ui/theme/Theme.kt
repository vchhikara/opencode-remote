package com.opencode.remote.ui.theme

import android.app.Activity
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.opencode.remote.domain.model.Appearance

val LocalOcColors = staticCompositionLocalOf { DarkOcColors }

/** Accessor for the editorial tokens: `Oc.colors.gold`, etc. */
object Oc {
    val colors: OcColors
        @Composable @ReadOnlyComposable get() = LocalOcColors.current
}

private val SquareShapes = Shapes(
    extraSmall = RoundedCornerShape(0.dp),
    small = RoundedCornerShape(0.dp),
    medium = RoundedCornerShape(0.dp),
    large = RoundedCornerShape(0.dp),
    extraLarge = RoundedCornerShape(0.dp)
)

@Composable
fun resolveDark(appearance: Appearance): Boolean = when (appearance) {
    Appearance.Dark -> true
    Appearance.Light -> false
    Appearance.System -> isSystemInDarkTheme()
}

@Composable
fun AppTheme(
    appearance: Appearance = Appearance.Dark,
    trueBlack: Boolean = false,
    content: @Composable () -> Unit
) {
    val dark = resolveDark(appearance)
    val colors = if (dark) (if (trueBlack) TrueBlackOcColors else DarkOcColors) else LightOcColors

    // Material scheme mapped onto the editorial tokens for the handful of stock
    // components still used (bottom sheet, drawer container, dialogs, selection).
    val scheme = if (dark) {
        darkColorScheme(
            primary = colors.gold, onPrimary = colors.onGold,
            secondary = colors.ink2, onSecondary = colors.bg,
            background = colors.bg, onBackground = colors.ink,
            surface = colors.panel, onSurface = colors.ink,
            surfaceVariant = colors.panel, onSurfaceVariant = colors.ink2,
            surfaceContainerLowest = colors.bg, surfaceContainerLow = colors.panel,
            surfaceContainer = colors.panel, surfaceContainerHigh = colors.panel,
            surfaceContainerHighest = colors.panel,
            outline = colors.ruleStrong, outlineVariant = colors.rule,
            error = colors.negInk, onError = colors.bg,
            scrim = Color.Black
        )
    } else {
        lightColorScheme(
            primary = colors.gold, onPrimary = colors.onGold,
            secondary = colors.ink2, onSecondary = colors.bg,
            background = colors.bg, onBackground = colors.ink,
            surface = colors.panel, onSurface = colors.ink,
            surfaceVariant = colors.panel, onSurfaceVariant = colors.ink2,
            surfaceContainerLowest = colors.panel, surfaceContainerLow = colors.panel,
            surfaceContainer = colors.panel, surfaceContainerHigh = colors.panel,
            surfaceContainerHighest = colors.panel,
            outline = colors.ruleStrong, outlineVariant = colors.rule,
            error = colors.negInk, onError = colors.panel,
            scrim = Color.Black
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }

    MaterialTheme(colorScheme = scheme, typography = AppTypography, shapes = SquareShapes) {
        CompositionLocalProvider(
            LocalOcColors provides colors,
            LocalTextSelectionColors provides TextSelectionColors(handleColor = colors.gold, backgroundColor = colors.goldLine)
        ) {
            content()
        }
    }
}

/** The app background, cross-faded on theme change like the prototype's
 *  `transition: background-color .3s`. Only the root surface animates, so a theme
 *  switch doesn't recompose the whole tree every frame. */
@Composable
fun animatedBackground(): State<Color> =
    animateColorAsState(Oc.colors.bg, tween(300, easing = OcMotion.Easing), label = "appBg")
