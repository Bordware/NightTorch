package com.bordware.nighttorch.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Shared furniture for the home screen.
 *
 * Extracted so the cards stop re-declaring the same padding, corner radius and typography.
 * Before this existed, improving the look of one card left the other three untouched.
 */

/**
 * Minimum height for anything tappable.
 *
 * Material 3 buttons and chips default to 40dp and 32dp respectively, both under the 48dp
 * minimum target that Android's own accessibility guidance asks for. This app argues that a
 * privileged API is justified on accessibility grounds, so its own controls have to clear
 * that bar rather than rely on the framework's defaults.
 */
val MinTouchTarget = 48.dp

/** Corner radius used by every card, so they read as one family. */
private val CardCorner = 20.dp

/** Padding inside a card. */
private val CardPadding = 16.dp

/**
 * The standard card shell: rounded, raised a step above the background, hairline outlined.
 *
 * @param accentBorder when set, replaces the hairline with a coloured outline. Used to make
 *   a card demand attention — currently only the service status card when it is inactive.
 */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    accentBorder: Color? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CardCorner),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(
            width = if (accentBorder != null) 1.5.dp else 1.dp,
            color = accentBorder ?: MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(CardPadding),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content,
        )
    }
}

/**
 * A circular tinted plate behind an icon.
 *
 * Gives each card a consistent visual anchor and keeps icons from floating against the
 * card surface.
 */
@Composable
fun IconBadge(
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary,
    background: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
    size: androidx.compose.ui.unit.Dp = 40.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .background(background, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(size * 0.55f),
        )
    }
}

/**
 * Card header: badge, title, optional subtitle, and optional trailing control.
 *
 * @param trailing typically a `Switch`. Placed here rather than in each card so the switch
 *   always lands in the same position.
 */
@Composable
fun CardHeader(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    iconContentDescription: String? = null,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    iconBackground: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
    trailing: @Composable (RowScope.() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconBadge(
            icon = icon,
            contentDescription = iconContentDescription,
            tint = iconTint,
            background = iconBackground,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        trailing?.invoke(this)
    }
}

/** A full-width row pairing a label with a switch. */
@Composable
fun LabelledSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

/**
 * A label on the left and a value on the right, the value emphasised.
 *
 * Used for readouts like "Brightness … Level 11 of 21 (52%)".
 */
@Composable
fun ValueRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.primary,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
            color = valueColor,
        )
    }
}

/**
 * A brightness slider that snaps to whole device levels.
 *
 * Percent-based sliders lie on this hardware. A device reporting 21 levels turns a 0–100%
 * range into 101 positions with only 21 distinct outcomes, so most drag steps change the
 * stored value without changing the torch at all — 10% and 11% both resolve to level 2 and
 * issue the identical `turnOnTorchWithStrengthLevel` call.
 *
 * Snapping to levels means every position the user can reach is a brightness they can
 * actually see. Preferences are still *stored* as percentages, because `maxLevel` varies
 * enormously between devices and a stored raw level would mean a different brightness on
 * different hardware — the conversion just happens at the edges now.
 *
 * @param level current device level, in `1..maxLevel`.
 * @param maxLevel the device's maximum, from `TorchCapability.Variable`.
 * @param onLevelChange called with a whole level, never a fraction.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LevelSlider(
    level: Int,
    maxLevel: Int,
    onLevelChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentDescription: String? = null,
    trackHeight: Dp = DefaultTrackHeight,
    trackCorner: Dp = DefaultTrackCorner,
    accent: Color? = null,
    colors: SliderColors = SliderDefaults.colors(),
) {
    // `steps` counts the stops *between* the endpoints, so a 1..maxLevel range with every
    // level reachable needs maxLevel - 2. A two-level device has no intermediate stops.
    val steps = (maxLevel - 2).coerceAtLeast(0)
    val clamped = level.coerceIn(1, maxLevel)

    Slider(
        value = clamped.toFloat(),
        onValueChange = { onLevelChange(it.roundToInt().coerceIn(1, maxLevel)) },
        valueRange = 1f..maxLevel.toFloat(),
        steps = steps,
        enabled = enabled,
        colors = colors,
        track = { sliderState ->
            if (accent != null) {
                // Drawn by hand rather than via SliderDefaults.Track, whose corner-radius
                // overload is internal. Doing it here also allows the fill to be translucent,
                // so a wide bar tints the card instead of sitting on top of it.
                LevelTrack(
                    level = clamped,
                    maxLevel = maxLevel,
                    accent = accent,
                    trackHeight = trackHeight,
                    trackCorner = trackCorner,
                    enabled = enabled,
                )
            } else {
                SliderDefaults.Track(
                    sliderState = sliderState,
                    enabled = enabled,
                    colors = colors,
                    modifier = Modifier.height(trackHeight),
                )
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (contentDescription != null) {
                    Modifier.semantics { this.contentDescription = contentDescription }
                } else {
                    Modifier
                },
            ),
    )
}

/**
 * The translucent, square-cornered track used by the primary brightness control.
 *
 * The fill fraction is computed from [level] directly rather than from `SliderState`, which
 * keeps it exact at the snapped positions and avoids depending on that class's shape.
 */
@Composable
private fun LevelTrack(
    level: Int,
    maxLevel: Int,
    accent: Color,
    trackHeight: Dp,
    trackCorner: Dp,
    enabled: Boolean,
) {
    val fraction = if (maxLevel > 1) (level - 1).toFloat() / (maxLevel - 1) else 1f
    val alpha = if (enabled) 1f else DISABLED_ALPHA
    val onSurface = MaterialTheme.colorScheme.onSurface

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(trackHeight),
    ) {
        val corner = CornerRadius(trackCorner.toPx(), trackCorner.toPx())

        drawRoundRect(
            color = accent.copy(alpha = INACTIVE_TRACK_ALPHA * alpha),
            cornerRadius = corner,
        )

        if (fraction > 0f) {
            drawRoundRect(
                color = accent.copy(alpha = ACTIVE_TRACK_ALPHA * alpha),
                size = Size(size.width * fraction, size.height),
                cornerRadius = corner,
            )
        }

        // Level ticks, but only while they stay legible. On a very fine device they would
        // merge into a smear and say nothing.
        if (maxLevel in 2..MAX_VISIBLE_TICKS) {
            val inset = trackCorner.toPx()
            val usable = size.width - inset * 2
            repeat(maxLevel) { index ->
                val x = inset + usable * index / (maxLevel - 1).toFloat()
                drawCircle(
                    color = onSurface.copy(alpha = TICK_ALPHA * alpha),
                    radius = TICK_RADIUS_PX,
                    center = Offset(x, size.height / 2f),
                )
            }
        }
    }
}

private const val ACTIVE_TRACK_ALPHA = 0.72f
private const val INACTIVE_TRACK_ALPHA = 0.16f
private const val TICK_ALPHA = 0.30f
private const val DISABLED_ALPHA = 0.4f
private const val TICK_RADIUS_PX = 3f
private const val MAX_VISIBLE_TICKS = 30

/** Corner radius for the hand-drawn track. Well below half the height, so it reads square. */
val ProminentTrackCorner = 10.dp

/** Matches the Material 3 expressive default; used by the secondary schedule sliders. */
private val DefaultTrackHeight = 16.dp

/** Only used when a track is hand-drawn. */
private val DefaultTrackCorner = 8.dp

/** For the primary brightness control, which is used one-handed and in the dark. */
val ProminentTrackHeight = 44.dp

/** One preset in a [PresetChipRow]. */
data class BrightnessPreset(val label: String, val percent: Int)

/**
 * A row of quick brightness presets.
 *
 * Sliders are fiddly one-handed and worse in the dark, which is exactly when this app gets
 * used — the presets give a reliable target that does not need precision.
 */
@Composable
fun PresetChipRow(
    presets: List<BrightnessPreset>,
    selectedPercent: Int,
    onPresetSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        presets.forEach { preset ->
            FilterChip(
                selected = selectedPercent == preset.percent,
                onClick = { onPresetSelected(preset.percent) },
                enabled = enabled,
                label = {
                    Text(
                        text = preset.label,
                        style = MaterialTheme.typography.labelMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = MinTouchTarget),
            )
        }
    }
}
