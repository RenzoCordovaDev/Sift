package com.callbloqued.sift.data.local.db

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

/**
 * Smoke tests for [PlaceholderEntity].
 *
 * This entity is temporary F0 scaffold (see its KDoc) with no real business logic; these
 * tests only lock in its default-value and equality contract so it doesn't regress silently
 * before it's deleted in F2/F3.
 */
class PlaceholderEntityTest {

    @Test
    fun `id should default to zero when not specified`() {
        val entity = PlaceholderEntity()

        assertEquals(0, entity.id)
    }

    @Test
    fun `equals should return true for entities with the same id`() {
        assertEquals(PlaceholderEntity(id = 1), PlaceholderEntity(id = 1))
    }

    @Test
    fun `equals should return false for entities with different ids`() {
        assertNotEquals(PlaceholderEntity(id = 1), PlaceholderEntity(id = 2))
    }

    @Test
    fun `copy should override only the requested property`() {
        val original = PlaceholderEntity(id = 1)

        val copy = original.copy(id = 2)

        assertEquals(2, copy.id)
    }
}
