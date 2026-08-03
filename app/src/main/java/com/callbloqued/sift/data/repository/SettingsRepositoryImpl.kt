package com.callbloqued.sift.data.repository

import com.callbloqued.sift.data.local.datastore.SettingsDataStore
import com.callbloqued.sift.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * DataStore-backed implementation of [SettingsRepository].
 *
 * Delegates all read and write operations to [SettingsDataStore], which owns the
 * preference keys and default values. This class is a thin adapter that satisfies the
 * domain interface contract so that the `domain` layer never imports DataStore types directly.
 *
 * Bound to [SettingsRepository] via `@Binds` in
 * [com.callbloqued.sift.core.di.RepositoryModule].
 *
 * @param settingsDataStore The application-scoped [SettingsDataStore] injected by Hilt.
 */
class SettingsRepositoryImpl @Inject constructor(
    private val settingsDataStore: SettingsDataStore
) : SettingsRepository {

    /**
     * Emits whether the call-screening filter is currently enabled.
     *
     * Delegates to [SettingsDataStore.filterEnabled]. Defaults to `true` on a fresh install.
     *
     * @return A [Flow] emitting the current enabled state and any subsequent changes.
     */
    override fun isFilterEnabled(): Flow<Boolean> = settingsDataStore.filterEnabled()

    /**
     * Enables or disables the call-screening filter.
     *
     * @param enabled `true` to activate filtering; `false` to let all calls ring through.
     */
    override suspend fun setFilterEnabled(enabled: Boolean) {
        settingsDataStore.setFilterEnabled(enabled)
    }

    /**
     * Emits the minimum number of blocked attempts required before a caller is allowed through.
     *
     * Delegates to [SettingsDataStore.requiredAttemptCount]. Defaults to 1.
     *
     * @return A [Flow] emitting the current threshold and any subsequent changes.
     */
    override fun getRequiredAttemptCount(): Flow<Int> = settingsDataStore.requiredAttemptCount()

    /**
     * Updates the minimum number of blocked attempts required before a caller is allowed through.
     *
     * @param count Must be a positive integer (≥1). Values ≤0 are rejected by the use case.
     */
    override suspend fun setRequiredAttemptCount(count: Int) {
        settingsDataStore.setRequiredAttemptCount(count)
    }
}
