package com.bordware.nighttorch.ui.home.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import com.bordware.nighttorch.R
import com.bordware.nighttorch.torch.TorchCapability
import com.bordware.nighttorch.torch.TorchError
import com.bordware.nighttorch.torch.TorchState
import com.bordware.nighttorch.ui.components.BrightnessPreset
import com.bordware.nighttorch.ui.components.CardHeader
import com.bordware.nighttorch.ui.components.LevelSlider
import com.bordware.nighttorch.ui.components.ProminentTrackCorner
import com.bordware.nighttorch.ui.components.ProminentTrackHeight
import com.bordware.nighttorch.ui.components.PresetChipRow
import com.bordware.nighttorch.ui.components.SectionCard
import com.bordware.nighttorch.ui.components.ValueRow
import com.bordware.nighttorch.ui.theme.NightTorchTheme
import com.bordware.nighttorch.ui.theme.torchAccentColor

/**
 * Direct torch control.
 *
 * The primary control is a large circular target rather than a switch: this app is used
 * one-handed and in the dark, where a big tap area beats a precise one. It still carries
 * `Role.Switch` semantics so screen readers announce it as a toggle, not a button.
 */
@Composable
fun ManualTorchCard(
    torch: TorchState,
    brightnessPercent: Int,
    onToggle: () -> Unit,
    onBrightnessChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    SectionCard(modifier = modifier) {
        CardHeader(
            icon = if (torch.isOn) Icons.Filled.FlashlightOn else Icons.Filled.FlashlightOff,
            title = stringResource(R.string.torch_card_title),
            iconContentDescription = null,
            trailing = {
                StatePill(isOn = torch.isOn)
            },
        )

        TorchButton(
            isOn = torch.isOn,
            enabled = torch.isOperable,
            onToggle = onToggle,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )

        when (torch.capability) {
            is TorchCapability.Variable -> BrightnessControls(
                capability = torch.capability,
                percent = brightnessPercent,
                enabled = torch.isOperable,
                onBrightnessChange = onBrightnessChange,
            )

            TorchCapability.BinaryOnly -> ExplanatoryLine(R.string.torch_card_binary_only)
            TorchCapability.Unsupported -> ExplanatoryLine(R.string.torch_card_unsupported)
        }

        if (!torch.isAvailable) {
            ExplanatoryLine(R.string.torch_card_unavailable, isError = true)
        }

        torch.error?.let { error ->
            // Unavailability already explains itself above; do not say it twice.
            if (!(error == TorchError.CameraInUse && !torch.isAvailable)) {
                ExplanatoryLine(error.messageRes(), isError = true)
            }
        }
    }
}

/** Small ON/OFF badge, so state is readable at a glance without decoding the button. */
@Composable
private fun StatePill(isOn: Boolean) {
    Surface(
        shape = RoundedCornerShape(50),
        color = if (isOn) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
    ) {
        Text(
            text = stringResource(
                if (isOn) R.string.torch_card_state_on else R.string.torch_card_state_off,
            ),
            style = MaterialTheme.typography.labelMedium,
            color = if (isOn) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun TorchButton(
    isOn: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val caption = stringResource(
        if (isOn) R.string.torch_card_tap_to_turn_off else R.string.torch_card_tap_to_turn_on,
    )
    val switchLabel = stringResource(R.string.torch_card_switch)

    val fill by animateColorAsState(
        targetValue = when {
            !enabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            isOn -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.surfaceVariant
        },
        label = "torchButtonFill",
    )

    Column(
        modifier = modifier.padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(112.dp)
                .background(fill, CircleShape)
                .border(
                    width = 2.dp,
                    color = if (isOn) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    },
                    shape = CircleShape,
                )
                .clickable(enabled = enabled, onClick = onToggle)
                .semantics {
                    // Announce as a toggle rather than a button, and give it the same name
                    // as the control it replaced.
                    role = Role.Switch
                    contentDescription = switchLabel
                    toggleableState = if (isOn) ToggleableState.On else ToggleableState.Off
                    // clickable(enabled = false) removes the click action but leaves the node
                    // reporting as enabled, so a screen reader would offer a control that
                    // does nothing. Say so explicitly.
                    if (!enabled) disabled()
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (isOn) Icons.Filled.FlashlightOn else Icons.Filled.FlashlightOff,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = if (isOn) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }

        Text(
            text = caption,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun BrightnessControls(
    capability: TorchCapability.Variable,
    percent: Int,
    enabled: Boolean,
    onBrightnessChange: (Int) -> Unit,
) {
    val level = capability.levelForPercent(percent)
    val accent = torchAccentColor()

    ValueRow(
        label = stringResource(R.string.torch_card_brightness),
        value = stringResource(R.string.torch_card_level_of, level, capability.maxLevel),
        valueColor = accent,
    )

    LevelSlider(
        level = level,
        maxLevel = capability.maxLevel,
        // Stored as a percentage, shown and adjusted as a level. See LevelSlider.
        onLevelChange = { onBrightnessChange(capability.percentForLevel(it)) },
        enabled = enabled,
        contentDescription = stringResource(
            R.string.torch_card_brightness_a11y,
            level,
            capability.maxLevel,
        ),
        // Taller and amber: this is the primary control, it stands for light output, and it
        // gets used one-handed in the dark. Everything else follows Material You. Passing an
        // accent switches on the hand-drawn translucent track, so the bar tints the card
        // rather than sitting on top of it.
        trackHeight = ProminentTrackHeight,
        trackCorner = ProminentTrackCorner,
        accent = accent,
        colors = SliderDefaults.colors(thumbColor = accent),
    )

    PresetChipRow(
        presets = capability.presets(),
        selectedPercent = percent,
        onPresetSelected = onBrightnessChange,
        enabled = enabled,
    )
}

/**
 * Quick targets at the bottom, a third, two thirds and the top of the *level* range.
 *
 * Derived from levels rather than fixed percentages so a preset's stored percentage is
 * exactly the one the slider produces at that level — otherwise tapping "67%" would leave
 * the chip unhighlighted, because 66% and 67% resolve to the same level but are not equal.
 */
@Composable
private fun TorchCapability.Variable.presets(): List<BrightnessPreset> {
    val minPercent = percentForLevel(1)
    val lowPercent = percentForLevel(((maxLevel + 2) / 3).coerceIn(1, maxLevel))
    val midPercent = percentForLevel(((maxLevel * 2 + 1) / 3).coerceIn(1, maxLevel))
    val maxPercent = percentForLevel(maxLevel)

    // distinctBy keeps a coarse device — two or three levels — from showing duplicate chips.
    return listOf(
        BrightnessPreset(stringResource(R.string.torch_card_preset_min), minPercent),
        BrightnessPreset(stringResource(R.string.torch_card_percent, lowPercent), lowPercent),
        BrightnessPreset(stringResource(R.string.torch_card_percent, midPercent), midPercent),
        BrightnessPreset(stringResource(R.string.torch_card_preset_max), maxPercent),
    ).distinctBy { it.percent }
}

@Composable
private fun ExplanatoryLine(textRes: Int, isError: Boolean = false) {
    Text(
        text = stringResource(textRes),
        style = MaterialTheme.typography.bodySmall,
        color = if (isError) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
    )
}

private fun TorchError.messageRes(): Int = when (this) {
    TorchError.CameraInUse -> R.string.torch_card_error_camera_in_use
    TorchError.CameraUnavailable -> R.string.torch_card_error_camera_unavailable
    TorchError.InvalidLevel -> R.string.torch_card_error_invalid_level
    TorchError.NoFlashUnit -> R.string.torch_card_error_no_flash
}

@Preview(showBackground = true, name = "Variable, on")
@Composable
private fun ManualTorchCardVariablePreview() {
    NightTorchTheme(darkTheme = true, dynamicColor = false) {
        ManualTorchCard(
            torch = TorchState(
                isOn = true,
                level = 11,
                capability = TorchCapability.Variable(maxLevel = 21, defaultLevel = 21),
            ),
            brightnessPercent = 50,
            onToggle = {},
            onBrightnessChange = {},
        )
    }
}

@Preview(showBackground = true, name = "Variable, off, light")
@Composable
private fun ManualTorchCardOffPreview() {
    NightTorchTheme(dynamicColor = false) {
        ManualTorchCard(
            torch = TorchState(
                isOn = false,
                level = 21,
                capability = TorchCapability.Variable(maxLevel = 21, defaultLevel = 21),
            ),
            brightnessPercent = 100,
            onToggle = {},
            onBrightnessChange = {},
        )
    }
}

@Preview(showBackground = true, name = "Binary only")
@Composable
private fun ManualTorchCardBinaryPreview() {
    NightTorchTheme(darkTheme = true, dynamicColor = false) {
        ManualTorchCard(
            torch = TorchState(isOn = false, capability = TorchCapability.BinaryOnly),
            brightnessPercent = 100,
            onToggle = {},
            onBrightnessChange = {},
        )
    }
}

@Preview(showBackground = true, name = "No flash unit")
@Composable
private fun ManualTorchCardUnsupportedPreview() {
    NightTorchTheme(darkTheme = true, dynamicColor = false) {
        ManualTorchCard(
            torch = TorchState(
                capability = TorchCapability.Unsupported,
                error = TorchError.NoFlashUnit,
            ),
            brightnessPercent = 100,
            onToggle = {},
            onBrightnessChange = {},
        )
    }
}

@Preview(showBackground = true, name = "Camera in use")
@Composable
private fun ManualTorchCardUnavailablePreview() {
    NightTorchTheme(darkTheme = true, dynamicColor = false) {
        ManualTorchCard(
            torch = TorchState(
                isOn = false,
                capability = TorchCapability.Variable(maxLevel = 5, defaultLevel = 5),
                isAvailable = false,
                error = TorchError.CameraInUse,
            ),
            brightnessPercent = 100,
            onToggle = {},
            onBrightnessChange = {},
        )
    }
}
