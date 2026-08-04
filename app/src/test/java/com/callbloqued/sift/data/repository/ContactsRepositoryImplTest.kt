package com.callbloqued.sift.data.repository

import com.callbloqued.sift.data.local.contacts.ContactsContentResolverGateway
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Unit tests for [ContactsRepositoryImpl].
 *
 * Validates the three outcomes of [ContactsRepositoryImpl.isKnownContact], mocking
 * [ContactsContentResolverGateway] directly so no real Android `ContactsContract` static state
 * needs to be touched (that gateway is itself verified separately, on-device):
 * - The gateway finds at least one match → `true`.
 * - The gateway finds zero matches → `false`.
 * - The gateway throws [SecurityException] (READ_CONTACTS not granted) → `true` (fail-safe:
 *   treats the caller as a known contact to honour CLAUDE.md rule 1 — never block a number
 *   that is in the device contacts).
 */
class ContactsRepositoryImplTest {

    private val gateway: ContactsContentResolverGateway = mockk()
    private val repository = ContactsRepositoryImpl(gateway)

    @BeforeEach
    fun setUp() {
        mockkStatic(android.util.Log::class)
        every { android.util.Log.w(any<String>(), any<String>(), any<Throwable>()) } returns 0
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(android.util.Log::class)
    }

    @Test
    fun `isKnownContact should return true when gateway finds one match`() = runTest {
        every { gateway.countMatchingContacts("+15551234567") } returns 1

        val result = repository.isKnownContact("+15551234567")

        assertTrue(result)
    }

    @Test
    fun `isKnownContact should return true when gateway finds multiple matches`() = runTest {
        every { gateway.countMatchingContacts("+15551234567") } returns 3

        val result = repository.isKnownContact("+15551234567")

        assertTrue(result)
    }

    @Test
    fun `isKnownContact should return false when gateway finds no match`() = runTest {
        every { gateway.countMatchingContacts("+15551234567") } returns 0

        val result = repository.isKnownContact("+15551234567")

        assertFalse(result)
    }

    @Test
    fun `isKnownContact should return true when gateway throws SecurityException`() = runTest {
        every {
            gateway.countMatchingContacts("+15551234567")
        } throws SecurityException("READ_CONTACTS permission not granted")

        val result = repository.isKnownContact("+15551234567")

        assertTrue(result)
    }

    @Test
    fun `isKnownContact should return true when SecurityException has no message`() = runTest {
        every { gateway.countMatchingContacts("+15551234567") } throws SecurityException()

        val result = repository.isKnownContact("+15551234567")

        assertTrue(result)
    }
}
