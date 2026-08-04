package com.callbloqued.sift.data.repository

import app.cash.turbine.test
import com.callbloqued.sift.data.local.db.ManualListDao
import com.callbloqued.sift.data.local.db.ManualListEntity
import com.callbloqued.sift.domain.model.ManualListType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Unit tests for [ManualListRepositoryImpl].
 *
 * Covers all public operations defined in [com.callbloqued.sift.domain.repository.ManualListRepository]:
 *
 * [ManualListRepositoryImpl.isBlacklisted]:
 * - Returns true when [ManualListDao.findByNumberAndType] returns a non-null entity for BLACKLIST.
 * - Returns false when [ManualListDao.findByNumberAndType] returns null for BLACKLIST.
 *
 * [ManualListRepositoryImpl.isWhitelisted]:
 * - Returns true when [ManualListDao.findByNumberAndType] returns a non-null entity for WHITELIST.
 * - Returns false when [ManualListDao.findByNumberAndType] returns null for WHITELIST.
 *
 * [ManualListRepositoryImpl.addToList]:
 * - Calls [ManualListDao.upsert] with the correct phone number and list type name.
 * - Stores [ManualListType] as its [Enum.name] so future values do not require a schema migration.
 * - Records a timestamp within the current wall-clock window.
 * - Replaces an existing entry when the phone number already belongs to the opposite list
 *   (the replacement itself is handled by Room's [androidx.room.OnConflictStrategy.REPLACE];
 *   the repository's responsibility is to always call [ManualListDao.upsert] with the new type).
 *
 * [ManualListRepositoryImpl.removeFromList]:
 * - Delegates to [ManualListDao.delete] with the correct phone number.
 * - Does not throw when the entry does not exist (silent no-op backed by SQL DELETE semantics).
 *
 * [ManualListRepositoryImpl.observeList]:
 * - Maps every [ManualListEntity] field to the corresponding [com.callbloqued.sift.domain.model.ManualListEntry] field.
 * - Excludes entries whose [ManualListEntity.listType] string cannot be parsed as a known
 *   [ManualListType] (forward-compatibility degradation via [mapNotNull]).
 * - Handles empty entity lists without errors.
 * - Re-emits an updated list whenever the DAO [kotlinx.coroutines.flow.Flow] emits a new value.
 * - Queries the DAO using the correct list type name string.
 *
 * [ManualListDao] is mocked with MockK; no Room runtime or Android context is required.
 */
class ManualListRepositoryImplTest {

    private lateinit var manualListDao: ManualListDao
    private lateinit var repository: ManualListRepositoryImpl

    @BeforeEach
    fun setUp() {
        manualListDao = mockk()
        repository = ManualListRepositoryImpl(manualListDao)
    }

    // ─── isBlacklisted ────────────────────────────────────────────────────────

    @Test
    fun `isBlacklisted should return true when dao returns entity for BLACKLIST`() = runTest {
        val entity = ManualListEntity("+15551234567", "BLACKLIST", 1_000L)
        coEvery { manualListDao.findByNumberAndType("+15551234567", "BLACKLIST") } returns entity

        assertTrue(repository.isBlacklisted("+15551234567"))
    }

    @Test
    fun `isBlacklisted should return false when dao returns null for BLACKLIST`() = runTest {
        coEvery { manualListDao.findByNumberAndType("+15551234567", "BLACKLIST") } returns null

        assertFalse(repository.isBlacklisted("+15551234567"))
    }

    @Test
    fun `isBlacklisted should query dao with BLACKLIST type name when called`() = runTest {
        coEvery { manualListDao.findByNumberAndType("+15551234567", "BLACKLIST") } returns null

        repository.isBlacklisted("+15551234567")

        coVerify(exactly = 1) { manualListDao.findByNumberAndType("+15551234567", "BLACKLIST") }
    }

    // ─── isWhitelisted ────────────────────────────────────────────────────────

    @Test
    fun `isWhitelisted should return true when dao returns entity for WHITELIST`() = runTest {
        val entity = ManualListEntity("+15551234567", "WHITELIST", 1_000L)
        coEvery { manualListDao.findByNumberAndType("+15551234567", "WHITELIST") } returns entity

        assertTrue(repository.isWhitelisted("+15551234567"))
    }

    @Test
    fun `isWhitelisted should return false when dao returns null for WHITELIST`() = runTest {
        coEvery { manualListDao.findByNumberAndType("+15551234567", "WHITELIST") } returns null

        assertFalse(repository.isWhitelisted("+15551234567"))
    }

    @Test
    fun `isWhitelisted should query dao with WHITELIST type name when called`() = runTest {
        coEvery { manualListDao.findByNumberAndType("+15559876543", "WHITELIST") } returns null

        repository.isWhitelisted("+15559876543")

        coVerify(exactly = 1) { manualListDao.findByNumberAndType("+15559876543", "WHITELIST") }
    }

    // ─── addToList ────────────────────────────────────────────────────────────

    @Test
    fun `addToList should call upsert with correct phoneNumber when adding to BLACKLIST`() = runTest {
        val entitySlot = slot<ManualListEntity>()
        coEvery { manualListDao.upsert(capture(entitySlot)) } returns Unit

        repository.addToList("+15551234567", ManualListType.BLACKLIST)

        assertEquals("+15551234567", entitySlot.captured.phoneNumber)
    }

    @Test
    fun `addToList should store BLACKLIST name when adding number to blacklist`() = runTest {
        val entitySlot = slot<ManualListEntity>()
        coEvery { manualListDao.upsert(capture(entitySlot)) } returns Unit

        repository.addToList("+15551234567", ManualListType.BLACKLIST)

        assertEquals("BLACKLIST", entitySlot.captured.listType)
    }

    @Test
    fun `addToList should store WHITELIST name when adding number to whitelist`() = runTest {
        val entitySlot = slot<ManualListEntity>()
        coEvery { manualListDao.upsert(capture(entitySlot)) } returns Unit

        repository.addToList("+15551234567", ManualListType.WHITELIST)

        assertEquals("WHITELIST", entitySlot.captured.listType)
    }

    @Test
    fun `addToList should store timestamp within current time boundaries when called`() = runTest {
        val entitySlot = slot<ManualListEntity>()
        coEvery { manualListDao.upsert(capture(entitySlot)) } returns Unit

        val before = System.currentTimeMillis()
        repository.addToList("+15551234567", ManualListType.BLACKLIST)
        val after = System.currentTimeMillis()

        val addedAt = entitySlot.captured.addedAt
        assertTrue(addedAt >= before) { "addedAt ($addedAt) should be >= before ($before)" }
        assertTrue(addedAt <= after) { "addedAt ($addedAt) should be <= after ($after)" }
    }

    @Test
    fun `addToList should call upsert exactly once when invoked`() = runTest {
        coEvery { manualListDao.upsert(any()) } returns Unit

        repository.addToList("+15551234567", ManualListType.BLACKLIST)

        coVerify(exactly = 1) { manualListDao.upsert(any()) }
    }

    @Test
    fun `addToList should call upsert with new list type when replacing entry from opposite list`() =
        runTest {
            // Room's OnConflictStrategy.REPLACE handles the actual row replacement when the same
            // phone_number PK already exists. The repository's contract is to always call upsert
            // with the newly requested list type — regardless of what the previous type was.
            val entitySlot = slot<ManualListEntity>()
            coEvery { manualListDao.upsert(capture(entitySlot)) } returns Unit

            repository.addToList("+15551234567", ManualListType.BLACKLIST)

            assertEquals("+15551234567", entitySlot.captured.phoneNumber)
            assertEquals("BLACKLIST", entitySlot.captured.listType)
            coVerify(exactly = 1) { manualListDao.upsert(any()) }
        }

    // ─── removeFromList ───────────────────────────────────────────────────────

    @Test
    fun `removeFromList should call delete with correct phoneNumber when removing entry`() = runTest {
        coEvery { manualListDao.delete("+15551234567") } returns Unit

        repository.removeFromList("+15551234567")

        coVerify(exactly = 1) { manualListDao.delete("+15551234567") }
    }

    @Test
    fun `removeFromList should complete without error when entry does not exist`() = runTest {
        // SQL DELETE is a no-op when no row matches the WHERE clause;
        // the repository delegates unconditionally to the DAO.
        coEvery { manualListDao.delete("+15551234567") } returns Unit

        repository.removeFromList("+15551234567")

        coVerify(exactly = 1) { manualListDao.delete("+15551234567") }
    }

    // ─── observeList ─────────────────────────────────────────────────────────

    @Test
    fun `observeList should map entity fields to ManualListEntry domain model correctly`() = runTest {
        val entity = ManualListEntity("+15551234567", "BLACKLIST", 1_000_000L)
        every { manualListDao.observeByType("BLACKLIST") } returns flowOf(listOf(entity))

        repository.observeList(ManualListType.BLACKLIST).test {
            val entries = awaitItem()
            assertEquals(1, entries.size)
            val entry = entries[0]
            assertEquals("+15551234567", entry.phoneNumber)
            assertEquals(ManualListType.BLACKLIST, entry.listType)
            assertEquals(1_000_000L, entry.addedAt)
            awaitComplete()
        }
    }

    @Test
    fun `observeList should exclude entry with unrecognised list type string`() = runTest {
        val unknownEntity = ManualListEntity("+15551234567", "FUTURE_TYPE", 1_000L)
        val validEntity = ManualListEntity("+15559876543", "BLACKLIST", 2_000L)
        every { manualListDao.observeByType("BLACKLIST") } returns flowOf(
            listOf(unknownEntity, validEntity)
        )

        repository.observeList(ManualListType.BLACKLIST).test {
            val entries = awaitItem()
            assertEquals(1, entries.size)
            assertEquals("+15559876543", entries[0].phoneNumber)
            awaitComplete()
        }
    }

    @Test
    fun `observeList should return empty list when all entities have unrecognised type string`() =
        runTest {
            val unknown1 = ManualListEntity("+15551111111", "UNKNOWN_TYPE", 1_000L)
            val unknown2 = ManualListEntity("+15552222222", "ANOTHER_FUTURE_TYPE", 2_000L)
            every { manualListDao.observeByType("BLACKLIST") } returns flowOf(
                listOf(unknown1, unknown2)
            )

            repository.observeList(ManualListType.BLACKLIST).test {
                val entries = awaitItem()
                assertEquals(0, entries.size)
                awaitComplete()
            }
        }

    @Test
    fun `observeList should return empty list when dao emits empty list`() = runTest {
        every { manualListDao.observeByType("BLACKLIST") } returns flowOf(emptyList())

        repository.observeList(ManualListType.BLACKLIST).test {
            val entries = awaitItem()
            assertEquals(0, entries.size)
            awaitComplete()
        }
    }

    @Test
    fun `observeList should emit updated list when dao flow emits a new value`() = runTest {
        val firstEntity = ManualListEntity("+15551111111", "WHITELIST", 1_000L)
        val secondEntity = ManualListEntity("+15552222222", "WHITELIST", 2_000L)
        every { manualListDao.observeByType("WHITELIST") } returns flow {
            emit(listOf(firstEntity))
            emit(listOf(secondEntity, firstEntity))
        }

        repository.observeList(ManualListType.WHITELIST).test {
            val first = awaitItem()
            assertEquals(1, first.size)
            assertEquals("+15551111111", first[0].phoneNumber)

            val second = awaitItem()
            assertEquals(2, second.size)
            assertEquals("+15552222222", second[0].phoneNumber)
            assertEquals("+15551111111", second[1].phoneNumber)

            awaitComplete()
        }
    }

    @Test
    fun `observeList should query dao with WHITELIST type name when listType is WHITELIST`() =
        runTest {
            every { manualListDao.observeByType("WHITELIST") } returns flowOf(emptyList())

            repository.observeList(ManualListType.WHITELIST).test {
                awaitItem()
                awaitComplete()
            }

            coVerify(exactly = 1) { manualListDao.observeByType("WHITELIST") }
        }

    @Test
    fun `observeList should preserve WHITELIST entries and map listType correctly`() = runTest {
        val entity = ManualListEntity("+15559876543", "WHITELIST", 5_000_000L)
        every { manualListDao.observeByType("WHITELIST") } returns flowOf(listOf(entity))

        repository.observeList(ManualListType.WHITELIST).test {
            val entries = awaitItem()
            assertEquals(1, entries.size)
            assertEquals(ManualListType.WHITELIST, entries[0].listType)
            assertEquals("+15559876543", entries[0].phoneNumber)
            assertEquals(5_000_000L, entries[0].addedAt)
            awaitComplete()
        }
    }

    @Test
    fun `observeList should map multiple entities preserving emission order`() = runTest {
        val entities = listOf(
            ManualListEntity("+15551111111", "BLACKLIST", 3_000L),
            ManualListEntity("+15552222222", "BLACKLIST", 2_000L),
            ManualListEntity("+15553333333", "BLACKLIST", 1_000L)
        )
        every { manualListDao.observeByType("BLACKLIST") } returns flowOf(entities)

        repository.observeList(ManualListType.BLACKLIST).test {
            val entries = awaitItem()
            assertEquals(3, entries.size)
            assertEquals("+15551111111", entries[0].phoneNumber)
            assertEquals("+15552222222", entries[1].phoneNumber)
            assertEquals("+15553333333", entries[2].phoneNumber)
            awaitComplete()
        }
    }
}
