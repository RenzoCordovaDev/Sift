package com.callbloqued.sift.core.di

import android.content.Context
import androidx.room.Room
import com.callbloqued.sift.data.local.db.AppDatabase
import com.callbloqued.sift.data.local.db.CallAttemptDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module that provides the [AppDatabase] singleton and its DAOs.
 *
 * Installed in [SingletonComponent] so the database instance outlives any individual
 * activity or fragment and is shared across the whole application lifetime.
 *
 * DAOs are provided as non-singleton functions: each call returns the same underlying
 * DAO object (Room creates a single instance per database), but Hilt need not track
 * them separately.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    /**
     * Creates and returns the application-scoped [AppDatabase] instance.
     *
     * Registers [AppDatabase.MIGRATION_1_2] so that devices upgrading from v1 (F0 placeholder
     * schema) to v2 (F1 real entities) are migrated without data loss. Destructive migration
     * is intentionally **not** enabled; any future schema change must ship a corresponding
     * [androidx.room.migration.Migration].
     *
     * @param context Application context used by Room to locate the database file.
     * @return The singleton [AppDatabase] instance.
     */
    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context
    ): AppDatabase =
        Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "sift_database"
        )
            .addMigrations(AppDatabase.MIGRATION_1_2)
            .build()

    /**
     * Provides the [CallAttemptDao] obtained directly from the [AppDatabase] singleton.
     *
     * Room generates a single DAO implementation per database instance, so this provider
     * effectively acts as a singleton even without the [Singleton] annotation.
     *
     * @param database The application-scoped [AppDatabase] from which the DAO is retrieved.
     * @return The Room-generated [CallAttemptDao] implementation.
     */
    @Provides
    fun provideCallAttemptDao(database: AppDatabase): CallAttemptDao =
        database.callAttemptDao()
}
