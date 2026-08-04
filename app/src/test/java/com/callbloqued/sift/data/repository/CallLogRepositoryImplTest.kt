package com.callbloqued.sift.data.repository

import app.cash.turbine.test
import com.callbloqued.sift.data.local.db.BlockedCallLogDao
import com.callbloqued.sift.data.local.db.BlockedCallLogEntity
import com.callbloqued.sift.domain.model.BlockReason
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Unit tests for [CallLogRepositoryImpl].
 *
 * Covers all public operations:
 *
 * [CallLogRepositoryImpl.logBlockedCall]:
 * - Inserts an entity with the correct phone number.
 * - Stores [BlockReason] as its enum [Enum.name] string so future values do not require a
 *   migration.
 * - Sends id = 0 so Room auto-generates the primary key.
 * - Records the current system timestamp at the moment of insertion.
 * - Delegates to the DAO exactly once.
 *
 * [CallLogRepositoryImpl.observeBlockedCallLog]:
 * - Maps every entity field to the corresponding [com.callbloqued.sift.domain.model.BlockedCallLogEntry]
 *   domain model field.
 * - Degrades unknown reason strings (written by a newer app version) to
 *   [BlockReason.ATTEMPT_THRESHOLD] to prevent crashes on forward-compatibility scenarios.
 * - Handles empty entity lists without errors.
 * - Preserves the emission order supplied by the DAO [kotlinx.coroutines.flow.Flow].
 * - Re-emits an updated list whenever the DAO Flow emits a new value.
 *
 * [BlockedCallLogDao] is mocked with MockK; no Room runtime or Android context is required.
 */
class CallLogRepositoryImplTest {

    private lateinit var blockedCallLogDao: BlockedCallLogDao
    private lateinit var repository: CallLogRepositoryImpl

    @BeforeEach
    fun setUp() {
        blockedCallLogDao = mockk()
        repository = CallLogRepositoryImpl(blockedCallLogDao)
    }

    // ─── logBlockedCall ──────────────────────────────────────────────────────

    @Test
    fun `logBlockedCall should insert entity with correct phoneNumber when called`() = runTest {
        val entitySlot = slot<BlockedCallLogEntity>()
        coEvery { blockedCallLogDao.insert(capture(entitySlot)) } returns Unit

        repository.logBlockedCall("+15551234567", BlockReason.ATTEMPT_THRESHOLD)

        assertEquals("+15551234567", entitySlot.captured.phoneNumber)
    }

    @Test
    fun `logBlockedCall should insert entity with ATTEMPT_THRESHOLD name when reason is ATTEMPT_THRESHOLD`() =
        runTest {
            val entitySlot = slot<BlockedCallLogEntity>()
            coEvery { blockedCallLogDao.insert(capture(entitySlot)) } returns Unit

            repository.logBlockedCall("+15551234567", BlockReason.ATTEMPT_THRESHOLD)

            assertEquals("ATTEMPT_THRESHOLD", entitySlot.captured.reason)
        }

    @Test
    fun `logBlockedCall should insert entity with id 0 to trigger Room autoGenerate`() = runTest {
        val entitySlot = slot<BlockedCallLogEntity>()
        coEvery { blockedCallLogDao.insert(capture(entitySlot)) } returns Unit

        repository.logBlockedCall("+15551234567", BlockReason.ATTEMPT_THRESHOLD)

        assertEquals(0L, entitySlot.captured.id)
    }

    @Test
    fun `logBlockedCall should insert entity with timestamp within current time boundaries`() =
        runTest {
            val entitySlot = slot<BlockedCallLogEntity>()
            coEvery { blockedCallLogDao.insert(capture(entitySlot)) } returns Unit

            val before = System.currentTimeMillis()
            repository.logBlockedCall("+15551234567", BlockReason.ATTEMPT_THRESHOLD)
            val after = System.currentTimeMillis()

            val timestamp = entitySlot.captured.timestamp
            assert(timestamp in before..after) {
                "timestamp ($timestamp) should be within [$before, $after]"
            }
        }

    @Test
    fun `logBlockedCall should call dao insert exactly once when invoked`() = runTest {
        coEvery { blockedCallLogDao.insert(any()) } returns Unit

        repository.logBlockedCall("+15551234567", BlockReason.ATTEMPT_THRESHOLD)

        coVerify(exactly = 1) { blockedCallLogDao.insert(any()) }
    }

    @Test
    fun `logBlockedCall should insert entity with different phoneNumber when different number is given`() =
        runTest {
            val entitySlot = slot<BlockedCallLogEntity>()
            coEvery { blockedCallLogDao.insert(capture(entitySlot)) } returns Unit

            repository.logBlockedCall("+449876543210", BlockReason.ATTEMPT_THRESHOLD)

            assertEquals("+449876543210", entitySlot.captured.phoneNumber)
        }

    // ─── observeBlockedCallLog ───────────────────────────────────────────────

    @Test
    fun `observeBlockedCallLog should map entity fields to domain model correctly`() = runTest {
        val entity = BlockedCallLogEntity(
            id = 42L,
            phoneNumber = "+15551234567",
            timestamp = 1_000_000L,
            reason = "ATTEMPT_THRESHOLD"
        )
        every { blockedCallLogDao.observeAll() } returns flowOf(listOf(entity))

        repository.observeBlockedCallLog().test {
            val entries = awaitItem()
            assertEquals(1, entries.size)
            val entry = entries[0]
            assertEquals(42L, entry.id)
            assertEquals("+15551234567", entry.phoneNumber)
            assertEquals(1_000_000L, entry.timestamp)
            assertEquals(BlockReason.ATTEMPT_THRESHOLD, entry.reason)
            awaitComplete()
        }
    }

    @Test
    fun `observeBlockedCallLog should degrade unknown reason string to ATTEMPT_THRESHOLD`() =
        runTest {
            val entity = BlockedCallLogEntity(
                id = 1L,
                phoneNumber = "+15559876543",
                timestamp = 2_000_000L,
                reason = "FUTURE_UNKNOWN_REASON"
            )
            every { blockedCallLogDao.observeAll() } returns flowOf(listOf(entity))

            repository.observeBlockedCallLog().test {
                val entries = awaitItem()
                assertEquals(1, entries.size)
                assertEquals(BlockReason.ATTEMPT_THRESHOLD, entries[0].reason)
                awaitComplete()
            }
        }

    @Test
    fun `observeBlockedCallLog should degrade empty string reason to ATTEMPT_THRESHOLD`() =
        runTest {
            val entity = BlockedCallLogEntity(
                id = 3L,
                phoneNumber = "+15551111111",
                timestamp = 3_000_000L,
                reason = ""
            )
            every { blockedCallLogDao.observeAll() } returns flowOf(listOf(entity))

            repository.observeBlockedCallLog().test {
                val entries = awaitItem()
                assertEquals(BlockReason.ATTEMPT_THRESHOLD, entries[0].reason)
                awaitComplete()
            }
        }

    @Test
    fun `observeBlockedCallLog should return empty list when dao emits empty list`() = runTest {
        every { blockedCallLogDao.observeAll() } returns flowOf(emptyList())

        repository.observeBlockedCallLog().test {
            val entries = awaitItem()
            assertEquals(0, entries.size)
            awaitComplete()
        }
    }

    @Test
    fun `observeBlockedCallLog should map multiple entities preserving emission order`() = runTest {
        val entities = listOf(
            BlockedCallLogEntity(
                id = 10L,
                phoneNumber = "+15551111111",
                timestamp = 3_000L,
                reason = "ATTEMPT_THRESHOLD"
            ),
            BlockedCallLogEntity(
                id = 11L,
                phoneNumber = "+15552222222",
                timestamp = 2_000L,
                reason = "ATTEMPT_THRESHOLD"
            ),
            BlockedCallLogEntity(
                id = 12L,
                phoneNumber = "+15553333333",
                timestamp = 1_000L,
                reason = "ATTEMPT_THRESHOLD"
            )
        )
        every { blockedCallLogDao.observeAll() } returns flowOf(entities)

        repository.observeBlockedCallLog().test {
            val entries = awaitItem()
            assertEquals(3, entries.size)
            assertEquals(10L, entries[0].id)
            assertEquals("+15551111111", entries[0].phoneNumber)
            assertEquals(11L, entries[1].id)
            assertEquals("+15552222222", entries[1].phoneNumber)
            assertEquals(12L, entries[2].id)
            assertEquals("+15553333333", entries[2].phoneNumber)
            awaitComplete()
        }
    }

    @Test
    fun `observeBlockedCallLog should emit updated list when dao flow emits a new value`() =
        runTest {
            val firstEmission = listOf(
                BlockedCallLogEntity(
                    id = 1L,
                    phoneNumber = "+15551234567",
                    timestamp = 1_000L,
                    reason = "ATTEMPT_THRESHOLD"
                )
            )
            val secondEmission = listOf(
                BlockedCallLogEntity(
                    id = 2L,
                    phoneNumber = "+15559876543",
                    timestamp = 2_000L,
                    reason = "ATTEMPT_THRESHOLD"
                ),
                BlockedCallLogEntity(
                    id = 1L,
                    phoneNumber = "+15551234567",
                    timestamp = 1_000L,
                    reason = "ATTEMPT_THRESHOLD"
                )
            )
            every { blockedCallLogDao.observeAll() } returns flow {
                emit(firstEmission)
                emit(secondEmission)
            }

            repository.observeBlockedCallLog().test {
                val first = awaitItem()
                assertEquals(1, first.size)
                assertEquals(1L, first[0].id)

                val second = awaitItem()
                assertEquals(2, second.size)
                assertEquals(2L, second[0].id)
                assertEquals(1L, second[1].id)

                awaitComplete()
            }
        }

    @Test
    fun `observeBlockedCallLog should preserve id and phoneNumber for each entry in list`() =
        runTest {
            val entity = BlockedCallLogEntity(
                id = 99L,
                phoneNumber = "+44987654321",
                timestamp = 5_000_000L,
                reason = "ATTEMPT_THRESHOLD"
            )
            every { blockedCallLogDao.observeAll() } returns flowOf(listOf(entity))

            repository.observeBlockedCallLog().test {
                val entries = awaitItem()
                assertEquals(99L, entries[0].id)
                assertEquals("+44987654321", entries[0].phoneNumber)
                assertEquals(5_000_000L, entries[0].timestamp)
                awaitComplete()
            }
        }
}
