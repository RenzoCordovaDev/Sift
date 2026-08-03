package com.callbloqued.sift.data.repository

import com.callbloqued.sift.data.local.datastore.SettingsDataStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.Runs
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Unit tests for [SettingsRepositoryImpl].
 *
 * Verifies that the thin adapter correctly delegates every operation to [SettingsDataStore]
 * without modifying return values or swallowing errors. Each test uses a MockK-mocked
 * [SettingsDataStore] and confirms delegation via [verify] / [coVerify].
 *
 * No Android runtime or DataStore infrastructure is required.
 */
class SettingsRepositoryImplTest {

    private lateinit var settingsDataStore: SettingsDataStore
    private lateinit var repository: SettingsRepositoryImpl

    @BeforeEach
    fun setUp() {
        settingsDataStore = mockk()
        repository = SettingsRepositoryImpl(settingsDataStore)
    }

    // ─── isFilterEnabled ─────────────────────────────────────────────────────

    @Test
    fun `isFilterEnabled should delegate to settingsDataStore filterEnabled`() = runTest {
        val flow = flowOf(true)
        every { settingsDataStore.filterEnabled() } returns flow

        val result = repository.isFilterEnabled()

        verify(exactly = 1) { settingsDataStore.filterEnabled() }
        assert(result === flow)
    }

    @Test
    fun `isFilterEnabled should return the same flow instance provided by settingsDataStore`() =
        runTest {
            val expected = flowOf(false)
            every { settingsDataStore.filterEnabled() } returns expected

            val actual = repository.isFilterEnabled()

            assert(actual === expected)
        }

    // ─── setFilterEnabled ────────────────────────────────────────────────────

    @Test
    fun `setFilterEnabled should delegate true to settingsDataStore setFilterEnabled`() = runTest {
        coEvery { settingsDataStore.setFilterEnabled(true) } just Runs

        repository.setFilterEnabled(true)

        coVerify(exactly = 1) { settingsDataStore.setFilterEnabled(true) }
    }

    @Test
    fun `setFilterEnabled should delegate false to settingsDataStore setFilterEnabled`() = runTest {
        coEvery { settingsDataStore.setFilterEnabled(false) } just Runs

        repository.setFilterEnabled(false)

        coVerify(exactly = 1) { settingsDataStore.setFilterEnabled(false) }
    }

    // ─── getRequiredAttemptCount ──────────────────────────────────────────────

    @Test
    fun `getRequiredAttemptCount should delegate to settingsDataStore requiredAttemptCount`() =
        runTest {
            val flow = flowOf(1)
            every { settingsDataStore.requiredAttemptCount() } returns flow

            val result = repository.getRequiredAttemptCount()

            verify(exactly = 1) { settingsDataStore.requiredAttemptCount() }
            assert(result === flow)
        }

    @Test
    fun `getRequiredAttemptCount should return the same flow instance provided by settingsDataStore`() =
        runTest {
            val expected = flowOf(2)
            every { settingsDataStore.requiredAttemptCount() } returns expected

            val actual = repository.getRequiredAttemptCount()

            assert(actual === expected)
        }

    // ─── setRequiredAttemptCount ──────────────────────────────────────────────

    @Test
    fun `setRequiredAttemptCount should delegate valid count to settingsDataStore`() = runTest {
        coEvery { settingsDataStore.setRequiredAttemptCount(2) } just Runs

        repository.setRequiredAttemptCount(2)

        coVerify(exactly = 1) { settingsDataStore.setRequiredAttemptCount(2) }
    }

    @Test
    fun `setRequiredAttemptCount should propagate IllegalArgumentException from settingsDataStore`() =
        runTest {
            coEvery {
                settingsDataStore.setRequiredAttemptCount(0)
            } throws IllegalArgumentException("requiredAttemptCount must be >= 1, got 0")

            assertThrows<IllegalArgumentException> {
                repository.setRequiredAttemptCount(0)
            }
        }
}
