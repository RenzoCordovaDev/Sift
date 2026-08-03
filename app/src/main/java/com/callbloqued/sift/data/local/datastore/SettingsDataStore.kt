package com.callbloqued.sift.data.local.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Typed wrapper around [DataStore] for reading and writing application settings.
 *
 * This class owns the DataStore instance, declares all preference keys as companion-object
 * constants, and exposes each setting as a typed [Flow] with a safe default value. All write
 * operations are suspending to allow callers to run them from a coroutine without blocking.
 *
 * The raw [data] flow is also kept public for consumers that need access to the full
 * [Preferences] snapshot (e.g. tests verifying key presence).
 *
 * A [Singleton] instance is provided by [com.callbloqued.sift.core.di.DataStoreModule].
 *
 * @param dataStore The application-scoped [DataStore] instance injected by Hilt.
 */
@Singleton
class SettingsDataStore @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {

    /**
     * Emits the current snapshot of all stored preferences and any subsequent writes.
     *
     * Downstream collectors observe a new value whenever any preference key changes.
     *
     * @return A cold [Flow] backed by the DataStore; never completes unless the DataStore is closed.
     */
    val data: Flow<Preferences> = dataStore.data

    /**
     * Emits the current state of the call-screening filter and any subsequent user changes.
     *
     * Defaults to [DEFAULT_FILTER_ENABLED] (`true`) so that filtering is active on a fresh install
     * without requiring the user to opt in.
     *
     * @return A [Flow] that emits `true` when screening is enabled, `false` when disabled.
     */
    fun filterEnabled(): Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_FILTER_ENABLED] ?: DEFAULT_FILTER_ENABLED
    }

    /**
     * Persists whether the call-screening filter should be active.
     *
     * @param enabled `true` to activate filtering; `false` to let all calls ring through.
     */
    suspend fun setFilterEnabled(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[KEY_FILTER_ENABLED] = enabled }
    }

    /**
     * Emits the minimum number of blocked attempts required before a number is allowed through,
     * and any subsequent changes made by the user.
     *
     * Defaults to [DEFAULT_REQUIRED_ATTEMPT_COUNT] (1), meaning a number is allowed on its
     * second call attempt.
     *
     * @return A [Flow] that emits the current threshold value.
     */
    fun requiredAttemptCount(): Flow<Int> = dataStore.data.map { prefs ->
        prefs[KEY_REQUIRED_ATTEMPT_COUNT] ?: DEFAULT_REQUIRED_ATTEMPT_COUNT
    }

    /**
     * Persists the minimum number of blocked attempts required before a caller is allowed through.
     *
     * @param count The new threshold value. Must be a positive integer (≥1); enforcement of the
     *   lower bound is the responsibility of the calling layer.
     */
    suspend fun setRequiredAttemptCount(count: Int) {
        dataStore.edit { prefs -> prefs[KEY_REQUIRED_ATTEMPT_COUNT] = count }
    }

    companion object {

        /** DataStore key for the filter-enabled preference. */
        val KEY_FILTER_ENABLED: Preferences.Key<Boolean> =
            booleanPreferencesKey("filter_enabled")

        /** DataStore key for the required-attempt-count preference. */
        val KEY_REQUIRED_ATTEMPT_COUNT: Preferences.Key<Int> =
            intPreferencesKey("required_attempt_count")

        /** Default value for [filterEnabled]: screening is on out of the box. */
        const val DEFAULT_FILTER_ENABLED: Boolean = true

        /**
         * Default value for [requiredAttemptCount]: the number is allowed on its second attempt
         * (i.e. one prior blocked attempt is required).
         */
        const val DEFAULT_REQUIRED_ATTEMPT_COUNT: Int = 1
    }
}
