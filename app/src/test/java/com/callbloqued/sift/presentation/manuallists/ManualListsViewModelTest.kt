package com.callbloqued.sift.presentation.manuallists

import app.cash.turbine.test
import com.callbloqued.sift.domain.model.ManualListEntry
import com.callbloqued.sift.domain.model.ManualListType
import com.callbloqued.sift.domain.repository.ManualListRepository
import com.callbloqued.sift.domain.util.PhoneNumberNormalizer
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
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
 * Unit tests for [ManualListsViewModel].
 *
 * [Dispatchers.Main] is replaced with [UnconfinedTestDispatcher] so that [viewModelScope]
 * coroutines run eagerly on the JVM without requiring an Android runtime.
 * [app.cash.turbine] is used to collect [kotlinx.coroutines.flow.StateFlow] emissions.
 *
 * The ViewModel calls [ManualListRepository.observeList] twice at construction time — once for
 * [ManualListType.BLACKLIST] and once for [ManualListType.WHITELIST]. Both calls are mocked
 * with a never-emitting flow in [setUp] so that each test starts from a clean, predictable
 * initial state; individual tests override the relevant mock before creating the ViewModel.
 *
 * Timing note: with [UnconfinedTestDispatcher] the upstream collection coroutine started by
 * [kotlinx.coroutines.flow.SharingStarted.WhileSubscribed] runs eagerly before the [StateFlow]
 * replays its current value to a new subscriber. Tests that need to observe emissions one-at-a-time
 * use [MutableSharedFlow] so the upstream suspends initially and emissions are delivered
 * manually inside the [app.cash.turbine.test] block.
 *
 * Covered behaviours:
 * - [ManualListsViewModel.blacklist] starts at [emptyList] before any repository emission.
 * - [ManualListsViewModel.blacklist] forwards every list emitted by the BLACKLIST upstream.
 * - [ManualListsViewModel.blacklist] re-emits on a second upstream value.
 * - [ManualListsViewModel.whitelist] starts at [emptyList] before any repository emission.
 * - [ManualListsViewModel.whitelist] forwards every list emitted by the WHITELIST upstream.
 * - [ManualListsViewModel.whitelist] re-emits on a second upstream value.
 * - [ManualListsViewModel.removeFromList] delegates to [ManualListRepository.removeFromList]
 *   with the correct phone number.
 * - [ManualListsViewModel.addToList] returns `false` and does not call the repository when
 *   [PhoneNumberNormalizer.normalize] returns `null` (invalid number).
 * - [ManualListsViewModel.addToList] returns `true` and calls [ManualListRepository.addToList]
 *   with the normalised number and the given [ManualListType] when normalization succeeds.
 * - [ManualListsViewModel.addToList] passes [rawNumber] to [PhoneNumberNormalizer.normalize].
 */
class ManualListsViewModelTest {

    private lateinit var manualListRepository: ManualListRepository
    private lateinit var normalizer: PhoneNumberNormalizer

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        manualListRepository = mockk()
        normalizer = mockk()
        every { manualListRepository.observeList(ManualListType.BLACKLIST) } returns
            flow { awaitCancellation() }
        every { manualListRepository.observeList(ManualListType.WHITELIST) } returns
            flow { awaitCancellation() }
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = ManualListsViewModel(manualListRepository, normalizer)

    // ─── blacklist StateFlow ──────────────────────────────────────────────────

    @Test
    fun `blacklist should have empty list as initial value before repository emits`() = runTest {
        val viewModel = createViewModel()

        viewModel.blacklist.test {
            assertEquals(emptyList<ManualListEntry>(), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `blacklist should emit repository entries when repository flow emits BLACKLIST entries`() =
        runTest {
            val entries = listOf(
                ManualListEntry("+15551111111", ManualListType.BLACKLIST, 1_000L),
                ManualListEntry("+15552222222", ManualListType.BLACKLIST, 2_000L)
            )
            // With UnconfinedTestDispatcher the upstream runs eagerly; the StateFlow value is
            // already `entries` when Turbine reads it, so the first awaitItem() returns entries.
            every { manualListRepository.observeList(ManualListType.BLACKLIST) } returns
                flowOf(entries)
            val viewModel = createViewModel()

            viewModel.blacklist.test {
                val actual = awaitItem()
                assertEquals(2, actual.size)
                assertEquals("+15551111111", actual[0].phoneNumber)
                assertEquals("+15552222222", actual[1].phoneNumber)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `blacklist should re-emit updated list when BLACKLIST repository flow emits a second value`() =
        runTest {
            val firstList = listOf(
                ManualListEntry("+15551111111", ManualListType.BLACKLIST, 1_000L)
            )
            val secondList = listOf(
                ManualListEntry("+15553333333", ManualListType.BLACKLIST, 3_000L),
                ManualListEntry("+15551111111", ManualListType.BLACKLIST, 1_000L)
            )
            val sharedFlow = MutableSharedFlow<List<ManualListEntry>>()
            every { manualListRepository.observeList(ManualListType.BLACKLIST) } returns sharedFlow
            val viewModel = createViewModel()

            viewModel.blacklist.test {
                assertEquals(emptyList<ManualListEntry>(), awaitItem()) // initial before any emission

                sharedFlow.emit(firstList)
                val first = awaitItem()
                assertEquals(1, first.size)
                assertEquals("+15551111111", first[0].phoneNumber)

                sharedFlow.emit(secondList)
                val second = awaitItem()
                assertEquals(2, second.size)
                assertEquals("+15553333333", second[0].phoneNumber)

                cancelAndIgnoreRemainingEvents()
            }
        }

    // ─── whitelist StateFlow ──────────────────────────────────────────────────

    @Test
    fun `whitelist should have empty list as initial value before repository emits`() = runTest {
        val viewModel = createViewModel()

        viewModel.whitelist.test {
            assertEquals(emptyList<ManualListEntry>(), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `whitelist should emit repository entries when repository flow emits WHITELIST entries`() =
        runTest {
            val entries = listOf(
                ManualListEntry("+15559876543", ManualListType.WHITELIST, 5_000L)
            )
            every { manualListRepository.observeList(ManualListType.WHITELIST) } returns
                flowOf(entries)
            val viewModel = createViewModel()

            viewModel.whitelist.test {
                val actual = awaitItem()
                assertEquals(1, actual.size)
                assertEquals("+15559876543", actual[0].phoneNumber)
                assertEquals(ManualListType.WHITELIST, actual[0].listType)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `whitelist should re-emit updated list when WHITELIST repository flow emits a second value`() =
        runTest {
            val firstList = listOf(
                ManualListEntry("+15559876543", ManualListType.WHITELIST, 5_000L)
            )
            val secondList = listOf(
                ManualListEntry("+15550000000", ManualListType.WHITELIST, 6_000L),
                ManualListEntry("+15559876543", ManualListType.WHITELIST, 5_000L)
            )
            val sharedFlow = MutableSharedFlow<List<ManualListEntry>>()
            every { manualListRepository.observeList(ManualListType.WHITELIST) } returns sharedFlow
            val viewModel = createViewModel()

            viewModel.whitelist.test {
                assertEquals(emptyList<ManualListEntry>(), awaitItem()) // initial before any emission

                sharedFlow.emit(firstList)
                val first = awaitItem()
                assertEquals(1, first.size)
                assertEquals("+15559876543", first[0].phoneNumber)

                sharedFlow.emit(secondList)
                val second = awaitItem()
                assertEquals(2, second.size)
                assertEquals("+15550000000", second[0].phoneNumber)

                cancelAndIgnoreRemainingEvents()
            }
        }

    // ─── blacklist and whitelist are independent ──────────────────────────────

    @Test
    fun `blacklist and whitelist should observe independently when both repositories emit`() =
        runTest {
            val blacklistEntry = ManualListEntry("+15551111111", ManualListType.BLACKLIST, 1_000L)
            val whitelistEntry = ManualListEntry("+15559876543", ManualListType.WHITELIST, 2_000L)
            every { manualListRepository.observeList(ManualListType.BLACKLIST) } returns
                flowOf(listOf(blacklistEntry))
            every { manualListRepository.observeList(ManualListType.WHITELIST) } returns
                flowOf(listOf(whitelistEntry))
            val viewModel = createViewModel()

            viewModel.blacklist.test {
                val bl = awaitItem()
                assertEquals(1, bl.size)
                assertEquals("+15551111111", bl[0].phoneNumber)
                cancelAndIgnoreRemainingEvents()
            }
            viewModel.whitelist.test {
                val wl = awaitItem()
                assertEquals(1, wl.size)
                assertEquals("+15559876543", wl[0].phoneNumber)
                cancelAndIgnoreRemainingEvents()
            }
        }

    // ─── removeFromList ───────────────────────────────────────────────────────

    @Test
    fun `removeFromList should call repository removeFromList with correct phone number when invoked`() =
        runTest {
            coEvery { manualListRepository.removeFromList("+15551234567") } returns Unit
            val viewModel = createViewModel()

            viewModel.removeFromList("+15551234567")

            coVerify(exactly = 1) { manualListRepository.removeFromList("+15551234567") }
        }

    @Test
    fun `removeFromList should call repository exactly once when invoked`() = runTest {
        coEvery { manualListRepository.removeFromList(any()) } returns Unit
        val viewModel = createViewModel()

        viewModel.removeFromList("+15551234567")

        coVerify(exactly = 1) { manualListRepository.removeFromList(any()) }
    }

    // ─── addToList — normalisation failure ────────────────────────────────────

    @Test
    fun `addToList should return false when normalizer returns null for invalid number`() = runTest {
        every { normalizer.normalize(any()) } returns null
        val viewModel = createViewModel()

        val result = viewModel.addToList("not-a-number", ManualListType.BLACKLIST)

        assertFalse(result)
    }

    @Test
    fun `addToList should not call repository when normalizer returns null for invalid number`() =
        runTest {
            every { normalizer.normalize(any()) } returns null
            val viewModel = createViewModel()

            viewModel.addToList("not-a-number", ManualListType.BLACKLIST)

            coVerify(exactly = 0) { manualListRepository.addToList(any(), any()) }
        }

    @Test
    fun `addToList should return false for empty string when normalizer returns null`() = runTest {
        every { normalizer.normalize(any()) } returns null
        val viewModel = createViewModel()

        val result = viewModel.addToList("", ManualListType.WHITELIST)

        assertFalse(result)
    }

    // ─── addToList — normalisation success ────────────────────────────────────

    @Test
    fun `addToList should return true when normalizer returns a valid E164 number`() = runTest {
        every { normalizer.normalize(any()) } returns "+15551234567"
        coEvery { manualListRepository.addToList(any(), any()) } returns Unit
        val viewModel = createViewModel()

        val result = viewModel.addToList("5551234567", ManualListType.BLACKLIST)

        assertTrue(result)
    }

    @Test
    fun `addToList should call repository with normalized number and BLACKLIST type when normalization succeeds`() =
        runTest {
            every { normalizer.normalize(any()) } returns "+15551234567"
            coEvery {
                manualListRepository.addToList("+15551234567", ManualListType.BLACKLIST)
            } returns Unit
            val viewModel = createViewModel()

            viewModel.addToList("5551234567", ManualListType.BLACKLIST)

            coVerify(exactly = 1) {
                manualListRepository.addToList("+15551234567", ManualListType.BLACKLIST)
            }
        }

    @Test
    fun `addToList should call repository with normalized number and WHITELIST type when normalization succeeds`() =
        runTest {
            every { normalizer.normalize(any()) } returns "+44987654321"
            coEvery {
                manualListRepository.addToList("+44987654321", ManualListType.WHITELIST)
            } returns Unit
            val viewModel = createViewModel()

            viewModel.addToList("0987654321", ManualListType.WHITELIST)

            coVerify(exactly = 1) {
                manualListRepository.addToList("+44987654321", ManualListType.WHITELIST)
            }
        }

    @Test
    fun `addToList should pass rawNumber to normalizer when invoked`() = runTest {
        every { normalizer.normalize("5551234567") } returns "+15551234567"
        coEvery { manualListRepository.addToList(any(), any()) } returns Unit
        val viewModel = createViewModel()

        viewModel.addToList("5551234567", ManualListType.BLACKLIST)

        verify(exactly = 1) { normalizer.normalize("5551234567") }
    }

    @Test
    fun `addToList should not call repository with raw number when normalizer returns a different normalized form`() =
        runTest {
            val rawNumber = "5551234567"
            val normalizedNumber = "+15551234567"
            every { normalizer.normalize(rawNumber) } returns normalizedNumber
            coEvery { manualListRepository.addToList(normalizedNumber, any()) } returns Unit
            val viewModel = createViewModel()

            viewModel.addToList(rawNumber, ManualListType.BLACKLIST)

            coVerify(exactly = 0) { manualListRepository.addToList(rawNumber, any()) }
            coVerify(exactly = 1) { manualListRepository.addToList(normalizedNumber, any()) }
        }
}
