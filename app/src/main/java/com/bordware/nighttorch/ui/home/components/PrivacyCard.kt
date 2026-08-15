package com.bordware.nighttorch.ui.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
 * What each permission is for, and what the app cannot do.
 *
 * Every claim here is checkable rather than promised: the permission list in Android
 * settings shows there is no internet permission, and the source is one tap away. A
 * privacy-positioned app that asks to be trusted has missed the point.
 */
@Composable
fun PrivacyCard(
    onOpenSourceCode: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SectionCard(modifier = modifier) {
        CardHeader(
            icon = Icons.Filled.Lock,
            title = stringResource(R.string.privacy_card_title),
            subtitle = stringResource(R.string.privacy_card_status),
            iconContentDescription = null,
            iconTint = MaterialTheme.colorScheme.tertiary,
            iconBackground = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.16f),
        )

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            PrivacyBullet(R.string.privacy_bullet_network)
            PrivacyBullet(R.string.privacy_bullet_accessibility)
            PrivacyBullet(R.string.privacy_bullet_audio)
            PrivacyBullet(R.string.privacy_bullet_vibrate)
        }

        TextButton(
            onClick = onOpenSourceCode,
            modifier = Modifier.heightIn(min = MinTouchTarget),
        ) {
            Text(stringResource(R.string.privacy_card_source))
            Icon(
                imageVector = Icons.Filled.OpenInNew,
                contentDescription = null,
                modifier = Modifier
                    .padding(start = 6.dp)
                    .size(16.dp),
            )
        }
    }
}

/**
 * One bullet.
 *
 * The marker is a drawn dot rather than a literal "•" in the string, so translators cannot
 * accidentally drop it and screen readers do not read punctuation aloud.
 */
@Composable
private fun PrivacyBullet(textRes: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            modifier = Modifier
                .padding(top = 7.dp)
                .size(6.dp)
                .background(MaterialTheme.colorScheme.tertiary, CircleShape),
        )
        Text(
            text = stringResource(textRes),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Preview(showBackground = true, name = "Privacy, dark")
@Composable
private fun PrivacyCardDarkPreview() {
    NightTorchTheme(darkTheme = true, dynamicColor = false) {
        PrivacyCard(onOpenSourceCode = {})
    }
}

@Preview(showBackground = true, name = "Privacy, light")
@Composable
private fun PrivacyCardPreview() {
    NightTorchTheme(dynamicColor = false) {
        PrivacyCard(onOpenSourceCode = {})
    }
}
