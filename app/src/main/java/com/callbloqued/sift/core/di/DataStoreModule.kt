package com.callbloqued.sift.core.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

// Top-level property delegate creates a single DataStore instance named "sift_settings".
// Using a top-level property (rather than calling PreferenceDataStoreFactory inside a function)
// is the approach recommended by the Jetpack DataStore docs to guarantee the singleton constraint
// enforced by the delegate internally.
private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "sift_settings"
)

/**
 * Hilt module that provides the [DataStore] singleton for application settings.
 *
 * Installed in [SingletonComponent] so the same DataStore file is used throughout
 * the application lifetime, preventing concurrent-write conflicts.
 *
 * Actual preference keys and typed read/write operations are introduced in F1 via
 * [com.callbloqued.sift.data.local.datastore.SettingsDataStore].
 */
@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {

    /**
     * Provides the application-scoped [DataStore] of [Preferences].
     *
     * The backing file is `sift_settings.preferences_pb` in the app's data directory.
     * The delegate ensures only one DataStore instance is created per process.
     *
     * @param context Application context used to locate the DataStore file.
     * @return The singleton [DataStore] instance.
     */
    @Provides
    @Singleton
    fun provideSettingsDataStore(
        @ApplicationContext context: Context
    ): DataStore<Preferences> = context.settingsDataStore
}
