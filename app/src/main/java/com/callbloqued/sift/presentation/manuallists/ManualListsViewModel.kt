package com.callbloqued.sift.presentation.manuallists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.callbloqued.sift.domain.model.ManualListEntry
import com.callbloqued.sift.domain.model.ManualListType
import com.callbloqued.sift.domain.repository.ManualListRepository
import com.callbloqued.sift.domain.util.PhoneNumberNormalizer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Milliseconds to keep the upstream Flow alive after the last subscriber disappears. */
private const val FLOW_TIMEOUT_MS = 5_000L

/**
 * ViewModel for the Manual Lists screen.
 *
 * Exposes live lists for both the blacklist and whitelist and provides actions for adding
 * and removing entries. Phone numbers entered by the user in the "add" form are normalised
 * to E.164 via [PhoneNumberNormalizer] before being handed to [ManualListRepository], as
 * the repository contract requires E.164 input. Normalisation uses the device's default
 * locale as the region hint.
 *
 * [PhoneNumberNormalizer] lives in the `domain` layer and has no Android dependencies,
 * so injecting it into this ViewModel does not violate the Clean Architecture rules.
 *
 * @param manualListRepository Source of truth for the user's blacklist and whitelist.
 * @param normalizer Converts raw user input to E.164 before persisting.
 */
@HiltViewModel
class ManualListsViewModel @Inject constructor(
    private val manualListRepository: ManualListRepository,
    private val normalizer: PhoneNumberNormalizer
) : ViewModel() {

    /**
     * Live list of entries currently in the user's blacklist, newest-first.
     */
    val blacklist: StateFlow<List<ManualListEntry>> = manualListRepository
        .observeList(ManualListType.BLACKLIST)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(FLOW_TIMEOUT_MS),
            initialValue = emptyList()
        )

    /**
     * Live list of entries currently in the user's whitelist, newest-first.
     */
    val whitelist: StateFlow<List<ManualListEntry>> = manualListRepository
        .observeList(ManualListType.WHITELIST)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(FLOW_TIMEOUT_MS),
            initialValue = emptyList()
        )

    /**
     * Removes [phoneNumber] from whichever list it belongs to.
     *
     * The number must be in E.164 format (as stored by [ManualListEntry.phoneNumber]).
     * This is guaranteed when called from existing list entries displayed in the UI.
     *
     * @param phoneNumber The E.164-formatted number to remove.
     */
    fun removeFromList(phoneNumber: String) {
        viewModelScope.launch { manualListRepository.removeFromList(phoneNumber) }
    }

    /**
     * Normalises [rawNumber] and adds it to [listType] if the input is valid.
     *
     * Returns `true` when the number was successfully normalised and persisted, or `false`
     * when [rawNumber] cannot be parsed to a valid E.164 number. The caller (typically the
     * composable's event handler) can use the return value to display an error message.
     *
     * @param rawNumber The phone number as typed by the user; may be in any format.
     * @param listType The list to add the number to.
     * @return `true` if the number was valid and the add was dispatched; `false` otherwise.
     */
    fun addToList(rawNumber: String, listType: ManualListType): Boolean {
        val normalised = normalizer.normalize(rawNumber) ?: return false
        viewModelScope.launch { manualListRepository.addToList(normalised, listType) }
        return true
    }
}
