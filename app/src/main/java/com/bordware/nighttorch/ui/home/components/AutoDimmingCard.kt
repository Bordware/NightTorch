package com.bordware.nighttorch.ui.home.components

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.bordware.nighttorch.R
import com.bordware.nighttorch.data.AppSettings
import com.bordware.nighttorch.schedule.BrightnessDecision
import com.bordware.nighttorch.torch.TorchCapability
import com.bordware.nighttorch.ui.components.CardHeader
import com.bordware.nighttorch.ui.components.LevelSlider
import com.bordware.nighttorch.ui.components.SectionCard
import com.bordware.nighttorch.ui.components.ValueRow
import com.bordware.nighttorch.ui.theme.NightTorchTheme
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** Which time picker is open, if any. */
private enum class OpenPicker { None, NightStart, NightEnd }

/**
 * Time-of-day brightness, plus the two behaviour switches for the shortcut.
 *
 * The preview strip is deliberately the loudest thing in the card. The schedule is
 * otherwise invisible — without it, a user has to wait until 21:00 to discover whether they
 * configured it correctly.
 */
@Composable
fun AutoDimmingCard(
    settings: AppSettings,
    now: LocalTime,
    decision: BrightnessDecision?,
    capability: TorchCapability,
    onAutoDimmingChange: (Boolean) -> Unit,
    onNightStartChange: (LocalTime) -> Unit,
    onNightEndChange: (LocalTime) -> Unit,
    onNightBrightnessChange: (Int) -> Unit,
    onDayBrightnessChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var openPicker by remember { mutableStateOf(OpenPicker.None) }

    SectionCard(modifier = modifier) {
        CardHeader(
            icon = Icons.Filled.Bedtime,
            title = stringResource(R.string.dimming_card_title),
            subtitle = stringResource(R.string.dimming_card_explainer),
            iconContentDescription = null,
            trailing = {
                androidx.compose.material3.Switch(
                    checked = settings.autoDimmingEnabled,
                    onCheckedChange = onAutoDimmingChange,
                )
            },
        )

        PreviewStrip(now = now, decision = decision, capability = capability)

        if (settings.autoDimmingEnabled) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TimeButton(
                    label = stringResource(R.string.dimming_card_night_start),
                    time = settings.nightStart,
                    modifier = Modifier.weight(1f),
                    onClick = { openPicker = OpenPicker.NightStart },
                )
                TimeButton(
                    label = stringResource(R.string.dimming_card_night_end),
                    time = settings.nightEnd,
                    modifier = Modifier.weight(1f),
                    onClick = { openPicker = OpenPicker.NightEnd },
                )
            }

            if (settings.nightStart == settings.nightEnd) {
                Text(
                    text = stringResource(R.string.dimming_card_same_time_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            if (capability is TorchCapability.Variable) {
                BrightnessRow(
                    icon = Icons.Filled.Bedtime,
                    label = stringResource(R.string.dimming_card_night_brightness),
                    percent = settings.nightBrightnessPercent,
                    capability = capability,
                    onPercentChange = onNightBrightnessChange,
                )
                BrightnessRow(
                    icon = Icons.Filled.LightMode,
                    label = stringResource(R.string.dimming_card_day_brightness),
                    percent = settings.dayBrightnessPercent,
                    capability = capability,
                    onPercentChange = onDayBrightnessChange,
                )
            } else {
                // Nothing to choose between: the torch has one brightness, so night and day
                // settings could not change anything. Showing dead sliders would imply
                // otherwise.
                Text(
                    text = stringResource(R.string.dimming_card_binary_only),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

    }

    when (openPicker) {
        OpenPicker.NightStart -> TimePickerDialog(
            titleRes = R.string.time_picker_night_start_title,
            initial = settings.nightStart,
            onConfirm = {
                onNightStartChange(it)
                openPicker = OpenPicker.None
            },
            onDismiss = { openPicker = OpenPicker.None },
        )

        OpenPicker.NightEnd -> TimePickerDialog(
            titleRes = R.string.time_picker_night_end_title,
            initial = settings.nightEnd,
            onConfirm = {
                onNightEndChange(it)
                openPicker = OpenPicker.None
            },
            onDismiss = { openPicker = OpenPicker.None },
        )

        OpenPicker.None -> Unit
    }
}

/**
 * One labelled brightness slider, snapping to whole device levels.
 *
 * Shows the level rather than a percentage because the percentage is finer than the
 * hardware: on a 21-level device, 10% and 11% both resolve to level 2 and produce exactly
 * the same light. See `LevelSlider`.
 */
@Composable
private fun BrightnessRow(
    icon: ImageVector,
    label: String,
    percent: Int,
    capability: TorchCapability.Variable,
    onPercentChange: (Int) -> Unit,
) {
    val level = capability.levelForPercent(percent)

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ValueRow(
            label = label,
            value = stringResource(R.string.torch_card_level_of, level, capability.maxLevel),
        )
    }
    LevelSlider(
        level = level,
        maxLevel = capability.maxLevel,
        onLevelChange = { onPercentChange(capability.percentForLevel(it)) },
        contentDescription = stringResource(
            R.string.torch_card_brightness_a11y,
            level,
            capability.maxLevel,
        ),
    )
}

@Composable
private fun TimeButton(
    label: String,
    time: LocalTime,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = time.formatForDisplay(),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Icon(
                imageVector = Icons.Filled.Schedule,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/**
 * The live schedule readout, styled to stand apart from the body text around it.
 */
@Composable
private fun PreviewStrip(
    now: LocalTime,
    decision: BrightnessDecision?,
    capability: TorchCapability,
) {
    val formattedNow = now.formatForDisplay()
    val maxLevel = (capability as? TorchCapability.Variable)?.maxLevel

    val text = when {
        decision == null -> stringResource(R.string.dimming_card_preview_off)

        maxLevel == null -> stringResource(
            if (decision.isNight) {
                R.string.dimming_card_preview_night_binary
            } else {
                R.string.dimming_card_preview_day_binary
            },
            formattedNow,
        )

        else -> stringResource(
            if (decision.isNight) {
                R.string.dimming_card_preview_night
            } else {
                R.string.dimming_card_preview_day
            },
            formattedNow,
            decision.level,
            maxLevel,
        )
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = if (decision?.isNight == true) {
                    Icons.Filled.Bedtime
                } else {
                    Icons.Filled.LightMode
                },
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(
    titleRes: Int,
    initial: LocalTime,
    onConfirm: (LocalTime) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val state = rememberTimePickerState(
        initialHour = initial.hour,
        initialMinute = initial.minute,
        is24Hour = DateFormat.is24HourFormat(context),
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(titleRes)) },
        text = { TimePicker(state = state) },
        confirmButton = {
            TextButton(onClick = { onConfirm(LocalTime.of(state.hour, state.minute)) }) {
                Text(stringResource(R.string.time_picker_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.time_picker_cancel))
            }
        },
    )
}

/** Formats using the device's 12/24-hour preference rather than a fixed pattern. */
@Composable
private fun LocalTime.formatForDisplay(): String {
    val is24Hour = DateFormat.is24HourFormat(LocalContext.current)
    val pattern = if (is24Hour) "HH:mm" else "h:mm a"
    return format(DateTimeFormatter.ofPattern(pattern))
}

private val variable21 = TorchCapability.Variable(maxLevel = 21, defaultLevel = 21)

@Composable
private fun PreviewCard(
    settings: AppSettings,
    now: LocalTime,
    decision: BrightnessDecision?,
    capability: TorchCapability = variable21,
) {
    AutoDimmingCard(
        settings = settings,
        now = now,
        decision = decision,
        capability = capability,
        onAutoDimmingChange = {},
        onNightStartChange = {},
        onNightEndChange = {},
        onNightBrightnessChange = {},
        onDayBrightnessChange = {},
    )
}

@Preview(showBackground = true, name = "Night, enabled")
@Composable
private fun AutoDimmingCardNightPreview() {
    NightTorchTheme(darkTheme = true, dynamicColor = false) {
        PreviewCard(
            settings = AppSettings.DEFAULT,
            now = LocalTime.of(22, 14),
            decision = BrightnessDecision(isNight = true, percent = 1, level = 1),
        )
    }
}

@Preview(showBackground = true, name = "Auto dimming off")
@Composable
private fun AutoDimmingCardOffPreview() {
    NightTorchTheme(darkTheme = true, dynamicColor = false) {
        PreviewCard(
            settings = AppSettings.DEFAULT.copy(autoDimmingEnabled = false),
            now = LocalTime.of(14, 30),
            decision = null,
        )
    }
}

@Preview(showBackground = true, name = "Degenerate equal window")
@Composable
private fun AutoDimmingCardEqualWindowPreview() {
    NightTorchTheme(darkTheme = true, dynamicColor = false) {
        PreviewCard(
            settings = AppSettings.DEFAULT.copy(
                nightStart = LocalTime.of(21, 0),
                nightEnd = LocalTime.of(21, 0),
            ),
            now = LocalTime.of(22, 14),
            decision = BrightnessDecision(isNight = false, percent = 100, level = 21),
        )
    }
}

@Preview(showBackground = true, name = "Binary device, light")
@Composable
private fun AutoDimmingCardBinaryPreview() {
    NightTorchTheme(dynamicColor = false) {
        PreviewCard(
            settings = AppSettings.DEFAULT,
            now = LocalTime.of(3, 5),
            decision = BrightnessDecision(isNight = true, percent = 1, level = 1),
            capability = TorchCapability.BinaryOnly,
        )
    }
}
