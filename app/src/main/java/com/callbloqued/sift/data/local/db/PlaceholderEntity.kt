package com.callbloqued.sift.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Temporary F0 placeholder entity required to satisfy Room's KSP processor.
 *
 * Room's annotation processor rejects `@Database(entities = [])` at compile time.
 * This entity provides a minimal, schema-valid table so the database module compiles
 * during the F0 setup phase.
 *
 * **Removal:** this entity and its corresponding table must be deleted once the first
 * real entity ([CallAttemptEntity] in F2 or [ManualListEntity] in F3) is introduced.
 * Do not persist any application data here.
 */
@Entity(tableName = "placeholder")
data class PlaceholderEntity(
    /** Sole primary key; always 0 for this placeholder table. */
    @PrimaryKey val id: Int = 0
)
