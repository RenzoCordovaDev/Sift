package com.callbloqued.sift.data.local.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin wrapper around [DataStore] for reading and writing application settings.
 *
 * This class owns the DataStore instance and exposes [data] as a raw [Flow] of [Preferences].
 * The actual preference keys and typed accessors are introduced in F1 when
 * [com.callbloqued.sift.data.repository.SettingsRepositoryImpl] is implemented.
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
     * @return A cold [Flow] backed by the DataStore; never completes unless the DataStore itself
     *         is closed.
     */
    val data: Flow<Preferences> = dataStore.data
}
