package com.callbloqued.sift.data.local.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import app.cash.turbine.test
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

/**
 * Smoke tests for [SettingsDataStore].
 *
 * Verifies that the wrapper correctly delegates [SettingsDataStore.data] to the underlying
 * [DataStore] and that values propagate through the exposed [kotlinx.coroutines.flow.Flow].
 * Uses MockK for dependency isolation and Turbine for Flow assertion.
 *
 * No Android runtime is required; [DataStore] and [Preferences] are mocked at the JVM level.
 */
class SettingsDataStoreTest {

    @Test
    fun `data should emit preferences when dataStore emits a single value`() = runTest {
        val mockPreferences: Preferences = mockk()
        val mockDataStore: DataStore<Preferences> = mockk {
            every { data } returns flowOf(mockPreferences)
        }
        val settingsDataStore = SettingsDataStore(mockDataStore)

        settingsDataStore.data.test {
            assertSame(mockPreferences, awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun `data should emit all preferences snapshots when dataStore emits multiple values`() = runTest {
        val firstSnapshot: Preferences = mockk()
        val secondSnapshot: Preferences = mockk()
        val mockDataStore: DataStore<Preferences> = mockk {
            every { data } returns flowOf(firstSnapshot, secondSnapshot)
        }
        val settingsDataStore = SettingsDataStore(mockDataStore)

        settingsDataStore.data.test {
            assertSame(firstSnapshot, awaitItem())
            assertSame(secondSnapshot, awaitItem())
            awaitComplete()
        }
    }
}
