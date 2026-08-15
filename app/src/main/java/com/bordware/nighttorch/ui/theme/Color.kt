package com.bordware.nighttorch.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Fallback palette for devices without Material You dynamic colour (API < 31).
 *
 * Warm amber rather than the template purple: it reads as lamplight, and it keeps the app's
 * own chrome from looking cold next to a lit torch.
 *
 * Contrast pairs here are the standard Material 3 tonal steps — each `on*` colour is taken
 * far enough from its container to clear WCAG AA for body text. Phase 7 verifies that.
 */

// Light scheme
val AmberPrimaryLight = Color(0xFF7C5800)
val AmberOnPrimaryLight = Color(0xFFFFFFFF)
val AmberPrimaryContainerLight = Color(0xFFFFDEA6)
val AmberOnPrimaryContainerLight = Color(0xFF271900)
val AmberSecondaryLight = Color(0xFF6C5C3F)
val AmberOnSecondaryLight = Color(0xFFFFFFFF)
val AmberSecondaryContainerLight = Color(0xFFF6E0BB)
val AmberOnSecondaryContainerLight = Color(0xFF251A04)
val AmberTertiaryLight = Color(0xFF4C6544)
val AmberOnTertiaryLight = Color(0xFFFFFFFF)
val AmberTertiaryContainerLight = Color(0xFFCEEBC1)
val AmberOnTertiaryContainerLight = Color(0xFF0A2007)

/**
 * Softer amber used for the brightness slider and its level readout.
 *
 * Lighter and less saturated than [AmberPrimaryDark], so a wide filled bar sits alongside a
 * dynamic palette without shouting over it. A full-width block of the punchier primary read
 * as a warning stripe rather than a control.
 *
 * Measured against WCAG AA. The dark variant reaches 14.4:1 on a card. The light variant was
 * originally `0xFF9A7828`, which is only **4.02:1** on the light surface and so fails the
 * 4.5:1 threshold for the level readout drawn in it; darkened to clear AA with margin.
 */
val AmberAccentDark = Color(0xFFF7DFAE)
val AmberAccentLight = Color(0xFF8A6A1E)

/**
 * True black for OLED panels.
 *
 * Black pixels are simply off, so a torch app — which is used at night, often for a long
 * time, and is the sort of thing left open on the bedside table — costs measurably less
 * battery this way, and throws far less light in a dark room than dark grey does.
 */
val AmoledBlack = Color(0xFF000000)
val AmoledSurfaceLow = Color(0xFF0A0A0B)
val AmoledSurface = Color(0xFF121214)
val AmoledSurfaceHigh = Color(0xFF1B1B1E)
val AmoledSurfaceHighest = Color(0xFF232326)

// Dark scheme
val AmberPrimaryDark = Color(0xFFF7BD48)
val AmberOnPrimaryDark = Color(0xFF422C00)
val AmberPrimaryContainerDark = Color(0xFF5E4200)
val AmberOnPrimaryContainerDark = Color(0xFFFFDEA6)
val AmberSecondaryDark = Color(0xFFD9C4A0)
val AmberOnSecondaryDark = Color(0xFF3B2F15)
val AmberSecondaryContainerDark = Color(0xFF53442A)
val AmberOnSecondaryContainerDark = Color(0xFFF6E0BB)
val AmberTertiaryDark = Color(0xFFB2CFA6)
val AmberOnTertiaryDark = Color(0xFF1F361A)
val AmberTertiaryContainerDark = Color(0xFF354C2E)
val AmberOnTertiaryContainerDark = Color(0xFFCEEBC1)
