package com.callbloqued.sift.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Root Room database for Sift.
 *
 * Currently contains only [PlaceholderEntity] — a temporary F0 scaffold required
 * because Room's KSP processor rejects an empty `entities` list at compile time.
 * Real entities ([CallAttemptEntity], [ManualListEntity]) replace the placeholder
 * in F2 (attempt history) and F3 (manual lists) once their schemas are finalised.
 *
 * Schema export is enabled so that migration files can be validated in CI.
 * Exported JSON schema files are committed to `app/schemas/` in version control.
 *
 * Singleton instance is provided by [com.callbloqued.sift.core.di.DatabaseModule].
 */
@Database(
    entities = [PlaceholderEntity::class],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase()
