package com.callbloqued.sift.data.local.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented smoke test for [AppDatabase].
 *
 * Verifies that Room can build an in-memory instance of [AppDatabase] and that the
 * database opens and closes cleanly. Uses [Room.inMemoryDatabaseBuilder] so no persistent
 * file is written to the device.
 *
 * This test lives in the [androidTest] source set because [Room.inMemoryDatabaseBuilder]
 * requires an Android [Context] that is not available in local JVM unit tests.
 *
 * Prerequisites to run this test:
 * - A connected Android device or running emulator (API 29+).
 * - Execute via `./gradlew connectedDebugAndroidTest` (NOT `./gradlew test`).
 *
 * Note for mobile-dev: if Room migration tests are needed in future phases, add
 * `androidTestImplementation(libs.room.testing)` to build.gradle.kts and declare
 * `room-testing` in `gradle/libs.versions.toml`.
 */
@RunWith(AndroidJUnit4::class)
class AppDatabaseSmokeTest {

    private lateinit var database: AppDatabase

    @Before
    fun `build in-memory database before each test`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun `close database after each test`() {
        if (::database.isInitialized && database.isOpen) {
            database.close()
        }
    }

    @Test
    fun `build should open database in-memory without error`() {
        assertNotNull(database)
        assertTrue(database.isOpen)
    }

    @Test
    fun `close should close the database cleanly`() {
        database.close()
        assertTrue(!database.isOpen)
    }
}
