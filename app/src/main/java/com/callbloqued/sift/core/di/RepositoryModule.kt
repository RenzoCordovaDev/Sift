package com.callbloqued.sift.core.di

import com.callbloqued.sift.data.repository.CallAttemptRepositoryImpl
import com.callbloqued.sift.data.repository.CallLogRepositoryImpl
import com.callbloqued.sift.data.repository.ContactsRepositoryImpl
import com.callbloqued.sift.data.repository.SettingsRepositoryImpl
import com.callbloqued.sift.domain.repository.CallAttemptRepository
import com.callbloqued.sift.domain.repository.CallLogRepository
import com.callbloqued.sift.domain.repository.ContactsRepository
import com.callbloqued.sift.domain.repository.SettingsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module that binds domain repository interfaces to their `data`-layer implementations.
 *
 * Using `@Binds` (instead of `@Provides`) lets Hilt generate a leaner binding that simply
 * delegates to the concrete class rather than calling a factory method, reducing generated code.
 *
 * All bindings are [Singleton]-scoped so that a single repository instance is shared across
 * the application, preventing inconsistent state between the use case and any future ViewModel
 * that accesses the same repository.
 *
 * Installed in [SingletonComponent] to match the scope of the [AppDatabase] and [DataStore]
 * singletons on which the implementations depend.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    /**
     * Binds [CallAttemptRepositoryImpl] as the singleton provider of [CallAttemptRepository].
     *
     * @param impl The concrete Room-backed implementation injected by Hilt.
     * @return The bound [CallAttemptRepository] interface.
     */
    @Binds
    @Singleton
    abstract fun bindCallAttemptRepository(
        impl: CallAttemptRepositoryImpl
    ): CallAttemptRepository

    /**
     * Binds [ContactsRepositoryImpl] as the singleton provider of [ContactsRepository].
     *
     * @param impl The concrete ContentResolver-backed implementation injected by Hilt.
     * @return The bound [ContactsRepository] interface.
     */
    @Binds
    @Singleton
    abstract fun bindContactsRepository(
        impl: ContactsRepositoryImpl
    ): ContactsRepository

    /**
     * Binds [SettingsRepositoryImpl] as the singleton provider of [SettingsRepository].
     *
     * @param impl The concrete DataStore-backed implementation injected by Hilt.
     * @return The bound [SettingsRepository] interface.
     */
    @Binds
    @Singleton
    abstract fun bindSettingsRepository(
        impl: SettingsRepositoryImpl
    ): SettingsRepository

    /**
     * Binds [CallLogRepositoryImpl] as the singleton provider of [CallLogRepository].
     *
     * @param impl The concrete Room-backed implementation injected by Hilt.
     * @return The bound [CallLogRepository] interface.
     */
    @Binds
    @Singleton
    abstract fun bindCallLogRepository(
        impl: CallLogRepositoryImpl
    ): CallLogRepository
}
