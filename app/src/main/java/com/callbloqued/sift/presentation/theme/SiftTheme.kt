package com.callbloqued.sift.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

/**
 * Root Material3 theme for Sift.
 *
 * Uses the system dark-mode preference to choose between the light and dark colour schemes.
 * Custom brand colours and typography will be introduced in F4 (UI phase) once the design
 * system is defined.  This minimal theme satisfies the Compose scaffold requirement for F0.
 *
 * @param darkTheme Whether to apply the dark colour scheme; defaults to the system setting.
 * @param content   The composable tree to theme.
 */
@Composable
fun SiftTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) darkColorScheme() else lightColorScheme()

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
