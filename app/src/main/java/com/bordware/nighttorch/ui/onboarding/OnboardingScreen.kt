package com.bordware.nighttorch.ui.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.bordware.nighttorch.R
import com.bordware.nighttorch.data.AppSettings
import com.bordware.nighttorch.torch.TorchCapability
import com.bordware.nighttorch.ui.components.CardHeader
import com.bordware.nighttorch.ui.components.MinTouchTarget
import com.bordware.nighttorch.ui.components.SectionCard
import com.bordware.nighttorch.ui.theme.NightTorchTheme
import kotlinx.coroutines.launch
import java.time.LocalTime

private const val PAGE_COUNT = 3

/**
 * First-run flow.
 *
 * Two deliberate constraints, both learned the hard way on a real
 * device during development:
 *
 * - Page two's Next is gated on the service being *detected as running*, not on the user
 *   having visited Settings. Visiting proves nothing; the toggle can be refused.
 * - Skip is present on every page. An accessibility prompt the user cannot escape is a dark
 *   pattern, and the app is still a perfectly good torch without the shortcut.
 */
@Composable
fun OnboardingScreen(
    state: OnboardingUiState,
    onOpenAccessibilitySettings: () -> Unit,
    onNightStartChange: (LocalTime) -> Unit,
    onNightEndChange: (LocalTime) -> Unit,
    onNightBrightnessChange: (Int) -> Unit,
    onDayBrightnessChange: (Int) -> Unit,
    onAutoDimmingChange: (Boolean) -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pagerState = rememberPagerState(pageCount = { PAGE_COUNT })
    val coroutineScope = rememberCoroutineScope()

    // Deliberately does NOT advance itself when the service comes on. Auto-advancing moves
    // the page out from under the user, and because the primary button's action depends on
    // which page is showing, it silently changes "Next" into "Done" beneath their finger —
    // a second tap then ends onboarding instead of continuing it. The page confirms success
    // in place and lets the user move on when they are ready.

    Column(modifier = modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) { page ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                when (page) {
                    0 -> WelcomePage()
                    1 -> EnableServicePage(
                        serviceEnabled = state.serviceEnabled,
                        showRestrictedHint = state.showRestrictedSettingsHint,
                        onOpenAccessibilitySettings = onOpenAccessibilitySettings,
                    )

                    else -> SchedulePage(
                        settings = state.settings,
                        capability = state.capability,
                        onNightStartChange = onNightStartChange,
                        onNightEndChange = onNightEndChange,
                        onNightBrightnessChange = onNightBrightnessChange,
                        onDayBrightnessChange = onDayBrightnessChange,
                        onAutoDimmingChange = onAutoDimmingChange,
                    )
                }
            }
        }

        NavigationBar(
            page = pagerState.currentPage,
            canAdvance = pagerState.currentPage != 1 || state.serviceEnabled,
            onBack = {
                coroutineScope.launch {
                    pagerState.animateScrollToPage(pagerState.currentPage - 1)
                }
            },
            onNext = {
                coroutineScope.launch {
                    pagerState.animateScrollToPage(pagerState.currentPage + 1)
                }
            },
            onFinish = onFinish,
        )
    }
}

@Composable
private fun WelcomePage() {
    Image(
        painter = painterResource(R.drawable.ic_launcher_foreground),
        contentDescription = stringResource(R.string.app_logo),
        modifier = Modifier
            .size(96.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    )
    Text(
        text = stringResource(R.string.onboarding_welcome_title),
        style = MaterialTheme.typography.headlineSmall,
    )
    Text(
        text = stringResource(R.string.onboarding_welcome_body),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
        text = stringResource(R.string.onboarding_welcome_dimming),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
        text = stringResource(R.string.onboarding_welcome_privacy),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun EnableServicePage(
    serviceEnabled: Boolean,
    showRestrictedHint: Boolean,
    onOpenAccessibilitySettings: () -> Unit,
) {
    Text(
        text = stringResource(R.string.onboarding_enable_title),
        style = MaterialTheme.typography.headlineSmall,
    )
    Text(
        text = stringResource(R.string.onboarding_enable_body),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    // Named before it happens. The system dialog is alarming and cannot be customised, so
    // the honest move is to explain it in advance rather than let the user meet it cold.
    SectionCard {
        CardHeader(
            icon = Icons.Filled.Info,
            title = stringResource(R.string.onboarding_enable_warning_title),
            iconContentDescription = null,
        )
        Text(
            text = stringResource(R.string.onboarding_enable_warning_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (serviceEnabled) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
            )
            Text(
                text = stringResource(R.string.onboarding_enable_done),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
    } else {
        Button(
            onClick = onOpenAccessibilitySettings,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = MinTouchTarget),
        ) {
            Icon(
                imageVector = Icons.Filled.Settings,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = stringResource(R.string.onboarding_enable_action),
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        Text(
            text = stringResource(R.string.onboarding_enable_waiting),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    AnimatedVisibility(visible = showRestrictedHint) {
        SectionCard(accentBorder = MaterialTheme.colorScheme.error) {
            CardHeader(
                icon = Icons.Filled.Info,
                title = stringResource(R.string.onboarding_restricted_title),
                iconContentDescription = null,
                iconTint = MaterialTheme.colorScheme.error,
                iconBackground = MaterialTheme.colorScheme.error.copy(alpha = 0.16f),
            )
            Text(
                text = stringResource(R.string.onboarding_restricted_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SchedulePage(
    settings: AppSettings,
    capability: TorchCapability,
    onNightStartChange: (LocalTime) -> Unit,
    onNightEndChange: (LocalTime) -> Unit,
    onNightBrightnessChange: (Int) -> Unit,
    onDayBrightnessChange: (Int) -> Unit,
    onAutoDimmingChange: (Boolean) -> Unit,
) {
    Text(
        text = stringResource(R.string.onboarding_schedule_title),
        style = MaterialTheme.typography.headlineSmall,
    )
    Text(
        text = stringResource(R.string.onboarding_schedule_body),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    // The same card as the home screen, so nothing here is a one-off the user has to learn
    // twice, and there is no second implementation to keep in step.
    com.bordware.nighttorch.ui.home.components.AutoDimmingCard(
        settings = settings,
        now = LocalTime.now(),
        decision = null,
        capability = capability,
        onAutoDimmingChange = onAutoDimmingChange,
        onNightStartChange = onNightStartChange,
        onNightEndChange = onNightEndChange,
        onNightBrightnessChange = onNightBrightnessChange,
        onDayBrightnessChange = onDayBrightnessChange,
    )
}

@Composable
private fun NavigationBar(
    page: Int,
    canAdvance: Boolean,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onFinish: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.onboarding_page_indicator, page + 1, PAGE_COUNT),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (page > 0) {
                TextButton(onClick = onBack, modifier = Modifier.heightIn(min = MinTouchTarget)) {
                    Text(stringResource(R.string.onboarding_back))
                }
            }

            // Always reachable. An accessibility prompt with no way out is a dark pattern,
            // and the torch works from the main screen regardless.
            TextButton(onClick = onFinish, modifier = Modifier.heightIn(min = MinTouchTarget)) {
                Text(stringResource(R.string.onboarding_skip))
            }

            Button(
                onClick = if (page == PAGE_COUNT - 1) onFinish else onNext,
                enabled = canAdvance,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = MinTouchTarget),
            ) {
                Text(
                    stringResource(
                        if (page == PAGE_COUNT - 1) {
                            R.string.onboarding_finish
                        } else {
                            R.string.onboarding_next
                        },
                    ),
                )
            }
        }
    }
}

@Composable
private fun PreviewHost(state: OnboardingUiState) {
    NightTorchTheme(darkTheme = true, dynamicColor = false) {
        OnboardingScreen(
            state = state,
            onOpenAccessibilitySettings = {},
            onNightStartChange = {},
            onNightEndChange = {},
            onNightBrightnessChange = {},
            onDayBrightnessChange = {},
            onAutoDimmingChange = {},
            onFinish = {},
        )
    }
}

@Preview(showBackground = true, heightDp = 900, name = "Welcome")
@Composable
private fun OnboardingWelcomePreview() {
    PreviewHost(OnboardingUiState())
}

@Preview(showBackground = true, heightDp = 900, name = "Restricted settings hint")
@Composable
private fun OnboardingRestrictedPreview() {
    PreviewHost(
        OnboardingUiState(serviceEnabled = false, showRestrictedSettingsHint = true),
    )
}

@Preview(showBackground = true, heightDp = 900, name = "Service enabled")
@Composable
private fun OnboardingEnabledPreview() {
    PreviewHost(
        OnboardingUiState(
            serviceEnabled = true,
            capability = TorchCapability.Variable(maxLevel = 21, defaultLevel = 21),
        ),
    )
}
