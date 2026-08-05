package com.callbloqued.sift.presentation.manuallists

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.callbloqued.sift.R
import com.callbloqued.sift.domain.model.ManualListEntry
import com.callbloqued.sift.domain.model.ManualListType
import com.callbloqued.sift.presentation.theme.Dimens

/** Index of the Blacklist tab in [TabRow]. */
private const val TAB_BLACKLIST = 0

/** Index of the Whitelist tab in [TabRow]. */
private const val TAB_WHITELIST = 1

/**
 * Root composable for the Manual Lists screen.
 *
 * Displays both the user's blacklist and whitelist via a [TabRow]. Each tab shows the
 * entries for that list and allows the user to remove any entry. An input row at the bottom
 * lets the user add a new number directly from this screen; the input is normalised to E.164
 * by [ManualListsViewModel.addToList] before persisting.
 *
 * @param viewModel Hilt-provided [ManualListsViewModel]; defaults to the Hilt-managed instance.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualListsScreen(viewModel: ManualListsViewModel = hiltViewModel()) {
    val blacklist by viewModel.blacklist.collectAsStateWithLifecycle()
    val whitelist by viewModel.whitelist.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableIntStateOf(TAB_BLACKLIST) }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.manual_lists_title)) }) }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            ManualListTabRow(selectedTab = selectedTab, onTabSelected = { selectedTab = it })
            val activeList = if (selectedTab == TAB_BLACKLIST) blacklist else whitelist
            val activeType = if (selectedTab == TAB_BLACKLIST) ManualListType.BLACKLIST
            else ManualListType.WHITELIST
            ManualListContent(
                entries = activeList,
                listType = activeType,
                modifier = Modifier.weight(1f),
                onRemove = viewModel::removeFromList
            )
            AddNumberRow(
                listType = activeType,
                onAdd = { rawNumber, listType -> viewModel.addToList(rawNumber, listType) }
            )
        }
    }
}

/**
 * Tab bar for switching between the Blacklist and Whitelist views.
 *
 * @param selectedTab Index of the currently active tab (0 = blacklist, 1 = whitelist).
 * @param onTabSelected Callback invoked with the new tab index when the user switches.
 */
@Composable
private fun ManualListTabRow(selectedTab: Int, onTabSelected: (Int) -> Unit) {
    TabRow(selectedTabIndex = selectedTab) {
        Tab(
            selected = selectedTab == TAB_BLACKLIST,
            onClick = { onTabSelected(TAB_BLACKLIST) },
            text = { Text(stringResource(R.string.manual_lists_tab_blacklist)) }
        )
        Tab(
            selected = selectedTab == TAB_WHITELIST,
            onClick = { onTabSelected(TAB_WHITELIST) },
            text = { Text(stringResource(R.string.manual_lists_tab_whitelist)) }
        )
    }
}

/**
 * Shows either the empty-state message or the scrollable entry list for [listType].
 *
 * @param entries The entries to display.
 * @param listType The type of list being shown (used to pick the correct empty-state message).
 * @param modifier Modifier passed from the parent layout.
 * @param onRemove Called with the phone number when the user taps the delete button.
 */
@Composable
private fun ManualListContent(
    entries: List<ManualListEntry>,
    listType: ManualListType,
    modifier: Modifier = Modifier,
    onRemove: (String) -> Unit
) {
    if (entries.isEmpty()) {
        EmptyListState(listType = listType, modifier = modifier)
    } else {
        LazyColumn(modifier = modifier) {
            items(entries, key = { it.phoneNumber }) { entry ->
                ManualListEntryRow(entry = entry, onRemove = { onRemove(entry.phoneNumber) })
                HorizontalDivider()
            }
        }
    }
}

/**
 * Centred empty-state message appropriate for the given [listType].
 *
 * @param listType Determines which empty-state string resource is displayed.
 * @param modifier Modifier propagated from the parent.
 */
@Composable
private fun EmptyListState(listType: ManualListType, modifier: Modifier = Modifier) {
    val message = if (listType == ManualListType.BLACKLIST) {
        stringResource(R.string.manual_lists_empty_blacklist)
    } else {
        stringResource(R.string.manual_lists_empty_whitelist)
    }
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Row displaying a single manual-list entry with a delete button.
 *
 * @param entry The [ManualListEntry] to display.
 * @param onRemove Invoked when the user taps the delete icon for this entry.
 */
@Composable
private fun ManualListEntryRow(entry: ManualListEntry, onRemove: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.paddingMedium, vertical = Dimens.paddingSmall),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = entry.phoneNumber,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onRemove) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = stringResource(R.string.manual_lists_remove_cd, entry.phoneNumber)
            )
        }
    }
}

/**
 * Input row for manually adding a phone number to the active list.
 *
 * Normalisation and validation are delegated to [ManualListsViewModel.addToList]. If the
 * returned value is `false` the field shows an inline error. On success the field is cleared.
 *
 * @param listType The list the number will be added to; determines the button label.
 * @param onAdd Callback accepting the raw typed number and [listType]; returns `true` on success.
 */
@Composable
private fun AddNumberRow(listType: ManualListType, onAdd: (String, ManualListType) -> Boolean) {
    var input by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }

    fun submit() {
        val success = onAdd(input.trim(), listType)
        if (success) {
            input = ""
            showError = false
        } else {
            showError = true
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(Dimens.paddingSmall),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = input,
            onValueChange = { input = it; showError = false },
            modifier = Modifier.weight(1f),
            placeholder = { Text(stringResource(R.string.manual_lists_add_hint)) },
            isError = showError,
            supportingText = if (showError) {
                { Text(stringResource(R.string.manual_lists_invalid_number)) }
            } else null,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Phone,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            singleLine = true
        )
        IconButton(
            onClick = { submit() },
            modifier = Modifier.padding(start = Dimens.paddingExtraSmall)
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = stringResource(R.string.manual_lists_add_button)
            )
        }
    }
}
