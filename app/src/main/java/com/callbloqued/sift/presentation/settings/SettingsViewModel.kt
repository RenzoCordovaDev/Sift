package com.callbloqued.sift.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.callbloqued.sift.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Milliseconds to keep the upstream Flow alive after the last subscriber disappears. */
private const val FLOW_TIMEOUT_MS = 5_000L

/** Minimum value that [SettingsRepository.setRequiredAttemptCount] accepts. */
private const val MIN_ATTEMPT_COUNT = 1

/**
 * ViewModel for the Settings screen.
 *
 * Bridges [SettingsRepository] to the Compose UI layer without exposing Android or Room
 * internals. All mutations run on [viewModelScope] so they survive brief recompositions.
 * UI-level validation (attempt count ≥ 1) is enforced here rather than relying on the
 * [IllegalArgumentException] that [SettingsRepository.setRequiredAttemptCount] would throw,
 * producing a better user experience.
 *
 * @param settingsRepository Source of truth for user-facing app settings (domain interface).
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    /**
     * Whether the call-screening filter is currently active.
     *
     * Emits `true` by default (filter on) until the first DataStore value arrives.
     */
    val isFilterEnabled: StateFlow<Boolean> = settingsRepository
        .isFilterEnabled()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(FLOW_TIMEOUT_MS),
            initialValue = true
        )

    /**
     * The minimum number of blocked attempts before a caller is allowed through.
     *
     * Emits `1` by default until the first DataStore value arrives.
     */
    val requiredAttemptCount: StateFlow<Int> = settingsRepository
        .getRequiredAttemptCount()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(FLOW_TIMEOUT_MS),
            initialValue = MIN_ATTEMPT_COUNT
        )

    /**
     * Enables or disables the call-screening filter.
     *
     * @param enabled `true` to activate screening; `false` to let all calls through.
     */
    fun setFilterEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setFilterEnabled(enabled) }
    }

    /**
     * Updates the required-attempt threshold, ignoring values below the minimum.
     *
     * UI-level guard: if [count] is less than [MIN_ATTEMPT_COUNT] the call is silently
     * dropped. This prevents the UI from triggering the [IllegalArgumentException] that
     * [SettingsRepository.setRequiredAttemptCount] would throw, keeping error handling
     * at the UI boundary rather than propagating to the domain layer.
     *
     * @param count New threshold value; must be ≥ 1 to take effect.
     */
    fun setRequiredAttemptCount(count: Int) {
        if (count < MIN_ATTEMPT_COUNT) return
        viewModelScope.launch { settingsRepository.setRequiredAttemptCount(count) }
    }
}
