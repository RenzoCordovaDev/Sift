package com.callbloqued.sift.data.local.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Persists the blocking history for a single phone number.
 *
 * Each row represents one distinct caller that was blocked at least once. The entity tracks
 * when the number first attempted to call, when it last attempted, and the cumulative count
 * of blocked attempts. This information drives the configurable "allow on Nth attempt" rule
 * implemented in [com.callbloqued.sift.domain.usecase.EvaluateIncomingCallUseCase].
 *
 * Numbers are stored in E.164 format (e.g. "+15551234567") after normalisation by
 * [com.callbloqued.sift.domain.util.PhoneNumberNormalizer], so the primary key is globally
 * unambiguous across different number representations.
 *
 * Timestamps are Unix epoch milliseconds ([System.currentTimeMillis]).
 */
@Entity(tableName = "call_attempts")
data class CallAttemptEntity(

    /**
     * Phone number in E.164 format. Acts as the primary key; one row per unique caller.
     */
    @PrimaryKey
    @ColumnInfo(name = "phone_number")
    val phoneNumber: String,

    /**
     * Timestamp (epoch ms) of the very first blocked attempt from this number.
     * Set once on insert and never updated for subsequent attempts.
     */
    @ColumnInfo(name = "first_attempt_at")
    val firstAttemptAt: Long,

    /**
     * Timestamp (epoch ms) of the most recent blocked attempt from this number.
     * Updated on every call to [com.callbloqued.sift.data.repository.CallAttemptRepositoryImpl.recordAttempt].
     */
    @ColumnInfo(name = "last_attempt_at")
    val lastAttemptAt: Long,

    /**
     * Total number of times this number has been blocked silently.
     * Compared against the user-configured threshold to decide when to allow the caller through.
     */
    @ColumnInfo(name = "attempt_count")
    val attemptCount: Int
)
