package com.callbloqued.sift.presentation.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.callbloqued.sift.R
import com.callbloqued.sift.presentation.theme.Dimens

/** Minimum allowed value for the required-attempt counter. */
private const val MIN_ATTEMPT_COUNT = 1

/**
 * Root composable for the Settings screen.
 *
 * Renders two controls connected to [SettingsViewModel]:
 * - A [Switch] toggling the call-screening filter on/off.
 * - An increment/decrement counter for the required-attempt threshold, validated to ≥ 1.
 *
 * @param viewModel Hilt-provided [SettingsViewModel]; defaults to the Hilt-managed instance.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val filterEnabled by viewModel.isFilterEnabled.collectAsStateWithLifecycle()
    val attemptCount by viewModel.requiredAttemptCount.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.settings_title)) }) }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            FilterToggleRow(
                enabled = filterEnabled,
                onToggle = viewModel::setFilterEnabled
            )
            AttemptCountRow(
                count = attemptCount,
                onDecrement = { viewModel.setRequiredAttemptCount(attemptCount - 1) },
                onIncrement = { viewModel.setRequiredAttemptCount(attemptCount + 1) }
            )
        }
    }
}

/**
 * Row displaying the filter-enabled toggle with a label and description.
 *
 * @param enabled Current state of the screening filter.
 * @param onToggle Callback invoked when the user flips the switch.
 */
@Composable
private fun FilterToggleRow(enabled: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.paddingMedium, vertical = Dimens.paddingSmall),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.settings_filter_enabled_label),
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = stringResource(R.string.settings_filter_enabled_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = enabled,
            onCheckedChange = onToggle
        )
    }
}

/**
 * Row displaying the required-attempt counter with decrement and increment buttons.
 *
 * The decrement button is disabled when [count] equals [MIN_ATTEMPT_COUNT] to prevent the
 * user from setting a value that would be rejected by [SettingsViewModel.setRequiredAttemptCount].
 * This UI-level guard provides immediate feedback instead of silently dropping the action.
 *
 * @param count Current threshold value to display.
 * @param onDecrement Callback invoked when the user taps the minus button.
 * @param onIncrement Callback invoked when the user taps the plus button.
 */
@Composable
private fun AttemptCountRow(count: Int, onDecrement: () -> Unit, onIncrement: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.paddingMedium, vertical = Dimens.paddingSmall),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.settings_attempt_count_label),
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = stringResource(R.string.settings_attempt_count_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(Dimens.paddingSmall))
        AttemptCountStepper(count = count, onDecrement = onDecrement, onIncrement = onIncrement)
    }
}

/**
 * Compact stepper control showing the current count with minus and plus buttons.
 *
 * @param count Current value displayed between the two buttons.
 * @param onDecrement Invoked when the minus button is tapped; disabled at [MIN_ATTEMPT_COUNT].
 * @param onIncrement Invoked when the plus button is tapped.
 */
@Composable
private fun AttemptCountStepper(count: Int, onDecrement: () -> Unit, onIncrement: () -> Unit) {
    val decrementCd = stringResource(R.string.settings_attempt_count_label) + " decrease"
    val incrementCd = stringResource(R.string.settings_attempt_count_label) + " increase"
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(
            onClick = onDecrement,
            enabled = count > MIN_ATTEMPT_COUNT,
            modifier = Modifier.semantics { contentDescription = decrementCd }
        ) {
            Text(text = "−", style = MaterialTheme.typography.titleLarge)
        }
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = Dimens.paddingSmall)
        )
        IconButton(
            onClick = onIncrement,
            modifier = Modifier.semantics { contentDescription = incrementCd }
        ) {
            Icon(imageVector = Icons.Filled.Add, contentDescription = null)
        }
    }
}
