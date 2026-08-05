package com.callbloqued.sift.presentation.settings

import app.cash.turbine.test
import com.callbloqued.sift.domain.repository.SettingsRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Unit tests for [SettingsViewModel].
 *
 * [Dispatchers.Main] is replaced with [UnconfinedTestDispatcher] so that [viewModelScope]
 * coroutines run eagerly on the JVM without requiring an Android runtime.
 * [app.cash.turbine] is used to collect [kotlinx.coroutines.flow.StateFlow] emissions.
 *
 * Timing note: with [UnconfinedTestDispatcher] the upstream collection coroutine started by
 * [kotlinx.coroutines.flow.SharingStarted.WhileSubscribed] runs eagerly before the [StateFlow]
 * replays its current value to a new subscriber. Tests that need to observe emissions one-at-a-time
 * use [MutableSharedFlow] so the upstream suspends initially and emissions are delivered
 * manually inside the [app.cash.turbine.test] block.
 *
 * Covered behaviours:
 * - [SettingsViewModel.isFilterEnabled] starts at `true` (the [kotlinx.coroutines.flow.stateIn]
 *   initial value) before any repository emission.
 * - [SettingsViewModel.isFilterEnabled] forwards the boolean emitted by [SettingsRepository].
 * - [SettingsViewModel.requiredAttemptCount] starts at `1` before any repository emission.
 * - [SettingsViewModel.requiredAttemptCount] forwards the integer emitted by [SettingsRepository].
 * - [SettingsViewModel.setFilterEnabled] delegates to [SettingsRepository.setFilterEnabled] for
 *   both `true` and `false`.
 * - [SettingsViewModel.setRequiredAttemptCount] delegates to [SettingsRepository.setRequiredAttemptCount]
 *   for valid counts (≥ 1).
 * - [SettingsViewModel.setRequiredAttemptCount] silently drops calls when [count] is 0 or negative
 *   — this is the critical UI-level guard that prevents [IllegalArgumentException] from propagating
 *   to the domain layer.
 */
class SettingsViewModelTest {

    private lateinit var settingsRepository: SettingsRepository

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        settingsRepository = mockk()
        every { settingsRepository.isFilterEnabled() } returns flow { awaitCancellation() }
        every { settingsRepository.getRequiredAttemptCount() } returns flow { awaitCancellation() }
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = SettingsViewModel(settingsRepository)

    // ─── isFilterEnabled StateFlow ────────────────────────────────────────────

    @Test
    fun `isFilterEnabled should have true as initial value before repository emits`() = runTest {
        // flow { awaitCancellation() } keeps the upstream suspended without emitting,
        // so the StateFlow stays at its hard-coded initialValue = true.
        val viewModel = createViewModel()

        viewModel.isFilterEnabled.test {
            assertTrue(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `isFilterEnabled should emit false when repository emits false`() = runTest {
        // MutableSharedFlow has no stored value; the StateFlow replays its initial true
        // to Turbine first. Then we manually emit false to observe the state change.
        val sharedFlow = MutableSharedFlow<Boolean>()
        every { settingsRepository.isFilterEnabled() } returns sharedFlow
        val viewModel = createViewModel()

        viewModel.isFilterEnabled.test {
            assertTrue(awaitItem()) // initial true before any upstream emission
            sharedFlow.emit(false)
            assertFalse(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `isFilterEnabled should reflect false value from repository`() = runTest {
        // With UnconfinedTestDispatcher the upstream runs eagerly; when the repository
        // flow emits false synchronously, the StateFlow's value is false by the time
        // Turbine reads it.
        every { settingsRepository.isFilterEnabled() } returns flowOf(false)
        val viewModel = createViewModel()

        viewModel.isFilterEnabled.test {
            assertFalse(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `isFilterEnabled should re-emit when repository flow emits a second value`() = runTest {
        val sharedFlow = MutableSharedFlow<Boolean>()
        every { settingsRepository.isFilterEnabled() } returns sharedFlow
        val viewModel = createViewModel()

        viewModel.isFilterEnabled.test {
            assertTrue(awaitItem()) // initial true
            sharedFlow.emit(false)
            assertFalse(awaitItem()) // first emission
            sharedFlow.emit(true)
            assertTrue(awaitItem()) // second emission
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ─── requiredAttemptCount StateFlow ──────────────────────────────────────

    @Test
    fun `requiredAttemptCount should have 1 as initial value before repository emits`() = runTest {
        val viewModel = createViewModel()

        viewModel.requiredAttemptCount.test {
            assertEquals(1, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `requiredAttemptCount should emit repository value when repository emits 3`() = runTest {
        val sharedFlow = MutableSharedFlow<Int>()
        every { settingsRepository.getRequiredAttemptCount() } returns sharedFlow
        val viewModel = createViewModel()

        viewModel.requiredAttemptCount.test {
            assertEquals(1, awaitItem()) // initial 1 before any upstream emission
            sharedFlow.emit(3)
            assertEquals(3, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `requiredAttemptCount should emit repository value when repository emits 2`() = runTest {
        val sharedFlow = MutableSharedFlow<Int>()
        every { settingsRepository.getRequiredAttemptCount() } returns sharedFlow
        val viewModel = createViewModel()

        viewModel.requiredAttemptCount.test {
            assertEquals(1, awaitItem()) // initial 1
            sharedFlow.emit(2)
            assertEquals(2, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `requiredAttemptCount should re-emit when repository flow emits multiple values`() =
        runTest {
            val sharedFlow = MutableSharedFlow<Int>()
            every { settingsRepository.getRequiredAttemptCount() } returns sharedFlow
            val viewModel = createViewModel()

            viewModel.requiredAttemptCount.test {
                assertEquals(1, awaitItem()) // initial 1
                sharedFlow.emit(2)
                assertEquals(2, awaitItem())
                sharedFlow.emit(5)
                assertEquals(5, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `requiredAttemptCount should reflect value from repository when emitting eagerly`() =
        runTest {
            // With UnconfinedTestDispatcher the upstream runs eagerly; the StateFlow's value
            // is already the repository value when Turbine reads it.
            every { settingsRepository.getRequiredAttemptCount() } returns flowOf(4)
            val viewModel = createViewModel()

            viewModel.requiredAttemptCount.test {
                assertEquals(4, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    // ─── setFilterEnabled ─────────────────────────────────────────────────────

    @Test
    fun `setFilterEnabled should call repository setFilterEnabled with true when enabled is true`() =
        runTest {
            coEvery { settingsRepository.setFilterEnabled(true) } returns Unit
            val viewModel = createViewModel()

            viewModel.setFilterEnabled(true)

            coVerify(exactly = 1) { settingsRepository.setFilterEnabled(true) }
        }

    @Test
    fun `setFilterEnabled should call repository setFilterEnabled with false when enabled is false`() =
        runTest {
            coEvery { settingsRepository.setFilterEnabled(false) } returns Unit
            val viewModel = createViewModel()

            viewModel.setFilterEnabled(false)

            coVerify(exactly = 1) { settingsRepository.setFilterEnabled(false) }
        }

    @Test
    fun `setFilterEnabled should call repository exactly once when invoked`() = runTest {
        coEvery { settingsRepository.setFilterEnabled(any()) } returns Unit
        val viewModel = createViewModel()

        viewModel.setFilterEnabled(false)

        coVerify(exactly = 1) { settingsRepository.setFilterEnabled(any()) }
    }

    // ─── setRequiredAttemptCount ──────────────────────────────────────────────

    @Test
    fun `setRequiredAttemptCount should call repository when count is exactly the minimum of 1`() =
        runTest {
            coEvery { settingsRepository.setRequiredAttemptCount(1) } returns Unit
            val viewModel = createViewModel()

            viewModel.setRequiredAttemptCount(1)

            coVerify(exactly = 1) { settingsRepository.setRequiredAttemptCount(1) }
        }

    @Test
    fun `setRequiredAttemptCount should call repository when count is a valid value above minimum`() =
        runTest {
            coEvery { settingsRepository.setRequiredAttemptCount(5) } returns Unit
            val viewModel = createViewModel()

            viewModel.setRequiredAttemptCount(5)

            coVerify(exactly = 1) { settingsRepository.setRequiredAttemptCount(5) }
        }

    @Test
    fun `setRequiredAttemptCount should not call repository when count is 0`() = runTest {
        coEvery { settingsRepository.setRequiredAttemptCount(any()) } returns Unit
        val viewModel = createViewModel()

        viewModel.setRequiredAttemptCount(0)

        coVerify(exactly = 0) { settingsRepository.setRequiredAttemptCount(any()) }
    }

    @Test
    fun `setRequiredAttemptCount should not call repository when count is negative`() = runTest {
        coEvery { settingsRepository.setRequiredAttemptCount(any()) } returns Unit
        val viewModel = createViewModel()

        viewModel.setRequiredAttemptCount(-1)

        coVerify(exactly = 0) { settingsRepository.setRequiredAttemptCount(any()) }
    }

    @Test
    fun `setRequiredAttemptCount should not call repository when count is large negative`() =
        runTest {
            coEvery { settingsRepository.setRequiredAttemptCount(any()) } returns Unit
            val viewModel = createViewModel()

            viewModel.setRequiredAttemptCount(Int.MIN_VALUE)

            coVerify(exactly = 0) { settingsRepository.setRequiredAttemptCount(any()) }
        }

    @Test
    fun `setRequiredAttemptCount should call repository exactly once for a valid count`() =
        runTest {
            coEvery { settingsRepository.setRequiredAttemptCount(any()) } returns Unit
            val viewModel = createViewModel()

            viewModel.setRequiredAttemptCount(3)

            coVerify(exactly = 1) { settingsRepository.setRequiredAttemptCount(any()) }
        }
}
