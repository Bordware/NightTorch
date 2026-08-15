package com.bordware.nighttorch.ui.home.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.bordware.nighttorch.R
import com.bordware.nighttorch.ui.components.CardHeader
import com.bordware.nighttorch.ui.components.MinTouchTarget
import com.bordware.nighttorch.ui.components.SectionCard
import com.bordware.nighttorch.ui.theme.NightTorchTheme

/**
 * Whether the volume key shortcut is live, and a way to switch it on.
 *
 * When inactive the card takes a coloured border and a prominent button, because the app's
 * headline feature does nothing until this is resolved. State is never signalled by colour
 * alone: the icon and the wording both change too.
 */
@Composable
fun ServiceStatusCard(
    serviceEnabled: Boolean,
    onOpenAccessibilitySettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = if (serviceEnabled) {
        MaterialTheme.colorScheme.tertiary
    } else {
        MaterialTheme.colorScheme.error
    }

    SectionCard(
        modifier = modifier,
        accentBorder = if (serviceEnabled) null else accent,
    ) {
        CardHeader(
            icon = if (serviceEnabled) Icons.Filled.CheckCircle else Icons.Filled.Warning,
            title = stringResource(R.string.service_card_title),
            subtitle = stringResource(
                if (serviceEnabled) R.string.service_card_enabled else R.string.service_card_disabled,
            ),
            iconContentDescription = stringResource(R.string.service_card_status_icon),
            iconTint = accent,
            iconBackground = accent.copy(alpha = 0.16f),
        )

        Text(
            text = stringResource(
                if (serviceEnabled) {
                    R.string.service_card_enabled_body
                } else {
                    R.string.service_card_disabled_body
                },
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (!serviceEnabled) {
            Button(
                onClick = onOpenAccessibilitySettings,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = MinTouchTarget),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            ) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = stringResource(R.string.service_card_open_settings),
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}

@Preview(showBackground = true, name = "Active")
@Composable
private fun ServiceStatusCardEnabledPreview() {
    NightTorchTheme(darkTheme = true, dynamicColor = false) {
        ServiceStatusCard(serviceEnabled = true, onOpenAccessibilitySettings = {})
    }
}

@Preview(showBackground = true, name = "Inactive")
@Composable
private fun ServiceStatusCardDisabledPreview() {
    NightTorchTheme(darkTheme = true, dynamicColor = false) {
        ServiceStatusCard(serviceEnabled = false, onOpenAccessibilitySettings = {})
    }
}

@Preview(showBackground = true, name = "Inactive, light")
@Composable
private fun ServiceStatusCardDisabledLightPreview() {
    NightTorchTheme(dynamicColor = false) {
        ServiceStatusCard(serviceEnabled = false, onOpenAccessibilitySettings = {})
    }
}
