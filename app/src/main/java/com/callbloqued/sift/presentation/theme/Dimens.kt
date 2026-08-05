package com.callbloqued.sift.presentation.theme

import androidx.compose.ui.unit.dp

/**
 * Centralised spacing constants for the Sift UI.
 *
 * All dimension values are named constants so that composables and layouts share a consistent
 * spacing system without magic numbers in their call sites. detekt's MagicNumber rule is
 * configured with `ignorePropertyDeclaration: true`, so these declarations are intentionally
 * allowed — the names are self-documenting.
 */
internal object Dimens {

    /** 4 dp — tight spacing between closely related elements (e.g. icon and label). */
    val paddingExtraSmall = 4.dp

    /** 8 dp — small spacing within a card or list item. */
    val paddingSmall = 8.dp

    /** 16 dp — standard horizontal/vertical screen padding. */
    val paddingMedium = 16.dp

    /** 24 dp — larger gap between distinct sections of a screen. */
    val paddingLarge = 24.dp

    /** 48 dp — minimum recommended touch-target size (Material guideline). */
    val minTouchTarget = 48.dp

    /** 56 dp — standard FAB size and bottom-nav icon touch area. */
    val iconButtonSize = 56.dp
}
