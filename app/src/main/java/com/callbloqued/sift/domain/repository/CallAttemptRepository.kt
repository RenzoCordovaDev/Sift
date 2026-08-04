package com.callbloqued.sift.domain.repository

/**
 * Contract for persisting and querying call-attempt records.
 *
 * A "call attempt" is logged every time an unknown number is blocked silently on its first
 * contact.  The repository exposes the attempt count so that [EvaluateIncomingCallUseCase]
 * can decide whether a subsequent call from the same number should be allowed through.
 *
 * Implementation ([CallAttemptRepositoryImpl]) lives in the `data` layer using Room
 * ([CallAttemptEntity], [CallAttemptDao]). This interface carries no Android imports,
 * keeping `domain` framework-agnostic.
 *
 * Delivered in F1 (call-screening core).
 */
interface CallAttemptRepository {

    /**
     * Returns how many times [phoneNumber] has previously been blocked silently.
     *
     * @param phoneNumber Phone number in E.164 format.
     * @return The total number of recorded blocked attempts for this number; 0 if none.
     */
    suspend fun getAttemptCount(phoneNumber: String): Int

    /**
     * Records one blocked attempt for [phoneNumber].
     *
     * If no prior record exists for this number a new one is created; otherwise the
     * existing record's attempt count and last-seen timestamp are incremented/updated.
     *
     * @param phoneNumber Phone number in E.164 format.
     */
    suspend fun recordAttempt(phoneNumber: String)
}
