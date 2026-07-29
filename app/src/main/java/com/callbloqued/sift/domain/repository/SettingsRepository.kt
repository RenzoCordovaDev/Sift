package com.callbloqued.sift.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * Contract for reading and writing user-facing application settings.
 *
 * Settings are persisted via DataStore Preferences (implementation in `data` layer).
 * Exposing settings as [Flow] lets UI and use-case layers react to changes in real time
 * without polling.
 *
 * Full implementation, including all preference keys and default values, is delivered in F1
 * (for screening-toggle and attempt threshold) and F5 (for onboarding completion state).
 */
interface SettingsRepository {

    /**
     * Emits whether the call-screening filter is currently enabled by the user.
     *
     * Defaults to `true` (filtering active) on a fresh install.
     *
     * @return A [Flow] that emits the current enabled state and any subsequent changes.
     */
    fun isFilterEnabled(): Flow<Boolean>

    /**
     * Enables or disables the call-screening filter.
     *
     * @param enabled `true` to activate filtering; `false` to let all calls through.
     */
    suspend fun setFilterEnabled(enabled: Boolean)

    /**
     * Emits the minimum number of blocked attempts required before a number is allowed through.
     *
     * Default value is 1 (i.e. the number is allowed on its second call attempt).
     * Configurable by the user from the settings screen (F4).
     *
     * @return A [Flow] that emits the current threshold and any subsequent changes.
     */
    fun getRequiredAttemptCount(): Flow<Int>

    /**
     * Updates the minimum number of blocked attempts required before a number is allowed through.
     *
     * @param count Must be a positive integer (≥1).  Values ≤0 are rejected by the use case.
     */
    suspend fun setRequiredAttemptCount(count: Int)
}
