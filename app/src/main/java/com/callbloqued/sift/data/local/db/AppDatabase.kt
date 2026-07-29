package com.callbloqued.sift.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Root Room database for Sift.
 *
 * The `entities` list is intentionally empty at F0.  Real entities
 * ([CallAttemptEntity], [BlockedCallLogEntity], [ManualListEntity]) are added
 * in F2 (attempt history) and F3 (manual lists) once their schemas are finalised.
 * Room allows an empty entity list at compile time; the database will contain no
 * tables until entities are introduced.
 *
 * Schema export is enabled so that migration files can be validated in CI.
 * Exported JSON schema files are committed to `app/schemas/` in version control.
 *
 * Singleton instance is provided by [com.callbloqued.sift.core.di.DatabaseModule].
 */
@Database(
    entities = [],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase()
