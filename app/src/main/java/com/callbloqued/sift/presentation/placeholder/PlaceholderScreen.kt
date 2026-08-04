package com.callbloqued.sift.presentation.placeholder

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.callbloqued.sift.R
import com.callbloqued.sift.presentation.theme.SiftTheme

/**
 * Temporary full-screen placeholder displayed while the real UI is being built in F4.
 *
 * This composable exists solely to give the F0 skeleton a runnable entry point.
 * It will be replaced by the navigation host and feature screens in F4 (UI phase).
 * No business logic or ViewModel interaction belongs here.
 */
@Composable
fun PlaceholderScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(text = stringResource(id = R.string.placeholder_setup_complete))
    }
}

@Preview(showBackground = true)
@Composable
private fun PlaceholderScreenPreview() {
    SiftTheme {
        PlaceholderScreen()
    }
}
