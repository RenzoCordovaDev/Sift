package com.callbloqued.sift.data.local.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import app.cash.turbine.test
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * Unit tests for [SettingsDataStore].
 *
 * F0 tests (existing) verify that the [SettingsDataStore.data] flow correctly delegates to the
 * underlying [DataStore].
 *
 * F1 tests cover the two settings introduced for call screening:
 * - [SettingsDataStore.filterEnabled] / [SettingsDataStore.setFilterEnabled]: verifies default
 *   value and round-trip persistence.
 * - [SettingsDataStore.requiredAttemptCount] / [SettingsDataStore.setRequiredAttemptCount]:
 *   verifies default value, round-trip persistence, and validation (values ≤ 0 are rejected
 *   with [IllegalArgumentException] to prevent the core screening rule from being disabled).
 *
 * F0 tests use a mocked [DataStore] for isolation. F1 tests use a real
 * [PreferenceDataStoreFactory]-backed instance with a temporary file so that
 * [DataStore.edit] behaves exactly as in production.
 */
class SettingsDataStoreTest {

    // ─── F0 tests: data flow delegation (mocked DataStore) ───────────────────

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
    fun `data should emit all preferences snapshots when dataStore emits multiple values`() =
        runTest {
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

    // ─── F1 helpers ──────────────────────────────────────────────────────────

    @TempDir
    lateinit var tempDir: File

    private fun buildRealSettingsDataStore(scope: TestScope): SettingsDataStore {
        val dataStore = PreferenceDataStoreFactory.create(
            scope = scope.backgroundScope,
            produceFile = { File(tempDir, "test_settings.preferences_pb") }
        )
        return SettingsDataStore(dataStore)
    }

    // ─── F1 tests: filterEnabled ──────────────────────────────────────────────

    @Test
    fun `filterEnabled should emit true by default when no value has been persisted`() = runTest {
        val settingsDataStore = buildRealSettingsDataStore(this)

        settingsDataStore.filterEnabled().test {
            assertTrue(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `filterEnabled should emit false after setFilterEnabled is called with false`() = runTest {
        val settingsDataStore = buildRealSettingsDataStore(this)

        settingsDataStore.setFilterEnabled(false)

        settingsDataStore.filterEnabled().test {
            assertFalse(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `filterEnabled should emit true after setFilterEnabled is called with true`() = runTest {
        val settingsDataStore = buildRealSettingsDataStore(this)
        settingsDataStore.setFilterEnabled(false)

        settingsDataStore.setFilterEnabled(true)

        settingsDataStore.filterEnabled().test {
            assertTrue(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ─── F1 tests: requiredAttemptCount ──────────────────────────────────────

    @Test
    fun `requiredAttemptCount should emit default value of 1 when no value has been persisted`() =
        runTest {
            val settingsDataStore = buildRealSettingsDataStore(this)

            settingsDataStore.requiredAttemptCount().test {
                assertEquals(
                    SettingsDataStore.DEFAULT_REQUIRED_ATTEMPT_COUNT,
                    awaitItem()
                )
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `requiredAttemptCount should emit updated value after setRequiredAttemptCount is called`() =
        runTest {
            val settingsDataStore = buildRealSettingsDataStore(this)

            settingsDataStore.setRequiredAttemptCount(3)

            settingsDataStore.requiredAttemptCount().test {
                assertEquals(3, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `setRequiredAttemptCount should persist value of 1`() = runTest {
        val settingsDataStore = buildRealSettingsDataStore(this)

        settingsDataStore.setRequiredAttemptCount(1)

        settingsDataStore.requiredAttemptCount().test {
            assertEquals(1, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setRequiredAttemptCount should throw IllegalArgumentException when count is 0`() =
        runTest {
            val settingsDataStore = buildRealSettingsDataStore(this)

            assertThrows<IllegalArgumentException> {
                settingsDataStore.setRequiredAttemptCount(0)
            }
        }

    @Test
    fun `setRequiredAttemptCount should throw IllegalArgumentException when count is negative`() =
        runTest {
            val settingsDataStore = buildRealSettingsDataStore(this)

            assertThrows<IllegalArgumentException> {
                settingsDataStore.setRequiredAttemptCount(-1)
            }
        }

    @Test
    fun `setRequiredAttemptCount should throw IllegalArgumentException when count is minus 10`() =
        runTest {
            val settingsDataStore = buildRealSettingsDataStore(this)

            assertThrows<IllegalArgumentException> {
                settingsDataStore.setRequiredAttemptCount(-10)
            }
        }
}
