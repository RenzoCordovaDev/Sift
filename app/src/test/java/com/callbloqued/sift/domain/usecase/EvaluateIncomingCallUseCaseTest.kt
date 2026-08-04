package com.callbloqued.sift.domain.usecase

import com.callbloqued.sift.domain.model.BlockReason
import com.callbloqued.sift.domain.model.CallDecision
import com.callbloqued.sift.domain.repository.CallAttemptRepository
import com.callbloqued.sift.domain.repository.CallLogRepository
import com.callbloqued.sift.domain.repository.ContactsRepository
import com.callbloqued.sift.domain.repository.SettingsRepository
import com.callbloqued.sift.domain.util.PhoneNumberNormalizer
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Unit tests for [EvaluateIncomingCallUseCase].
 *
 * This is the most critical test class in the project — it exercises every branch of the
 * five-step call-screening decision flow and validates CLAUDE.md non-negotiable rule 1
 * (known contacts are never blocked). All dependencies are mocked with MockK so the tests
 * run on the JVM without any Android runtime.
 *
 * Decision branches covered:
 * 1. Filter disabled → [CallDecision.Allow], no further calls.
 * 2. Number not normalizable → [CallDecision.Allow].
 * 3. Number is a known contact → [CallDecision.Allow].
 * 4. Previous attempts >= configured threshold → [CallDecision.Allow].
 * 5. First call from unknown number → [CallDecision.DisallowSilently] + attempt recorded +
 *    blocked-call event logged.
 * 6. Nth call still below threshold → [CallDecision.DisallowSilently] + attempt recorded +
 *    blocked-call event logged.
 *
 * CallLogRepository wiring (F2):
 * - [CallLogRepository.logBlockedCall] is called exactly once with the normalized number and
 *   [BlockReason.ATTEMPT_THRESHOLD] for every [CallDecision.DisallowSilently] outcome.
 * - [CallLogRepository.logBlockedCall] is never called for any [CallDecision.Allow] outcome.
 */
class EvaluateIncomingCallUseCaseTest {

    private lateinit var contactsRepository: ContactsRepository
    private lateinit var callAttemptRepository: CallAttemptRepository
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var normalizer: PhoneNumberNormalizer
    private lateinit var callLogRepository: CallLogRepository
    private lateinit var useCase: EvaluateIncomingCallUseCase

    @BeforeEach
    fun setUp() {
        contactsRepository = mockk()
        callAttemptRepository = mockk()
        settingsRepository = mockk()
        normalizer = mockk()
        callLogRepository = mockk()
        useCase = EvaluateIncomingCallUseCase(
            contactsRepository,
            callAttemptRepository,
            settingsRepository,
            normalizer,
            callLogRepository
        )
    }

    @Test
    fun `invoke should return Allow when filter is disabled`() = runTest {
        every { settingsRepository.isFilterEnabled() } returns flowOf(false)

        val result = useCase("+15551234567")

        assertEquals(CallDecision.Allow, result)
        coVerify(exactly = 0) { normalizer.normalize(any()) }
        coVerify(exactly = 0) { contactsRepository.isKnownContact(any()) }
        coVerify(exactly = 0) { callAttemptRepository.getAttemptCount(any()) }
        coVerify(exactly = 0) { callAttemptRepository.recordAttempt(any()) }
        coVerify(exactly = 0) { callLogRepository.logBlockedCall(any(), any()) }
    }

    @Test
    fun `invoke should return Allow when number cannot be normalized`() = runTest {
        every { settingsRepository.isFilterEnabled() } returns flowOf(true)
        every { normalizer.normalize(any()) } returns null

        val result = useCase("not-a-number")

        assertEquals(CallDecision.Allow, result)
        coVerify(exactly = 0) { contactsRepository.isKnownContact(any()) }
        coVerify(exactly = 0) { callAttemptRepository.recordAttempt(any()) }
        coVerify(exactly = 0) { callLogRepository.logBlockedCall(any(), any()) }
    }

    @Test
    fun `invoke should return Allow when number is a known contact`() = runTest {
        every { settingsRepository.isFilterEnabled() } returns flowOf(true)
        every { normalizer.normalize(any()) } returns "+15551234567"
        coEvery { contactsRepository.isKnownContact("+15551234567") } returns true

        val result = useCase("+15551234567")

        assertEquals(CallDecision.Allow, result)
        coVerify(exactly = 0) { callAttemptRepository.getAttemptCount(any()) }
        coVerify(exactly = 0) { callAttemptRepository.recordAttempt(any()) }
        coVerify(exactly = 0) { callLogRepository.logBlockedCall(any(), any()) }
    }

    @Test
    fun `invoke should return Allow when previous attempts equal required threshold`() = runTest {
        every { settingsRepository.isFilterEnabled() } returns flowOf(true)
        every { normalizer.normalize(any()) } returns "+15551234567"
        coEvery { contactsRepository.isKnownContact("+15551234567") } returns false
        every { settingsRepository.getRequiredAttemptCount() } returns flowOf(1)
        coEvery { callAttemptRepository.getAttemptCount("+15551234567") } returns 1

        val result = useCase("+15551234567")

        assertEquals(CallDecision.Allow, result)
        coVerify(exactly = 0) { callAttemptRepository.recordAttempt(any()) }
        coVerify(exactly = 0) { callLogRepository.logBlockedCall(any(), any()) }
    }

    @Test
    fun `invoke should return Allow when previous attempts exceed required threshold`() = runTest {
        every { settingsRepository.isFilterEnabled() } returns flowOf(true)
        every { normalizer.normalize(any()) } returns "+15551234567"
        coEvery { contactsRepository.isKnownContact("+15551234567") } returns false
        every { settingsRepository.getRequiredAttemptCount() } returns flowOf(1)
        coEvery { callAttemptRepository.getAttemptCount("+15551234567") } returns 3

        val result = useCase("+15551234567")

        assertEquals(CallDecision.Allow, result)
        coVerify(exactly = 0) { callAttemptRepository.recordAttempt(any()) }
        coVerify(exactly = 0) { callLogRepository.logBlockedCall(any(), any()) }
    }

    @Test
    fun `invoke should return DisallowSilently and record attempt on first call from unknown number`() =
        runTest {
            every { settingsRepository.isFilterEnabled() } returns flowOf(true)
            every { normalizer.normalize(any()) } returns "+15551234567"
            coEvery { contactsRepository.isKnownContact("+15551234567") } returns false
            every { settingsRepository.getRequiredAttemptCount() } returns flowOf(1)
            coEvery { callAttemptRepository.getAttemptCount("+15551234567") } returns 0
            coEvery { callAttemptRepository.recordAttempt("+15551234567") } returns Unit
            coEvery { callLogRepository.logBlockedCall(any(), any()) } returns Unit

            val result = useCase("+15551234567")

            assertEquals(CallDecision.DisallowSilently, result)
            coVerify(exactly = 1) { callAttemptRepository.recordAttempt("+15551234567") }
            coVerify(exactly = 1) {
                callLogRepository.logBlockedCall("+15551234567", BlockReason.ATTEMPT_THRESHOLD)
            }
        }

    @Test
    fun `invoke should return DisallowSilently when attempts are below custom threshold`() =
        runTest {
            every { settingsRepository.isFilterEnabled() } returns flowOf(true)
            every { normalizer.normalize(any()) } returns "+15551234567"
            coEvery { contactsRepository.isKnownContact("+15551234567") } returns false
            every { settingsRepository.getRequiredAttemptCount() } returns flowOf(2)
            coEvery { callAttemptRepository.getAttemptCount("+15551234567") } returns 1
            coEvery { callAttemptRepository.recordAttempt("+15551234567") } returns Unit
            coEvery { callLogRepository.logBlockedCall(any(), any()) } returns Unit

            val result = useCase("+15551234567")

            assertEquals(CallDecision.DisallowSilently, result)
            coVerify(exactly = 1) { callAttemptRepository.recordAttempt("+15551234567") }
            coVerify(exactly = 1) {
                callLogRepository.logBlockedCall("+15551234567", BlockReason.ATTEMPT_THRESHOLD)
            }
        }

    @Test
    fun `invoke should return Allow when attempts reach custom threshold of 2`() = runTest {
        every { settingsRepository.isFilterEnabled() } returns flowOf(true)
        every { normalizer.normalize(any()) } returns "+15551234567"
        coEvery { contactsRepository.isKnownContact("+15551234567") } returns false
        every { settingsRepository.getRequiredAttemptCount() } returns flowOf(2)
        coEvery { callAttemptRepository.getAttemptCount("+15551234567") } returns 2

        val result = useCase("+15551234567")

        assertEquals(CallDecision.Allow, result)
        coVerify(exactly = 0) { callAttemptRepository.recordAttempt(any()) }
        coVerify(exactly = 0) { callLogRepository.logBlockedCall(any(), any()) }
    }

    @Test
    fun `invoke should use normalized number for all repository calls`() = runTest {
        val rawNumber = "5551234567"
        val normalized = "+15551234567"
        every { settingsRepository.isFilterEnabled() } returns flowOf(true)
        every { normalizer.normalize(rawNumber) } returns normalized
        coEvery { contactsRepository.isKnownContact(normalized) } returns false
        every { settingsRepository.getRequiredAttemptCount() } returns flowOf(1)
        coEvery { callAttemptRepository.getAttemptCount(normalized) } returns 0
        coEvery { callAttemptRepository.recordAttempt(normalized) } returns Unit
        coEvery { callLogRepository.logBlockedCall(any(), any()) } returns Unit

        useCase(rawNumber)

        coVerify(exactly = 1) { contactsRepository.isKnownContact(normalized) }
        coVerify(exactly = 1) { callAttemptRepository.getAttemptCount(normalized) }
        coVerify(exactly = 1) { callAttemptRepository.recordAttempt(normalized) }
        coVerify(exactly = 1) { callLogRepository.logBlockedCall(normalized, any()) }
    }

    // ─── CallLogRepository wiring (F2) ───────────────────────────────────────

    @Test
    fun `invoke should log block with ATTEMPT_THRESHOLD reason when call is blocked on first attempt`() =
        runTest {
            every { settingsRepository.isFilterEnabled() } returns flowOf(true)
            every { normalizer.normalize(any()) } returns "+15551234567"
            coEvery { contactsRepository.isKnownContact("+15551234567") } returns false
            every { settingsRepository.getRequiredAttemptCount() } returns flowOf(1)
            coEvery { callAttemptRepository.getAttemptCount("+15551234567") } returns 0
            coEvery { callAttemptRepository.recordAttempt(any()) } returns Unit
            coEvery { callLogRepository.logBlockedCall(any(), any()) } returns Unit

            useCase("+15551234567")

            coVerify(exactly = 1) {
                callLogRepository.logBlockedCall("+15551234567", BlockReason.ATTEMPT_THRESHOLD)
            }
        }

    @Test
    fun `invoke should not call logBlockedCall when number belongs to a contact`() = runTest {
        every { settingsRepository.isFilterEnabled() } returns flowOf(true)
        every { normalizer.normalize(any()) } returns "+15551234567"
        coEvery { contactsRepository.isKnownContact("+15551234567") } returns true

        useCase("+15551234567")

        coVerify(exactly = 0) { callLogRepository.logBlockedCall(any(), any()) }
    }

    @Test
    fun `invoke should call logBlockedCall exactly once not twice when DisallowSilently`() =
        runTest {
            every { settingsRepository.isFilterEnabled() } returns flowOf(true)
            every { normalizer.normalize(any()) } returns "+15551234567"
            coEvery { contactsRepository.isKnownContact("+15551234567") } returns false
            every { settingsRepository.getRequiredAttemptCount() } returns flowOf(3)
            coEvery { callAttemptRepository.getAttemptCount("+15551234567") } returns 0
            coEvery { callAttemptRepository.recordAttempt(any()) } returns Unit
            coEvery { callLogRepository.logBlockedCall(any(), any()) } returns Unit

            useCase("+15551234567")

            coVerify(exactly = 1) { callLogRepository.logBlockedCall(any(), any()) }
        }
}
