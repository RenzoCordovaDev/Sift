package com.callbloqued.sift.core.di

import android.content.Context
import androidx.room.Room
import com.callbloqued.sift.data.local.db.AppDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module that provides the [AppDatabase] singleton.
 *
 * Installed in [SingletonComponent] so the database instance outlives any individual
 * activity or fragment and is shared across the whole application lifetime.
 *
 * DAOs will be provided from this module in F2 once [AppDatabase] gains entity tables.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    /**
     * Creates and returns the application-scoped [AppDatabase] instance.
     *
     * The database is built lazily on first access.  Destructive migrations are disabled
     * intentionally — proper Room migration strategies are introduced alongside each new
     * entity in F2/F3 to avoid silent data loss.
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
        ).build()
}
