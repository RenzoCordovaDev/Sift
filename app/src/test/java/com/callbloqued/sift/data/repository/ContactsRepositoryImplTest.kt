package com.callbloqued.sift.data.repository

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.ContactsContract
import android.util.Log
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.Runs
import io.mockk.unmockkAll
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

/**
 * Unit tests for [ContactsRepositoryImpl].
 *
 * Validates the four outcomes of [ContactsRepositoryImpl.isKnownContact]:
 * - [ContentResolver.query] returns a cursor with at least one row → `true` (contact found).
 * - [ContentResolver.query] returns a cursor with zero rows → `false`.
 * - [ContentResolver.query] returns a null cursor → `false` (OEM quirk).
 * - [ContentResolver.query] throws [SecurityException] (READ_CONTACTS not granted) → `true`
 *   (fail-safe: treats caller as a known contact to honour CLAUDE.md rule 1 —
 *   never block a number that is in the device contacts).
 *
 * **Setup note**: [ContactsContract.PhoneLookup.CONTENT_FILTER_URI] is `null` in the Android SDK
 * unit-test stub JAR because the stub does not execute the field's runtime initialiser. If left
 * null, `isKnownContact` throws [NullPointerException] before reaching [ContentResolver.query],
 * making the tests impossible to run. To fix this without modifying production code, [BeforeAll]
 * uses [sun.misc.Unsafe] to inject a MockK stub into the static final field. The stub's
 * [Uri.buildUpon] / [Uri.Builder.appendPath] / [Uri.Builder.build] chain is wired to return a
 * stable mock so every test exercises the full method body.
 *
 * [TestInstance.Lifecycle.PER_CLASS] ensures [mockLookupUri] and [mockUriBuilder] are the same
 * object across all tests (the static field holds a reference, so it must not change between runs).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ContactsRepositoryImplTest {

    private val mockLookupUri: Uri = mockk(name = "contentFilterUri")
    private val mockUriBuilder: Uri.Builder = mockk(name = "uriBuilder")

    private lateinit var mockContentResolver: ContentResolver
    private lateinit var mockContext: Context
    private lateinit var repository: ContactsRepositoryImpl

    @BeforeAll
    fun injectMockContentFilterUri() {
        val field = ContactsContract.PhoneLookup::class.java
            .getDeclaredField("CONTENT_FILTER_URI")

        val unsafeClass = Class.forName("sun.misc.Unsafe")
        val theUnsafeField = unsafeClass.getDeclaredField("theUnsafe")
            .also { it.isAccessible = true }
        val unsafe = theUnsafeField.get(null)

        val staticFieldBase = unsafeClass.getMethod(
            "staticFieldBase", java.lang.reflect.Field::class.java
        )
        val staticFieldOffset = unsafeClass.getMethod(
            "staticFieldOffset", java.lang.reflect.Field::class.java
        )
        val putObject = unsafeClass.getMethod(
            "putObject", Any::class.java, Long::class.javaPrimitiveType, Any::class.java
        )

        val base = staticFieldBase.invoke(unsafe, field)
        val offset = staticFieldOffset.invoke(unsafe, field) as Long
        putObject.invoke(unsafe, base, offset, mockLookupUri)

        every { mockLookupUri.buildUpon() } returns mockUriBuilder
        every { mockUriBuilder.appendPath(any<String>()) } returns mockUriBuilder
        every { mockUriBuilder.build() } returns mockLookupUri

        mockkStatic("android.util.Log")
        every { Log.w(any<String>(), any<String>(), any<Throwable>()) } returns 0
    }

    @BeforeEach
    fun setUp() {
        mockContentResolver = mockk()
        mockContext = mockk {
            every { contentResolver } returns mockContentResolver
        }
        repository = ContactsRepositoryImpl(mockContext)
    }

    @AfterEach
    fun tearDown() {
        clearMocks(mockContentResolver, mockContext)
    }

    @AfterAll
    fun cleanUp() {
        unmockkAll()
    }

    @Test
    fun `isKnownContact should return true when cursor has at least one row`() = runTest {
        val mockCursor: Cursor = mockk {
            every { count } returns 1
            every { close() } just Runs
        }
        every { mockContentResolver.query(any(), any(), any(), any(), any()) } returns mockCursor

        val result = repository.isKnownContact("+15551234567")

        assertTrue(result)
    }

    @Test
    fun `isKnownContact should return true when cursor has multiple rows`() = runTest {
        val mockCursor: Cursor = mockk {
            every { count } returns 3
            every { close() } just Runs
        }
        every { mockContentResolver.query(any(), any(), any(), any(), any()) } returns mockCursor

        val result = repository.isKnownContact("+15551234567")

        assertTrue(result)
    }

    @Test
    fun `isKnownContact should return false when cursor has zero rows`() = runTest {
        val mockCursor: Cursor = mockk {
            every { count } returns 0
            every { close() } just Runs
        }
        every { mockContentResolver.query(any(), any(), any(), any(), any()) } returns mockCursor

        val result = repository.isKnownContact("+15551234567")

        assertFalse(result)
    }

    @Test
    fun `isKnownContact should return false when cursor is null`() = runTest {
        every { mockContentResolver.query(any(), any(), any(), any(), any()) } returns null

        val result = repository.isKnownContact("+15551234567")

        assertFalse(result)
    }

    @Test
    fun `isKnownContact should return true when query throws SecurityException`() = runTest {
        every {
            mockContentResolver.query(any(), any(), any(), any(), any())
        } throws SecurityException("READ_CONTACTS permission not granted")

        val result = repository.isKnownContact("+15551234567")

        assertTrue(result)
    }

    @Test
    fun `isKnownContact should return true when SecurityException has no message`() = runTest {
        every {
            mockContentResolver.query(any(), any(), any(), any(), any())
        } throws SecurityException()

        val result = repository.isKnownContact("+15551234567")

        assertTrue(result)
    }
}
