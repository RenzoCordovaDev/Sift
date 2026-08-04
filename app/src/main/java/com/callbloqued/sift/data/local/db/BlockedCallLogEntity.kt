package com.callbloqued.sift.data.local.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity that records a single blocked-call event.
 *
 * Unlike [CallAttemptEntity] — which is an aggregate per caller number used to drive the
 * "allow on Nth attempt" decision — each row in this table corresponds to one individual
 * silent-block event. The table grows monotonically; no rows are updated or deleted by
 * normal operation.
 *
 * Numbers are stored in E.164 format after normalisation by
 * [com.callbloqued.sift.domain.util.PhoneNumberNormalizer].
 *
 * The [reason] column stores the [com.callbloqued.sift.domain.model.BlockReason] enum
 * value by name (e.g. `"ATTEMPT_THRESHOLD"`). Storing by name rather than ordinal ensures
 * that reordering or inserting new enum entries in a later phase does not corrupt
 * existing records.
 *
 * Timestamps are Unix epoch milliseconds ([System.currentTimeMillis]).
 */
@Entity(tableName = "blocked_call_log")
data class BlockedCallLogEntity(

    /**
     * Auto-generated surrogate primary key. The value 0 signals Room to generate a new id
     * on insert (Room treats `autoGenerate = true` with value 0 as "assign me a new id").
     */
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    /**
     * Phone number in E.164 format of the caller that was blocked.
     */
    @ColumnInfo(name = "phone_number")
    val phoneNumber: String,

    /**
     * Timestamp (epoch ms) when the block event was recorded.
     */
    @ColumnInfo(name = "timestamp")
    val timestamp: Long,

    /**
     * Name of the [com.callbloqued.sift.domain.model.BlockReason] enum value that describes
     * why this call was blocked (e.g. `"ATTEMPT_THRESHOLD"`).
     */
    @ColumnInfo(name = "reason")
    val reason: String
)
