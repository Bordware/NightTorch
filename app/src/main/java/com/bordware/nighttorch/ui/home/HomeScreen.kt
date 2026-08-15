package com.bordware.nighttorch.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.bordware.nighttorch.R
import com.bordware.nighttorch.data.AppSettings
import com.bordware.nighttorch.schedule.BrightnessDecision
import com.bordware.nighttorch.torch.TorchCapability
import com.bordware.nighttorch.torch.TorchState
import com.bordware.nighttorch.ui.home.components.AutoDimmingCard
import com.bordware.nighttorch.ui.home.components.ManualTorchCard
import com.bordware.nighttorch.ui.home.components.PrivacyCard
import com.bordware.nighttorch.ui.home.components.ServiceStatusCard
import com.bordware.nighttorch.ui.theme.NightTorchTheme
import java.time.LocalTime

/**
 * The whole home screen: four cards in a scrolling column.
 *
 * Takes data and lambdas only — it holds no state of its own, so it renders identically in
 * a preview, a test and the running app.
 */
@Composable
fun HomeScreen(
    state: HomeUiState,
    onOpenAccessibilitySettings: () -> Unit,
    onToggleTorch: () -> Unit,
    onManualBrightnessChange: (Int) -> Unit,
    onAutoDimmingChange: (Boolean) -> Unit,
    onNightStartChange: (LocalTime) -> Unit,
    onNightEndChange: (LocalTime) -> Unit,
    onNightBrightnessChange: (Int) -> Unit,
    onDayBrightnessChange: (Int) -> Unit,
    onOpenSourceCode: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AppHeader()

        ServiceStatusCard(
            serviceEnabled = state.serviceEnabled,
            onOpenAccessibilitySettings = onOpenAccessibilitySettings,
        )

        ManualTorchCard(
            torch = state.torch,
            brightnessPercent = state.manualBrightnessPercent,
            onToggle = onToggleTorch,
            onBrightnessChange = onManualBrightnessChange,
        )

        AutoDimmingCard(
            settings = state.settings,
            now = state.now,
            decision = state.scheduleDecision,
            capability = state.torch.capability,
            onAutoDimmingChange = onAutoDimmingChange,
            onNightStartChange = onNightStartChange,
            onNightEndChange = onNightEndChange,
            onNightBrightnessChange = onNightBrightnessChange,
            onDayBrightnessChange = onDayBrightnessChange,
        )

        PrivacyCard(onOpenSourceCode = onOpenSourceCode)
    }
}

/**
 * App identity: the launcher mark, the name, and one line saying what this thing is.
 *
 * Reuses the launcher icon drawable rather than a separate asset, so the icon in the app
 * drawer and the icon at the top of the screen can never drift apart.
 */
@Composable
private fun AppHeader(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = stringResource(R.string.app_logo),
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        )
        Column {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = stringResource(R.string.app_tagline),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun previewState(
    serviceEnabled: Boolean = true,
    torch: TorchState = TorchState(
        isOn = true,
        level = 1,
        capability = TorchCapability.Variable(maxLevel = 21, defaultLevel = 21),
    ),
    settings: AppSettings = AppSettings.DEFAULT,
    now: LocalTime = LocalTime.of(22, 14),
    decision: BrightnessDecision? = BrightnessDecision(isNight = true, percent = 1, level = 1),
) = HomeUiState(
    serviceEnabled = serviceEnabled,
    torch = torch,
    settings = settings,
    manualBrightnessPercent = 100,
    now = now,
    scheduleDecision = decision,
)

@Composable
private fun PreviewHost(state: HomeUiState, darkTheme: Boolean = false) {
    NightTorchTheme(darkTheme = darkTheme, dynamicColor = false) {
        HomeScreen(
            state = state,
            onOpenAccessibilitySettings = {},
            onToggleTorch = {},
            onManualBrightnessChange = {},
            onAutoDimmingChange = {},
            onNightStartChange = {},
            onNightEndChange = {},
            onNightBrightnessChange = {},
            onDayBrightnessChange = {},
            onOpenSourceCode = {},
        )
    }
}

@Preview(showBackground = true, heightDp = 1400, name = "Everything working")
@Composable
private fun HomeScreenPreview() {
    PreviewHost(previewState())
}

@Preview(showBackground = true, heightDp = 1400, name = "Service off, torch off")
@Composable
private fun HomeScreenServiceOffPreview() {
    PreviewHost(
        previewState(
            serviceEnabled = false,
            torch = TorchState(
                isOn = false,
                capability = TorchCapability.Variable(maxLevel = 21, defaultLevel = 21),
            ),
        ),
    )
}

@Preview(showBackground = true, heightDp = 1400, name = "Binary-only device, dark")
@Composable
private fun HomeScreenBinaryDarkPreview() {
    PreviewHost(
        state = previewState(
            torch = TorchState(isOn = false, capability = TorchCapability.BinaryOnly),
        ),
        darkTheme = true,
    )
}
