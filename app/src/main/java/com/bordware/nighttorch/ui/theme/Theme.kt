package com.bordware.nighttorch.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = AmberPrimaryDark,
    onPrimary = AmberOnPrimaryDark,
    primaryContainer = AmberPrimaryContainerDark,
    onPrimaryContainer = AmberOnPrimaryContainerDark,
    secondary = AmberSecondaryDark,
    onSecondary = AmberOnSecondaryDark,
    secondaryContainer = AmberSecondaryContainerDark,
    onSecondaryContainer = AmberOnSecondaryContainerDark,
    tertiary = AmberTertiaryDark,
    onTertiary = AmberOnTertiaryDark,
    tertiaryContainer = AmberTertiaryContainerDark,
    onTertiaryContainer = AmberOnTertiaryContainerDark,
)

private val LightColorScheme = lightColorScheme(
    primary = AmberPrimaryLight,
    onPrimary = AmberOnPrimaryLight,
    primaryContainer = AmberPrimaryContainerLight,
    onPrimaryContainer = AmberOnPrimaryContainerLight,
    secondary = AmberSecondaryLight,
    onSecondary = AmberOnSecondaryLight,
    secondaryContainer = AmberSecondaryContainerLight,
    onSecondaryContainer = AmberOnSecondaryContainerLight,
    tertiary = AmberTertiaryLight,
    onTertiary = AmberOnTertiaryLight,
    tertiaryContainer = AmberTertiaryContainerLight,
    onTertiaryContainer = AmberOnTertiaryContainerLight,
)

/**
 * The brand amber, for the few places that should stay warm regardless of the wallpaper.
 *
 * The app otherwise honours Material You, so its colours follow whatever the user has set.
 * The brightness control is the deliberate exception: it represents light output, so amber
 * carries meaning there rather than decoration, and it ties the control to the crescent
 * mark in the icon.
 *
 * Picks the light or dark variant from the current scheme's luminance rather than
 * `isSystemInDarkTheme()`, so it stays correct inside a `@Preview` that forces a theme.
 */
@Composable
fun torchAccentColor(): Color =
    if (MaterialTheme.colorScheme.surface.luminance() < DARK_SURFACE_THRESHOLD) {
        AmberAccentDark
    } else {
        AmberAccentLight
    }

private const val DARK_SURFACE_THRESHOLD = 0.5f

/**
 * Applies Material You dynamic colour on API 31+ and falls back to a static palette below that.
 *
 * @param dynamicColor set false to force the static palette, which is what the `@Preview`
 *   functions do so previews render deterministically.
 */
@Composable
fun NightTorchTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = if (darkTheme) colorScheme.toAmoled() else colorScheme,
        typography = Typography,
        content = content,
    )
}

/**
 * Drops the dark scheme's backgrounds to true black, keeping the surface *containers* just
 * light enough to separate cards from the page.
 *
 * Applied on top of dynamic colour rather than instead of it, so the palette still follows
 * the user's wallpaper — only the ground goes black. Material's own dark scheme uses a dark
 * grey, which on OLED still lights every pixel.
 */
private fun ColorScheme.toAmoled(): ColorScheme = copy(
    background = AmoledBlack,
    surface = AmoledBlack,
    surfaceContainerLowest = AmoledBlack,
    surfaceContainerLow = AmoledSurfaceLow,
    surfaceContainer = AmoledSurface,
    surfaceContainerHigh = AmoledSurfaceHigh,
    surfaceContainerHighest = AmoledSurfaceHighest,
)