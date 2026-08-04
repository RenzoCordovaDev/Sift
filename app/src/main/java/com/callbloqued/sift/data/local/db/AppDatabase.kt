package com.callbloqued.sift.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Root Room database for Sift.
 *
 * **Schema history:**
 * - v1 (F0): contained only [PlaceholderEntity] as a temporary KSP compilation scaffold.
 * - v2 (F1): replaces the placeholder with [CallAttemptEntity], which records blocked-call
 *   history and drives the configurable "allow on Nth attempt" rule.
 * - v3 (F2): adds [BlockedCallLogEntity], which records individual blocked-call events for
 *   user-visible history. [CallAttemptEntity] is unchanged.
 * - v4 (F3): adds [ManualListEntity], which stores the user's manual blacklist and whitelist.
 *   Neither [CallAttemptEntity] nor [BlockedCallLogEntity] is affected.
 *
 * Schema export is enabled so that migration SQL can be reviewed and validated in CI.
 * Exported JSON schema files are committed under `app/schemas/` in version control.
 *
 * The singleton instance is provided by [com.callbloqued.sift.core.di.DatabaseModule].
 * Abstract DAO accessor functions are the single access point for entity persistence.
 */
@Database(
    entities = [CallAttemptEntity::class, BlockedCallLogEntity::class, ManualListEntity::class],
    version = 4,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    /**
     * Returns the DAO for reading and writing [CallAttemptEntity] records.
     *
     * @return The Room-generated [CallAttemptDao] implementation.
     */
    abstract fun callAttemptDao(): CallAttemptDao

    /**
     * Returns the DAO for inserting and observing [BlockedCallLogEntity] records.
     *
     * @return The Room-generated [BlockedCallLogDao] implementation.
     */
    abstract fun blockedCallLogDao(): BlockedCallLogDao

    /**
     * Returns the DAO for reading and writing [ManualListEntity] records.
     *
     * @return The Room-generated [ManualListDao] implementation.
     */
    abstract fun manualListDao(): ManualListDao

    /**
     * Holds Room migration objects shared between [com.callbloqued.sift.core.di.DatabaseModule]
     * and test harnesses that build in-memory databases with the same migration path.
     */
    companion object {

        /**
         * Migration from database version 1 (F0 placeholder) to version 2 (F1 real entities).
         *
         * Drops the temporary `placeholder` table introduced in F0 solely to satisfy Room's
         * requirement for a non-empty `entities` list. Creates the `call_attempts` table that
         * backs [CallAttemptEntity] and drives the call-screening history logic.
         */
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS `placeholder`")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `call_attempts` (
                        `phone_number` TEXT NOT NULL,
                        `first_attempt_at` INTEGER NOT NULL,
                        `last_attempt_at` INTEGER NOT NULL,
                        `attempt_count` INTEGER NOT NULL,
                        PRIMARY KEY(`phone_number`)
                    )
                    """.trimIndent()
                )
            }
        }

        /**
         * Migration from database version 2 (F1) to version 3 (F2).
         *
         * Adds the `blocked_call_log` table that backs [BlockedCallLogEntity]. Each row
         * represents one individual silent-block event. [CallAttemptEntity] is not affected.
         *
         * The `reason` column stores a [com.callbloqued.sift.domain.model.BlockReason] enum
         * value by name so that new reasons added in later phases do not require an additional
         * migration.
         */
        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `blocked_call_log` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `phone_number` TEXT NOT NULL,
                        `timestamp` INTEGER NOT NULL,
                        `reason` TEXT NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        /**
         * Migration from database version 3 (F2) to version 4 (F3).
         *
         * Adds the `manual_list` table that backs [ManualListEntity]. [CallAttemptEntity] and
         * [BlockedCallLogEntity] are not affected.
         *
         * The `list_type` column stores a [com.callbloqued.sift.domain.model.ManualListType]
         * enum value by name (e.g. `"BLACKLIST"`) so that future enum extensions do not require
         * an additional migration. The `phone_number` column is the sole primary key, enforcing
         * mutual exclusivity between the blacklist and whitelist at the database level.
         */
        val MIGRATION_3_4: Migration = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `manual_list` (
                        `phone_number` TEXT NOT NULL,
                        `list_type` TEXT NOT NULL,
                        `added_at` INTEGER NOT NULL,
                        PRIMARY KEY(`phone_number`)
                    )
                    """.trimIndent()
                )
            }
        }
    }
}
