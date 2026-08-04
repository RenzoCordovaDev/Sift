package com.callbloqued.sift.presentation.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.callbloqued.sift.R
import com.callbloqued.sift.domain.model.BlockReason
import com.callbloqued.sift.domain.model.BlockedCallLogEntry
import com.callbloqued.sift.presentation.theme.Dimens
import java.text.DateFormat
import java.util.Date

/**
 * Root composable for the History screen.
 *
 * Observes [HistoryViewModel.callLog] and renders a scrollable list of blocked-call events.
 * Tapping any row opens an in-context dialog (see [ListActionDialog]) that lets the user add
 * the caller to the whitelist or blacklist with a single additional tap — satisfying product
 * rule 5 (PROJECT_CONTEXT.md §7) while requiring explicit intent before any action fires.
 *
 * The selected-entry state lives in this composable rather than the ViewModel because it is
 * purely ephemeral UI state (the dialog should be dismissed on configuration change).
 *
 * @param viewModel Hilt-provided [HistoryViewModel]; defaults to the Hilt-managed instance.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(viewModel: HistoryViewModel = hiltViewModel()) {
    val callLog by viewModel.callLog.collectAsStateWithLifecycle()
    var selectedEntry by remember { mutableStateOf<BlockedCallLogEntry?>(null) }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.history_title)) }) }
    ) { innerPadding ->
        HistoryContent(
            entries = callLog,
            modifier = Modifier.padding(innerPadding),
            onEntryClick = { entry -> selectedEntry = entry }
        )
    }

    selectedEntry?.let { entry ->
        ListActionDialog(
            phoneNumber = entry.phoneNumber,
            onAddToWhitelist = {
                viewModel.addToWhitelist(entry.phoneNumber)
                selectedEntry = null
            },
            onAddToBlacklist = {
                viewModel.addToBlacklist(entry.phoneNumber)
                selectedEntry = null
            },
            onDismiss = { selectedEntry = null }
        )
    }
}

/**
 * Renders either the empty-state message or the scrollable log list.
 *
 * @param entries The list of blocked-call entries to display.
 * @param modifier Modifier passed from the parent scaffold content slot.
 * @param onEntryClick Called when the user taps a log row; receives the tapped entry.
 */
@Composable
private fun HistoryContent(
    entries: List<BlockedCallLogEntry>,
    modifier: Modifier = Modifier,
    onEntryClick: (BlockedCallLogEntry) -> Unit
) {
    if (entries.isEmpty()) {
        EmptyHistoryState(modifier = modifier)
    } else {
        LazyColumn(modifier = modifier) {
            items(entries, key = { it.id }) { entry ->
                HistoryRow(entry = entry, onClick = { onEntryClick(entry) })
                HorizontalDivider()
            }
        }
    }
}

/**
 * Full-screen centred message shown when no calls have been blocked yet.
 *
 * @param modifier Modifier propagated from the parent.
 */
@Composable
private fun EmptyHistoryState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = stringResource(R.string.history_empty),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Single row in the blocked-call log list.
 *
 * Displays the caller's phone number, a human-readable timestamp, and the block reason.
 * The entire row is clickable so the user can open the list-action dialog via one tap.
 *
 * @param entry The blocked-call event data to display.
 * @param onClick Invoked when the row is tapped.
 */
@Composable
private fun HistoryRow(entry: BlockedCallLogEntry, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Dimens.paddingMedium, vertical = Dimens.paddingSmall),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.phoneNumber,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = formatTimestamp(entry.timestamp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = reasonLabel(entry.reason),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.padding(start = Dimens.paddingSmall)
        )
    }
}

/**
 * Dialog that presents "Add to whitelist" and "Add to blacklist" actions for a given number.
 *
 * Design rationale: the tap on a history row opens this dialog rather than immediately
 * executing an action. This satisfies the product requirement of "one tap" (row tap triggers
 * the action menu) while preventing accidental list modification. The user must make a
 * deliberate second choice inside the dialog, which is the standard mobile pattern for
 * context-sensitive destructive/important actions.
 *
 * @param phoneNumber The E.164 number being acted upon; displayed in the dialog title.
 * @param onAddToWhitelist Invoked when the user confirms "Add to whitelist".
 * @param onAddToBlacklist Invoked when the user confirms "Add to blacklist".
 * @param onDismiss Invoked when the user taps "Dismiss" or outside the dialog.
 */
@Composable
private fun ListActionDialog(
    phoneNumber: String,
    onAddToWhitelist: () -> Unit,
    onAddToBlacklist: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.history_action_title, phoneNumber)) },
        text = null,
        confirmButton = {
            TextButton(onClick = onAddToWhitelist) {
                Text(stringResource(R.string.history_add_to_whitelist))
            }
        },
        dismissButton = {
            Column {
                TextButton(onClick = onAddToBlacklist) {
                    Text(stringResource(R.string.history_add_to_blacklist))
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.history_dismiss))
                }
            }
        }
    )
}

/**
 * Converts a Unix-epoch millisecond timestamp to a locale-aware short date/time string.
 *
 * @param timestamp Unix epoch milliseconds (e.g. from [BlockedCallLogEntry.timestamp]).
 * @return Human-readable date and time string using the device's current locale.
 */
private fun formatTimestamp(timestamp: Long): String {
    val date = Date(timestamp)
    val datePart = DateFormat.getDateInstance(DateFormat.SHORT).format(date)
    val timePart = DateFormat.getTimeInstance(DateFormat.SHORT).format(date)
    return "$datePart $timePart"
}

/**
 * Maps a [BlockReason] to its corresponding localised display label.
 *
 * Returns the string resource value so that Composable call sites can remain stateless
 * with respect to string resolution.
 *
 * @param reason The [BlockReason] enum value to convert.
 * @return A human-readable label string.
 */
@Composable
private fun reasonLabel(reason: BlockReason): String = when (reason) {
    BlockReason.ATTEMPT_THRESHOLD -> stringResource(R.string.history_reason_attempt_threshold)
    BlockReason.MANUAL_BLACKLIST -> stringResource(R.string.history_reason_manual_blacklist)
}
