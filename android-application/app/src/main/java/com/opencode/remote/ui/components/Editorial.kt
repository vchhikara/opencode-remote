package com.opencode.remote.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.opencode.remote.ui.theme.Oc
import com.opencode.remote.ui.theme.OcMotion
import com.opencode.remote.ui.theme.OcType

// ---------------------------------------------------------------------------
// Rules — the redesign separates things with 1px hairlines, not elevation.
// ---------------------------------------------------------------------------

fun Modifier.bottomRule(color: Color, width: Dp = 1.dp): Modifier = drawBehind {
    val w = width.toPx()
    drawRect(color, topLeft = Offset(0f, size.height - w), size = Size(size.width, w))
}

fun Modifier.topRule(color: Color, width: Dp = 1.dp): Modifier = drawBehind {
    drawRect(color, topLeft = Offset.Zero, size = Size(size.width, width.toPx()))
}

fun Modifier.startRule(color: Color, width: Dp = 1.dp): Modifier = drawBehind {
    drawRect(color, topLeft = Offset.Zero, size = Size(width.toPx(), size.height))
}

fun Modifier.endRule(color: Color, width: Dp = 1.dp): Modifier = drawBehind {
    val w = width.toPx()
    drawRect(color, topLeft = Offset(size.width - w, 0f), size = Size(w, size.height))
}

// ---------------------------------------------------------------------------
// Type primitives
// ---------------------------------------------------------------------------

/** Small uppercase tracking-heavy label (11sp, .14em). */
@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier, color: Color = Oc.colors.ink2, style: TextStyle = OcType.eyebrow) {
    Text(
        text = text.uppercase(),
        style = style,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
    )
}

/** Monospace text for paths, commands, git and code. */
@Composable
fun CodeText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Oc.colors.ink,
    style: TextStyle = OcType.mono,
    maxLines: Int = 1
) {
    Text(text = text, style = style, color = color, maxLines = maxLines, overflow = TextOverflow.Ellipsis, modifier = modifier)
}

// ---------------------------------------------------------------------------
// Live-state dot
// ---------------------------------------------------------------------------

/** 7dp status dot; pulses (opacity 1 → .35, 1.6s) only while [pulsing]. The pulse is
 *  applied in the draw phase, so it never recomposes its caller. */
@Composable
fun StatusDot(color: Color, pulsing: Boolean, modifier: Modifier = Modifier, size: Dp = 7.dp) {
    val alpha: State<Float> = if (pulsing) {
        val transition = rememberInfiniteTransition(label = "pulse")
        transition.animateFloat(
            initialValue = 1f,
            targetValue = 0.35f,
            animationSpec = infiniteRepeatable(tween(OcMotion.PULSE_HALF_MS, easing = OcMotion.PulseEasing), RepeatMode.Reverse),
            label = "pulseAlpha"
        )
    } else {
        remember { mutableFloatStateOf(1f) }
    }
    Box(
        modifier
            .size(size)
            .graphicsLayer { this.alpha = alpha.value }
            .background(color, CircleShape)
    )
}

// ---------------------------------------------------------------------------
// Buttons — square, bordered; gold reserved for the one decision on screen.
// ---------------------------------------------------------------------------

enum class OcButtonKind {
    /** Primary decision / commit action. */
    Gold,
    /** Default: hairline border, ink text. */
    Neutral,
    /** Negative action with a border, red text (e.g. Deny, Revoke). */
    NegativeOutline,
    /** Destructive fill (Forget this bridge). */
    Destructive
}

@Composable
fun OcButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: OcButtonKind = OcButtonKind.Neutral,
    enabled: Boolean = true,
    height: Dp = 44.dp,
    textStyle: TextStyle? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 14.dp)
) {
    val c = Oc.colors
    val (bg, fg, border) = when (kind) {
        OcButtonKind.Gold -> Triple(if (enabled) c.gold else c.gold.copy(alpha = 0.35f), c.onGold, null)
        OcButtonKind.Neutral -> Triple(Color.Transparent, if (enabled) c.ink else c.ink3, c.ruleStrong)
        OcButtonKind.NegativeOutline -> Triple(Color.Transparent, if (enabled) c.negInk else c.ink3, c.ruleStrong)
        OcButtonKind.Destructive -> Triple(if (enabled) c.burgundy else c.burgundy.copy(alpha = 0.4f), c.onBurgundy, null)
    }
    val style = textStyle ?: if (kind == OcButtonKind.Gold || kind == OcButtonKind.Destructive) OcType.buttonStrong else OcType.button
    Box(
        modifier = modifier
            .heightIn(min = height)
            .background(bg)
            .then(if (border != null) Modifier.border(1.dp, border) else Modifier)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(contentPadding),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, style = style, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    }
}

/** Compact outlined chip (presets, reusable commands). */
@Composable
fun OcChip(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, mono: Boolean = false, enabled: Boolean = true) {
    val c = Oc.colors
    Box(
        modifier
            .border(1.dp, c.ruleStrong)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 7.dp)
    ) {
        Text(
            text = text,
            style = if (mono) OcType.monoSmall.copy(lineHeight = OcType.chip.lineHeight) else OcType.chip,
            color = if (enabled) c.ink2 else c.ink3,
            maxLines = 1
        )
    }
}

/** A row of cells separated by 1px rules (prototype: `gap:1px;background:var(--rule)`). */
@Composable
fun SegmentedBar(
    modifier: Modifier = Modifier,
    divider: Color = Oc.colors.ruleStrong,
    topRule: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(divider)
            .padding(top = if (topRule) 1.dp else 0.dp),
        horizontalArrangement = Arrangement.spacedBy(1.dp),
        content = content
    )
}

@Composable
fun RowScope.SegmentCell(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    weight: Float = 1f,
    height: Dp = 52.dp,
    color: Color = Oc.colors.ink,
    background: Color = Oc.colors.panel,
    style: TextStyle = OcType.button,
    enabled: Boolean = true
) {
    Box(
        modifier
            .weight(weight)
            .height(height)
            .background(background)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, style = style, color = if (enabled) color else Oc.colors.ink3, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// ---------------------------------------------------------------------------
// Text fields — square, panel fill, hairline border that turns gold-line on focus.
// ---------------------------------------------------------------------------

@Composable
fun OcTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = OcType.input,
    singleLine: Boolean = true,
    maxLines: Int = if (singleLine) 1 else 5,
    minHeight: Dp = 46.dp,
    enabled: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default
) {
    val c = Oc.colors
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .heightIn(min = minHeight)
            .background(c.panel)
            .border(1.dp, if (focused) c.goldLine else c.ruleStrong),
        enabled = enabled,
        textStyle = textStyle.copy(color = c.ink),
        cursorBrush = SolidColor(c.goldInk),
        singleLine = singleLine,
        maxLines = maxLines,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        interactionSource = source,
        decorationBox = { inner ->
            Box(Modifier.padding(horizontal = 13.dp, vertical = 12.dp), contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) {
                    Text(placeholder, style = textStyle, color = c.ink3, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                inner()
            }
        }
    )
}

/** [TextFieldValue] variant (the terminal needs cursor control for its key row). */
@Composable
fun OcTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = OcType.input,
    minHeight: Dp = 46.dp,
    enabled: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default
) {
    val c = Oc.colors
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .heightIn(min = minHeight)
            .background(c.panel)
            .border(1.dp, if (focused) c.goldLine else c.ruleStrong),
        enabled = enabled,
        textStyle = textStyle.copy(color = c.ink),
        cursorBrush = SolidColor(c.goldInk),
        singleLine = true,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        interactionSource = source,
        decorationBox = { inner ->
            Box(Modifier.padding(horizontal = 12.dp, vertical = 12.dp), contentAlignment = Alignment.CenterStart) {
                if (value.text.isEmpty()) Text(placeholder, style = textStyle, color = c.ink3, maxLines = 1)
                inner()
            }
        }
    )
}

// ---------------------------------------------------------------------------
// Screen scaffolding
// ---------------------------------------------------------------------------

/** 15sp display title with an optional trailing slot, over a hairline. */
@Composable
fun ScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: @Composable RowScope.() -> Unit = {}
) {
    val c = Oc.colors
    Column(
        modifier
            .fillMaxWidth()
            .bottomRule(c.rule)
            .padding(horizontal = 18.dp, vertical = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = OcType.screenTitle, color = c.ink, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            trailing()
        }
        if (subtitle != null) {
            Spacer(Modifier.height(6.dp))
            Text(subtitle, style = OcType.body, color = c.ink2)
        }
    }
}

/** A small bordered text action for headers ("New", "Refresh", "Clear"). */
@Composable
fun HeaderAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val c = Oc.colors
    Box(
        modifier
            .border(1.dp, c.ruleStrong)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(text, style = OcType.chip, color = if (enabled) c.ink else c.ink3)
    }
}

/** Uppercase section label with the prototype's 16/18/8 padding. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, color: Color = Oc.colors.ink2) {
    Eyebrow(text, modifier.padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 8.dp), color = color)
}

/** Empty / unavailable state: a title and a one-line direction, left-aligned. */
@Composable
fun EmptyNote(title: String, detail: String, modifier: Modifier = Modifier) {
    val c = Oc.colors
    Column(modifier.fillMaxWidth().padding(18.dp)) {
        Text(title, style = OcType.rowTitle, color = c.ink)
        Spacer(Modifier.height(6.dp))
        Text(detail, style = OcType.body, color = c.ink2)
    }
}

/** Chevron "›" that rotates to point down when [open]. */
@Composable
fun Chevron(rotation: Float, modifier: Modifier = Modifier, color: Color = Oc.colors.ink3) {
    Text(
        "›",
        style = OcType.bodyLarge.copy(fontSize = OcType.statusTitle.fontSize, lineHeight = OcType.statusTitle.fontSize),
        color = color,
        modifier = modifier.graphicsLayer { rotationZ = rotation }
    )
}

/** Fixed-width spacer shorthand used in dense rows. */
@Composable
fun HSpace(width: Dp) = Spacer(Modifier.width(width))
