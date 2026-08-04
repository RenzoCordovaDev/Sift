package com.callbloqued.sift.domain.model

/**
 * Represents a single blocked-call event in the user-visible call history.
 *
 * Unlike [com.callbloqued.sift.data.local.db.CallAttemptEntity], which is an aggregate
 * record per phone number used by the screening decision logic, this model represents an
 * individual event: every silent block produces exactly one [BlockedCallLogEntry].
 *
 * Instances are produced by [com.callbloqued.sift.domain.repository.CallLogRepository]
 * and consumed by the UI layer (F4) without any Android or Room dependencies.
 *
 * @property id Auto-generated unique identifier for this log entry.
 * @property phoneNumber The blocked caller's phone number in E.164 format.
 * @property timestamp Unix epoch milliseconds when the block occurred.
 * @property reason The reason the call was blocked (see [BlockReason]).
 */
data class BlockedCallLogEntry(
    val id: Long,
    val phoneNumber: String,
    val timestamp: Long,
    val reason: BlockReason
)
