package com.callbloqued.sift.data.repository

import com.callbloqued.sift.data.local.db.CallAttemptDao
import com.callbloqued.sift.data.local.db.CallAttemptEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Unit tests for [CallAttemptRepositoryImpl].
 *
 * Covers both public operations:
 *
 * [CallAttemptRepositoryImpl.getAttemptCount]:
 * - No existing record → returns 0.
 * - Existing record → returns the stored [CallAttemptEntity.attemptCount].
 *
 * [CallAttemptRepositoryImpl.recordAttempt]:
 * - No prior record → inserts a new entity with [CallAttemptEntity.attemptCount] = 1 and
 *   equal [CallAttemptEntity.firstAttemptAt] / [CallAttemptEntity.lastAttemptAt].
 * - Prior record exists → updates the entity, increments [CallAttemptEntity.attemptCount]
 *   by one, refreshes [CallAttemptEntity.lastAttemptAt], and preserves
 *   [CallAttemptEntity.firstAttemptAt].
 *
 * [CallAttemptDao] is mocked with MockK; no Room runtime is required.
 */
class CallAttemptRepositoryImplTest {

    private lateinit var callAttemptDao: CallAttemptDao
    private lateinit var repository: CallAttemptRepositoryImpl

    @BeforeEach
    fun setUp() {
        callAttemptDao = mockk()
        repository = CallAttemptRepositoryImpl(callAttemptDao)
    }

    // ─── getAttemptCount ─────────────────────────────────────────────────────

    @Test
    fun `getAttemptCount should return 0 when no record exists for phone number`() = runTest {
        coEvery { callAttemptDao.findByPhoneNumber("+15551234567") } returns null

        val result = repository.getAttemptCount("+15551234567")

        assertEquals(0, result)
    }

    @Test
    fun `getAttemptCount should return stored attemptCount when record exists`() = runTest {
        val entity = CallAttemptEntity(
            phoneNumber = "+15551234567",
            firstAttemptAt = 1_000L,
            lastAttemptAt = 2_000L,
            attemptCount = 3
        )
        coEvery { callAttemptDao.findByPhoneNumber("+15551234567") } returns entity

        val result = repository.getAttemptCount("+15551234567")

        assertEquals(3, result)
    }

    @Test
    fun `getAttemptCount should return 1 when record has attemptCount of 1`() = runTest {
        val entity = CallAttemptEntity(
            phoneNumber = "+15551234567",
            firstAttemptAt = 1_000L,
            lastAttemptAt = 1_000L,
            attemptCount = 1
        )
        coEvery { callAttemptDao.findByPhoneNumber("+15551234567") } returns entity

        val result = repository.getAttemptCount("+15551234567")

        assertEquals(1, result)
    }

    // ─── recordAttempt ───────────────────────────────────────────────────────

    @Test
    fun `recordAttempt should insert new entity with attemptCount 1 when no prior record exists`() =
        runTest {
            coEvery { callAttemptDao.findByPhoneNumber("+15551234567") } returns null
            val insertedSlot = slot<CallAttemptEntity>()
            coEvery { callAttemptDao.insert(capture(insertedSlot)) } returns Unit

            repository.recordAttempt("+15551234567")

            val inserted = insertedSlot.captured
            assertEquals("+15551234567", inserted.phoneNumber)
            assertEquals(1, inserted.attemptCount)
            assertEquals(inserted.firstAttemptAt, inserted.lastAttemptAt)
        }

    @Test
    fun `recordAttempt should increment attemptCount when prior record exists`() = runTest {
        val existing = CallAttemptEntity(
            phoneNumber = "+15551234567",
            firstAttemptAt = 1_000L,
            lastAttemptAt = 2_000L,
            attemptCount = 1
        )
        coEvery { callAttemptDao.findByPhoneNumber("+15551234567") } returns existing
        val updatedSlot = slot<CallAttemptEntity>()
        coEvery { callAttemptDao.update(capture(updatedSlot)) } returns Unit

        repository.recordAttempt("+15551234567")

        val updated = updatedSlot.captured
        assertEquals(2, updated.attemptCount)
    }

    @Test
    fun `recordAttempt should preserve firstAttemptAt when updating existing record`() = runTest {
        val existing = CallAttemptEntity(
            phoneNumber = "+15551234567",
            firstAttemptAt = 1_000L,
            lastAttemptAt = 2_000L,
            attemptCount = 1
        )
        coEvery { callAttemptDao.findByPhoneNumber("+15551234567") } returns existing
        val updatedSlot = slot<CallAttemptEntity>()
        coEvery { callAttemptDao.update(capture(updatedSlot)) } returns Unit

        repository.recordAttempt("+15551234567")

        assertEquals(1_000L, updatedSlot.captured.firstAttemptAt)
    }

    @Test
    fun `recordAttempt should update lastAttemptAt to a newer time when updating existing record`() =
        runTest {
            val existing = CallAttemptEntity(
                phoneNumber = "+15551234567",
                firstAttemptAt = 1_000L,
                lastAttemptAt = 2_000L,
                attemptCount = 1
            )
            coEvery { callAttemptDao.findByPhoneNumber("+15551234567") } returns existing
            val updatedSlot = slot<CallAttemptEntity>()
            coEvery { callAttemptDao.update(capture(updatedSlot)) } returns Unit

            val beforeCall = System.currentTimeMillis()
            repository.recordAttempt("+15551234567")
            val afterCall = System.currentTimeMillis()

            val lastAttemptAt = updatedSlot.captured.lastAttemptAt
            assert(lastAttemptAt >= beforeCall) {
                "lastAttemptAt ($lastAttemptAt) should be >= beforeCall ($beforeCall)"
            }
            assert(lastAttemptAt <= afterCall) {
                "lastAttemptAt ($lastAttemptAt) should be <= afterCall ($afterCall)"
            }
        }

    @Test
    fun `recordAttempt should call insert not update when no prior record exists`() = runTest {
        coEvery { callAttemptDao.findByPhoneNumber("+15551234567") } returns null
        coEvery { callAttemptDao.insert(any()) } returns Unit

        repository.recordAttempt("+15551234567")

        coVerify(exactly = 1) { callAttemptDao.insert(any()) }
        coVerify(exactly = 0) { callAttemptDao.update(any()) }
    }

    @Test
    fun `recordAttempt should call update not insert when prior record exists`() = runTest {
        val existing = CallAttemptEntity(
            phoneNumber = "+15551234567",
            firstAttemptAt = 1_000L,
            lastAttemptAt = 2_000L,
            attemptCount = 2
        )
        coEvery { callAttemptDao.findByPhoneNumber("+15551234567") } returns existing
        coEvery { callAttemptDao.update(any()) } returns Unit

        repository.recordAttempt("+15551234567")

        coVerify(exactly = 1) { callAttemptDao.update(any()) }
        coVerify(exactly = 0) { callAttemptDao.insert(any()) }
    }
}
