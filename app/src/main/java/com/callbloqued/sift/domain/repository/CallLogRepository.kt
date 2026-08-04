package com.callbloqued.sift.domain.repository

import com.callbloqued.sift.domain.model.BlockReason
import com.callbloqued.sift.domain.model.BlockedCallLogEntry
import kotlinx.coroutines.flow.Flow

/**
 * Contract for persisting and observing the blocked-call event log.
 *
 * Each call that is silently blocked by [com.callbloqued.sift.domain.usecase.EvaluateIncomingCallUseCase]
 * produces exactly one [BlockedCallLogEntry]. This is distinct from
 * [CallAttemptRepository], which maintains an aggregate record per caller used for the
 * "allow on Nth attempt" decision logic.
 *
 * Implementation ([com.callbloqued.sift.data.repository.CallLogRepositoryImpl]) lives in the
 * `data` layer using Room. This interface has no Android imports, keeping `domain`
 * framework-agnostic.
 *
 * Delivered in F2 (attempt history). The UI layer (F4) observes [observeBlockedCallLog]
 * to display the history screen.
 */
interface CallLogRepository {

    /**
     * Persists a single blocked-call event for the given phone number.
     *
     * Called by [com.callbloqued.sift.domain.usecase.EvaluateIncomingCallUseCase] every time
     * a call results in [com.callbloqued.sift.domain.model.CallDecision.DisallowSilently].
     * The timestamp is recorded at the time of insertion.
     *
     * @param phoneNumber The blocked caller's phone number in E.164 format.
     * @param reason The reason the call was blocked (see [BlockReason]).
     */
    suspend fun logBlockedCall(phoneNumber: String, reason: BlockReason)

    /**
     * Returns a [Flow] that emits the full blocked-call log ordered by most-recent first.
     *
     * The flow stays active and emits a new list whenever a new entry is inserted, making
     * it suitable for direct collection by a ViewModel without manual refresh logic.
     *
     * @return A hot [Flow] of [BlockedCallLogEntry] lists, ordered by timestamp descending.
     */
    fun observeBlockedCallLog(): Flow<List<BlockedCallLogEntry>>
}
