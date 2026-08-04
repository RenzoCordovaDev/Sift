package com.callbloqued.sift.presentation.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.callbloqued.sift.domain.model.BlockedCallLogEntry
import com.callbloqued.sift.domain.model.ManualListType
import com.callbloqued.sift.domain.repository.CallLogRepository
import com.callbloqued.sift.domain.repository.ManualListRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Milliseconds to keep the upstream Flow alive after the last subscriber disappears. */
private const val FLOW_TIMEOUT_MS = 5_000L

/**
 * ViewModel for the History screen.
 *
 * Exposes a reactive list of blocked-call log entries and provides actions for adding a
 * caller to the user's manual whitelist or blacklist directly from the history. By placing
 * the action here (not in the composable) the business-level intent is testable with
 * JUnit5 + MockK + Turbine without any Android instrumentation.
 *
 * @param callLogRepository Source of truth for the blocked-call event log (domain interface).
 * @param manualListRepository Used to add a caller to the blacklist or whitelist.
 */
@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val callLogRepository: CallLogRepository,
    private val manualListRepository: ManualListRepository
) : ViewModel() {

    /**
     * Live list of blocked-call events ordered newest-first.
     *
     * Backed by [CallLogRepository.observeBlockedCallLog] and converted to a [StateFlow] so
     * that the composable can use [androidx.lifecycle.compose.collectAsStateWithLifecycle]
     * without manual lifecycle management.
     */
    val callLog: StateFlow<List<BlockedCallLogEntry>> = callLogRepository
        .observeBlockedCallLog()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(FLOW_TIMEOUT_MS),
            initialValue = emptyList()
        )

    /**
     * Adds [phoneNumber] to the user's whitelist so future calls from that number bypass
     * the attempt-threshold check.
     *
     * The number is already in E.164 format as stored in [BlockedCallLogEntry.phoneNumber];
     * no additional normalisation is needed here.
     *
     * @param phoneNumber The caller's E.164-formatted phone number to whitelist.
     */
    fun addToWhitelist(phoneNumber: String) {
        viewModelScope.launch {
            manualListRepository.addToList(phoneNumber, ManualListType.WHITELIST)
        }
    }

    /**
     * Adds [phoneNumber] to the user's blacklist so all future calls from that number are
     * silently rejected regardless of attempt count.
     *
     * The number is already in E.164 format as stored in [BlockedCallLogEntry.phoneNumber];
     * no additional normalisation is needed here.
     *
     * @param phoneNumber The caller's E.164-formatted phone number to blacklist.
     */
    fun addToBlacklist(phoneNumber: String) {
        viewModelScope.launch {
            manualListRepository.addToList(phoneNumber, ManualListType.BLACKLIST)
        }
    }
}
