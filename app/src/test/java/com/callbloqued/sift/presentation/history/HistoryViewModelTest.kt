package com.callbloqued.sift.presentation.history

import app.cash.turbine.test
import com.callbloqued.sift.domain.model.BlockReason
import com.callbloqued.sift.domain.model.BlockedCallLogEntry
import com.callbloqued.sift.domain.model.ManualListType
import com.callbloqued.sift.domain.repository.CallLogRepository
import com.callbloqued.sift.domain.repository.ManualListRepository
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
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Unit tests for [HistoryViewModel].
 *
 * [Dispatchers.Main] is replaced with [UnconfinedTestDispatcher] so that [viewModelScope]
 * coroutines run eagerly on the JVM without requiring an Android runtime.
 * [app.cash.turbine] is used to collect [kotlinx.coroutines.flow.StateFlow] emissions in a
 * structured, deterministic way.
 *
 * Each test creates its own [HistoryViewModel] instance after configuring the repository mocks
 * for that specific scenario; this ensures the [kotlinx.coroutines.flow.stateIn] call inside the
 * ViewModel sees the correct upstream flow at construction time.
 *
 * Timing note: with [UnconfinedTestDispatcher] the upstream collection coroutine started by
 * [kotlinx.coroutines.flow.SharingStarted.WhileSubscribed] runs eagerly before the [StateFlow]
 * replays its current value to a new subscriber. As a result:
 * - When the upstream is a [MutableSharedFlow] (no stored item), the StateFlow replays its
 *   hard-coded initial value first and subsequent emissions arrive one by one.
 * - When the upstream is a [flowOf] or plain [flow], the StateFlow's value is already the
 *   upstream result by the time [app.cash.turbine] calls the first [awaitItem].
 *
 * Covered behaviours:
 * - [HistoryViewModel.callLog] starts with [emptyList] as its initial value before any upstream
 *   emission.
 * - [HistoryViewModel.callLog] forwards every list emitted by [CallLogRepository.observeBlockedCallLog].
 * - [HistoryViewModel.callLog] re-emits when the upstream delivers a second list.
 * - [HistoryViewModel.addToWhitelist] delegates to [ManualListRepository.addToList] with
 *   [ManualListType.WHITELIST] and the provided phone number, and never calls with BLACKLIST.
 * - [HistoryViewModel.addToBlacklist] delegates to [ManualListRepository.addToList] with
 *   [ManualListType.BLACKLIST] and the provided phone number, and never calls with WHITELIST.
 */
class HistoryViewModelTest {

    private lateinit var callLogRepository: CallLogRepository
    private lateinit var manualListRepository: ManualListRepository

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        callLogRepository = mockk()
        manualListRepository = mockk()
        every { callLogRepository.observeBlockedCallLog() } returns flow { awaitCancellation() }
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = HistoryViewModel(callLogRepository, manualListRepository)

    // ─── callLog StateFlow ────────────────────────────────────────────────────

    @Test
    fun `callLog should have empty list as initial value before repository emits`() = runTest {
        // flow { awaitCancellation() } suspends the upstream without emitting, so the StateFlow
        // stays at its hard-coded initialValue = emptyList().
        val viewModel = createViewModel()

        viewModel.callLog.test {
            assertEquals(emptyList<BlockedCallLogEntry>(), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `callLog should emit repository entries when repository flow emits a non-empty list`() =
        runTest {
            val entries = listOf(
                BlockedCallLogEntry(
                    id = 1L,
                    phoneNumber = "+15551234567",
                    timestamp = 1_000_000L,
                    reason = BlockReason.ATTEMPT_THRESHOLD
                )
            )
            every { callLogRepository.observeBlockedCallLog() } returns flowOf(entries)
            val viewModel = createViewModel()

            // With UnconfinedTestDispatcher the upstream runs eagerly before the StateFlow
            // replays its value; the first awaitItem() is already the upstream emission.
            viewModel.callLog.test {
                val actual = awaitItem()
                assertEquals(1, actual.size)
                assertEquals("+15551234567", actual[0].phoneNumber)
                assertEquals(1L, actual[0].id)
                assertEquals(BlockReason.ATTEMPT_THRESHOLD, actual[0].reason)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `callLog should emit multiple entries preserving order when repository emits a list`() =
        runTest {
            val entries = listOf(
                BlockedCallLogEntry(2L, "+15552222222", 2_000L, BlockReason.MANUAL_BLACKLIST),
                BlockedCallLogEntry(1L, "+15551111111", 1_000L, BlockReason.ATTEMPT_THRESHOLD)
            )
            every { callLogRepository.observeBlockedCallLog() } returns flowOf(entries)
            val viewModel = createViewModel()

            viewModel.callLog.test {
                val actual = awaitItem()
                assertEquals(2, actual.size)
                assertEquals(2L, actual[0].id)
                assertEquals(1L, actual[1].id)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `callLog should re-emit updated list when repository flow emits a second value`() = runTest {
        val firstList = listOf(
            BlockedCallLogEntry(1L, "+15551111111", 1_000L, BlockReason.ATTEMPT_THRESHOLD)
        )
        val secondList = listOf(
            BlockedCallLogEntry(2L, "+15552222222", 2_000L, BlockReason.MANUAL_BLACKLIST),
            BlockedCallLogEntry(1L, "+15551111111", 1_000L, BlockReason.ATTEMPT_THRESHOLD)
        )
        // MutableSharedFlow has no stored value; the upstream suspends with no initial emission,
        // so the StateFlow keeps its emptyList initial value. Emissions are delivered manually
        // inside the test block to give Turbine deterministic, one-at-a-time control.
        val sharedFlow = MutableSharedFlow<List<BlockedCallLogEntry>>()
        every { callLogRepository.observeBlockedCallLog() } returns sharedFlow
        val viewModel = createViewModel()

        viewModel.callLog.test {
            assertEquals(emptyList<BlockedCallLogEntry>(), awaitItem()) // initial before any emission

            sharedFlow.emit(firstList)
            val first = awaitItem()
            assertEquals(1, first.size)
            assertEquals(1L, first[0].id)

            sharedFlow.emit(secondList)
            val second = awaitItem()
            assertEquals(2, second.size)
            assertEquals(2L, second[0].id)
            assertEquals(1L, second[1].id)

            cancelAndIgnoreRemainingEvents()
        }
    }

    // ─── addToWhitelist ───────────────────────────────────────────────────────

    @Test
    fun `addToWhitelist should call addToList with WHITELIST type when invoked`() = runTest {
        coEvery { manualListRepository.addToList(any(), ManualListType.WHITELIST) } returns Unit
        val viewModel = createViewModel()

        viewModel.addToWhitelist("+15551234567")

        coVerify(exactly = 1) {
            manualListRepository.addToList("+15551234567", ManualListType.WHITELIST)
        }
    }

    @Test
    fun `addToWhitelist should forward the phone number to the repository when invoked`() = runTest {
        coEvery { manualListRepository.addToList(any(), any()) } returns Unit
        val viewModel = createViewModel()

        viewModel.addToWhitelist("+44987654321")

        coVerify(exactly = 1) {
            manualListRepository.addToList("+44987654321", ManualListType.WHITELIST)
        }
    }

    @Test
    fun `addToWhitelist should not call addToList with BLACKLIST type when invoked`() = runTest {
        coEvery { manualListRepository.addToList(any(), any()) } returns Unit
        val viewModel = createViewModel()

        viewModel.addToWhitelist("+15551234567")

        coVerify(exactly = 0) {
            manualListRepository.addToList(any(), ManualListType.BLACKLIST)
        }
    }

    // ─── addToBlacklist ───────────────────────────────────────────────────────

    @Test
    fun `addToBlacklist should call addToList with BLACKLIST type when invoked`() = runTest {
        coEvery { manualListRepository.addToList(any(), ManualListType.BLACKLIST) } returns Unit
        val viewModel = createViewModel()

        viewModel.addToBlacklist("+15551234567")

        coVerify(exactly = 1) {
            manualListRepository.addToList("+15551234567", ManualListType.BLACKLIST)
        }
    }

    @Test
    fun `addToBlacklist should forward the phone number to the repository when invoked`() = runTest {
        coEvery { manualListRepository.addToList(any(), any()) } returns Unit
        val viewModel = createViewModel()

        viewModel.addToBlacklist("+44987654321")

        coVerify(exactly = 1) {
            manualListRepository.addToList("+44987654321", ManualListType.BLACKLIST)
        }
    }

    @Test
    fun `addToBlacklist should not call addToList with WHITELIST type when invoked`() = runTest {
        coEvery { manualListRepository.addToList(any(), any()) } returns Unit
        val viewModel = createViewModel()

        viewModel.addToBlacklist("+15551234567")

        coVerify(exactly = 0) {
            manualListRepository.addToList(any(), ManualListType.WHITELIST)
        }
    }

    @Test
    fun `addToBlacklist should call addToList exactly once when invoked`() = runTest {
        coEvery { manualListRepository.addToList(any(), any()) } returns Unit
        val viewModel = createViewModel()

        viewModel.addToBlacklist("+15551234567")

        coVerify(exactly = 1) { manualListRepository.addToList(any(), any()) }
    }
}
